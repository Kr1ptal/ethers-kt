package io.ethers.providers

import io.ethers.logger.err
import io.ethers.logger.getLogger
import kotlin.jvm.JvmField

/**
 * A rate limit the server reported for a call.
 *
 * @param retryAfterMs how long to wait before retrying, in milliseconds, or null if the server did not say.
 */
data class RateLimit(val retryAfterMs: Long?)

/**
 * Decides which errors are rate limits, and how long to wait before retrying them.
 *
 * A client asks its policy about every error the server returns and stores the answer in [RpcError.rateLimit], so
 * code that retries or throttles calls can read it the same way for every chain and transport. The HTTP status and
 * `Retry-After` header mean the same for every chain, and [HTTP] handles them. Codes and messages differ per chain
 * and provider, so each chain's module has a policy that adds its own on top of [HTTP].
 *
 * Set one with [RpcClientConfig.rateLimitPolicy].
 */
fun interface RateLimitPolicy {
    /** The rate limit [error] reports, or null if it is not a rate limit. */
    fun rateLimit(error: RpcError): RateLimit?

    companion object {
        /**
         * HTTP 429 responses only, with the wait from their `Retry-After` header. Errors in a JSON-RPC response, and
         * every WebSocket error, are not rate limits under this policy.
         */
        @JvmField
        val HTTP: RateLimitPolicy = RateLimitPolicy { error ->
            if (error.isHttpRateLimited) RateLimit(error.retryAfterMs) else null
        }
    }
}

private val LOG = getLogger()

/**
 * This error with [RpcError.rateLimit] set from [policy], or this same error if it is not a rate limit. A policy
 * that throws leaves the error as it is, so a faulty policy can't turn a server error into a different failure.
 */
internal fun RpcError.withRateLimit(policy: RateLimitPolicy): RpcError {
    val rateLimit = try {
        policy.rateLimit(this)
    } catch (e: Exception) {
        LOG.err(e) { "Rate limit policy failed for $this" }
        null
    } ?: return this
    return copy(rateLimit = rateLimit)
}
