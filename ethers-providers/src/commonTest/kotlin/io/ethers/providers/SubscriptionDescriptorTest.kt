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
    var resolutions = 0
    beforeEach {
        server = mockServerWebsocket()
        ktor = KtorHttpClient { install(WebSockets) }
        resolutions = 0
        client = WsClient(
            server.url,
            RpcClientConfig().client(ktor).subscriptionDescriptor(
                SubscriptionDescriptor { params ->
                    resolutions++
                    val name = params[0] as String
                    SubscriptionDescriptor.Resolved("${name}Subscribe", "${name}Unsubscribe", "${name}Notification", params.copyOfRange(1, params.size)) {
                        name == "signature" && it.jsonPrimitive.content == "done"
                    }
                },
            ),
        )
    }
    afterEach {
        client.close()
        ktor.close()
        server.stop()
    }

    test("numeric subscription ids and method-specific unsubscribe survive reconnect") {
        server.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":42}""")
        val stream = client.subscribe(arrayOf("slot")) { it.jsonPrimitive.content }.unwrap()
        val subscribe = Kotlinx.DEFAULT.parseToJsonElement(server.takeReceivedText()!!).jsonObject
        subscribe["method"] shouldBe JsonPrimitive("slotSubscribe")
        subscribe.getValue("params").jsonArray.size shouldBe 0
        server.sendJson("""{"jsonrpc":"2.0","method":"wrongNotification","params":{"subscription":42,"result":"wrong"}}""")
        server.sendJson("""{"jsonrpc":"2.0","method":"slotNotification","params":{"subscription":42,"result":"before"}}""")
        eventually(30.seconds) { stream.isEmpty shouldBe false }
        stream.take() shouldBe "before"

        server.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":43}""")
        server.closeConnection()
        val resubscribe = Kotlinx.DEFAULT.parseToJsonElement(server.takeReceivedText(5000)!!).jsonObject
        resubscribe["method"] shouldBe JsonPrimitive("slotSubscribe")
        resubscribe["params"] shouldBe subscribe["params"]
        resolutions shouldBe 1
        eventually(30.seconds) {
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
        server.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":7}""")
        val stream = client.subscribe(arrayOf("signature", "test-signature")) { it.jsonPrimitive.content }.unwrap()
        val subscribe = Kotlinx.DEFAULT.parseToJsonElement(server.takeReceivedText()!!).jsonObject
        subscribe.getValue("params").jsonArray shouldBe listOf(JsonPrimitive("test-signature"))
        server.sendJson("""{"jsonrpc":"2.0","method":"signatureNotification","params":{"subscription":7,"result":"received"}}""")
        eventually(30.seconds) { stream.isEmpty shouldBe false }
        stream.take() shouldBe "received"
        stream.isClosed shouldBe false
        server.sendJson("""{"jsonrpc":"2.0","method":"signatureNotification","params":{"subscription":7,"result":"done"}}""")
        eventually(30.seconds) { stream.isClosed shouldBe true }
        stream.take() shouldBe "done"
        server.takeReceivedText(100) shouldBe null
        server.closeConnection()
        server.takeReceivedText(3000) shouldBe null
    }

    test("cancelling an in-flight subscription unsubscribes a late server response") {
        coroutineScope {
            val pending = async {
                client.subscribe(arrayOf("root")) { it.jsonPrimitive.content }
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
        server.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":42}""")
        val stream = client.subscribe(arrayOf("slot")) { it.jsonPrimitive.content }.unwrap()
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
