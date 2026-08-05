package io.ethers.providers

import io.ethers.core.Kotlinx
import io.ethers.core.isFailure
import io.ethers.core.isSuccess
import io.ethers.core.types.Address
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.collections.mapOf
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.HttpClient as KtorHttpClient
import kotlinx.serialization.json.JsonElement as KJsonElement

/**
 * WsClient tests demonstrating the funSpec factory pattern for JsonRpcClient testing.
 *
 * This shows how the same factory pattern used for HttpClient can conceptually be applied
 * to WsClient, backed by an embedded ktor server on loopback.
 * The main demonstration is showing WebSocket-specific capabilities like subscriptions.
 */
// Waits that span a reconnect have to allow for a full WebSocket handshake against the mock server, not just a
// local round trip - CI runners are far slower than a dev machine. `eventually` returns as soon as the condition
// holds, so a generous budget costs nothing when things are fast, it only bounds how long a genuine failure takes
// to surface.
private val RECONNECT_WINDOW = 5.seconds

class WsClientTest : FunSpec({
    @Suppress("MoveLambdaOutsideParentheses")
    val commonJsonRpcTests = JsonRpcTestFactory.commonTests(
        RpcClientVariant.WS,
        { url ->
            WsClient(url, KtorHttpClient { install(WebSockets) }, readTimeoutMs = 50L)
        },
    )
    include(commonJsonRpcTests)

    context("WebSocket request payload") {
        lateinit var mockServer: MockWSServer
        lateinit var wsClient: WsClient
        val stringDecoder: (KJsonElement) -> String = { element -> element.jsonPrimitive.content }

        beforeEach {
            mockServer = mockServerWebsocket()
            wsClient = WsClient(mockServer.url, KtorHttpClient { install(WebSockets) })
        }

        afterEach {
            wsClient.close()
        }

        test("complex Map / BigInteger / ByteArray params are emitted as proper JSON") {
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"0x1234567"}""")

            val callMap = mapOf(
                "from" to Address("0x1111111111111111111111111111111111111111"),
                "to" to Address("0x2222222222222222222222222222222222222222"),
                "value" to bigIntegerOf(1),
                "data" to byteArrayOf(0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte()),
                "gas" to 21000L,
            )
            wsClient.request("eth_call", arrayOf(callMap, "latest"), stringDecoder)

            val sentText = mockServer.takeReceivedText()!!
            val body = Kotlinx.DEFAULT.parseToJsonElement(sentText).jsonObject

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

        test("request(KSerializer<T>) with ByteArray decodes a 0x-prefixed hex string") {
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"0xdeadbeef"}""")

            val result = wsClient.request("eth_getCode", emptyArray<Any>(), HexByteArraySerializer)

            result.isSuccess() shouldBe true
            result.unwrap() shouldBe byteArrayOf(0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte())
        }

        test("subscribe(KSerializer<T>) with ByteArray decodes hex-string notification results") {
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"0xsub123"}""")

            val subscriptionResult = wsClient.subscribe(arrayOf("newPendingTransactions"), HexByteArraySerializer)
            subscriptionResult.isSuccess() shouldBe true
            val stream = subscriptionResult.unwrap()

            mockServer.sendJson(
                """{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":"0xsub123","result":"0xdeadbeef"}}""",
            )

            eventually(1.seconds) { stream.isEmpty shouldBe false }
            val payload = stream.take()!!
            payload shouldBe byteArrayOf(0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte())
        }
    }

    context("WebSocket subscription tests") {
        lateinit var mockServer: MockWSServer
        lateinit var wsClient: WsClient

        beforeEach {
            mockServer = mockServerWebsocket()
            wsClient = WsClient(mockServer.url, KtorHttpClient { install(WebSockets) })
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
            val resultDecoder: (KJsonElement) -> JsonObject = { it.jsonObject }

            val subscriptionResult = wsClient.subscribe(params, resultDecoder)
            subscriptionResult.isSuccess() shouldBe true

            val stream = subscriptionResult.unwrap()
            stream shouldNotBe null

            // Send multiple notifications
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

            // Verify all notifications are received in order.
            //
            // Each notification is awaited separately: the three frames are written independently and there is no
            // guarantee they are read and dispatched in a single batch, so asserting that later ones have already
            // arrived just because the first has is a race - one that shows up on loaded CI machines.

            // First notification
            eventually(1.seconds) {
                stream.isEmpty shouldBe false
            }
            val event1 = stream.take()!!
            event1["number"]?.jsonPrimitive?.content shouldBe "0x1234"
            event1["hash"]?.jsonPrimitive?.content shouldBe "0xabcd"
            event1["timestamp"]?.jsonPrimitive?.content shouldBe "0x1111"

            // Second notification
            eventually(1.seconds) {
                stream.isEmpty shouldBe false
            }
            val event2 = stream.take()!!
            event2["number"]?.jsonPrimitive?.content shouldBe "0x1235"
            event2["hash"]?.jsonPrimitive?.content shouldBe "0xefgh"
            event2["timestamp"]?.jsonPrimitive?.content shouldBe "0x2222"

            // Third notification
            eventually(1.seconds) {
                stream.isEmpty shouldBe false
            }
            val event3 = stream.take()!!
            event3["number"]?.jsonPrimitive?.content shouldBe "0x1236"
            event3["hash"]?.jsonPrimitive?.content shouldBe "0xijkl"
            event3["timestamp"]?.jsonPrimitive?.content shouldBe "0x3333"
        }

        test("resubscribeOnReconnect=false closes streams on reconnection") {
            val subscriptionId = "0xreconnect123"

            // Close the default wsClient; the embedded server keeps accepting, so no reconnect setup is needed
            wsClient.close()

            // Pre-queue subscription response
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"$subscriptionId"}""")

            // Create client with resubscribeOnReconnect = false
            wsClient = WsClient(
                mockServer.url,
                KtorHttpClient { install(WebSockets) },
                emptyMap(),
                resubscribeOnReconnect = false,
            )

            // Subscribe to new block headers
            val params = arrayOf("newHeads")
            val resultDecoder: (KJsonElement) -> JsonObject = { it.jsonObject }

            val subscriptionResult = wsClient.subscribe(params, resultDecoder)
            subscriptionResult.isSuccess() shouldBe true

            val stream = subscriptionResult.unwrap()
            stream shouldNotBe null
            stream.isClosed shouldBe false

            // No reconnect setup needed - the embedded server accepts connections continuously

            // Close the connection from server side to trigger reconnection
            mockServer.closeConnection()

            // Stream should be closed because resubscribeOnReconnect = false
            eventually(RECONNECT_WINDOW) {
                stream.isClosed shouldBe true
            }
        }

        test("resubscribeOnReconnect=true (default) resubscribes on reconnection") {
            val subscriptionId = "0xdefault123"
            val newSubscriptionId = "0xdefault456"

            // Close the default wsClient; the embedded server keeps accepting, so no reconnect setup is needed
            wsClient.close()

            // Pre-queue subscription response
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"$subscriptionId"}""")

            // Create client with default settings (resubscribeOnReconnect = true)
            wsClient = WsClient(mockServer.url, KtorHttpClient { install(WebSockets) })

            // Subscribe to new block headers
            val params = arrayOf("newHeads")
            val resultDecoder: (KJsonElement) -> JsonObject = { it.jsonObject }

            val subscriptionResult = wsClient.subscribe(params, resultDecoder)
            subscriptionResult.isSuccess() shouldBe true

            val stream = subscriptionResult.unwrap()
            stream shouldNotBe null
            stream.isClosed shouldBe false

            // Queue the new subscription response for auto-resubscription
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"$newSubscriptionId"}""")

            // Close the connection from server side to trigger reconnection
            mockServer.closeConnection()

            // Verify stream is still open and receives messages after reconnection
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

            eventually(RECONNECT_WINDOW) {
                mockServer.sendJson(notification)
                stream.isClosed shouldBe false
                stream.isEmpty shouldBe false
            }
            val event = stream.take()!!
            event["number"]?.jsonPrimitive?.content shouldBe "0x9999"
        }

        test("successful unsubscribe") {
            val subscriptionId = "0xdef456"

            // Pre-queue responses for subscribe (ID 1) and unsubscribe (ID 2)
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"$subscriptionId"}""")
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":2,"result":true}""")

            // First create a subscription
            val params = arrayOf("newHeads")
            val resultDecoder: (KJsonElement) -> JsonObject = { it.jsonObject }

            val subscriptionResult = wsClient.subscribe(params, resultDecoder)
            subscriptionResult.isSuccess() shouldBe true

            // Send multiple notifications
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
            event1["number"]?.jsonPrimitive?.content shouldBe "0x1234"
            event1["hash"]?.jsonPrimitive?.content shouldBe "0xabcd"
            event1["timestamp"]?.jsonPrimitive?.content shouldBe "0x1111"

            stream.close()

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
            delay(100)

            stream.isEmpty shouldBe true
            stream.isClosed shouldBe true
        }

        test("request queued during reconnect times out before reconnect succeeds") {
            wsClient.close()
            wsClient = WsClient(
                mockServer.url,
                KtorHttpClient { install(WebSockets) },
                // These were 50ms/100ms, tuned to MockWebServer's in-process accept. Over a real socket a round
                // trip cannot reliably complete that fast under the load of a full multi-module run, and the
                // warm-up request below started failing. They only need to be short relative to the budget the
                // second half of the test allows, which is RECONNECT_BACKOFF + 3s.
                connectTimeoutMs = 2000L,
                readTimeoutMs = 1000L,
            )

            val stringDecoder: (KJsonElement) -> String = { element -> element.jsonPrimitive.content }
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"0x1234567"}""")
            withTimeout(10.seconds) {
                wsClient.request("eth_blockNumber", emptyArray<Any>(), stringDecoder)
            }.isSuccess() shouldBe true

            mockServer.closeConnection()
            // stop the server so the reconnect attempt is refused - previously this fell out of MockWebServer
            // only accepting a connection when an upgrade had been enqueued
            mockServer.stop()
            delay(50)

            // The request is only failed after the processor loop has been through a reconnect attempt, and a
            // failed attempt parks for WsClient.RECONNECT_BACKOFF. The budget here has to exceed that backoff
            // rather than equal it, otherwise the test races the wait it depends on.
            val result = withTimeout(WsClient.RECONNECT_BACKOFF + 3.seconds) {
                wsClient.request("eth_blockNumber", emptyArray<Any>(), stringDecoder)
            }

            result.isFailure() shouldBe true
            result.unwrapError().code shouldBe RpcError.CODE_CALL_TIMEOUT
        }
    }
})
