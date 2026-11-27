package io.ethers.providers

import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.CallRequest
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.client.HttpClient as KtorHttpClient

private const val FEE_HISTORY_RESPONSE = """
{
  "jsonrpc": "2.0",
  "id": %ID%,
  "result": {
    "oldestBlock": "0x1",
    "baseFeePerGas": ["0x1", "0x1"],
    "gasUsedRatio": [0.5],
    "reward": [["0x2", "0x3"]]
  }
}
"""

class ProviderFeeHistoryTest : FunSpec({
    lateinit var server: MockServer
    lateinit var client: HttpClient
    lateinit var provider: Provider

    beforeEach {
        server = mockServerHttp()
        client = HttpClient(server.url, KtorHttpClient())
        provider = Provider(client, 1L)
    }

    afterEach {
        client.close()
        server.stop()
    }

    suspend fun takeParams(): JsonArray {
        val body = Json.parseToJsonElement(server.takeRequest().bodyText).jsonObject
        return body["params"]!!.jsonArray
    }

    // JSON-RPC spec defines reward percentiles as numbers, geth/reth/Alchemy reject strings with "invalid params"
    fun JsonElement.shouldBeNumberArray(vararg expected: Double) {
        val array = jsonArray
        array.forEach { it.jsonPrimitive.isString shouldBe false }
        array.map { it.jsonPrimitive.content.toDouble() } shouldBe expected.toList()
    }

    test("BigInteger reward percentiles are sent as JSON numbers, not strings") {
        server.enqueueJson(FEE_HISTORY_RESPONSE.replace("%ID%", "0"))

        provider.getFeeHistory(10, BlockId.LATEST, listOf(bigIntegerOf(25L), bigIntegerOf(75L))).send().unwrap()

        val params = takeParams()
        params[0] shouldBe JsonPrimitive("0xa")
        params[1] shouldBe JsonPrimitive("latest")
        params[2].shouldBeNumberArray(25.0, 75.0)
    }

    test("fractional Double reward percentiles are sent as JSON numbers") {
        server.enqueueJson(FEE_HISTORY_RESPONSE.replace("%ID%", "0"))

        val result = provider.getFeeHistory(5, 100L, doubleArrayOf(12.5, 99.9)).send().unwrap()

        val params = takeParams()
        params[0] shouldBe JsonPrimitive("0x5")
        params[1] shouldBe JsonPrimitive("0x64")
        params[2].shouldBeNumberArray(12.5, 99.9)
        result.rewards shouldBe listOf(listOf(bigIntegerOf(2L), bigIntegerOf(3L)))
    }

    test("omitted reward percentiles are sent as an empty array") {
        server.enqueueJson(FEE_HISTORY_RESPONSE.replace("%ID%", "0"))

        provider.getFeeHistory(1, BlockId.Number(7L)).send().unwrap()

        takeParams()[2] shouldBe JsonArray(emptyList())
    }

    test("fillTransaction fallback sends the fee history percentile as a JSON number") {
        server.enqueueJson("""{"jsonrpc":"2.0","id":0,"error":{"code":-32601,"message":"Method not found"}}""")
        server.enqueueJson(FEE_HISTORY_RESPONSE.replace("%ID%", "1"))

        // everything except the fees is set, so fee history is the only extra request
        val call = CallRequest.Builder()
            .from(Address("0x0000000000000000000000000000000000000001"))
            .to(Address("0x0000000000000000000000000000000000000002"))
            .gas(21000L)
            .nonce(0L)
            .chainId(1L)
            .build()

        provider.fillTransaction(call).send().unwrap()

        server.takeRequest() // eth_fillTransaction
        takeParams()[2].shouldBeNumberArray(20.0)
    }
})
