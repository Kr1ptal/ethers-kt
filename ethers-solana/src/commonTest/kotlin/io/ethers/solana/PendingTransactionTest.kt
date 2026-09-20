package io.ethers.solana

import io.ethers.core.Kotlinx
import io.ethers.providers.HttpClient
import io.ethers.solana.instruction.SystemProgram
import io.ethers.solana.providers.ConfirmationTracking
import io.ethers.solana.providers.PendingSolanaTransaction
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.providers.middleware.SolanaApi
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.rpc.Commitment
import io.ethers.solana.types.rpc.SolanaSendConfig
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
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.milliseconds
import io.ktor.client.HttpClient as KtorHttpClient

class PendingTransactionTest : FunSpec({
    val signature = SolanaSignature(ByteArray(64) { 1 })
    val blockhash = SolanaBlockhash(ByteArray(32) { 2 })

    /** Answers each method from a queue, so a poll loop can be walked one response at a time. */
    val requests = mutableListOf<String>()

    fun providerFor(answers: Map<String, MutableList<String>>, calls: MutableList<String>): SolanaProvider {
        requests.clear()
        val ktor = KtorHttpClient(
            MockEngine { request ->
                val body = Kotlinx.DEFAULT.parseToJsonElement((request.body as TextContent).text).jsonObject
                val method = body.getValue("method").jsonPrimitive.content
                calls.add(method)
                requests += body.getValue("params").toString()
                val queue = answers.getValue(method)
                val result = if (queue.size > 1) queue.removeAt(0) else queue.first()
                val response = if (result.startsWith("ERROR:")) "\"error\":${result.removePrefix("ERROR:")}" else "\"result\":$result"
                respond(
                    """{"jsonrpc":"2.0","id":${body.getValue("id")},$response}""",
                    HttpStatusCode.OK,
                    headersOf("Content-Type", "application/json"),
                )
            },
        )
        return SolanaProvider(HttpClient("https://example.invalid", ktor), defaultCommitment = Commitment.CONFIRMED)
    }

    fun status(confirmationStatus: String, err: String = "null") = """{"context":{"slot":9},"value":[{"slot":9,"confirmations":1,"err":$err,"confirmationStatus":"$confirmationStatus"}]}"""

    val unseen = """{"context":{"slot":9},"value":[null]}"""

    fun nonceAccount(slot: Int, value: Int = 2, version: Int = 1, state: Int = 1, size: Int = 80, owner: String = Programs.SYSTEM.toString()): String {
        val data = ByteArray(80)
        data[0] = version.toByte()
        data[4] = state.toByte()
        for (i in 40 until 72) data[i] = value.toByte()
        return """{"context":{"slot":$slot},"value":{"data":["${Base64.encode(data.copyOf(size))}","base64"],"executable":false,"lamports":1000000,"owner":"$owner","rentEpoch":0,"space":$size}}"""
    }

    test("polls until the transaction reaches the requested commitment") {
        val calls = mutableListOf<String>()
        val provider = providerFor(
            mapOf(
                "getSignatureStatuses" to mutableListOf(unseen, status("processed"), status("confirmed")),
                "isBlockhashValid" to mutableListOf("""{"context":{"slot":7},"value":true}"""),
            ),
            calls,
        )
        try {
            val pending = PendingSolanaTransaction(signature, provider, blockhash)
            val result = pending.confirmation(Commitment.CONFIRMED, 1.milliseconds, 5000.milliseconds).unwrap()

            result.confirmationStatus shouldBe Commitment.CONFIRMED
            result.isSuccess shouldBe true
            // The finalized bank may trail the processed status context without aborting polling.
            requests[1] shouldBe """["$blockhash",{"commitment":"confirmed"}]"""
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
                "isBlockhashValid" to mutableListOf("""{"context":{"slot":7},"value":true}""", """{"context":{"slot":9},"value":false}"""),
            ),
            calls,
        )
        try {
            val error = PendingSolanaTransaction(signature, provider, blockhash)
                .confirmation(Commitment.CONFIRMED, 1.milliseconds, 5000.milliseconds)
                .unwrapError()

            error.shouldBeInstanceOf<PendingSolanaTransaction.Error.Expired>().signature shouldBe signature
            // it gave a definite answer instead of burning the whole timeout
            calls.count { it == "getSignatureStatuses" } shouldBe 3
            requests[3] shouldBe """["$blockhash",{"commitment":"finalized","minContextSlot":7}]"""
            requests.last() shouldBe """[["$signature"],{"searchTransactionHistory":true}]"""
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
                "isBlockhashValid" to mutableListOf("""{"context":{"slot":7},"value":true}""", """{"context":{"slot":9},"value":false}"""),
            ),
            calls,
        )
        try {
            // Raw bytes use the shared envelope decoder to recover confirmation inputs.
            val pending = provider.sendTransaction(signed.serialize()).send().unwrap()
            pending.signature shouldBe signed.id

            pending.confirmation(Commitment.CONFIRMED, 1.milliseconds, 5000.milliseconds)
                .unwrapError().shouldBeInstanceOf<PendingSolanaTransaction.Error.Expired>()
            calls.count { it == "isBlockhashValid" } shouldBe 2
        } finally {
            provider.close()
        }
    }

    test("finalized context lag is retried with a fixed validity anchor") {
        val calls = mutableListOf<String>()
        val provider = providerFor(
            mapOf(
                "getSignatureStatuses" to mutableListOf(unseen, unseen.replace("9", "10"), unseen.replace("9", "11"), unseen.replace("9", "12")),
                "isBlockhashValid" to mutableListOf(
                    """{"context":{"slot":7},"value":true}""",
                    """ERROR:{"code":-32016,"message":"Minimum context slot has not been reached"}""",
                    """{"context":{"slot":9},"value":false}""",
                ),
            ),
            calls,
        )
        try {
            PendingSolanaTransaction(signature, provider, blockhash)
                .confirmation(Commitment.CONFIRMED, 1.milliseconds, 5000.milliseconds)
                .unwrapError().shouldBeInstanceOf<PendingSolanaTransaction.Error.Expired>()
            requests[3] shouldBe """["$blockhash",{"commitment":"finalized","minContextSlot":7}]"""
            requests[5] shouldBe requests[3]
        } finally {
            provider.close()
        }
    }

    test("an unobserved blockhash times out instead of being declared expired") {
        val calls = mutableListOf<String>()
        val provider = providerFor(
            mapOf(
                "getSignatureStatuses" to mutableListOf(unseen),
                "isBlockhashValid" to mutableListOf("""{"context":{"slot":9},"value":false}"""),
            ),
            calls,
        )
        try {
            PendingSolanaTransaction(signature, provider, blockhash)
                .confirmation(Commitment.CONFIRMED, 1.milliseconds, 50.milliseconds)
                .unwrapError().shouldBeInstanceOf<PendingSolanaTransaction.Error.TimedOut>()
        } finally {
            provider.close()
        }
    }

    test("expiry waits for a current history lookup and preserves an inclusion racing expiry") {
        val calls = mutableListOf<String>()
        val provider = providerFor(
            mapOf(
                "getSignatureStatuses" to mutableListOf(unseen, unseen, unseen.replace("9", "8"), status("confirmed")),
                "isBlockhashValid" to mutableListOf("""{"context":{"slot":7},"value":true}""", """{"context":{"slot":9},"value":false}"""),
            ),
            calls,
        )
        try {
            PendingSolanaTransaction(signature, provider, blockhash)
                .confirmation(Commitment.CONFIRMED, 1.milliseconds, 5000.milliseconds)
                .unwrap().confirmationStatus shouldBe Commitment.CONFIRMED
            calls.count { it == "getSignatureStatuses" } shouldBe 4
            calls.count { it == "isBlockhashValid" } shouldBe 2
            requests.last() shouldBe """[["$signature"],{"searchTransactionHistory":true}]"""
        } finally {
            provider.close()
        }
    }

    test("non-context RPC errors remain terminal") {
        val calls = mutableListOf<String>()
        val provider = providerFor(
            mapOf(
                "getSignatureStatuses" to mutableListOf(unseen),
                "isBlockhashValid" to mutableListOf("""ERROR:{"code":-32005,"message":"Node unhealthy"}"""),
            ),
            calls,
        )
        try {
            PendingSolanaTransaction(signature, provider, blockhash)
                .confirmation(Commitment.CONFIRMED, 1.milliseconds, 5000.milliseconds)
                .unwrapError().shouldBeInstanceOf<PendingSolanaTransaction.Error.Rpc>().error.code shouldBe -32005
        } finally {
            provider.close()
        }
    }

    test("typed and raw nonce submissions track the nonce account") {
        val signer = KeypairSigner.fromSeed(ByteArray(32) { 7 })
        val signed = SolanaTransactionRequest {
            feePayer(signer.publicKey)
            blockhash(blockhash)
            instruction(SystemProgram.advanceNonceAccount(Programs.SYSVAR_RENT, signer.publicKey))
        }.compileLegacy().unwrap().sign(signer)
        val calls = mutableListOf<String>()
        val provider = providerFor(
            mapOf(
                "sendTransaction" to mutableListOf("\"${signed.id}\""),
                "getAccountInfo" to mutableListOf(nonceAccount(7)),
                "getSignatureStatuses" to mutableListOf(unseen),
            ),
            calls,
        )
        try {
            val typed = provider.sendTransaction(signed).send().unwrap()
            val raw = provider.sendTransaction(signed.serialize()).send().unwrap()
            for (pending in listOf(typed, raw)) {
                pending.confirmation(Commitment.CONFIRMED, 1.milliseconds, 30.milliseconds)
                    .unwrapError().shouldBeInstanceOf<PendingSolanaTransaction.Error.TimedOut>()
            }
            calls.none { it == "isBlockhashValid" || it == "getEpochInfo" } shouldBe true
        } finally {
            provider.close()
        }
    }

    test("nonce invalidation retries finalized lag and waits for a current history lookup") {
        val calls = mutableListOf<String>()
        val provider = providerFor(
            mapOf(
                "getSignatureStatuses" to mutableListOf(unseen, unseen, unseen, """{"context":{"slot":8},"value":[null]}""", unseen),
                "getAccountInfo" to mutableListOf(nonceAccount(7), """ERROR:{"code":-32016,"message":"Minimum context slot has not been reached"}""", nonceAccount(9, value = 3)),
            ),
            calls,
        )
        try {
            val strategy = ConfirmationTracking.DurableNonce(Programs.SYSVAR_RENT, blockhash)
            PendingSolanaTransaction(signature, provider, strategy).confirmation(Commitment.CONFIRMED, 1.milliseconds, 5000.milliseconds)
                .unwrapError().shouldBeInstanceOf<PendingSolanaTransaction.Error.NonceInvalidated>().nonceAccount shouldBe Programs.SYSVAR_RENT
            calls.count { it == "getAccountInfo" } shouldBe 3
            calls.count { it == "getSignatureStatuses" } shouldBe 5
            Kotlinx.DEFAULT.parseToJsonElement(requests[1]) shouldBe Kotlinx.DEFAULT.parseToJsonElement("""["${Programs.SYSVAR_RENT}",{"encoding":"base64","commitment":"confirmed"}]""")
            Kotlinx.DEFAULT.parseToJsonElement(requests[3]) shouldBe Kotlinx.DEFAULT.parseToJsonElement("""["${Programs.SYSVAR_RENT}",{"encoding":"base64","commitment":"finalized","minContextSlot":7}]""")
            requests.last().contains("\"searchTransactionHistory\":true") shouldBe true
        } finally {
            provider.close()
        }
    }

    test("nonce advancement by the submitted transaction returns its eventual on-chain failure") {
        val calls = mutableListOf<String>()
        val provider = providerFor(
            mapOf(
                "getSignatureStatuses" to mutableListOf(unseen, unseen, status("processed"), status("finalized", "\"AccountNotFound\"")),
                "getAccountInfo" to mutableListOf(nonceAccount(7), nonceAccount(9, value = 3)),
            ),
            calls,
        )
        try {
            val strategy = ConfirmationTracking.DurableNonce(Programs.SYSVAR_RENT, blockhash)
            PendingSolanaTransaction(signature, provider, strategy).confirmation(Commitment.FINALIZED, 1.milliseconds, 5000.milliseconds)
                .unwrap().isSuccess shouldBe false
            calls.count { it == "getAccountInfo" } shouldBe 2
        } finally {
            provider.close()
        }
    }

    test("missing closed and unusable nonce accounts invalidate only with an anchor") {
        val invalidAccounts = listOf(
            """{"context":{"slot":9},"value":null}""",
            nonceAccount(9, state = 0),
            nonceAccount(9, version = 0),
            nonceAccount(9, owner = Programs.SYSVAR_RENT.toString()),
            nonceAccount(9, value = 3),
        )
        for (account in invalidAccounts) {
            for (anchored in listOf(false, true)) {
                val provider = providerFor(mapOf("getSignatureStatuses" to mutableListOf(unseen), "getAccountInfo" to (if (anchored) mutableListOf(nonceAccount(7), account) else mutableListOf(account))), mutableListOf())
                try {
                    val strategy = ConfirmationTracking.DurableNonce(Programs.SYSVAR_RENT, blockhash)
                    val result = PendingSolanaTransaction(signature, provider, strategy).confirmation(Commitment.CONFIRMED, 1.milliseconds, 100.milliseconds).unwrapError()
                    if (anchored) result.shouldBeInstanceOf<PendingSolanaTransaction.Error.NonceInvalidated>() else result.shouldBeInstanceOf<PendingSolanaTransaction.Error.TimedOut>()
                } finally {
                    provider.close()
                }
            }
        }
    }

    test("malformed nonce data and stale responses never prove invalidation") {
        for (account in listOf(nonceAccount(9, size = 40), nonceAccount(9, version = 2), nonceAccount(9, state = 2), nonceAccount(6, value = 3))) {
            val provider = providerFor(mapOf("getSignatureStatuses" to mutableListOf(unseen), "getAccountInfo" to mutableListOf(nonceAccount(7), account)), mutableListOf())
            try {
                val strategy = ConfirmationTracking.DurableNonce(Programs.SYSVAR_RENT, blockhash)
                PendingSolanaTransaction(signature, provider, strategy).confirmation(Commitment.CONFIRMED, 1.milliseconds, 100.milliseconds)
                    .unwrapError().shouldBeInstanceOf<PendingSolanaTransaction.Error.TimedOut>()
            } finally {
                provider.close()
            }
        }
    }

    test("typed send overloads never enter the raw decoding path") {
        val signer = KeypairSigner.fromSeed(ByteArray(32) { 7 })
        val tx = SolanaTransactionRequest {
            feePayer(signer.publicKey)
            blockhash(blockhash)
            instruction(SystemProgram.transfer(signer.publicKey, Programs.SYSTEM, 1L))
        }.compileLegacy().unwrap().sign(signer)
        val calls = mutableListOf<String>()
        val provider = providerFor(mapOf("sendTransaction" to mutableListOf("\"${tx.id}\"")), calls)
        val api = object : SolanaApi {
            override val client = provider.client
            override val defaultCommitment = Commitment.CONFIRMED
            override fun sendTransaction(transaction: ByteArray, options: SolanaSendConfig): Nothing = error("Typed sends must not enter the raw path")
        }
        try {
            api.sendTransaction(tx).send().unwrap().signature shouldBe tx.id
            api.sendTransaction(tx, Commitment.CONFIRMED).send().unwrap().signature shouldBe tx.id
            api.sendTransaction(tx, SolanaSendConfig()).send().unwrap().signature shouldBe tx.id
            calls shouldBe listOf("sendTransaction", "sendTransaction", "sendTransaction")
        } finally {
            provider.close()
        }
    }

    test("unknown raw envelopes automatically use status-only confirmation") {
        val calls = mutableListOf<String>()
        val provider = providerFor(
            mapOf("sendTransaction" to mutableListOf("\"$signature\""), "getSignatureStatuses" to mutableListOf(unseen)),
            calls,
        )
        try {
            provider.sendTransaction(byteArrayOf(1)).send().unwrap()
                .confirmation(Commitment.CONFIRMED, 1.milliseconds, 100.milliseconds)
                .unwrapError().shouldBeInstanceOf<PendingSolanaTransaction.Error.TimedOut>()
            calls.all { it == "sendTransaction" || it == "getSignatureStatuses" } shouldBe true
        } finally {
            provider.close()
        }
    }

    test("inclusion at the requested commitment returns without a validity check") {
        for (commitment in listOf(Commitment.PROCESSED, Commitment.CONFIRMED, Commitment.FINALIZED)) {
            val calls = mutableListOf<String>()
            val provider = providerFor(mapOf("getSignatureStatuses" to mutableListOf(status(commitment.toString()))), calls)
            try {
                PendingSolanaTransaction(signature, provider, blockhash)
                    .confirmation(commitment, 1.milliseconds, 5000.milliseconds)
                    .unwrap().confirmationStatus shouldBe commitment
                calls shouldBe listOf("getSignatureStatuses")
            } finally {
                provider.close()
            }
        }
    }

    test("confirmation timeout cancels an outstanding RPC request") {
        val ktor = KtorHttpClient(
            MockEngine {
                kotlinx.coroutines.delay(5000)
                respond("{}", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            },
        )
        val provider = SolanaProvider(HttpClient("https://example.invalid", ktor))
        try {
            PendingSolanaTransaction(signature, provider)
                .confirmation(Commitment.CONFIRMED, 1.milliseconds, 50.milliseconds)
                .unwrapError().shouldBeInstanceOf<PendingSolanaTransaction.Error.TimedOut>()
        } finally {
            provider.close()
            ktor.close()
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
