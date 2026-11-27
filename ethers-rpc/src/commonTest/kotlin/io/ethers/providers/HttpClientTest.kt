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
