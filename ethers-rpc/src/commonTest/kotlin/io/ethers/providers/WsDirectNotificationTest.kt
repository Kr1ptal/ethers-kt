package io.ethers.providers

import io.channels.core.ChannelReceiver
import io.ethers.core.Kotlinx
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.HttpClient as KtorHttpClient

@Serializable
private data class NotificationItem(val a: Long, val b: String? = null)

/**
 * Notifications for the subscription `SUB`, covering field order, other methods and subscriptions, missing, duplicate
 * and escaped fields, an `id` or `error` alongside, unknown keys and malformed input.
 */
private val NOTIFICATIONS = listOf(
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":SUB,"result":RESULT}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"result":RESULT,"subscription":SUB}}""",
    """{"params":{"result":RESULT,"subscription":SUB},"method":"eth_subscription","jsonrpc":"2.0"}""",
    """ { "jsonrpc" : "2.0" , "method" : "eth_subscription" ,
         "params" : { "subscription" : SUB , "result" : RESULT } } """,
    """{"jsonrpc":"2.0","method":"other","params":{"subscription":SUB,"result":RESULT}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":"0xother","result":RESULT}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":"SUB","result":RESULT}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":SUB}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":SUB,"result":null}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":SUB,"result":{"bad":true}}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":SUB,"result":RESULT,"result":{"bad":true}}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":SUB,"result":{"bad":true},"result":RESULT}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":"0xother","subscription":SUB,"result":RESULT}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":SUB,"subscription":"0xother","result":RESULT}}""",
    """{"jsonrpc":"2.0","method":"other","method":"eth_subscription","params":{"subscription":SUB,"result":RESULT}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","method":"other","params":{"subscription":SUB,"result":RESULT}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":"0xother"},"params":{"subscription":SUB,"result":RESULT}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":SUB,"result":RESULT,"x":[{"subscription":"y"}]},"x":{"method":"m"}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"s":"\"}{","subscription":SUB,"result":RESULT}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"result":RESULT , "subscription" : SUB } }""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":SUB,"result":RESULT},"x":{"subscription":"0xother"}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":"0xother","result":RESULT},"x":{"subscription":SUB}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"result":RESULT,"subscription":"0xother"},"params":{"result":RESULT,"subscription":SUB}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"result":RESULT,"subscription":SUB},"params":{"result":RESULT,"subscription":"0xother"}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"result":RESULT,"subscription":SUB,"subscription":"0xother"}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"result":RESULT,"subscription":"0xother","subscription":SUB}}""",
    "{\"jsonrpc\":\"2.0\",\"method\":\"eth_subscription\",\"params\":{\"\\u0073ubscription\":SUB,\"result\":RESULT}}",
    "{\"jsonrpc\":\"2.0\",\"method\":\"eth_\\u0073ubscription\",\"params\":{\"subscription\":SUB,\"result\":RESULT}}",
    "{\"jsonrpc\":\"2.0\",\"\\u006dethod\":\"eth_subscription\",\"params\":{\"subscription\":SUB,\"result\":RESULT}}",
    """{"jsonrpc":"2.0","method":"eth_subscription","id":5,"params":{"subscription":SUB,"result":RESULT}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","id":{"x":1},"params":{"subscription":SUB,"result":RESULT}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","error":"text","params":{"subscription":SUB,"result":RESULT}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","error":null,"params":{"subscription":SUB,"result":RESULT}}""",
    """{"jsonrpc":"2.0","method":null,"params":{"subscription":SUB,"result":RESULT}}""",
    """{"jsonrpc":"2.0","method":1,"params":{"subscription":SUB,"result":RESULT}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":[SUB,RESULT]}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":[SUB],"result":RESULT}}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":SUB,"result":RESULT}""",
    """{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":SUB,"result":RESULT}}}""",
    """[{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":SUB,"result":RESULT}}]""",
    "not json",
)

private class NotificationCase<T : Any>(
    val name: String,
    val serializer: KSerializer<T>,
    val result: String,
    val sentinel: String,
)

private val NOTIFICATION_CASES = listOf(
    NotificationCase("string", String.serializer(), "\"a\\\"b\\u0041\"", "\"sentinel\""),
    NotificationCase("long", Long.serializer(), "42", "-1"),
    NotificationCase("list", ListSerializer(String.serializer()), """["x","y"]""", """["sentinel"]"""),
    NotificationCase("object", NotificationItem.serializer(), """{"b":"q","a":5,"unknown":{"z":[1]}}""", """{"a":-1}"""),
)

/**
 * The events [body] delivered to the subscription [subscriptionId] the way WsClient handled every message before it
 * decoded notifications directly: parse the message into a tree, route it on `id`, `method`, `error` and `params`,
 * and decode `params.result` from the tree. A message that throws on the way delivers nothing.
 */
private fun treeEvents(body: String, subscriptionId: JsonPrimitive, decode: (JsonElement) -> Any): List<Any> = try {
    val obj = Kotlinx.DEFAULT.parseToJsonElement(body) as? JsonObject ?: return emptyList()
    obj["id"]?.jsonPrimitive?.longOrNull
    val method = obj["method"]?.jsonPrimitive?.content
    val errorEl = obj["error"]
    if (errorEl != null && errorEl !is JsonNull) RpcError.fromJsonObject(errorEl.jsonObject)
    val params = obj["params"]
    if (method == null || params == null) return emptyList()

    val paramsObj = params.jsonObject
    val id = paramsObj["subscription"]?.jsonPrimitive ?: return emptyList()
    val result = paramsObj["result"] ?: return emptyList()
    if (id != subscriptionId || method != "eth_subscription") return emptyList()
    listOf(decode(result))
} catch (_: Exception) {
    emptyList()
}

/** Records how each value was decoded: straight from the message, or from a tree. */
private class DecoderRecorder<T>(private val delegate: DeserializationStrategy<T>) : DeserializationStrategy<T> {
    val decoders = ArrayList<String>()
    override val descriptor get() = delegate.descriptor

    override fun deserialize(decoder: Decoder): T {
        decoders += decoder::class.simpleName.orEmpty()
        return delegate.deserialize(decoder)
    }
}

private suspend fun <T : Any> ChannelReceiver<T>.next(): T = withTimeout(10.seconds) {
    var event = poll()
    while (event == null) {
        delay(5)
        event = poll()
    }
    event
}

class WsDirectNotificationTest : FunSpec({
    test("decoding a notification straight into its event matches decoding it through a JSON tree") {
        for (subscriptionId in listOf("\"0x9cef478923ff08bf\"", "23784")) {
            for (case in NOTIFICATION_CASES) {
                checkCase(case, subscriptionId)
            }
        }
    }

    test("a notification is decoded straight from the message, and again through a tree if that fails") {
        val server = mockServerWebsocket()
        val client = WsClient(server.url, KtorHttpClient { install(WebSockets) })
        try {
            val recorder = DecoderRecorder(String.serializer())
            server.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":"0xs"}""")
            val stream = client.subscribe(arrayOf("newHeads"), recorder).unwrap()

            server.sendJson("""{"jsonrpc":"2.0","method":"eth_subscription","params":{"result":"a","subscription":"0xs"}}""")
            stream.next() shouldBe "a"
            recorder.decoders shouldBe listOf("StreamingJsonDecoder")

            // a result that does not decode is tried through the tree, then dropped
            recorder.decoders.clear()
            server.sendJson("""{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":"0xs","result":{"bad":true}}}""")
            server.sendJson("""{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":"0xs","result":"b"}}""")
            stream.next() shouldBe "b"
            recorder.decoders.size shouldBe 3
            recorder.decoders[0] shouldBe "StreamingJsonDecoder"
            (recorder.decoders[1] == "StreamingJsonDecoder") shouldBe false
            recorder.decoders[2] shouldBe "StreamingJsonDecoder"
        } finally {
            client.close()
            server.stop()
        }
    }

    test("a terminal event decoded straight from the message ends the subscription") {
        val server = mockServerWebsocket()
        val ktor = KtorHttpClient { install(WebSockets) }
        val descriptor = SubscriptionDescriptor { params ->
            SubscriptionDescriptor.Resolved("eth_subscribe", "eth_unsubscribe", "eth_subscription", params.copyOf()) { it == "done" }
        }
        val client = WsClient(server.url, RpcClientConfig().client(ktor).subscriptionDescriptor(descriptor))
        try {
            server.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":7}""")
            val stream = client.subscribe(arrayOf("newHeads"), String.serializer()).unwrap()
            server.takeReceivedText()

            server.sendJson("""{"jsonrpc":"2.0","method":"eth_subscription","params":{"result":"done","subscription":7}}""")
            stream.next() shouldBe "done"
            withTimeout(10.seconds) { while (!stream.isClosed) delay(5) }
            // the server already ended it, so closing does not unsubscribe
            server.takeReceivedText(300) shouldBe null
        } finally {
            client.close()
            ktor.close()
            server.stop()
        }
    }
})

/**
 * Send each notification for one subscription, each followed by a [NotificationCase.sentinel] event, and check that
 * the events delivered before each sentinel are those the tree route delivered.
 */
private suspend fun <T : Any> checkCase(case: NotificationCase<T>, subscriptionId: String) {
    val server = mockServerWebsocket()
    val client = WsClient(server.url, KtorHttpClient { install(WebSockets) })
    try {
        server.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":$subscriptionId}""")
        val stream = client.subscribe(arrayOf("newHeads"), case.serializer).unwrap()
        val id = Kotlinx.DEFAULT.parseToJsonElement(subscriptionId).jsonPrimitive
        val sentinel = Kotlinx.DEFAULT.decodeFromString(case.serializer, case.sentinel)
        val decodeTree = { element: JsonElement -> Kotlinx.DEFAULT.decodeFromJsonElement(case.serializer, element) as Any }

        // every message goes out before any is checked, rather than one round trip each: they arrive in order, so the
        // sentinels still split the events between them
        val bodies = NOTIFICATIONS.map { it.replace("RESULT", case.result).replace("SUB", subscriptionId).replace("\"\"", "\"") }
        for (body in bodies) {
            server.sendJson(body)
            server.sendJson("""{"jsonrpc":"2.0","method":"eth_subscription","params":{"subscription":$subscriptionId,"result":${case.sentinel}}}""")
        }

        for (body in bodies) {
            val events = ArrayList<Any>()
            while (true) {
                val event = stream.next()
                if (event == sentinel) break
                events += event
            }
            withClue("${case.name}, subscription $subscriptionId: $body") { events shouldBe treeEvents(body, id, decodeTree) }
        }
    } finally {
        client.close()
        server.stop()
    }
}
