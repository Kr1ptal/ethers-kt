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
import kotlinx.atomicfu.update
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
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
    /** How many WebSocket sessions the server has open. */
    val openSessions: Int

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
 * The mock servers started and not yet stopped.
 *
 * Every running server holds one of `Dispatchers.IO`'s threads in its selector. Leaked servers add up across a run
 * until IO's 64 threads are all taken, and from then on every network call in every test waits for a selector to
 * time out and free one: on CI, seconds per request. `io.kotest.provided.ProjectConfig` fails a spec that leaves one
 * running, and stops it, so a leak fails where it happens.
 */
internal object RunningMockServers {
    private val servers = atomic(emptyList<MockServer>())

    fun add(server: MockServer) = servers.update { it + server }

    fun remove(server: MockServer) = servers.update { it - server }

    /** Stop every server still running, returning how many there were. */
    suspend fun stopAll(): Int {
        val leaked = servers.getAndSet(emptyList())
        for (server in leaked) server.stop()
        return leaked.size
    }
}

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

    /**
     * Open WebSocket sessions, the one the client last sent a frame on last. A session that has sent nothing goes
     * first: a client a test closes right after creating it can have its session open here after its replacement
     * has already spoken, and must not take that one's place.
     */
    private val sessions = atomic(emptyList<DefaultWebSocketServerSession>())

    override val openSessions: Int
        get() = sessions.value.size

    private val server = embeddedServer(CIO, port = 0) {
        if (websocket) {
            install(ServerWebSockets)
        }

        routing {
            if (websocket) {
                webSocket("/") {
                    sessions.update { listOf(this) + it }
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val text = frame.readText()
                            sessions.update { it - this + this }
                            received.trySend(text)

                            // reply only if a response was queued, mirroring MockWebServer's queue semantics
                            responses.tryReceive().getOrNull()?.let { (_, body) ->
                                send(Frame.Text(body))
                            }
                        }
                    } finally {
                        sessions.update { it - this }
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
        RunningMockServers.add(this)
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
        // never discard silently: a dropped frame used to surface only as a test timing out minutes later
        val current = sessions.value.lastOrNull() ?: throw AssertionError("sendJson called with no connected session")
        current.send(Frame.Text(json))
    }

    override suspend fun closeConnection(code: Short, reason: String) {
        // Every open session, not only the current one: any other belongs to a client the test has closed and the
        // server has not yet seen go, and closing that one alone would leave the client under test connected.
        // A client that reconnects during the close handshake opens a session that is not in this snapshot. One
        // already closing rejects the close frame, which is fine: it is going anyway.
        for (session in sessions.value) {
            try {
                session.close(CloseReason(code, reason))
            } catch (_: ClosedSendChannelException) {
            }
        }
    }

    override suspend fun stop() {
        RunningMockServers.remove(this)
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
