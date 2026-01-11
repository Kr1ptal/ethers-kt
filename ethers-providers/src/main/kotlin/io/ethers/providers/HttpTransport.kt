package io.ethers.providers

import java.io.InputStream

/**
 * HTTP transport abstraction. The transport is pre-configured with URL and headers.
 */
interface HttpTransport {
    /**
     * Execute an HTTP POST request with the given body.
     *
     * @param body The request body bytes
     * @param callback Callback for handling the response
     */
    fun execute(body: ByteArray, callback: HttpCallback)
}

/**
 * Callback interface for HTTP responses.
 */
interface HttpCallback {
    /**
     * Called when a response is received (may be successful or not).
     *
     * @param response The HTTP response
     */
    fun onResponse(response: HttpResponse)

    /**
     * Called when the request fails (network error, etc.).
     *
     * @param error The exception that occurred
     */
    fun onFailure(error: Throwable)
}

/**
 * HTTP response abstraction.
 */
interface HttpResponse : AutoCloseable {
    /**
     * Whether the response was successful (2xx status code).
     */
    val isSuccessful: Boolean

    /**
     * The HTTP status code.
     */
    val code: Int

    /**
     * The HTTP status message.
     */
    val message: String

    /**
     * The response body as an input stream.
     */
    val body: InputStream
}
