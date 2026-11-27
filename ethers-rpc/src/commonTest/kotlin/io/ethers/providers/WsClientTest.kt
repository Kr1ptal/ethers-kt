package io.ethers.providers

import io.ethers.core.BigIntegerNumberSerializer
import io.ethers.core.Kotlinx
import io.ethers.core.isFailure
import io.ethers.core.isSuccess
import io.ethers.providers.types.BatchRpcRequest
import io.ethers.providers.types.RpcCall
import io.ethers.providers.types.RpcParams
import io.ethers.providers.types.buildRpcParams
import io.ethers.providers.types.rpcParams
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.HttpClient as KtorHttpClient

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
// to surface. A reconnect can include WsClient.RECONNECT_BACKOFF and a slow handshake on each side, so this is a
// must-not-hang guard rather than a timing expectation.
private val RECONNECT_WINDOW = 30.seconds

// Waits for a frame to cross the loopback socket. A loaded CI runner has taken over a second for that.
private val DELIVERY_WINDOW = 5.seconds

/** Stands in for any domain type that reaches the transport with a serializer of its own. */
@Serializable(with = SerializableValueSerializer::class)
data class SerializableValue(val value: String)

object SerializableValueSerializer : KSerializer<SerializableValue> {
    override val descriptor = PrimitiveSerialDescriptor("SerializableValue", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder) = SerializableValue(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: SerializableValue) = encoder.encodeString(value.value)
}

/** A call object mixing field types, each written by its own serializer. */
@Serializable
class CallParams(
    val from: SerializableValue,
    val to: SerializableValue,
    @Serializable(with = BigIntegerNumberSerializer::class) val value: BigInteger,
    @Serializable(with = HexByteArraySerializer::class) val data: ByteArray,
    val gas: Long,
)

val CALL_PARAMS = CallParams(
    SerializableValue("0x1111111111111111111111111111111111111111"),
    SerializableValue("0x2222222222222222222222222222222222222222"),
    bigIntegerOf(1),
    byteArrayOf(0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte()),
    21000L,
)

/** Params whose encoding fails, as a caller's own serializer can when the client encodes a request. */
fun unencodableParams(vararg leading: String): RpcParams = buildRpcParams {
    for (param in leading) add(param)
    add(1, UnencodableSerializer)
}

/** Writes its value as a string and counts how often it is encoded, to show how often a client encodes params. */
class CountingSerializer : SerializationStrategy<String> {
    private val count = atomic(0)

    val encodings: Int get() = count.value

    override val descriptor = PrimitiveSerialDescriptor("Counting", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) {
        count.incrementAndGet()
        encoder.encodeString(value)
    }
}

/** Params of [leading] and then [value], encoded by [counting]. */
fun countedParams(counting: CountingSerializer, value: String, vararg leading: String): RpcParams = buildRpcParams {
    for (param in leading) add(param)
    add(value, counting)
}

private object UnencodableSerializer : SerializationStrategy<Int> {
    override val descriptor = PrimitiveSerialDescriptor("Unencodable", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: Int) = throw SerializationException("Unencodable can't be encoded")
}

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
        val stringSerializer = String.serializer()

        beforeEach {
            mockServer = mockServerWebsocket()
            wsClient = WsClient(mockServer.url, KtorHttpClient { install(WebSockets) })
        }

        afterEach {
            wsClient.close()
            mockServer.stop()
        }

        test("a call object with big number and byte array fields is emitted as proper JSON") {
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"0x1234567"}""")

            wsClient.request("eth_call", rpcParams(CALL_PARAMS, "latest"), stringSerializer)

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

            val result = wsClient.request("eth_getCode", RpcParams.EMPTY, HexByteArraySerializer)

            result.isSuccess() shouldBe true
            result.unwrap() shouldBe byteArrayOf(0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte())
        }

        test("subscribe(KSerializer<T>) with ByteArray decodes hex-string notification results") {
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"0xsub123"}""")

            val subscriptionResult = wsClient.subscribe(rpcParams("newPendingTransactions"), HexByteArraySerializer)
            subscriptionResult.isSuccess() shouldBe true
            val stream = subscriptionResult.unwrap()

            mockServer.sendJson(
                """{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":"0xsub123","result":"0xdeadbeef"}}""",
            )

            eventually(DELIVERY_WINDOW) { stream.isEmpty shouldBe false }
            val payload = stream.receive()!!
            payload shouldBe byteArrayOf(0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte())
        }

        // the call must fail, not wait forever for a response that was already received
        test("a subscribe response that is not a subscription id fails the call") {
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":{"not":"an id"}}""")

            val result = withTimeout(30.seconds) {
                wsClient.subscribe(rpcParams("newHeads"), JsonObject.serializer())
            }

            result.isFailure() shouldBe true
            result.unwrapError().code shouldBe RpcError.CODE_INVALID_RESPONSE
        }

        test("a malformed batch entry fails the calls it leaves unanswered") {
            mockServer.enqueueJson("""[{"jsonrpc":"2.0","id":1,"result":"0x1"},"not a response"]""")

            val batch = BatchRpcRequest(2)
            batch.addRpcCall(RpcCall(wsClient, "eth_blockNumber", RpcParams.EMPTY, stringSerializer))
            batch.addRpcCall(RpcCall(wsClient, "eth_chainId", RpcParams.EMPTY, stringSerializer))

            withTimeout(30.seconds) {
                batch.send() shouldBe false
                batch.responses[0].await().unwrap() shouldBe "0x1"
                batch.responses[1].await().unwrapError().code shouldBe RpcError.CODE_INVALID_RESPONSE
            }
        }

        // Connect with a request that succeeds, so a reconnect afterwards shows as a second session.
        suspend fun connect() {
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":$REQUEST_ID,"result":"0x1"}""")
            withTimeout(30.seconds) {
                wsClient.request("eth_blockNumber", RpcParams.EMPTY, stringSerializer).unwrap() shouldBe "0x1"
            }
            mockServer.takeReceivedText(DELIVERY_WINDOW.inWholeMilliseconds)!! shouldContain "eth_blockNumber"
        }

        // The next request after a failed send is the next message the server receives, and the connection the
        // client opened at the start is still the only one.
        suspend fun assertStillConnectedAndNothingSent() {
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":$REQUEST_ID,"result":"0x2"}""")
            withTimeout(30.seconds) {
                wsClient.request("eth_chainId", RpcParams.EMPTY, stringSerializer).unwrap() shouldBe "0x2"
            }
            mockServer.takeReceivedText(DELIVERY_WINDOW.inWholeMilliseconds)!! shouldContain "eth_chainId"
            mockServer.sessionsOpened shouldBe 1
        }

        test("params encoded before a request, as a caching wrapper does, are not encoded again to send it") {
            val counting = CountingSerializer()
            val params = countedParams(counting, "0xabc", "latest")
            val key = params.encode().decodeToString()

            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":$REQUEST_ID,"result":"0x1"}""")
            withTimeout(30.seconds) {
                wsClient.request("eth_getBalance", params, stringSerializer).unwrap() shouldBe "0x1"
            }

            val sent = Kotlinx.DEFAULT.parseToJsonElement(mockServer.takeReceivedText(DELIVERY_WINDOW.inWholeMilliseconds)!!)
            sent.jsonObject["params"].toString() shouldBe key
            counting.encodings shouldBe 1
        }

        test("a batch sends params encoded before, and a call in two batches is encoded once") {
            val counting = CountingSerializer()
            val first = RpcCall(wsClient, "eth_getBalance", countedParams(counting, "0xa"), stringSerializer)
            val second = RpcCall(wsClient, "eth_getCode", countedParams(counting, "0xb"), stringSerializer)
            first.params.encode()
            second.params.encode()
            counting.encodings shouldBe 2

            mockServer.enqueueJson(
                """[{"jsonrpc":"2.0","id":1,"result":"0x1"},{"jsonrpc":"2.0","id":2,"result":"0x2"}]""",
            )
            val batch = BatchRpcRequest(2)
            val firstResponse = batch.addRpcCall(first)
            val secondResponse = batch.addRpcCall(second)
            withTimeout(30.seconds) {
                batch.send() shouldBe true
                firstResponse.await().unwrap() shouldBe "0x1"
                secondResponse.await().unwrap() shouldBe "0x2"
            }
            val sent = Kotlinx.DEFAULT.parseToJsonElement(mockServer.takeReceivedText(DELIVERY_WINDOW.inWholeMilliseconds)!!)
            sent.jsonArray.map { it.jsonObject["params"].toString() } shouldBe listOf(first.params.encode().decodeToString(), second.params.encode().decodeToString())
            counting.encodings shouldBe 2

            // the first call again, in a new batch
            mockServer.enqueueJson("""[{"jsonrpc":"2.0","id":3,"result":"0x3"}]""")
            val again = BatchRpcRequest(1)
            val againResponse = again.addRpcCall(first)
            withTimeout(30.seconds) {
                again.send() shouldBe true
                againResponse.await().unwrap() shouldBe "0x3"
            }
            val resent = Kotlinx.DEFAULT.parseToJsonElement(mockServer.takeReceivedText(DELIVERY_WINDOW.inWholeMilliseconds)!!)
            resent.jsonArray.single().jsonObject["params"].toString() shouldBe first.params.encode().decodeToString()
            counting.encodings shouldBe 2
        }

        test("a call sent in two batches without encoding it first is encoded once") {
            val counting = CountingSerializer()
            val call = RpcCall(wsClient, "eth_getBalance", countedParams(counting, "0xa"), stringSerializer)

            for (id in 1..2) {
                mockServer.enqueueJson("""[{"jsonrpc":"2.0","id":$id,"result":"0x$id"}]""")
                val batch = BatchRpcRequest(1)
                val response = batch.addRpcCall(call)
                withTimeout(30.seconds) {
                    batch.send() shouldBe true
                    response.await().unwrap() shouldBe "0x$id"
                }
                mockServer.takeReceivedText(DELIVERY_WINDOW.inWholeMilliseconds)!! shouldContain "\"0xa\""
            }
            counting.encodings shouldBe 1
        }

        test("a batch call whose params can't be encoded fails only that send, without reconnecting") {
            connect()

            val batch = BatchRpcRequest(2)
            val valid = batch.addRpcCall(RpcCall(wsClient, "eth_gasPrice", RpcParams.EMPTY, stringSerializer))
            val invalid = batch.addRpcCall(RpcCall(wsClient, "eth_getBalance", unencodableParams(), stringSerializer))

            // the encoding error reaches the sender, and every call of the unsent batch fails instead of waiting
            withTimeout(DELIVERY_WINDOW) {
                shouldThrow<IllegalArgumentException> { batch.send() }
                valid.await().unwrapError().code shouldBe RpcError.CODE_CALL_FAILED
                val error = invalid.await().unwrapError()
                error.code shouldBe RpcError.CODE_CALL_FAILED
                error.message shouldContain "eth_getBalance"
            }

            assertStillConnectedAndNothingSent()
        }

        test("a subscription whose params can't be encoded fails only that call, without reconnecting") {
            connect()

            withTimeout(DELIVERY_WINDOW) {
                shouldThrow<IllegalArgumentException> {
                    wsClient.subscribe(unencodableParams("newHeads"), JsonObject.serializer())
                }
            }

            assertStillConnectedAndNothingSent()
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
            mockServer.stop()
        }

        test("subscription with multiple notifications") {
            val subscriptionId = "0xmulti123"

            // Pre-queue the subscription response
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"$subscriptionId"}""")

            // Subscribe to new block headers
            val params = rpcParams("newHeads")
            val resultSerializer = JsonObject.serializer()

            val subscriptionResult = wsClient.subscribe(params, resultSerializer)
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
            eventually(DELIVERY_WINDOW) {
                stream.isEmpty shouldBe false
            }
            val event1 = stream.receive()!!
            event1["number"]?.jsonPrimitive?.content shouldBe "0x1234"
            event1["hash"]?.jsonPrimitive?.content shouldBe "0xabcd"
            event1["timestamp"]?.jsonPrimitive?.content shouldBe "0x1111"

            // Second notification
            eventually(DELIVERY_WINDOW) {
                stream.isEmpty shouldBe false
            }
            val event2 = stream.receive()!!
            event2["number"]?.jsonPrimitive?.content shouldBe "0x1235"
            event2["hash"]?.jsonPrimitive?.content shouldBe "0xefgh"
            event2["timestamp"]?.jsonPrimitive?.content shouldBe "0x2222"

            // Third notification
            eventually(DELIVERY_WINDOW) {
                stream.isEmpty shouldBe false
            }
            val event3 = stream.receive()!!
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
            val params = rpcParams("newHeads")
            val resultSerializer = JsonObject.serializer()

            val subscriptionResult = wsClient.subscribe(params, resultSerializer)
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
            val params = rpcParams("newHeads")
            val resultSerializer = JsonObject.serializer()

            val subscriptionResult = wsClient.subscribe(params, resultSerializer)
            subscriptionResult.isSuccess() shouldBe true

            val stream = subscriptionResult.unwrap()
            stream shouldNotBe null
            stream.isClosed shouldBe false

            // Queue the new subscription response for auto-resubscription
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"$newSubscriptionId"}""")

            // Drop the original subscribe request so the next message the server takes is the resubscribe
            mockServer.takeReceivedText() shouldNotBe null

            // Close the connection from server side to trigger reconnection
            mockServer.closeConnection()

            // Wait for the client to resubscribe over the new connection before notifying it. A reconnect spans
            // the close handshake, a fresh handshake and possibly a backoff, which a loaded runner can stretch
            // past any short window.
            val resubscribe = mockServer.takeReceivedText(RECONNECT_WINDOW.inWholeMilliseconds)
            resubscribe shouldNotBe null
            resubscribe!! shouldContain "eth_subscribe"

            // Verify stream is still open and receives messages after reconnection. The server answers the
            // resubscribe just after recording it, so retry the notification until it follows that response.
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

            eventually(DELIVERY_WINDOW) {
                mockServer.sendJson(notification)
                stream.isClosed shouldBe false
                stream.isEmpty shouldBe false
            }
            val event = stream.receive()!!
            event["number"]?.jsonPrimitive?.content shouldBe "0x9999"
        }

        test("resubscribing after a reconnect sends the params encoded at subscribe time") {
            // encodes to something new each time, so the resubscribe only matches if it reuses the first encoding
            val counting = object : SerializationStrategy<String> {
                private var encodings = 0
                override val descriptor = PrimitiveSerialDescriptor("Counting", PrimitiveKind.STRING)
                override fun serialize(encoder: Encoder, value: String) {
                    encoder.encodeString("$value-${++encodings}")
                }
            }
            val params = buildRpcParams {
                add("logs")
                add("filter", counting)
            }

            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":$REQUEST_ID,"result":"0xfirst"}""")
            wsClient.subscribe(params, JsonObject.serializer()).isSuccess() shouldBe true
            val subscribe = mockServer.takeReceivedText(DELIVERY_WINDOW.inWholeMilliseconds)!!

            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":$REQUEST_ID,"result":"0xsecond"}""")
            mockServer.closeConnection()

            val resubscribe = mockServer.takeReceivedText(RECONNECT_WINDOW.inWholeMilliseconds)
            resubscribe shouldNotBe null

            val sent = Kotlinx.DEFAULT.parseToJsonElement(subscribe).jsonObject
            val resent = Kotlinx.DEFAULT.parseToJsonElement(resubscribe!!).jsonObject
            resent["method"] shouldBe sent["method"]
            resent["params"] shouldBe sent["params"]
            sent["params"]!!.jsonArray[1].jsonPrimitive.content shouldContain "filter-"
        }

        test("successful unsubscribe") {
            val subscriptionId = "0xdef456"

            // Pre-queue responses for subscribe (ID 1) and unsubscribe (ID 2)
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"$subscriptionId"}""")
            mockServer.enqueueJson("""{"jsonrpc":"2.0","id":2,"result":true}""")

            // First create a subscription
            val params = rpcParams("newHeads")
            val resultSerializer = JsonObject.serializer()

            val subscriptionResult = wsClient.subscribe(params, resultSerializer)
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
            eventually(DELIVERY_WINDOW) {
                stream.isEmpty shouldBe false
            }

            val event1 = stream.receive()!!
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

            val stringSerializer = String.serializer()
            // Connect with a request that succeeds. Its read timeout counts from when it is queued, so on a loaded
            // runner it can expire while the client is still connecting; try again until one succeeds. The reply
            // carries the id of whichever request reaches the server.
            withTimeout(30.seconds) {
                while (true) {
                    mockServer.enqueueJson("""{"jsonrpc":"2.0","id":$REQUEST_ID,"result":"0x1234567"}""")
                    if (wsClient.request("eth_blockNumber", RpcParams.EMPTY, stringSerializer).isSuccess()) break
                }
            }

            mockServer.closeConnection()
            // stop the server so the reconnect attempt is refused - previously this fell out of MockWebServer
            // only accepting a connection when an upgrade had been enqueued
            mockServer.stop()
            delay(50)

            // The request is only failed after the processor loop has been through a reconnect attempt, and a
            // failed attempt parks for WsClient.RECONNECT_BACKOFF. The budget here has to exceed that backoff
            // rather than equal it, otherwise the test races the wait it depends on.
            val result = withTimeout(WsClient.RECONNECT_BACKOFF + 3.seconds) {
                wsClient.request("eth_blockNumber", RpcParams.EMPTY, stringSerializer)
            }

            result.isFailure() shouldBe true
            result.unwrapError().code shouldBe RpcError.CODE_CALL_TIMEOUT
        }
    }
})
