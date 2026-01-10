package io.ethers.providers

import kotlin.time.Duration

/**
 * WebSocket transport abstraction. The transport is pre-configured with URL and settings.
 * Calling [connect] multiple times on the same instance creates new connections (for reconnection).
 */
interface WebSocketTransport {
    /**
     * Timeout for establishing a connection.
     */
    val connectTimeout: Duration

    /**
     * Timeout for read operations (used for request expiration).
     */
    val readTimeout: Duration

    /**
     * Connect to the WebSocket server.
     *
     * @param listener Callback interface for connection events
     * @return A handle to the active connection
     */
    fun connect(listener: WebSocketListener): WebSocketConnection
}

/**
 * Handle to an active WebSocket connection.
 * Allows sending messages and closing the connection.
 */
interface WebSocketConnection {
    /**
     * Send a text message over the WebSocket.
     *
     * @param text The message to send
     * @return true if the message was enqueued successfully, false otherwise
     */
    fun send(text: String): Boolean

    /**
     * Initiate graceful close of the connection.
     *
     * @param code The WebSocket close code
     * @param reason Human-readable close reason
     * @return true if close was initiated, false if already closing/closed
     */
    fun close(code: Int, reason: String): Boolean
}

/**
 * Callback interface for WebSocket events.
 * All callbacks are invoked on the WebSocket's internal thread.
 */
interface WebSocketListener {
    /**
     * Called when the connection is successfully opened.
     */
    fun onOpen()

    /**
     * Called when a text message is received.
     *
     * @param text The received message
     */
    fun onMessage(text: String)

    /**
     * Called when the remote side initiates a close.
     *
     * @param code The WebSocket close code
     * @param reason Human-readable close reason
     */
    fun onClosing(code: Int, reason: String)

    /**
     * Called when the connection is fully closed.
     *
     * @param code The WebSocket close code
     * @param reason Human-readable close reason
     */
    fun onClosed(code: Int, reason: String)

    /**
     * Called when an error occurs.
     *
     * @param error The exception that occurred
     */
    fun onFailure(error: Throwable)
}
