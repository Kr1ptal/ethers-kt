package io.ethers.providers

import com.fasterxml.jackson.core.JsonParser
import io.ethers.core.isFailure
import io.ethers.core.isSuccess
import io.ethers.providers.types.BatchRpcRequest
import io.ethers.providers.types.RpcCall
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language

enum class RpcClientVariant {
    HTTP,
    WS,
}

/**
 * Factory for creating reusable JsonRpcClient tests that work with any JsonRpcClient implementation.
 * This allows testing common JSON-RPC behavior across different transport mechanisms (HTTP, WebSocket, etc.).
 *
 * Usage:
 * ```kotlin
 * class MyClientTest : FunSpec({
 *     include(JsonRpcClientTestFactory.commonJsonRpcTests<MyClient> {
 *         // setup and return your client instance
 *     })
 * })
 * ```
 */
object JsonRpcTestFactory {
    private val stringDecoder: (JsonParser) -> String = { parser -> parser.text }

    /**
     * Creates common JSON-RPC tests that can be used with any [JsonRpcClient] implementation.
     * This extracts the transport-agnostic testing logic that applies to all [JsonRpcClient] implementations.
     *
     * @param clientFactory Function that creates and configures a client instance for testing.
     */
    fun commonTests(
        variant: RpcClientVariant,
        clientFactory: (url: String) -> JsonRpcClient,
    ) = funSpec {
        lateinit var server: MockServer
        lateinit var client: JsonRpcClient
        beforeEach {
            server = when (variant) {
                RpcClientVariant.HTTP -> mockServerHttp()
                RpcClientVariant.WS -> mockServerWebsocket()
            }
            client = clientFactory(server.url)
        }

        afterEach {
            client.close()
        }

        context("Common JSON-RPC client tests") {
            test("successful request with result") {
                server.enqueueJson(SUCCESSFUL_RESPONSE)

                val result = client.request("eth_blockNumber", emptyArray<Any>(), stringDecoder).get()

                result.isSuccess() shouldBe true
                result.unwrap() shouldBe "0x1234567"
            }

            test("RPC error response") {
                server.enqueueJson(RPC_ERROR_RESPONSE)

                val result = client.request("eth_blockNumber", emptyArray<Any>(), stringDecoder).get()

                result.isFailure() shouldBe true
                val error = result.unwrapError()
                error.code shouldBe -32601
                error.message shouldBe "Method not found"
            }

            test("invalid JSON response") {
                server.enqueueJson("invalid json")

                val result = client.request("eth_blockNumber", emptyArray<Any>(), stringDecoder).get()

                result.isFailure() shouldBe true
                val error = result.unwrapError()
                error.code shouldBe if (variant == RpcClientVariant.WS) RpcError.CODE_CALL_TIMEOUT else RpcError.CODE_CALL_FAILED
            }

            test("missing response fields") {
                server.enqueueJson(RESPONSE_MISSING_FIELDS)

                val result = client.request("eth_blockNumber", emptyArray<Any>(), stringDecoder).get()

                result.isFailure() shouldBe true
                val error = result.unwrapError()
                error.code shouldBe RpcError.CODE_INVALID_RESPONSE
            }

            test("request with parameters") {
                server.enqueueJson(SUCCESSFUL_RESPONSE)

                val result = client.request("eth_getBalance", arrayOf("0x1234", "latest"), stringDecoder).get()

                result.isSuccess() shouldBe true
                result.unwrap() shouldBe "0x1234567"
            }
        }

        context("Common JSON-RPC batch tests") {
            test("empty batch request") {
                val batch = BatchRpcRequest(0)
                val batchResult = client.requestBatch(batch).get()
                batchResult shouldBe true // Should complete successfully even with no requests
            }

            test("mixed batch with success and error") {
                server.enqueueJson(MIXED_BATCH_RESPONSE)

                val batch = BatchRpcRequest(2)
                val call1 = RpcCall(client, "eth_blockNumber", emptyArray<Any>(), stringDecoder)
                val call2 = RpcCall(client, "invalid_method", emptyArray<Any>(), stringDecoder)
                batch.addRpcCall(call1)
                batch.addRpcCall(call2)

                val batchResult = client.requestBatch(batch).get()
                batchResult shouldBe true

                val result1 = batch.responses[0].get()
                result1.isSuccess() shouldBe true
                result1.unwrap() shouldBe "0x1234567"

                val result2 = batch.responses[1].get()
                result2.isFailure() shouldBe true
                result2.unwrapError().code shouldBe -32601
            }

            test("out-of-order batch responses") {
                server.enqueueJson(OUT_OF_ORDER_BATCH_RESPONSE)

                val batch = BatchRpcRequest(3)
                val call1 = RpcCall(client, "method1", emptyArray<Any>(), stringDecoder)
                val call2 = RpcCall(client, "method2", emptyArray<Any>(), stringDecoder)
                val call3 = RpcCall(client, "method3", emptyArray<Any>(), stringDecoder)
                batch.addRpcCall(call1)
                batch.addRpcCall(call2)
                batch.addRpcCall(call3)

                val batchResult = client.requestBatch(batch).get()
                batchResult shouldBe true

                // Verify responses are correctly matched to requests despite out-of-order response
                batch.responses[0].get().unwrap() shouldBe "result1"
                batch.responses[1].get().unwrap() shouldBe "result2"
                batch.responses[2].get().unwrap() shouldBe "result3"
            }

            test("invalid batch JSON response") {
                server.enqueueJson("invalid json")

                val batch = BatchRpcRequest(1)
                val call1 = RpcCall(client, "eth_blockNumber", emptyArray<Any>(), stringDecoder)
                batch.addRpcCall(call1)

                val batchResult = client.requestBatch(batch).get()
                batchResult shouldBe false

                batch.responses[0].get().isFailure() shouldBe true
            }

            test("single item batch") {
                server.enqueueJson(SINGLE_ITEM_BATCH_RESPONSE)

                val batch = BatchRpcRequest(1)
                val call1 = RpcCall(client, "eth_blockNumber", emptyArray<Any>(), stringDecoder)
                batch.addRpcCall(call1)

                val batchResult = client.requestBatch(batch).get()
                batchResult shouldBe true

                val result = batch.responses[0].get()
                result.isSuccess() shouldBe true
                result.unwrap() shouldBe "0x1234567"
            }

            test("batch with invalid request ID in response") {
                server.enqueueJson(BATCH_INVALID_ID_RESPONSE)

                val batch = BatchRpcRequest(1)
                val call1 = RpcCall(client, "eth_blockNumber", emptyArray<Any>(), stringDecoder)
                batch.addRpcCall(call1)

                val batchResult = client.requestBatch(batch).get()
                batchResult shouldBe false

                batch.responses[0].get().isFailure() shouldBe true
            }
        }
    }

    // Test response constants
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

    @Language("JSON")
    private val RESPONSE_MISSING_FIELDS = """
    {
        "jsonrpc": "2.0",
        "id": 1
    }
    """.trimIndent()

    @Language("JSON")
    private val MIXED_BATCH_RESPONSE = """
    [
        {
            "jsonrpc": "2.0",
            "id": 1,
            "result": "0x1234567"
        },
        {
            "jsonrpc": "2.0",
            "id": 2,
            "error": {
                "code": -32601,
                "message": "Method not found"
            }
        }
    ]
    """.trimIndent()

    @Language("JSON")
    private val OUT_OF_ORDER_BATCH_RESPONSE = """
    [
        {
            "jsonrpc": "2.0",
            "id": 3,
            "result": "result3"
        },
        {
            "jsonrpc": "2.0",
            "id": 1,
            "result": "result1"
        },
        {
            "jsonrpc": "2.0",
            "id": 2,
            "result": "result2"
        }
    ]
    """.trimIndent()

    @Language("JSON")
    private val SINGLE_ITEM_BATCH_RESPONSE = """
    [
        {
            "jsonrpc": "2.0",
            "id": 1,
            "result": "0x1234567"
        }
    ]
    """.trimIndent()

    @Language("JSON")
    private val BATCH_INVALID_ID_RESPONSE = """
    [
        {
            "jsonrpc": "2.0",
            "id": 999,
            "result": "0x1234567"
        }
    ]
    """.trimIndent()
}
