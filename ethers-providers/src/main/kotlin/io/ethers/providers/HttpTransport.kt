package io.ethers.providers

import java.io.InputStream
import java.util.concurrent.CompletableFuture

/**
 * HTTP transport abstraction. The transport is pre-configured with URL and headers.
 */
interface HttpTransport {
    /**
     * Execute an HTTP POST request with the given body.
     *
     * @param body The request body bytes
     * @return A future that completes with the result
     */
    fun execute(body: ByteArray): CompletableFuture<HttpResult>
}

/**
 * Result of an HTTP request.
 */
sealed class HttpResult {
    /**
     * Successful HTTP response (2xx status code).
     */
    class Success(val body: InputStream) : HttpResult()

    /**
     * HTTP error response (non-2xx status code).
     */
    class HttpError(val code: Int, val message: String, val body: InputStream) : HttpResult()

    /**
     * Network or other failure (no response received).
     */
    class Failure(val error: Throwable) : HttpResult()
}
