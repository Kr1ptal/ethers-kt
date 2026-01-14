package io.ethers.providers

import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okio.ByteString
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import okhttp3.WebSocketListener as OkHttpWebSocketListener

/**
 * OkHttp-based implementation of [WebSocketTransport].
 */
class OkHttpWebSocketTransport(
    private val url: String,
    private val headers: Map<String, String>,
    private val client: OkHttpClient,
) : WebSocketTransport {

    override val connectTimeout: Duration
        get() = client.connectTimeoutMillis.toLong().milliseconds

    override val readTimeout: Duration
        get() = client.readTimeoutMillis.toLong().milliseconds

    override fun connect(listener: WebSocketListener): WebSocketConnection {
        val requestHeaders = Headers.Builder().apply {
            headers.forEach { (key, value) -> add(key, value) }
        }.build()
        val wsRequest = Request.Builder().url(url).headers(requestHeaders).build()

        val okHttpListener = object : OkHttpWebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                listener.onMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // will trigger "onFailure" callback
                throw IOException("Binary messages are not supported")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                listener.onClosing(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onClosed(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onFailure(t)
            }
        }

        val webSocket = client.newWebSocket(wsRequest, okHttpListener)
        return OkHttpWebSocketConnection(webSocket)
    }

    private class OkHttpWebSocketConnection(private val webSocket: WebSocket) : WebSocketConnection {
        override fun send(text: String): Boolean = webSocket.send(text)
        override fun close(code: Int, reason: String): Boolean = webSocket.close(code, reason)
    }
}
