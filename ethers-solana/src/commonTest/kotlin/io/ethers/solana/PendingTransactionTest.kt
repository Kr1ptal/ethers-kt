package io.ethers.solana

import io.ethers.core.Kotlinx
import io.ethers.providers.HttpClient
import io.ethers.solana.instruction.SystemProgram
import io.ethers.solana.providers.PendingSolanaTransaction
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.rpc.Commitment
import io.ethers.solana.types.rpc.TransactionError
import io.ethers.solana.types.transaction.SolanaTransactionRequest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.milliseconds
import io.ktor.client.HttpClient as KtorHttpClient

class PendingTransactionTest : FunSpec({
    val signature = SolanaSignature(ByteArray(64) { 1 })
    val blockhash = SolanaBlockhash(ByteArray(32) { 2 })

    /** Answers each method from a queue, so a poll loop can be walked one response at a time. */
    fun providerFor(answers: Map<String, MutableList<String>>, calls: MutableList<String>): SolanaProvider {
        val ktor = KtorHttpClient(
            MockEngine { request ->
                val body = Kotlinx.DEFAULT.parseToJsonElement((request.body as TextContent).text).jsonObject
                val method = body.getValue("method").jsonPrimitive.content
                calls.add(method)
                val queue = answers.getValue(method)
                val result = if (queue.size > 1) queue.removeAt(0) else queue.first()
                respond(
                    """{"jsonrpc":"2.0","id":${body.getValue("id")},"result":$result}""",
                    HttpStatusCode.OK,
                    headersOf("Content-Type", "application/json"),
                )
            },
        )
        return SolanaProvider(HttpClient("https://example.invalid", ktor), defaultCommitment = Commitment.CONFIRMED)
    }

    fun status(confirmationStatus: String, err: String = "null") = """{"context":{"slot":9},"value":[{"slot":9,"confirmations":1,"err":$err,"confirmationStatus":"$confirmationStatus"}]}"""

    val unseen = """{"context":{"slot":9},"value":[null]}"""

    test("polls until the transaction reaches the requested commitment") {
        val calls = mutableListOf<String>()
        val provider = providerFor(
            mapOf(
                "getSignatureStatuses" to mutableListOf(unseen, status("processed"), status("confirmed")),
                "isBlockhashValid" to mutableListOf("""{"context":{"slot":9},"value":true}"""),
            ),
            calls,
        )
        try {
            val pending = PendingSolanaTransaction(signature, provider, blockhash)
            val result = pending.confirmation(Commitment.CONFIRMED, 1.milliseconds, 5000.milliseconds).unwrap()

            result.confirmationStatus shouldBe Commitment.CONFIRMED
            result.isSuccess shouldBe true
            // processed does not satisfy confirmed, so it kept polling
            calls.count { it == "getSignatureStatuses" } shouldBe 3
        } finally {
            provider.close()
        }
    }

    test("a transaction whose blockhash has expired is reported, not waited on") {
        val calls = mutableListOf<String>()
        val provider = providerFor(
            mapOf(
                "getSignatureStatuses" to mutableListOf(unseen),
                "isBlockhashValid" to mutableListOf("""{"context":{"slot":9},"value":false}"""),
            ),
            calls,
        )
        try {
            val error = PendingSolanaTransaction(signature, provider, blockhash)
                .confirmation(Commitment.CONFIRMED, 1.milliseconds, 5000.milliseconds)
                .unwrapError()

            error.shouldBeInstanceOf<PendingSolanaTransaction.Error.Expired>().signature shouldBe signature
            // it gave a definite answer instead of burning the whole timeout
            calls.count { it == "getSignatureStatuses" } shouldBe 1
        } finally {
            provider.close()
        }
    }

    test("without a blockhash there is nothing to expire, so it waits and then times out") {
        val calls = mutableListOf<String>()
        val provider = providerFor(mapOf("getSignatureStatuses" to mutableListOf(unseen)), calls)
        try {
            val error = PendingSolanaTransaction(signature, provider)
                .confirmation(Commitment.CONFIRMED, 1.milliseconds, 50.milliseconds)
                .unwrapError()

            error.shouldBeInstanceOf<PendingSolanaTransaction.Error.TimedOut>()
            calls.none { it == "isBlockhashValid" } shouldBe true
        } finally {
            provider.close()
        }
    }

    test("a transaction that lands and then fails is returned, since it was included") {
        val calls = mutableListOf<String>()
        val provider = providerFor(
            mapOf("getSignatureStatuses" to mutableListOf(status("confirmed", """{"InstructionError":[0,"InvalidAccountData"]}"""))),
            calls,
        )
        try {
            val result = PendingSolanaTransaction(signature, provider, blockhash)
                .confirmation(Commitment.CONFIRMED, 1.milliseconds, 5000.milliseconds)
                .unwrap()

            result.isSuccess shouldBe false
            result.err.shouldBeInstanceOf<TransactionError.InstructionFailure>().instructionIndex shouldBe 0
        } finally {
            provider.close()
        }
    }

    test("finalized is only satisfied by finalized") {
        val calls = mutableListOf<String>()
        val provider = providerFor(
            mapOf(
                "getSignatureStatuses" to mutableListOf(status("confirmed"), status("finalized")),
                "isBlockhashValid" to mutableListOf("""{"context":{"slot":9},"value":true}"""),
            ),
            calls,
        )
        try {
            val result = PendingSolanaTransaction(signature, provider, blockhash)
                .confirmation(Commitment.FINALIZED, 1.milliseconds, 5000.milliseconds).unwrap()
            result.confirmationStatus shouldBe Commitment.FINALIZED
            calls.count { it == "getSignatureStatuses" } shouldBe 2
        } finally {
            provider.close()
        }
    }

    test("submitting raw bytes still detects expiry, by recovering their blockhash") {
        val signer = KeypairSigner.fromSeed(ByteArray(32) { 7 })
        val signed = SolanaTransactionRequest {
            feePayer(signer.publicKey)
            blockhash(blockhash)
            instruction(SystemProgram.transfer(signer.publicKey, Programs.SYSTEM, 1L))
        }.compileLegacy().unwrap().sign(signer)

        val calls = mutableListOf<String>()
        val provider = providerFor(
            mapOf(
                "sendTransaction" to mutableListOf("\"${signed.id}\""),
                "getSignatureStatuses" to mutableListOf(unseen),
                "isBlockhashValid" to mutableListOf("""{"context":{"slot":9},"value":false}"""),
            ),
            calls,
        )
        try {
            // the bytes carry no type, so the handle has to decode them to learn the blockhash
            val pending = provider.sendTransaction(signed.serialize()).send().unwrap()
            pending.signature shouldBe signed.id

            pending.confirmation(Commitment.CONFIRMED, 1.milliseconds, 5000.milliseconds)
                .unwrapError().shouldBeInstanceOf<PendingSolanaTransaction.Error.Expired>()
            calls.count { it == "isBlockhashValid" } shouldBe 1
        } finally {
            provider.close()
        }
    }

    test("a rejected submission never produces a handle, so there is nothing to confirm") {
        val ktor = KtorHttpClient(
            MockEngine { request ->
                val id = Kotlinx.DEFAULT.parseToJsonElement((request.body as TextContent).text).jsonObject.getValue("id")
                respond(
                    """{"jsonrpc":"2.0","id":$id,"error":{"code":-32002,"message":"Blockhash not found"}}""",
                    HttpStatusCode.OK,
                    headersOf("Content-Type", "application/json"),
                )
            },
        )
        val provider = SolanaProvider(HttpClient("https://example.invalid", ktor), defaultCommitment = Commitment.CONFIRMED)
        try {
            val signer = KeypairSigner.fromSeed(ByteArray(32) { 7 })
            val signed = SolanaTransactionRequest {
                feePayer(signer.publicKey)
                blockhash(blockhash)
                instruction(SystemProgram.transfer(signer.publicKey, Programs.SYSTEM, 1L))
            }.compileLegacy().unwrap().sign(signer)

            // the failure belongs to the send, not to confirmation: there is no transaction to track
            provider.sendTransaction(signed).send().unwrapError().code shouldBe -32002
        } finally {
            provider.close()
        }
    }
})
