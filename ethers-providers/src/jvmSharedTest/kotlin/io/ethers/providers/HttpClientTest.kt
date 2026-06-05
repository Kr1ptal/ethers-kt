package io.ethers.providers

import io.ethers.core.Kotlinx
import io.ethers.core.isFailure
import io.ethers.core.isSuccess
import io.ethers.core.types.Address
import io.github.artificialpb.bignum.BigInteger
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.engine.cio.CIO
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.intellij.lang.annotations.Language
import java.math.BigInteger
import io.ktor.client.HttpClient as KtorHttpClient
import kotlinx.serialization.json.JsonElement as KJsonElement

/**
 * HttpClient tests demonstrating extraction of common JsonRpcClient tests into a factory pattern.
 *
 * This shows how the `funSpec` factory pattern can be used to extract reusable test suites
 * that work across different JsonRpcClient implementations (HTTP, WebSocket, etc.).
 */
@Suppress("MoveLambdaOutsideParentheses")
class HttpClientTest : FunSpec({
    include(
        JsonRpcTestFactory.commonTests(
            RpcClientVariant.HTTP,
            { url -> HttpClient(url, KtorHttpClient(CIO)) },
        ),
    )

    include(httpSpecificTests())
})

private fun httpSpecificTests() = funSpec {
    val stringDecoder: (KJsonElement) -> String = { element -> element.jsonPrimitive.content }

    context("HTTP-specific error handling") {
        lateinit var server: MockServer
        lateinit var client: HttpClient

        beforeEach {
            server = mockServerHttp()
            client = HttpClient(server.url, KtorHttpClient(CIO))
        }

        afterEach {
            client.close()
        }

        test("HTTP error with JSON response") {
            server.enqueue(500, RPC_ERROR_RESPONSE)

            val result = client.request("eth_blockNumber", emptyArray<Any>(), stringDecoder).get()

            result.isFailure() shouldBe true
            val error = result.unwrapError()
            error.code shouldBe -32601 // Should decode the JSON error
            error.message shouldBe "Method not found"
        }

        test("HTTP error with non-JSON response") {
            server.enqueue(500, "Internal Server Error")

            val result = client.request("eth_blockNumber", emptyArray<Any>(), stringDecoder).get()

            result.isFailure() shouldBe true
            val error = result.unwrapError()
            error.code shouldBe RpcError.CODE_CALL_FAILED
            error.message shouldContain "HTTP 500"
        }

        test("empty response body") {
            server.enqueue(200, "")

            val result = client.request("eth_blockNumber", emptyArray<Any>(), stringDecoder).get()

            result.isFailure() shouldBe true
            val error = result.unwrapError()
            error.code shouldBe RpcError.CODE_CALL_FAILED
        }

        test("custom headers are sent") {
            server.enqueueJson(SUCCESSFUL_RESPONSE)

            val headersMap = mapOf("Authorization" to "Bearer token123", "Custom-Header" to "value")
            val clientWithHeaders = HttpClient(server.url, KtorHttpClient(CIO), headersMap)

            clientWithHeaders.request("eth_blockNumber", emptyArray<Any>(), stringDecoder).get()

            val request = server.takeRequest()
            request.getHeader("Authorization") shouldBe "Bearer token123"
            request.getHeader("Custom-Header") shouldBe "value"
        }

        test("content-Type header is set correctly") {
            server.enqueueJson(SUCCESSFUL_RESPONSE)

            client.request("eth_blockNumber", emptyArray<Any>(), stringDecoder).get()

            val request = server.takeRequest()
            request.getHeader("Content-Type") shouldContain "application/json"
        }

        test("complex Map / BigInteger / ByteArray params are emitted as proper JSON") {
            server.enqueue(createMockResponse(SUCCESSFUL_RESPONSE))

            val callMap = mapOf(
                "from" to Address("0x1111111111111111111111111111111111111111"),
                "to" to Address("0x2222222222222222222222222222222222222222"),
                "value" to BigInteger.ONE,
                "data" to byteArrayOf(0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte()),
                "gas" to 21000L,
            )
            client.request("eth_call", arrayOf(callMap, "latest"), stringDecoder).get()

            val body = Kotlinx.DEFAULT.parseToJsonElement(
                server.takeRequest().body.readUtf8(),
            ).jsonObject

            body["method"]!!.jsonPrimitive.content shouldBe "eth_call"

            val params = body["params"]!!.jsonArray
            params.size shouldBe 2

            val callObj = params[0].jsonObject
            callObj["from"]!!.jsonPrimitive.content shouldBe "0x1111111111111111111111111111111111111111"
            callObj["to"]!!.jsonPrimitive.content shouldBe "0x2222222222222222222222222222222222222222"
            callObj["value"]!!.jsonPrimitive.content shouldBe "1"
            callObj["data"]!!.jsonPrimitive.content shouldBe "0xdeadbeef"
            callObj["gas"]!!.jsonPrimitive.long shouldBe 21000L

            params[1].jsonPrimitive.content shouldBe "latest"
        }

        test("non-@Serializable param fails fast with a clear error") {
            class CustomBag(val x: Int)

            val ex = shouldThrow<IllegalArgumentException> {
                client.request("eth_call", arrayOf(CustomBag(1)), stringDecoder)
            }
            ex.message!! shouldContain "CustomBag"
        }

        test("request(Class<T>) with ByteArray decodes a 0x-prefixed hex string") {
            server.enqueue(
                createMockResponse(
                    """{"jsonrpc":"2.0","id":1,"result":"0xdeadbeef"}""",
                ),
            )

            val result = client.request("eth_getCode", emptyArray<Any>(), ByteArray::class.java).get()

            result.isSuccess() shouldBe true
            result.unwrap() shouldBe byteArrayOf(0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte())
        }

        test("request(Class<T>) with ByteArray handles empty payloads (\"0x\" / \"\")") {
            server.enqueue(createMockResponse("""{"jsonrpc":"2.0","id":1,"result":"0x"}"""))
            val emptyHex = client.request("eth_getCode", emptyArray<Any>(), ByteArray::class.java).get()
            emptyHex.isSuccess() shouldBe true
            emptyHex.unwrap() shouldBe ByteArray(0)

            server.enqueue(createMockResponse("""{"jsonrpc":"2.0","id":2,"result":""}"""))
            val emptyString = client.request("eth_getCode", emptyArray<Any>(), ByteArray::class.java).get()
            emptyString.isSuccess() shouldBe true
            emptyString.unwrap() shouldBe ByteArray(0)
        }

        test("request(Class<T>) with @Serializable type still uses its KSerializer") {
            server.enqueue(
                createMockResponse(
                    """{"jsonrpc":"2.0","id":1,"result":"0x1111111111111111111111111111111111111111"}""",
                ),
            )

            val result = client.request("eth_coinbase", emptyArray<Any>(), Address::class.java).get()

            result.isSuccess() shouldBe true
            result.unwrap() shouldBe Address("0x1111111111111111111111111111111111111111")
        }

        test("unique request IDs are generated") {
            server.enqueueJson(SUCCESSFUL_RESPONSE)
            server.enqueueJson(SUCCESSFUL_RESPONSE)

            client.request("method1", emptyArray<Any>(), stringDecoder).get()
            client.request("method2", emptyArray<Any>(), stringDecoder).get()

            val request1 = server.takeRequest()
            val request2 = server.takeRequest()

            val body1 = Kotlinx.DEFAULT.parseToJsonElement(request1.bodyText).jsonObject
            val body2 = Kotlinx.DEFAULT.parseToJsonElement(request2.bodyText).jsonObject

            body1["id"]!!.jsonPrimitive.long shouldNotBe body2["id"]!!.jsonPrimitive.long
        }
    }

    context("Subscription tests") {
        test("subscription is not supported") {
            val httpClient = HttpClient("http://localhost:8545", KtorHttpClient(CIO))
            val result = httpClient.subscribe(arrayOf("newHeads"), stringDecoder).get()

            result.isFailure() shouldBe true
            val error = result.unwrapError()
            error.code shouldBe RpcError.CODE_METHOD_NOT_FOUND
            error.message shouldContain "not supported by HTTP client"
        }
    }
}

@Language("JSON")
private val SUCCESSFUL_RESPONSE = """
{
    "jsonrpc": "2.0",
    "id": 1,
    "result": "0x1234567"
}
""".trimIndent()

@Language("JSON")
private val RPC_ERROR_RESPONSE = """
{
    "jsonrpc": "2.0",
    "id": 1,
    "error": {
        "code": -32601,
        "message": "Method not found"
    }
}
""".trimIndent()
