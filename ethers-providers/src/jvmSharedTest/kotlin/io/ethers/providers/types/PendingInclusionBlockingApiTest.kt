package io.ethers.providers.types

import io.ethers.core.isFailure
import io.ethers.core.types.Hash
import io.ethers.providers.Provider
import io.ethers.providers.mockServerHttp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.time.Duration.Companion.milliseconds

/**
 * `awaitInclusion` is the blocking wrapper supplied by the JVM platform seam, so it cannot be exercised from
 * commonTest. PendingTransactionTest covers the retry and confirmation logic through the suspending `inclusion`
 * that this delegates to; the only thing left to check here is that the wrapper itself runs it to completion and
 * returns the same result.
 */
class PendingInclusionBlockingApiTest : FunSpec({
    test("awaitInclusion runs the suspending inclusion to completion") {
        val server = mockServerHttp()
        val provider = Provider.builder(server.url).build(chainId = 999999).unwrap()
        val pendingTransaction = PendingTransaction(
            Hash("0xce15f8ce74845b0d254fcbfda722ba89976ca6e09936d6761a648a6492b82e9b"),
            provider,
        )

        // never return a receipt, so the retries are exhausted and the call fails rather than hanging
        val retries = 2
        repeat(retries) {
            server.enqueueJson("""{"jsonrpc":"2.0","id":1,"result":null}""")
        }

        val result = pendingTransaction.awaitInclusion(retries, 10.milliseconds, 0)

        result.isFailure() shouldBe true
        result.unwrapError().shouldBeInstanceOf<PendingInclusion.Error>()

        server.stop()
    }
})
