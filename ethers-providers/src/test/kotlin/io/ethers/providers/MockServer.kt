package io.ethers.providers

import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

interface MockServer {
    val url: String

    fun enqueueJson(json: String)
    fun enqueue(response: MockResponse)
    fun takeRequest(): RecordedRequest
}

interface MockWSServer : MockServer {
    fun sendJson(json: String)
}

/**
 * Create [MockServer] that supports enqueuing mock requests.
 * */
fun mockServerHttp(): MockServer {
    val server = MockWebServer()
    server.start()

    return object : MockServer {
        override val url: String
            get() = server.url("/").toString()

        override fun enqueueJson(json: String) {
            server.enqueue(MockResponse().setResponseCode(200).setBody(json))
        }

        override fun enqueue(response: MockResponse) {
            server.enqueue(response)
        }

        override fun takeRequest(): RecordedRequest {
            return server.takeRequest()
        }
    }
}

/**
 * Create a [MockWSServer] that automatically upgrade the connection to Websockets and supports sending WS text
 * messages.
 * */
fun mockServerWebsocket(): MockWSServer {
    val server = MockWebServer()
    server.start()

    return object : WebSocketListener(), MockWSServer {
        private val msgQueue = ArrayDeque<String>()
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
            webSocket.send(msgQueue.removeFirst())
        }

        override fun enqueueJson(json: String) {
            msgQueue.add(json)
        }

        override fun enqueue(response: MockResponse) {
            server.enqueue(response)
        }

        override fun takeRequest(): RecordedRequest {
            return server.takeRequest()
        }

        override fun sendJson(json: String) {
            ws.send(json)
        }
    }
}
