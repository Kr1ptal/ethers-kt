package io.ethers.solana.providers

import io.ethers.providers.RateLimit
import io.ethers.providers.RateLimitPolicy
import io.ethers.providers.RpcError
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.ceil

/**
 * [RateLimitPolicy] for Solana JSON-RPC providers, used by default for clients made by [SolanaProvider.builder].
 *
 * On top of [RateLimitPolicy.HTTP] it finds rate limits that providers report in a JSON-RPC error, which is the only
 * way they can over WebSocket:
 * - codes that only providers' rate limits use: 429 (public endpoints, Alchemy), -32029 (QuickNode), -32090 (Ankr)
 *   and -32429 (Helius)
 * - the messages providers use for rate limits, whatever their code. Providers reuse codes that the validator's own
 *   RPC uses for errors that are not rate limits, such as -32005 (node unhealthy, but Helius' and Chainstack's rate
 *   limit) and -32007 (skipped slot, but QuickNode's rate limit), so only the message tells them apart.
 *
 * An error that says a quota has run out, such as a monthly one, is not a rate limit, even with HTTP 429: waiting for
 * a few seconds won't get past it.
 *
 * The wait is the `Retry-After` header's, or else Chainstack's `try_again_in` in the error's data (e.g.
 * `"292.270667ms"`), or else a "retry after N seconds" in the message.
 */
object SolanaRateLimitPolicy : RateLimitPolicy {
    private val RATE_LIMIT_CODES = intArrayOf(429, -32029, -32090, -32429)

    private val MESSAGE_FRAGMENTS = listOf(
        "too many requests", // public endpoints, Helius
        "rate limit", // Helius' "rate limited", the public endpoints' "exceeding the rate limits"
        "request limit reached", // QuickNode
        "exceeded the rps limit", // Chainstack
        "compute units per second", // Alchemy
    )

    private val QUOTA_FRAGMENTS = listOf(
        "max usage", // Helius
        "monthly", // Alchemy's "Monthly capacity limit exceeded", Chainstack's "monthly quota"
        "quota",
        "capacity limit exceeded",
    )

    private val DURATION = Regex("""^\s*(\d+(?:\.\d+)?)\s*(ms|s)\s*$""", RegexOption.IGNORE_CASE)
    private val MESSAGE_WAIT = Regex("""retry after (\d+(?:\.\d+)?) ?(ms|milliseconds?|s|seconds?)\b""", RegexOption.IGNORE_CASE)

    override fun rateLimit(error: RpcError): RateLimit? {
        val message = error.message
        if (QUOTA_FRAGMENTS.any { message.contains(it, ignoreCase = true) }) return null

        val isRateLimited = error.isHttpRateLimited ||
            error.code in RATE_LIMIT_CODES ||
            MESSAGE_FRAGMENTS.any { message.contains(it, ignoreCase = true) }
        if (!isRateLimited) return null

        return RateLimit(error.retryAfterMs ?: dataWaitMs(error) ?: messageWaitMs(message))
    }

    /** Chainstack's `{"try_again_in": "292.270667ms"}` error data. */
    private fun dataWaitMs(error: RpcError): Long? {
        val value = ((error.data as? JsonObject)?.get("try_again_in") as? JsonPrimitive)?.takeIf { it.isString } ?: return null
        val match = DURATION.matchEntire(value.content) ?: return null
        return toMs(match.groupValues[1], match.groupValues[2])
    }

    private fun messageWaitMs(message: String): Long? {
        val match = MESSAGE_WAIT.find(message) ?: return null
        return toMs(match.groupValues[1], match.groupValues[2])
    }

    // rounded up, so a retry never comes before the wait is over
    private fun toMs(amount: String, unit: String): Long? {
        val value = amount.toDoubleOrNull() ?: return null
        val ms = if (unit.startsWith("m", ignoreCase = true)) value else value * 1000
        return ceil(ms).toLong()
    }
}
