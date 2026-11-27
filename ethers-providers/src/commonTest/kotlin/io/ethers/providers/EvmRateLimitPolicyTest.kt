package io.ethers.providers

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class EvmRateLimitPolicyTest : FunSpec({
    test("finds the rate limits EVM providers report") {
        data class TestCase(val error: RpcError, val expected: RateLimit?)

        Exhaustive.of(
            // HTTP 429, whatever the error, with its Retry-After
            TestCase(RpcError(RpcError.CODE_CALL_FAILED, "HTTP 429: Too Many Requests", httpStatus = 429, retryAfterMs = 2000L), RateLimit(2000L)),
            TestCase(RpcError(-32000, "server busy", httpStatus = 429), RateLimit(null)),
            // Alchemy
            TestCase(RpcError(429, "Your app has exceeded its compute units per second capacity."), RateLimit(null)),
            // Alchemy with HTTP 429 and Retry-After
            TestCase(RpcError(429, "Your app has exceeded its compute units per second capacity.", httpStatus = 429, retryAfterMs = 1000L), RateLimit(1000L)),
            // Infura
            TestCase(RpcError(-32005, "daily request count exceeded, request rate limited"), RateLimit(null)),
            TestCase(RpcError(-32005, "project ID request rate exceeded"), RateLimit(null)),
            // QuickNode
            TestCase(RpcError(-32007, "15/second request limit reached - reduce calls per second"), RateLimit(null)),
            TestCase(RpcError(-32000, "Too Many Requests"), RateLimit(null)),
            TestCase(RpcError(-32000, "Rate-limited, try again later"), RateLimit(null)),
            // -32005 alone is a limit that retrying won't get past
            TestCase(RpcError(-32005, "query returned more than 10000 results"), null),
            TestCase(RpcError(RpcError.CODE_CALL_FAILED, "HTTP 503: Service Unavailable", httpStatus = 503), null),
            TestCase(RpcError(RpcError.CODE_EXECUTION_ERROR, "execution reverted"), null),
            TestCase(RpcError(-32000, "exceeds block gas limit"), null),
        ).checkAll { (error, expected) ->
            EvmRateLimitPolicy.rateLimit(error) shouldBe expected
        }
    }

    context("Provider.builder") {
        val infuraRateLimit = """{"jsonrpc":"2.0","id":1,"error":{"code":-32005,"message":"project ID request rate exceeded"}}"""

        test("uses the EVM policy by default") {
            val server = mockServerHttp()
            val provider = Provider.builder(server.url).build(1L).unwrap()
            try {
                server.enqueueJson(infuraRateLimit)

                val error = provider.getBlockNumber().send().unwrapError()

                error.rateLimit shouldBe RateLimit(null)
            } finally {
                provider.close()
                server.stop()
            }
        }

        test("uses a policy set on the builder instead") {
            val server = mockServerHttp()
            val provider = Provider.builder(server.url).rateLimitPolicy(RateLimitPolicy.HTTP).build(1L).unwrap()
            try {
                server.enqueueJson(infuraRateLimit)

                val error = provider.getBlockNumber().send().unwrapError()

                error.rateLimit shouldBe null
            } finally {
                provider.close()
                server.stop()
            }
        }

        test("uses a policy set on the config, and leaves a config without one unchanged") {
            val custom = RateLimitPolicy { RateLimit(42L) }
            val withPolicy = RpcClientConfig().rateLimitPolicy(custom)
            val withoutPolicy = RpcClientConfig()

            val server = mockServerHttp()
            val customProvider = Provider.builder(server.url).config(withPolicy).build(1L).unwrap()
            val defaultProvider = Provider.builder(server.url).config(withoutPolicy).build(1L).unwrap()
            try {
                server.enqueueJson(infuraRateLimit)
                customProvider.getBlockNumber().send().unwrapError().rateLimit shouldBe RateLimit(42L)

                server.enqueueJson(infuraRateLimit)
                defaultProvider.getBlockNumber().send().unwrapError().rateLimit shouldBe RateLimit(null)

                // the caller's config may also be used for another chain, so the builder doesn't set its policy there
                withoutPolicy.rateLimitPolicy shouldBe null
            } finally {
                customProvider.close()
                defaultProvider.close()
                server.stop()
            }
        }
    }
})
