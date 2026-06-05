package io.ethers.providers

import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

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
    fun takeRequest(): RecordedRequest
}

interface MockWSServer : MockServer {
    fun sendJson(json: String)
    fun closeConnection(code: Int = 1000, reason: String = "Close")
    fun allowReconnect()

    /**
     * Take the next text message received from the client, or null if none arrived
     * within [timeoutMs]. Each message is returned at most once.
     */
    fun takeReceivedText(timeoutMs: Long = 1000): String?
}

/**
 * Create [MockServer] that supports enqueuing mock HTTP requests.
 * */
fun mockServerHttp(): MockServer {
    val server = MockWebServer()
    server.start()

    return object : MockServer {
        override val url: String
            get() = server.url("/").toString()

        override fun enqueueJson(json: String) = enqueue(200, json)

        override fun enqueue(statusCode: Int, body: String) {
            server.enqueue(MockResponse().setResponseCode(statusCode).setBody(body))
        }

        override fun takeRequest(): RecordedRequest {
            val req = server.takeRequest()
            val headers = req.headers.toMultimap().mapValues { (_, v) -> v.firstOrNull().orEmpty() }
            return RecordedRequest(headers, req.body.readByteArray())
        }
    }
}

/**
 * Create a [MockWSServer] that automatically upgrades the connection to WebSocket and supports
 * sending / receiving WS text messages.
 * */
fun mockServerWebsocket(): MockWSServer {
    val server = MockWebServer()
    server.start()

    return object : WebSocketListener(), MockWSServer {
        private val msgQueue = ArrayDeque<String>()
        private val receivedMessages = LinkedBlockingQueue<String>()
        private lateinit var ws: WebSocket

        init {
            server.enqueue(MockResponse().withWebSocketUpgrade(this))
        }

        override val url: String
            get() = server.url("/").toString()

        override fun onOpen(webSocket: WebSocket, response: Response) {
            this.ws = webSocket
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            receivedMessages.add(text)
            val response = msgQueue.removeFirstOrNull()
            if (response != null) webSocket.send(response)
        }

        override fun takeReceivedText(timeoutMs: Long): String? = receivedMessages.poll(timeoutMs, TimeUnit.MILLISECONDS)

        override fun enqueueJson(json: String) {
            msgQueue.add(json)
        }

        override fun enqueue(statusCode: Int, body: String) {
            server.enqueue(MockResponse().setResponseCode(statusCode).setBody(body))
        }

        override fun takeRequest(): RecordedRequest {
            val req = server.takeRequest()
            val headers = req.headers.toMultimap().mapValues { (_, v) -> v.firstOrNull().orEmpty() }
            return RecordedRequest(headers, req.body.readByteArray())
        }

        override fun sendJson(json: String) {
            ws.send(json)
        }

        override fun closeConnection(code: Int, reason: String) {
            ws.close(code, reason)
        }

        override fun allowReconnect() {
            server.enqueue(MockResponse().withWebSocketUpgrade(this))
        }
    }
}
