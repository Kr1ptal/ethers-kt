package io.ethers.solana

import io.ethers.core.Kotlinx
import io.ethers.core.isFailure
import io.ethers.providers.HttpClient
import io.ethers.providers.RpcClientConfig
import io.ethers.providers.RpcError
import io.ethers.solana.instruction.TransferInstruction
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.Blockhash
import io.ethers.solana.types.Commitment
import io.ethers.solana.types.Health
import io.ethers.solana.types.Programs
import io.ethers.solana.types.Signature
import io.ethers.solana.types.transaction.SolanaTransaction
import io.ethers.solana.types.transaction.TransactionMessage
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import io.ktor.client.HttpClient as KtorHttpClient

class SolanaProviderTest : FunSpec({
    val address = Programs.SYSTEM
    val signature = Signature(ByteArray(64) { 1 })
    val blockhash = Blockhash(ByteArray(32))
    val account = """{"data":["AQID","base64"],"executable":false,"lamports":18446744073709551615,"owner":"$address","rentEpoch":18446744073709551615}"""
    fun contextual(value: String) = """{"context":{"slot":9007199254740993,"apiVersion":"3.0.0"},"value":$value}"""
    lateinit var provider: SolanaProvider
    lateinit var ktor: KtorHttpClient
    var response = "null"
    var error: String? = null
    var status = HttpStatusCode.OK
    val requests = mutableListOf<JsonObject>()
    beforeEach {
        requests.clear()
        error = null
        status = HttpStatusCode.OK
        ktor = KtorHttpClient(
            MockEngine { request ->
                val body = Kotlinx.DEFAULT.parseToJsonElement((request.body as TextContent).text).jsonObject
                requests.add(body)
                val payload = error?.let { "\"error\":$it" } ?: "\"result\":$response"
                respond("""{"jsonrpc":"2.0","id":${body.getValue("id")},$payload}""", status, headersOf("Content-Type", "application/json"))
            },
        )
        provider = SolanaProvider(HttpClient("https://example.invalid", ktor), Commitment.CONFIRMED)
    }
    afterEach {
        provider.close()
        ktor.close()
    }
    fun assertRequest(method: String, expectedParams: String) {
        requests.last().getValue("method").jsonPrimitive.content shouldBe method
        requests.last().getValue("params") shouldBe Kotlinx.DEFAULT.parseToJsonElement(expectedParams)
    }

    test("account queries preserve u64, context, missing accounts and binary data") {
        response = contextual("18446744073709551615")
        provider.getBalance(address).send().unwrap() shouldBe BigInteger("18446744073709551615")
        assertRequest("getBalance", """["$address",{"commitment":"confirmed"}]""")
        provider.getBalanceWithContext(address, Commitment.FINALIZED).send().unwrap().context.slot shouldBe BigInteger("9007199254740993")
        assertRequest("getBalance", """["$address",{"commitment":"finalized"}]""")
        response = contextual(account)
        val info = provider.getAccountInfo(address).send().unwrap().value!!
        info.data shouldBe byteArrayOf(1, 2, 3)
        info.space shouldBe bigIntegerOf(3)
        info.lamports shouldBe BigInteger("18446744073709551615")
        assertRequest("getAccountInfo", """["$address",{"commitment":"confirmed","encoding":"base64"}]""")
        response = contextual("null")
        provider.getAccountInfo(address).send().unwrap().value shouldBe null
        response = contextual("[$account,null]")
        val multiple = provider.getMultipleAccounts(listOf(address, address)).send().unwrap().value
        multiple.size shouldBe 2
        multiple[0] shouldBe info
        multiple[1] shouldBe null
        assertRequest("getMultipleAccounts", """[["$address","$address"],{"commitment":"confirmed","encoding":"base64"}]""")
    }

    test("token, blockhash and fee queries decode their contextual results") {
        response = contextual("""{"amount":"18446744073709551615","decimals":9,"uiAmount":null,"uiAmountString":"18446744073.709551615"}""")
        provider.getTokenAccountBalance(address).send().unwrap().value.amount shouldBe BigInteger("18446744073709551615")
        assertRequest("getTokenAccountBalance", """["$address",{"commitment":"confirmed"}]""")
        provider.getTokenSupply(address).send().unwrap().value.decimals shouldBe 9
        assertRequest("getTokenSupply", """["$address",{"commitment":"confirmed"}]""")
        response = contextual("""{"blockhash":"$blockhash","lastValidBlockHeight":1000}""")
        provider.getLatestBlockhash().send().unwrap().value.lastValidBlockHeight shouldBe bigIntegerOf(1000)
        assertRequest("getLatestBlockhash", """[{"commitment":"confirmed"}]""")
        response = contextual("true")
        provider.isBlockhashValid(blockhash).send().unwrap().value shouldBe true
        assertRequest("isBlockhashValid", """["$blockhash",{"commitment":"confirmed"}]""")
        response = contextual("null")
        provider.getFeeForMessage(byteArrayOf(1, 2)).send().unwrap().value shouldBe null
        assertRequest("getFeeForMessage", """["AQI=",{"commitment":"confirmed"}]""")
        response = contextual("5000")
        provider.getFeeForMessage(byteArrayOf()).send().unwrap().value shouldBe bigIntegerOf(5000)
    }

    test("cluster, rent and prioritization queries use decimal values") {
        response = "\"ok\""
        provider.getHealth().send().unwrap() shouldBe Health.OK
        assertRequest("getHealth", "[]")
        response = """{"absoluteSlot":1,"blockHeight":2,"epoch":3,"slotIndex":4,"slotsInEpoch":5,"transactionCount":null}"""
        provider.getEpochInfo().send().unwrap().slotsInEpoch shouldBe bigIntegerOf(5)
        assertRequest("getEpochInfo", """[{"commitment":"confirmed"}]""")
        response = """{"identity":"$address"}"""
        provider.getIdentity().send().unwrap() shouldBe address
        assertRequest("getIdentity", "[]")
        response = """{"solana-core":"3.0.0","feature-set":42}"""
        provider.getVersion().send().unwrap().solanaCore shouldBe "3.0.0"
        assertRequest("getVersion", "[]")
        response = "123"
        provider.getTransactionCount().send().unwrap() shouldBe bigIntegerOf(123)
        assertRequest("getTransactionCount", """[{"commitment":"confirmed"}]""")
        provider.getMinimumBalanceForRentExemption(165).send().unwrap() shouldBe bigIntegerOf(123)
        assertRequest("getMinimumBalanceForRentExemption", """[165,{"commitment":"confirmed"}]""")
        response = """[{"slot":42,"prioritizationFee":500}]"""
        provider.getRecentPrioritizationFees(listOf(address)).send().unwrap().single().prioritizationFee shouldBe bigIntegerOf(500)
        assertRequest("getRecentPrioritizationFees", """[["$address"]]""")
    }

    test("airdrop, signature history, submission and simulation") {
        response = "\"$signature\""
        provider.requestAirdrop(address, BigInteger("18446744073709551615")).send().unwrap() shouldBe signature
        assertRequest("requestAirdrop", """["$address",18446744073709551615,{"commitment":"confirmed"}]""")
        val signer = KeypairSigner.fromSeed(ByteArray(32))
        val transaction = SolanaTransaction(TransactionMessage.compile(signer.publicKey, blockhash, TransferInstruction(signer.publicKey, address, 1L))).sign(signer)
        provider.sendTransaction(transaction).send().unwrap() shouldBe signature
        assertRequest("sendTransaction", """["${transaction.toBase64()}",{"encoding":"base64","preflightCommitment":"confirmed"}]""")
        response = contextual("""{"err":{"InstructionError":[0,"InvalidArgument"]},"logs":["failed"],"unitsConsumed":9007199254740993}""")
        val simulation = provider.simulateTransaction(transaction).send().unwrap().value
        simulation.isSuccess shouldBe false
        simulation.err shouldBe Kotlinx.DEFAULT.parseToJsonElement("""{"InstructionError":[0,"InvalidArgument"]}""")
        assertRequest("simulateTransaction", """["${transaction.toBase64()}",{"commitment":"confirmed","encoding":"base64"}]""")
        response = contextual("""{"err":null,"logs":null}""")
        provider.simulateTransaction(transaction).send().unwrap().value.isSuccess shouldBe true
        response = """[{"signature":"$signature","slot":42,"err":null,"memo":null,"blockTime":null,"confirmationStatus":"finalized"}]"""
        provider.getSignaturesForAddress(address, 10, Commitment.FINALIZED, before = signature).send().unwrap().single().isError shouldBe false
        assertRequest("getSignaturesForAddress", """["$address",{"limit":10,"commitment":"finalized","before":"$signature"}]""")
        shouldThrow<IllegalArgumentException> { provider.getSignaturesForAddress(address, 0) }
        shouldThrow<IllegalArgumentException> { provider.requestAirdrop(address, -1L) }
    }

    test("RPC errors retain structured data and HTTP does not support subscriptions") {
        error = """{"code":-32002,"message":"Transaction simulation failed","data":{"logs":["failed"]}}"""
        status = HttpStatusCode.BadRequest
        val result = provider.getHealth().send()
        result.isFailure() shouldBe true
        result.unwrapError().code shouldBe -32002
        result.unwrapError().data.toString() shouldBe """{"logs":["failed"]}"""
        provider.subscribeSlot().send().unwrapError().code shouldBe RpcError.CODE_METHOD_NOT_FOUND
    }

    test("builder performs no RPC and uses finalized by default") {
        val built = SolanaProvider.builder("https://example.invalid").config(RpcClientConfig().client(ktor)).build().unwrap()
        built.commitment shouldBe Commitment.FINALIZED
        requests.size shouldBe 0
        built.close()
        SolanaProvider.builder("ftp://example.invalid").build().isFailure() shouldBe true
    }
})
