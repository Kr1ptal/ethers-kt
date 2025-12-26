package io.ethers.providers

import com.fasterxml.jackson.core.JsonParser
import io.ethers.core.isFailure
import io.ethers.json.jackson.Jackson
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import org.intellij.lang.annotations.Language
import java.util.function.Function

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
            { url -> HttpClient(url, OkHttpClient()) },
        ),
    )

    include(httpSpecificTests())
})

private fun httpSpecificTests() = funSpec {
    val stringDecoder = Function<JsonParser, String> { parser -> parser.text }

    context("HTTP-specific error handling") {
        lateinit var server: MockServer
        lateinit var client: HttpClient

        beforeEach {
            server = mockServerHttp()
            client = HttpClient(server.url, OkHttpClient())
        }

        afterEach {
            client.close()
        }

        test("HTTP error with JSON response") {
            server.enqueue(createMockResponse(RPC_ERROR_RESPONSE, 500))

            val result = client.request("eth_blockNumber", emptyArray<Any>(), stringDecoder).get()

            result.isFailure() shouldBe true
            val error = result.unwrapError()
            error.code shouldBe -32601 // Should decode the JSON error
            error.message shouldBe "Method not found"
        }

        test("HTTP error with non-JSON response") {
            server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

            val result = client.request("eth_blockNumber", emptyArray<Any>(), stringDecoder).get()

            result.isFailure() shouldBe true
            val error = result.unwrapError()
            error.code shouldBe RpcError.CODE_CALL_FAILED
            error.message shouldContain "HTTP 500"
        }

        test("empty response body") {
            server.enqueue(MockResponse().setResponseCode(200).setBody(""))

            val result = client.request("eth_blockNumber", emptyArray<Any>(), stringDecoder).get()

            result.isFailure() shouldBe true
            val error = result.unwrapError()
            error.code shouldBe RpcError.CODE_CALL_FAILED // Jackson parsing error for empty content
        }

        test("custom headers are sent") {
            server.enqueue(createMockResponse(SUCCESSFUL_RESPONSE))

            val headersMap = mapOf("Authorization" to "Bearer token123", "Custom-Header" to "value")
            val clientWithHeaders = HttpClient(server.url, OkHttpClient(), headersMap)

            clientWithHeaders.request("eth_blockNumber", emptyArray<Any>(), stringDecoder).get()

            val request = server.takeRequest()
            request.getHeader("Authorization") shouldBe "Bearer token123"
            request.getHeader("Custom-Header") shouldBe "value"
        }

        test("content-Type header is set correctly") {
            server.enqueue(createMockResponse(SUCCESSFUL_RESPONSE))

            client.request("eth_blockNumber", emptyArray<Any>(), stringDecoder).get()

            val request = server.takeRequest()
            request.getHeader("Content-Type") shouldBe "application/json"
        }

        test("unique request IDs are generated") {
            server.enqueue(createMockResponse(SUCCESSFUL_RESPONSE))
            server.enqueue(createMockResponse(SUCCESSFUL_RESPONSE))

            client.request("method1", emptyArray<Any>(), stringDecoder).get()
            client.request("method2", emptyArray<Any>(), stringDecoder).get()

            val request1 = server.takeRequest()
            val request2 = server.takeRequest()

            val body1 = Jackson.MAPPER.readTree(request1.body.readUtf8())
            val body2 = Jackson.MAPPER.readTree(request2.body.readUtf8())

            body1.get("id").asLong() shouldNotBe body2.get("id").asLong()
        }
    }

    context("Subscription tests") {
        test("subscription is not supported") {
            val httpClient = HttpClient("http://localhost:8545", OkHttpClient())
            val result = httpClient.subscribe(arrayOf("newHeads"), stringDecoder).get()

            result.isFailure() shouldBe true
            val error = result.unwrapError()
            error.code shouldBe RpcError.CODE_METHOD_NOT_FOUND
            error.message shouldContain "not supported by HTTP client"
        }
    }
}

private fun createMockResponse(body: String, code: Int = 200): MockResponse {
    return MockResponse().setResponseCode(code).setBody(body)
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
