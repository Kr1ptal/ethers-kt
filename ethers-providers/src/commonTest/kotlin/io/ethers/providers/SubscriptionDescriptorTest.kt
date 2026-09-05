package io.ethers.providers

import io.ethers.core.Kotlinx
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.HttpClient as KtorHttpClient

class SubscriptionDescriptorTest : FunSpec({
    lateinit var server: MockWSServer
    lateinit var client: WsClient
    lateinit var ktor: KtorHttpClient
    beforeEach {
        server = mockServerWebsocket()
        ktor = KtorHttpClient { install(WebSockets) }
        client = WsClient(server.url, ktor)
    }
    afterEach {
        client.close()
        ktor.close()
        server.stop()
    }

    test("numeric subscription ids and method-specific unsubscribe survive reconnect") {
        val descriptor = SubscriptionDescriptor("slotSubscribe", "slotUnsubscribe", "slotNotification")
        server.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":42}""")
        val stream = client.subscribe(descriptor, emptyArray<Any>()) { it.jsonPrimitive.content }.unwrap()
        Kotlinx.DEFAULT.parseToJsonElement(server.takeReceivedText()!!).jsonObject["method"] shouldBe JsonPrimitive("slotSubscribe")
        server.sendJson("""{"jsonrpc":"2.0","method":"wrongNotification","params":{"subscription":42,"result":"wrong"}}""")
        server.sendJson("""{"jsonrpc":"2.0","method":"slotNotification","params":{"subscription":42,"result":"before"}}""")
        eventually(5.seconds) { stream.isEmpty shouldBe false }
        stream.take() shouldBe "before"

        server.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":43}""")
        server.closeConnection()
        val resubscribe = Kotlinx.DEFAULT.parseToJsonElement(server.takeReceivedText(5000)!!).jsonObject
        resubscribe["method"] shouldBe JsonPrimitive("slotSubscribe")
        eventually(5.seconds) {
            server.sendJson("""{"jsonrpc":"2.0","method":"slotNotification","params":{"subscription":43,"result":"after"}}""")
            stream.isEmpty shouldBe false
        }
        stream.take() shouldBe "after"
        stream.close()
        val unsubscribe = Kotlinx.DEFAULT.parseToJsonElement(server.takeReceivedText(5000)!!).jsonObject
        unsubscribe["method"] shouldBe JsonPrimitive("slotUnsubscribe")
        unsubscribe.getValue("params").jsonArray[0] shouldBe JsonPrimitive(43)
    }

    test("terminal notification is delivered before close and never unsubscribed or resubscribed") {
        val descriptor = SubscriptionDescriptor("signatureSubscribe", "signatureUnsubscribe", "signatureNotification") { it.jsonPrimitive.content == "done" }
        server.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":7}""")
        val stream = client.subscribe(descriptor, arrayOf("signature")) { it.jsonPrimitive.content }.unwrap()
        server.takeReceivedText()
        server.sendJson("""{"jsonrpc":"2.0","method":"signatureNotification","params":{"subscription":7,"result":"received"}}""")
        eventually(5.seconds) { stream.isEmpty shouldBe false }
        stream.take() shouldBe "received"
        stream.isClosed shouldBe false
        server.sendJson("""{"jsonrpc":"2.0","method":"signatureNotification","params":{"subscription":7,"result":"done"}}""")
        eventually(5.seconds) { stream.isClosed shouldBe true }
        stream.take() shouldBe "done"
        server.takeReceivedText(100) shouldBe null
        server.closeConnection()
        server.takeReceivedText(3000) shouldBe null
    }

    test("cancelling an in-flight subscription unsubscribes a late server response") {
        coroutineScope {
            val pending = async {
                client.subscribe(SubscriptionDescriptor("rootSubscribe", "rootUnsubscribe", "rootNotification"), emptyArray<Any>()) { it.jsonPrimitive.content }
            }
            server.takeReceivedText(5000)
            pending.cancelAndJoin()
            server.sendJson("""{"jsonrpc":"2.0","id":1,"result":99}""")
            val unsubscribe = Kotlinx.DEFAULT.parseToJsonElement(server.takeReceivedText(5000)!!).jsonObject
            unsubscribe["method"] shouldBe JsonPrimitive("rootUnsubscribe")
            unsubscribe.getValue("params").jsonArray[0] shouldBe JsonPrimitive(99)
        }
    }

    test("close during resubscription waits for the new numeric id") {
        val descriptor = SubscriptionDescriptor("slotSubscribe", "slotUnsubscribe", "slotNotification")
        server.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":42}""")
        val stream = client.subscribe(descriptor, emptyArray<Any>()) { it.jsonPrimitive.content }.unwrap()
        server.takeReceivedText()
        server.closeConnection()
        server.takeReceivedText(5000)
        stream.close()
        // The reconnect handshake hasn't replied yet. Sending the old ID here could cancel another stream.
        server.takeReceivedText(100) shouldBe null
        server.sendJson("""{"jsonrpc":"2.0","id":1,"result":43}""")
        val unsubscribe = Kotlinx.DEFAULT.parseToJsonElement(server.takeReceivedText(5000)!!).jsonObject
        unsubscribe["method"] shouldBe JsonPrimitive("slotUnsubscribe")
        unsubscribe.getValue("params").jsonArray[0] shouldBe JsonPrimitive(43)
    }
})
