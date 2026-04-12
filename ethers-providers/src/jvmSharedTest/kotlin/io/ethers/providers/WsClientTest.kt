package io.ethers.providers

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import io.ethers.core.Jackson
import io.ethers.core.isSuccess
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import okhttp3.OkHttpClient
import org.intellij.lang.annotations.Language
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

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

            // Subscribe to new block headers
            val params = arrayOf("newHeads")
            val resultDecoder: (JsonParser) -> JsonNode = { parser ->
                Jackson.MAPPER.readTree(parser)
            }

            val subscriptionResult = wsClient.subscribe(params, resultDecoder).get()
            subscriptionResult.isSuccess() shouldBe true

            val stream = subscriptionResult.unwrap()
            stream shouldNotBe null

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

            // Verify all notifications are received in order

            // First notification
            eventually(1.seconds) {
                stream.isEmpty shouldBe false
            }
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

        test("resubscribeOnReconnect=false closes streams on reconnection") {
            val subscriptionId = "0xreconnect123"

            // Close default wsClient and prepare mock server for new connection
            wsClient.close()
            mockServer.allowReconnect()

            // Pre-queue subscription response
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"$subscriptionId"}""")

            // Create client with resubscribeOnReconnect = false
            wsClient = WsClient(
                mockServer.url,
                OkHttpClient(),
                emptyMap(),
                Jackson.MAPPER,
                resubscribeOnReconnect = false,
            )

            // Subscribe to new block headers
            val params = arrayOf("newHeads")
            val resultDecoder: (JsonParser) -> JsonNode = { Jackson.MAPPER.readTree(it) }

            val subscriptionResult = wsClient.subscribe(params, resultDecoder).get()
            subscriptionResult.isSuccess() shouldBe true

            val stream = subscriptionResult.unwrap()
            stream shouldNotBe null
            stream.isClosed shouldBe false

            // Allow reconnection (no need to queue subscription response since streams will be closed)
            mockServer.allowReconnect()

            // Close the connection from server side to trigger reconnection
            mockServer.closeConnection()

            // Stream should be closed because resubscribeOnReconnect = false
            eventually(1.seconds) {
                stream.isClosed shouldBe true
            }
        }

        test("resubscribeOnReconnect=true (default) resubscribes on reconnection") {
            val subscriptionId = "0xdefault123"
            val newSubscriptionId = "0xdefault456"

            // Close default wsClient and prepare mock server for new connection
            wsClient.close()
            mockServer.allowReconnect()

            // Pre-queue subscription response
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"$subscriptionId"}""")

            // Create client with default settings (resubscribeOnReconnect = true)
            wsClient = WsClient(mockServer.url, OkHttpClient())

            // Subscribe to new block headers
            val params = arrayOf("newHeads")
            val resultDecoder: (JsonParser) -> JsonNode = { Jackson.MAPPER.readTree(it) }

            val subscriptionResult = wsClient.subscribe(params, resultDecoder).get()
            subscriptionResult.isSuccess() shouldBe true

            val stream = subscriptionResult.unwrap()
            stream shouldNotBe null
            stream.isClosed shouldBe false

            // Allow reconnection and queue new subscription response for auto-resubscription
            mockServer.allowReconnect()
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"$newSubscriptionId"}""")

            // Close the connection from server side to trigger reconnection
            mockServer.closeConnection()

            // Verify stream is still open and receives messages after reconnection
            @Language("JSON")
            val notification = """
            {
                "jsonrpc": "2.0",
                "method": "eth_subscription",
                "params": {
                    "subscription": "$newSubscriptionId",
                    "result": {
                        "number": "0x9999",
                        "hash": "0xnew"
                    }
                }
            }
            """.trimIndent()

            eventually(1.seconds) {
                mockServer.sendJson(notification)
                stream.isClosed shouldBe false
                stream.isEmpty shouldBe false
            }
            val event = stream.take()!!
            event.get("number")?.asText() shouldBe "0x9999"
        }

        test("successful unsubscribe") {
            val subscriptionId = "0xdef456"

            // Pre-queue responses for subscribe (ID 1) and unsubscribe (ID 2)
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"$subscriptionId"}""")
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":2,"result":true}""")

            // First create a subscription
            val params = arrayOf("newHeads")
            val resultDecoder: (JsonParser) -> JsonNode = { Jackson.MAPPER.readTree(it) }

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

            val stream = subscriptionResult.unwrap()
            eventually(1.seconds) {
                stream.isEmpty shouldBe false
            }

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
