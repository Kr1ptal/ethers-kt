package io.ethers.solana

import io.ethers.core.Kotlinx
import io.ethers.providers.RateLimit
import io.ethers.providers.RpcError
import io.ethers.solana.providers.SolanaRateLimitPolicy
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class SolanaRateLimitPolicyTest : FunSpec({
    fun data(json: String) = Kotlinx.DEFAULT.parseToJsonElement(json)

    test("finds the rate limits Solana providers report") {
        data class TestCase(val name: String, val error: RpcError, val expected: RateLimit?)

        Exhaustive.of(
            TestCase(
                "HTTP 429 with Retry-After",
                RpcError(RpcError.CODE_CALL_FAILED, "HTTP 429: Too Many Requests", httpStatus = 429, retryAfterMs = 10_000L),
                RateLimit(10_000L),
            ),
            TestCase(
                "public endpoints",
                RpcError(429, "Too many requests for a specific RPC call, contact your app developer or support@rpcpool.com."),
                RateLimit(null),
            ),
            TestCase("Helius", RpcError(-32005, "Too many requests"), RateLimit(null)),
            TestCase("Helius, undocumented code", RpcError(-32429, "rate limited"), RateLimit(null)),
            TestCase("QuickNode per second", RpcError(-32007, "100/second request limit reached - reduce calls per second"), RateLimit(null)),
            TestCase("QuickNode code alone", RpcError(-32029, "Rate limit exceeded"), RateLimit(null)),
            TestCase("Ankr", RpcError(-32090, "Too many requests"), RateLimit(null)),
            TestCase(
                "Alchemy",
                RpcError(429, "Your app has exceeded its compute units per second capacity.", httpStatus = 429, retryAfterMs = 1000L),
                RateLimit(1000L),
            ),
            TestCase(
                "Chainstack, with its wait in the data",
                RpcError(-32005, "You've exceeded the RPS limit available on the current plan.", data("""{"try_again_in":"292.270667ms"}""")),
                RateLimit(293L),
            ),
            TestCase(
                "Chainstack wait in seconds",
                RpcError(-32005, "You've exceeded the RPS limit available on the current plan.", data("""{"try_again_in":"1.5s"}""")),
                RateLimit(1500L),
            ),
            TestCase(
                "the Retry-After header wins over the data",
                RpcError(-32005, "You've exceeded the RPS limit", data("""{"try_again_in":"5s"}"""), httpStatus = 429, retryAfterMs = 2000L),
                RateLimit(2000L),
            ),
            TestCase(
                "unparseable data has no wait",
                RpcError(-32005, "You've exceeded the RPS limit", data("""{"try_again_in":"soon"}""")),
                RateLimit(null),
            ),
            TestCase("wait in the message", RpcError(429, "Too many requests. Retry after 2 seconds."), RateLimit(2000L)),
        ).checkAll { (_, error, expected) ->
            SolanaRateLimitPolicy.rateLimit(error) shouldBe expected
        }
    }

    test("errors that are not rate limits, though some look like limits or reuse a provider's code") {
        Exhaustive.of(
            // the validator's own errors under the codes providers reuse
            RpcError(-32005, "Node is behind by 42 slots"),
            RpcError(-32005, "Node is unhealthy"),
            RpcError(-32007, "Slot 123 was skipped, or missing due to ledger jump to recent snapshot"),
            RpcError(-32008, "No snapshot"),
            RpcError(-32011, "Transaction history is not available from this node"),
            // limits that retrying won't get past
            RpcError(-32602, "Slot range too large; max 500000"),
            RpcError(-32602, "Too many inputs provided; max 100"),
            RpcError(-32603, "Internal Error: Subscription refused. Node subscription limit reached"),
            // quotas, even with HTTP 429
            RpcError(-32429, "max usage reached"),
            RpcError(-32005, "You've reached your monthly quota of Request Units (RUs)", httpStatus = 429),
            RpcError(-32600, "Monthly capacity limit exceeded."),
            RpcError(RpcError.CODE_CALL_FAILED, "HTTP 403: Forbidden", httpStatus = 403),
        ).checkAll { error ->
            SolanaRateLimitPolicy.rateLimit(error) shouldBe null
        }
    }
})
