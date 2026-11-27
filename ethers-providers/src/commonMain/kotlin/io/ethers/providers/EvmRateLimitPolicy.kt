package io.ethers.providers

/**
 * [RateLimitPolicy] for EVM JSON-RPC providers, used by default for clients made by [Provider.builder].
 *
 * On top of [RateLimitPolicy.HTTP] it finds rate limits that providers report in a JSON-RPC error, which is the only
 * way they can over WebSocket: the error code 429 that some providers use (e.g. Alchemy), and the messages providers
 * use to report rate limits (e.g. Infura, QuickNode), whatever their code.
 *
 * The EIP-1474 "limit exceeded" code -32005 alone is not a rate limit, because providers also return it for limits that
 * retrying won't get past, such as an `eth_getLogs` query that matches too many logs.
 */
object EvmRateLimitPolicy : RateLimitPolicy {
    /** Code some providers (e.g. Alchemy) use for a rate-limited call, mirroring HTTP 429. */
    const val CODE_RATE_LIMITED = 429

    private val MESSAGE_FRAGMENTS = listOf(
        "rate limit", // "rate limited", "rate limit exceeded", Infura's "request rate limited"
        "rate-limit",
        "ratelimit",
        "too many requests",
        "request rate", // Infura's "project ID request rate exceeded"
        "request limit reached", // QuickNode
        "compute units per second", // Alchemy
    )

    override fun rateLimit(error: RpcError): RateLimit? {
        RateLimitPolicy.HTTP.rateLimit(error)?.let { return it }

        val isRateLimited = error.code == CODE_RATE_LIMITED ||
            MESSAGE_FRAGMENTS.any { error.message.contains(it, ignoreCase = true) }
        return if (isRateLimited) RateLimit(error.retryAfterMs) else null
    }
}
