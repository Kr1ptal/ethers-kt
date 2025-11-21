package io.ethers.providers

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import io.ethers.core.isSuccess
import io.ethers.json.jackson.Jackson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import okhttp3.OkHttpClient
import org.intellij.lang.annotations.Language
import java.util.concurrent.TimeUnit
import java.util.function.Function

/**
 * WsClient tests demonstrating the funSpec factory pattern for JsonRpcClient testing.
 *
 * This shows how the same factory pattern used for HttpClient can conceptually be applied
 * to WsClient, though WebSocket testing with MockWebServer has additional complexity.
 * The main demonstration is showing WebSocket-specific capabilities like subscriptions.
 */
class WsClientTest : FunSpec({
    @Suppress("MoveLambdaOutsideParentheses")
    val commonJsonRpcTests = JsonRpcTestFactory.commonTests(
        RpcClientVariant.WS,
        { url ->
            val okhttp = OkHttpClient.Builder()
                .readTimeout(50, TimeUnit.MILLISECONDS)
                .build()

            WsClient(url, okhttp)
        },
    )
    include(commonJsonRpcTests)

    context("WebSocket subscription tests") {
        lateinit var mockServer: MockWSServer
        lateinit var wsClient: WsClient

        beforeEach {
            mockServer = mockServerWebsocket()
            wsClient = WsClient(mockServer.url, OkHttpClient())
        }

        afterEach {
            wsClient.close()
        }

        test("subscription with multiple notifications") {
            val subscriptionId = "0xmulti123"

            // Pre-queue the subscription response
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"$subscriptionId"}""")

            // Give time for WebSocket connection to be established
            Thread.sleep(200)

            // Subscribe to new block headers
            val params = arrayOf("newHeads")
            val resultDecoder = Function<JsonParser, com.fasterxml.jackson.databind.JsonNode> { parser ->
                Jackson.MAPPER.readTree(parser)
            }

            val subscriptionResult = wsClient.subscribe(params, resultDecoder).get()
            subscriptionResult.isSuccess() shouldBe true

            val stream = subscriptionResult.unwrap()
            stream shouldNotBe null

            Thread.sleep(100) // Give time for subscription to be established

            // Send multiple notifications
            @Language("JSON")
            val notification1 = """
            {
                "jsonrpc": "2.0",
                "method": "eth_subscription",
                "params": {
                    "subscription": "$subscriptionId",
                    "result": {
                        "number": "0x1234",
                        "hash": "0xabcd",
                        "timestamp": "0x1111"
                    }
                }
            }
            """.trimIndent()

            @Language("JSON")
            val notification2 = """
            {
                "jsonrpc": "2.0",
                "method": "eth_subscription",
                "params": {
                    "subscription": "$subscriptionId",
                    "result": {
                        "number": "0x1235",
                        "hash": "0xefgh",
                        "timestamp": "0x2222"
                    }
                }
            }
            """.trimIndent()

            @Language("JSON")
            val notification3 = """
            {
                "jsonrpc": "2.0",
                "method": "eth_subscription",
                "params": {
                    "subscription": "$subscriptionId",
                    "result": {
                        "number": "0x1236",
                        "hash": "0xijkl",
                        "timestamp": "0x3333"
                    }
                }
            }
            """.trimIndent()

            mockServer.sendJson(notification1)
            mockServer.sendJson(notification2)
            mockServer.sendJson(notification3)
            Thread.sleep(50)

            // Verify all notifications are received in order

            // First notification
            stream.isEmpty shouldBe false
            val event1 = stream.take()!!
            event1.get("number")?.asText() shouldBe "0x1234"
            event1.get("hash")?.asText() shouldBe "0xabcd"
            event1.get("timestamp")?.asText() shouldBe "0x1111"

            // Second notification
            stream.isEmpty shouldBe false
            val event2 = stream.take()!!
            event2.get("number")?.asText() shouldBe "0x1235"
            event2.get("hash")?.asText() shouldBe "0xefgh"
            event2.get("timestamp")?.asText() shouldBe "0x2222"

            // Third notification
            stream.isEmpty shouldBe false
            val event3 = stream.take()!!
            event3.get("number")?.asText() shouldBe "0x1236"
            event3.get("hash")?.asText() shouldBe "0xijkl"
            event3.get("timestamp")?.asText() shouldBe "0x3333"
        }

        test("successful unsubscribe") {
            val subscriptionId = "0xdef456"

            // Pre-queue responses for subscribe (ID 1) and unsubscribe (ID 2)
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"$subscriptionId"}""")
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":2,"result":true}""")

            // Give time for WebSocket connection to be established
            Thread.sleep(200)

            // First create a subscription
            val params = arrayOf("newHeads")
            val resultDecoder = Function<JsonParser, JsonNode> { Jackson.MAPPER.readTree(it) }

            val subscriptionResult = wsClient.subscribe(params, resultDecoder).get()
            subscriptionResult.isSuccess() shouldBe true

            // Send multiple notifications
            @Language("JSON")
            val notification1 = """
            {
                "jsonrpc": "2.0",
                "method": "eth_subscription",
                "params": {
                    "subscription": "$subscriptionId",
                    "result": {
                        "number": "0x1234",
                        "hash": "0xabcd",
                        "timestamp": "0x1111"
                    }
                }
            }
            """.trimIndent()

            mockServer.sendJson(notification1)
            Thread.sleep(100)

            val stream = subscriptionResult.unwrap()
            stream.isEmpty shouldBe false

            val event1 = stream.take()!!
            event1.get("number")?.asText() shouldBe "0x1234"
            event1.get("hash")?.asText() shouldBe "0xabcd"
            event1.get("timestamp")?.asText() shouldBe "0x1111"

            stream.close()

            @Language("JSON")
            val notification2 = """
            {
                "jsonrpc": "2.0",
                "method": "eth_subscription",
                "params": {
                    "subscription": "$subscriptionId",
                    "result": {
                        "number": "0x1235",
                        "hash": "0xefgh",
                        "timestamp": "0x2222"
                    }
                }
            }
            """.trimIndent()

            mockServer.sendJson(notification2)
            Thread.sleep(100)

            stream.isEmpty shouldBe true
            stream.isClosed shouldBe true
        }
    }
})
