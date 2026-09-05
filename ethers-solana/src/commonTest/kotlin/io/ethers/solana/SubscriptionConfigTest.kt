package io.ethers.solana

import io.ethers.core.Kotlinx
import io.ethers.providers.RpcClientConfig
import io.ethers.providers.SubscriptionDescriptor
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SignatureNotification
import io.ethers.solana.types.SolanaSignature
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.HttpClient as KtorHttpClient
import io.ktor.server.websocket.WebSockets as ServerWebSockets

class SubscriptionConfigTest : FunSpec({
    for (separateWebSocket in listOf(false, true)) {
        test("builder configures all Solana streams with separate WebSocket = $separateWebSocket") {
            val key = Programs.SYSTEM
            val signature = SolanaSignature(ByteArray(64))
            val account = """{"data":["","base64"],"executable":false,"lamports":1,"owner":"$key","rentEpoch":0}"""
            fun contextual(value: String) = """{"context":{"slot":42},"value":$value}"""
            val events = mapOf(
                "account" to contextual(account),
                "program" to contextual("""{"pubkey":"$key","account":$account}"""),
                "logs" to contextual("""{"signature":"$signature","err":null,"logs":[]}"""),
                "signature" to contextual("""{"err":null}"""),
                "slot" to """{"parent":40,"root":39,"slot":42}""",
                "root" to "42",
            )
            val requests = Channel<Pair<JsonObject, String?>>(Channel.UNLIMITED)
            val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
                install(ServerWebSockets)
                routing {
                    webSocket("/") {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val request = Kotlinx.DEFAULT.parseToJsonElement(frame.readText()).jsonObject
                            val method = request.getValue("method").jsonPrimitive.content
                            val id = request.getValue("id")
                            if (!method.endsWith("Subscribe")) {
                                send(Frame.Text("""{"jsonrpc":"2.0","id":$id,"result":true}"""))
                                continue
                            }
                            requests.send(request to call.request.headers["X-Test"])
                            send(Frame.Text("""{"jsonrpc":"2.0","id":$id,"result":$id}"""))
                            val name = method.removeSuffix("Subscribe")
                            suspend fun notify(event: String) {
                                send(Frame.Text("""{"jsonrpc":"2.0","method":"${name}Notification","params":{"subscription":$id,"result":$event}}"""))
                            }
                            if (name == "signature") notify(contextual("\"receivedSignature\""))
                            notify(events.getValue(name))
                        }
                    }
                }
            }
            val ktor = KtorHttpClient { install(WebSockets) }
            var provider: SolanaProvider? = null
            try {
                server.start(wait = false)
                val port = server.engine.resolvedConnectors().first().port
                val config = RpcClientConfig().client(ktor).requestHeaders(mapOf("X-Test" to "configured"))
                    .resubscribeOnReconnect(false).connectTimeoutMs(5000).readTimeoutMs(5000)
                val builder = if (separateWebSocket) {
                    SolanaProvider.builder("http://127.0.0.1:$port/").webSocketUrl("ws://127.0.0.1:$port/")
                } else {
                    SolanaProvider.builder("ws://127.0.0.1:$port/")
                }
                val built = builder.config(config).build().unwrap()
                provider = built
                config.subscriptionDescriptor shouldBe SubscriptionDescriptor.ETHEREUM
                val signatureStream = withTimeout(10.seconds) {
                    val streams = listOf(
                        built.subscribeAccount(key).send().unwrap(),
                        built.subscribeProgram(key).send().unwrap(),
                        built.subscribeLogs().send().unwrap(),
                        built.subscribeSlot().send().unwrap(),
                        built.subscribeRoot().send().unwrap(),
                    )
                    val signatureStream = built.subscribeSignature(signature, enableReceivedNotification = true).send().unwrap()
                    eventually(5.seconds) { streams.all { !it.isEmpty && !it.isClosed } shouldBe true }
                    streams.forEach { it.close() }
                    signatureStream
                }
                eventually(5.seconds) { signatureStream.isClosed shouldBe true }
                signatureStream.take()!!::class shouldBe SignatureNotification.Received::class
                signatureStream.take()!!::class shouldBe SignatureNotification.Status::class

                val expectedParams = linkedMapOf(
                    "account" to """["$key",{"commitment":"finalized","encoding":"base64"}]""",
                    "program" to """["$key",{"commitment":"finalized","encoding":"base64","filters":[]}]""",
                    "logs" to """["all",{"commitment":"finalized"}]""",
                    "slot" to "[]",
                    "root" to "[]",
                    "signature" to """["$signature",{"commitment":"finalized","enableReceivedNotification":true}]""",
                )
                for ((name, params) in expectedParams) {
                    val (request, header) = withTimeout(5.seconds) { requests.receive() }
                    header shouldBe "configured"
                    request["method"] shouldBe JsonPrimitive("${name}Subscribe")
                    request["params"] shouldBe Kotlinx.DEFAULT.parseToJsonElement(params)
                }
            } finally {
                provider?.close()
                ktor.close()
                server.stop(gracePeriodMillis = 0, timeoutMillis = 500)
                requests.close()
            }
        }
    }
})
