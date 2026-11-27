package io.ethers.providers

import io.ethers.core.Kotlinx
import io.ethers.core.isFailure
import io.ethers.core.isSuccess
import io.ethers.providers.SerializableValue
import io.ethers.providers.types.BatchRpcRequest
import io.ethers.providers.types.RpcCall
import io.ethers.providers.types.RpcParams
import io.ethers.providers.types.rpcParams
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.HttpClient as KtorHttpClient

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
            { url -> HttpClient(url, KtorHttpClient()) },
        ),
    )

    include(httpSpecificTests())
})

private fun httpSpecificTests() = funSpec {
    val stringSerializer = String.serializer()

    context("HTTP-specific error handling") {
        lateinit var server: MockServer
        lateinit var client: HttpClient

        beforeEach {
            server = mockServerHttp()
            client = HttpClient(server.url, KtorHttpClient())
        }

        afterEach {
            client.close()
            server.stop()
        }

        test("HTTP error with JSON response") {
            server.enqueue(500, RPC_ERROR_RESPONSE)

            val result = client.request("eth_blockNumber", RpcParams.EMPTY, stringSerializer)

            result.isFailure() shouldBe true
            val error = result.unwrapError()
            error.code shouldBe -32601 // Should decode the JSON error
            error.message shouldBe "Method not found"
        }

        test("HTTP error with non-JSON response") {
            server.enqueue(500, "Internal Server Error")

            val result = client.request("eth_blockNumber", RpcParams.EMPTY, stringSerializer)

            result.isFailure() shouldBe true
            val error = result.unwrapError()
            error.code shouldBe RpcError.CODE_CALL_FAILED
            error.message shouldContain "HTTP 500"
        }

        test("HTTP error with a batch response that leaves out a call fails that call with the HTTP error") {
            server.enqueue(500, """[{"jsonrpc":"2.0","id":2,"error":{"code":-32000,"message":"boom"}}]""")

            val batch = BatchRpcRequest(2)
            batch.addRpcCall(RpcCall(client, "eth_blockNumber", RpcParams.EMPTY, stringSerializer))
            batch.addRpcCall(RpcCall(client, "eth_chainId", RpcParams.EMPTY, stringSerializer))

            withTimeout(30.seconds) {
                val sent = batch.send()
                val missing = batch.responses[0].await().unwrapError()
                missing.code shouldBe RpcError.CODE_CALL_FAILED
                missing.message shouldContain "HTTP 500"
                batch.responses[1].await().unwrapError().message shouldBe "boom"
                sent shouldBe false
            }
        }

        test("HTTP 429 with a non-JSON body is rate limited and carries the status and Retry-After") {
            server.enqueue(429, "Too Many Requests", mapOf("Retry-After" to "7"))

            val error = client.request("eth_blockNumber", RpcParams.EMPTY, stringSerializer).unwrapError()

            error.code shouldBe RpcError.CODE_CALL_FAILED
            error.message shouldBe "HTTP 429: Too Many Requests"
            error.httpStatus shouldBe 429
            error.retryAfterMs shouldBe 7000L
            error.isHttpRateLimited shouldBe true
            error.rateLimit shouldBe RateLimit(7000L)
        }

        test("HTTP 429 with a JSON-RPC error body keeps the provider's error and adds the status and Retry-After") {
            server.enqueue(429, RATE_LIMITED_RESPONSE, mapOf("Retry-After" to "2"))

            val error = client.request("eth_blockNumber", RpcParams.EMPTY, stringSerializer).unwrapError()

            error.code shouldBe 429
            error.message shouldContain "compute units per second"
            error.httpStatus shouldBe 429
            error.retryAfterMs shouldBe 2000L
            error.rateLimit shouldBe RateLimit(2000L)
        }

        test("HTTP error without Retry-After carries the status only and is not rate limited") {
            server.enqueue(500, RPC_ERROR_RESPONSE)

            val error = client.request("eth_blockNumber", RpcParams.EMPTY, stringSerializer).unwrapError()

            error.httpStatus shouldBe 500
            error.retryAfterMs shouldBe null
            error.isHttpRateLimited shouldBe false
            error.rateLimit shouldBe null
        }

        test("JSON-RPC error with a successful HTTP status has no HTTP status") {
            server.enqueue(200, RPC_ERROR_RESPONSE, mapOf("Retry-After" to "5"))

            val error = client.request("eth_blockNumber", RpcParams.EMPTY, stringSerializer).unwrapError()

            error.code shouldBe -32601
            error.httpStatus shouldBe null
            error.retryAfterMs shouldBe null
            error.rateLimit shouldBe null
        }

        test("a rate limit in a JSON-RPC error is found by a policy that knows it, not by the default") {
            val body = """{"jsonrpc":"2.0","id":1,"error":{"code":-32000,"message":"slow down"}}"""
            val policy = RateLimitPolicy { if (it.message == "slow down") RateLimit(1500L) else null }
            val withPolicy = HttpClient(
                server.url,
                RpcClientConfig {
                    this.client = KtorHttpClient()
                    rateLimitPolicy = policy
                },
            )
            try {
                server.enqueue(200, body)
                val default = client.request("eth_blockNumber", RpcParams.EMPTY, stringSerializer).unwrapError()
                default.isRateLimited shouldBe false

                server.enqueue(200, body)
                val found = withPolicy.request("eth_blockNumber", RpcParams.EMPTY, stringSerializer).unwrapError()
                found.isRateLimited shouldBe true
                found.rateLimit shouldBe RateLimit(1500L)
                found.httpStatus shouldBe null
            } finally {
                withPolicy.close()
            }
        }

        test("a policy sees the HTTP status and Retry-After of the error it checks") {
            val policy = RateLimitPolicy { if (it.httpStatus == 503) RateLimit(it.retryAfterMs) else null }
            val withPolicy = HttpClient(server.url, KtorHttpClient(), rateLimitPolicy = policy)
            try {
                server.enqueue(503, "Service Unavailable", mapOf("Retry-After" to "6"))

                val error = withPolicy.request("eth_blockNumber", RpcParams.EMPTY, stringSerializer).unwrapError()

                error.rateLimit shouldBe RateLimit(6000L)
            } finally {
                withPolicy.close()
            }
        }

        test("a policy that throws leaves the error as it is") {
            val throwing = HttpClient(server.url, KtorHttpClient(), rateLimitPolicy = { throw IllegalStateException("bug") })
            try {
                server.enqueue(429, "Too Many Requests")

                val error = throwing.request("eth_blockNumber", RpcParams.EMPTY, stringSerializer).unwrapError()

                error.code shouldBe RpcError.CODE_CALL_FAILED
                error.httpStatus shouldBe 429
                error.rateLimit shouldBe null
            } finally {
                throwing.close()
            }
        }

        test("a batch call's JSON-RPC error with a successful HTTP status is checked with the policy") {
            val policy = RateLimitPolicy { if (it.message == "slow down") RateLimit(null) else null }
            val withPolicy = HttpClient(server.url, KtorHttpClient(), rateLimitPolicy = policy)
            try {
                server.enqueue(
                    200,
                    """[{"jsonrpc":"2.0","id":1,"result":"0x1"},{"jsonrpc":"2.0","id":2,"error":{"code":-32000,"message":"slow down"}}]""",
                )

                val batch = BatchRpcRequest(2)
                batch.addRpcCall(RpcCall(withPolicy, "eth_blockNumber", RpcParams.EMPTY, stringSerializer))
                batch.addRpcCall(RpcCall(withPolicy, "eth_chainId", RpcParams.EMPTY, stringSerializer))

                withTimeout(30.seconds) {
                    batch.send()
                    batch.responses[0].await().unwrap() shouldBe "0x1"
                    batch.responses[1].await().unwrapError().rateLimit shouldBe RateLimit(null)
                }
            } finally {
                withPolicy.close()
            }
        }

        test("HTTP 429 batch fails answered and unanswered calls with the status and Retry-After") {
            server.enqueue(
                429,
                """[{"jsonrpc":"2.0","id":2,"error":{"code":429,"message":"Too many requests"}}]""",
                mapOf("Retry-After" to "3"),
            )

            val batch = BatchRpcRequest(2)
            batch.addRpcCall(RpcCall(client, "eth_blockNumber", RpcParams.EMPTY, stringSerializer))
            batch.addRpcCall(RpcCall(client, "eth_chainId", RpcParams.EMPTY, stringSerializer))

            withTimeout(30.seconds) {
                batch.send() shouldBe false

                val missing = batch.responses[0].await().unwrapError()
                missing.code shouldBe RpcError.CODE_CALL_FAILED
                missing.httpStatus shouldBe 429
                missing.retryAfterMs shouldBe 3000L
                missing.rateLimit shouldBe RateLimit(3000L)

                val answered = batch.responses[1].await().unwrapError()
                answered.code shouldBe 429
                answered.httpStatus shouldBe 429
                answered.retryAfterMs shouldBe 3000L
                answered.rateLimit shouldBe RateLimit(3000L)
            }
        }

        test("HTTP 429 batch with a non-JSON body fails every call with the status and Retry-After") {
            server.enqueue(429, "Too Many Requests", mapOf("Retry-After" to "4"))

            val batch = BatchRpcRequest(2)
            batch.addRpcCall(RpcCall(client, "eth_blockNumber", RpcParams.EMPTY, stringSerializer))
            batch.addRpcCall(RpcCall(client, "eth_chainId", RpcParams.EMPTY, stringSerializer))

            withTimeout(30.seconds) {
                batch.send() shouldBe false
                for (response in batch.responses) {
                    val error = response.await().unwrapError()
                    error.httpStatus shouldBe 429
                    error.retryAfterMs shouldBe 4000L
                    error.rateLimit shouldBe RateLimit(4000L)
                }
            }
        }

        test("HTTP 429 thrown by a client configured with expectSuccess carries the status and Retry-After") {
            val throwingClient = HttpClient(server.url, KtorHttpClient { expectSuccess = true })
            try {
                server.enqueue(429, "Too Many Requests", mapOf("Retry-After" to "9"))

                val error = throwingClient.request("eth_blockNumber", RpcParams.EMPTY, stringSerializer).unwrapError()

                error.code shouldBe RpcError.CODE_CALL_FAILED
                error.message shouldBe "HTTP 429: Too Many Requests"
                error.httpStatus shouldBe 429
                error.retryAfterMs shouldBe 9000L
                error.rateLimit shouldBe RateLimit(9000L)
                (error.cause is ResponseException) shouldBe true
            } finally {
                throwingClient.close()
            }
        }

        test("a batch call whose params can't be encoded fails every call of the unsent batch") {
            val batch = BatchRpcRequest(2)
            val valid = batch.addRpcCall(RpcCall(client, "eth_gasPrice", RpcParams.EMPTY, stringSerializer))
            val invalid = batch.addRpcCall(RpcCall(client, "eth_getBalance", unencodableParams(), stringSerializer))

            // the encoding error reaches the sender, like a single request's, and no awaited call is left waiting
            withTimeout(5.seconds) {
                shouldThrow<IllegalArgumentException> { batch.send() }
                valid.await().unwrapError().code shouldBe RpcError.CODE_CALL_FAILED
                val error = invalid.await().unwrapError()
                error.code shouldBe RpcError.CODE_CALL_FAILED
                error.message shouldContain "eth_getBalance"
            }

            // the batch never reached the server: the next request is the first it sees
            server.enqueueJson(SUCCESSFUL_RESPONSE)
            client.request("eth_chainId", RpcParams.EMPTY, stringSerializer).isSuccess() shouldBe true
            server.takeRequest(5_000L).bodyText shouldContain "eth_chainId"
        }

        test("a batch whose params can't be encoded consumes no request IDs") {
            // the unencodable call is not the first, so IDs taken per call before encoding it would show up
            val batch = BatchRpcRequest(3)
            batch.addRpcCall(RpcCall(client, "eth_gasPrice", RpcParams.EMPTY, stringSerializer))
            batch.addRpcCall(RpcCall(client, "eth_chainId", RpcParams.EMPTY, stringSerializer))
            batch.addRpcCall(RpcCall(client, "eth_getBalance", unencodableParams(), stringSerializer))

            withTimeout(5.seconds) {
                shouldThrow<IllegalArgumentException> { batch.send() }
            }

            // a fresh client's first request ID is 1, which the next request still gets
            server.enqueueJson(SUCCESSFUL_RESPONSE)
            client.request("eth_blockNumber", RpcParams.EMPTY, stringSerializer).isSuccess() shouldBe true
            val body = Kotlinx.DEFAULT.parseToJsonElement(server.takeRequest(5_000L).bodyText).jsonObject
            body["id"]!!.jsonPrimitive.long shouldBe 1L
        }

        test("params encoded before a request, as a caching wrapper does, are not encoded again to send it") {
            val counting = CountingSerializer()
            val params = countedParams(counting, "0xabc", "latest")
            val key = params.encode().decodeToString()

            server.enqueueJson(SUCCESSFUL_RESPONSE)
            client.request("eth_getBalance", params, stringSerializer).unwrap() shouldBe "0x1234567"

            val body = Kotlinx.DEFAULT.parseToJsonElement(server.takeRequest(5_000L).bodyText).jsonObject
            body["params"].toString() shouldBe key
            counting.encodings shouldBe 1
        }

        test("a batch sends params encoded before, and a call in two batches is encoded once") {
            val counting = CountingSerializer()
            val first = RpcCall(client, "eth_getBalance", countedParams(counting, "0xa"), stringSerializer)
            val second = RpcCall(client, "eth_getCode", countedParams(counting, "0xb"), stringSerializer)
            first.params.encode()
            second.params.encode()
            counting.encodings shouldBe 2

            server.enqueueJson("""[{"jsonrpc":"2.0","id":1,"result":"0x1"},{"jsonrpc":"2.0","id":2,"result":"0x2"}]""")
            val batch = BatchRpcRequest(2)
            val firstResponse = batch.addRpcCall(first)
            val secondResponse = batch.addRpcCall(second)
            withTimeout(30.seconds) {
                batch.send() shouldBe true
                firstResponse.await().unwrap() shouldBe "0x1"
                secondResponse.await().unwrap() shouldBe "0x2"
            }
            val sent = Kotlinx.DEFAULT.parseToJsonElement(server.takeRequest(5_000L).bodyText).jsonArray
            sent.map { it.jsonObject["params"].toString() } shouldBe listOf(first.params.encode().decodeToString(), second.params.encode().decodeToString())
            counting.encodings shouldBe 2

            // the first call again, in a new batch
            server.enqueueJson("""[{"jsonrpc":"2.0","id":3,"result":"0x3"}]""")
            val again = BatchRpcRequest(1)
            val againResponse = again.addRpcCall(first)
            withTimeout(30.seconds) {
                again.send() shouldBe true
                againResponse.await().unwrap() shouldBe "0x3"
            }
            val resent = Kotlinx.DEFAULT.parseToJsonElement(server.takeRequest(5_000L).bodyText).jsonArray
            resent.single().jsonObject["params"].toString() shouldBe first.params.encode().decodeToString()
            counting.encodings shouldBe 2
        }

        test("a call sent in two batches without encoding it first is encoded once") {
            val counting = CountingSerializer()
            val call = RpcCall(client, "eth_getBalance", countedParams(counting, "0xa"), stringSerializer)

            for (id in 1..2) {
                server.enqueueJson("""[{"jsonrpc":"2.0","id":$id,"result":"0x$id"}]""")
                val batch = BatchRpcRequest(1)
                val response = batch.addRpcCall(call)
                withTimeout(30.seconds) {
                    batch.send() shouldBe true
                    response.await().unwrap() shouldBe "0x$id"
                }
                server.takeRequest(5_000L).bodyText shouldContain "\"0xa\""
            }
            counting.encodings shouldBe 1
        }

        test("empty response body") {
            server.enqueue(200, "")

            val result = client.request("eth_blockNumber", RpcParams.EMPTY, stringSerializer)

            result.isFailure() shouldBe true
            val error = result.unwrapError()
            error.code shouldBe RpcError.CODE_CALL_FAILED
        }

        test("custom headers are sent") {
            server.enqueueJson(SUCCESSFUL_RESPONSE)

            val headersMap = mapOf("Authorization" to "Bearer token123", "Custom-Header" to "value")
            val clientWithHeaders = HttpClient(server.url, KtorHttpClient(), headersMap)

            clientWithHeaders.request("eth_blockNumber", RpcParams.EMPTY, stringSerializer)

            val request = server.takeRequest()
            request.getHeader("Authorization") shouldBe "Bearer token123"
            request.getHeader("Custom-Header") shouldBe "value"
        }

        test("content-Type header is set correctly") {
            server.enqueueJson(SUCCESSFUL_RESPONSE)

            client.request("eth_blockNumber", RpcParams.EMPTY, stringSerializer)

            val request = server.takeRequest()
            request.getHeader("Content-Type") shouldContain "application/json"
        }

        test("a call object with big number and byte array fields is emitted as proper JSON") {
            server.enqueueJson(SUCCESSFUL_RESPONSE)

            client.request("eth_call", rpcParams(CALL_PARAMS, "latest"), stringSerializer)

            val body = Kotlinx.DEFAULT.parseToJsonElement(
                server.takeRequest().bodyText,
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

            val ex = try {
                client.request("eth_call", rpcParams(CustomBag(1)), stringSerializer)
                throw AssertionError("Expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                e
            }
            ex.message!! shouldContain "CustomBag"
        }

        test("request(KSerializer<T>) with ByteArray decodes a 0x-prefixed hex string") {
            server.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"0xdeadbeef"}""")

            val result = client.request("eth_getCode", RpcParams.EMPTY, HexByteArraySerializer)

            result.isSuccess() shouldBe true
            result.unwrap() shouldBe byteArrayOf(0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte())
        }

        test("request(KSerializer<T>) with ByteArray handles empty payloads (\"0x\" / \"\")") {
            server.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"0x"}""")
            val emptyHex = client.request("eth_getCode", RpcParams.EMPTY, HexByteArraySerializer)
            emptyHex.isSuccess() shouldBe true
            emptyHex.unwrap() shouldBe ByteArray(0)

            server.enqueueJson("""{"jsonrpc":"2.0","id":2,"result":""}""")
            val emptyString = client.request("eth_getCode", RpcParams.EMPTY, HexByteArraySerializer)
            emptyString.isSuccess() shouldBe true
            emptyString.unwrap() shouldBe ByteArray(0)
        }

        test("request(KSerializer<T>) with @Serializable type still uses its KSerializer") {
            server.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"0x1111111111111111111111111111111111111111"}""")

            val result = client.request("eth_coinbase", RpcParams.EMPTY, SerializableValue.serializer())

            result.isSuccess() shouldBe true
            result.unwrap() shouldBe SerializableValue("0x1111111111111111111111111111111111111111")
        }

        test("unique request IDs are generated") {
            server.enqueueJson(SUCCESSFUL_RESPONSE)
            server.enqueueJson(SUCCESSFUL_RESPONSE)

            client.request("method1", RpcParams.EMPTY, stringSerializer)
            client.request("method2", RpcParams.EMPTY, stringSerializer)

            val request1 = server.takeRequest()
            val request2 = server.takeRequest()

            val body1 = Kotlinx.DEFAULT.parseToJsonElement(request1.bodyText).jsonObject
            val body2 = Kotlinx.DEFAULT.parseToJsonElement(request2.bodyText).jsonObject

            body1["id"]!!.jsonPrimitive.long shouldNotBe body2["id"]!!.jsonPrimitive.long
        }
    }

    context("Subscription tests") {
        test("subscription is not supported") {
            val httpClient = HttpClient("http://localhost:8545", KtorHttpClient())
            val result = httpClient.subscribe(rpcParams("newHeads"), stringSerializer)

            result.isFailure() shouldBe true
            val error = result.unwrapError()
            error.code shouldBe RpcError.CODE_METHOD_NOT_FOUND
            error.message shouldContain "not supported by HTTP client"
        }
    }
}

private val SUCCESSFUL_RESPONSE = """
{
    "jsonrpc": "2.0",
    "id": 1,
    "result": "0x1234567"
}
""".trimIndent()

private val RATE_LIMITED_RESPONSE = """
{
    "jsonrpc": "2.0",
    "id": 1,
    "error": {
        "code": 429,
        "message": "Your app has exceeded its compute units per second capacity."
    }
}
""".trimIndent()

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
