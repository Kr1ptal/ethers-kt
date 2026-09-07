package io.ethers.providers

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/** Captured HTTP request from a mock server. */
data class RecordedRequest(
    val headersMap: Map<String, String>,
    val bodyBytes: ByteArray,
) {
    fun getHeader(name: String): String? = headersMap.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    val bodyText: String get() = bodyBytes.decodeToString()
}

interface MockServer {
    val url: String

    fun enqueueJson(json: String)
    fun enqueue(statusCode: Int, body: String)

    /** Take the next request the server handled, or fail after [timeoutMs] rather than hanging. */
    suspend fun takeRequest(timeoutMs: Long = DEFAULT_TIMEOUT_MS): RecordedRequest

    suspend fun stop()
}

interface MockWSServer : MockServer {
    /** Push a frame to the connected client outside of the request/response flow, e.g. a subscription event. */
    suspend fun sendJson(json: String)

    /** Close the current session from the server side. The route keeps accepting, so the client can reconnect. */
    suspend fun closeConnection(code: Short = 1000, reason: String = "Close")

    /**
     * Take the next text message received from the client, or null if none arrived within [timeoutMs]. Each
     * message is returned at most once.
     */
    suspend fun takeReceivedText(timeoutMs: Long = DEFAULT_TIMEOUT_MS): String?
}

private const val DEFAULT_TIMEOUT_MS = 1000L

/**
 * How long a handler waits for a response to be enqueued. This is a guard against hanging forever when a test
 * forgets to enqueue, not a functional timeout, so it is generous - a tight value here flakes under the load of a
 * full multi-module test run.
 */
private const val RESPONSE_WAIT_MS = 10_000L

/**
 * An embedded ktor server bound to an ephemeral loopback port.
 *
 * This replaces okhttp's MockWebServer, which is JVM-only and kept these suites out of commonTest. ktor's
 * `testApplication` was the obvious alternative but cannot serve a WebSocket upgrade on Kotlin/Native - only a
 * real socket works there, which is what this uses.
 *
 * A real server also means connections are always accepted, so there is no per-connection upgrade to enqueue the
 * way MockWebServer required.
 */
private class EmbeddedMockServer(private val websocket: Boolean) : MockWSServer {
    // UNLIMITED so enqueueing never suspends and ordering is preserved
    private val responses = Channel<Pair<Int, String>>(Channel.UNLIMITED)
    private val requests = Channel<RecordedRequest>(Channel.UNLIMITED)
    private val received = Channel<String>(Channel.UNLIMITED)
    private val session = atomic<DefaultWebSocketServerSession?>(null)

    private val server = embeddedServer(CIO, port = 0) {
        if (websocket) {
            install(ServerWebSockets)
        }

        routing {
            if (websocket) {
                webSocket("/") {
                    session.value = this
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        received.trySend(frame.readText())

                        // reply only if a response was queued, mirroring MockWebServer's queue semantics
                        responses.tryReceive().getOrNull()?.let { (_, body) -> send(Frame.Text(body)) }
                    }
                }
            }

            post("/") {
                val headers = call.request.headers.entries()
                    .associate { (k, v) -> k to v.firstOrNull().orEmpty() }
                requests.trySend(RecordedRequest(headers, call.receiveText().encodeToByteArray()))

                val (status, body) = withTimeoutOrNull(RESPONSE_WAIT_MS) { responses.receive() }
                    ?: (500 to """{"error":"no response enqueued in mock server"}""")

                call.respondText(body, status = HttpStatusCode.fromValue(status))
            }
        }
    }

    private var port: Int = 0

    override val url: String
        get() = if (websocket) "ws://127.0.0.1:$port/" else "http://127.0.0.1:$port/"

    suspend fun start() {
        server.start(wait = false)
        port = server.engine.resolvedConnectors().first().port
    }

    override fun enqueueJson(json: String) = enqueue(200, json)

    override fun enqueue(statusCode: Int, body: String) {
        responses.trySend(statusCode to body)
    }

    override suspend fun takeRequest(timeoutMs: Long): RecordedRequest {
        return withTimeoutOrNull(timeoutMs) { requests.receive() }
            ?: throw AssertionError("no request received within ${timeoutMs}ms")
    }

    override suspend fun takeReceivedText(timeoutMs: Long): String? {
        return withTimeoutOrNull(timeoutMs) { received.receive() }
    }

    override suspend fun sendJson(json: String) {
        session.value?.send(Frame.Text(json))
    }

    override suspend fun closeConnection(code: Short, reason: String) {
        session.value?.close(CloseReason(code, reason))
        session.value = null
    }

    override suspend fun stop() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 500)
    }
}

/**
 * Create a [MockServer] that serves enqueued HTTP responses.
 * */
suspend fun mockServerHttp(): MockServer = EmbeddedMockServer(websocket = false).also { it.start() }

/**
 * Create a [MockWSServer] that upgrades to WebSocket and supports sending / receiving WS text messages.
 * */
suspend fun mockServerWebsocket(): MockWSServer = EmbeddedMockServer(websocket = true).also { it.start() }
