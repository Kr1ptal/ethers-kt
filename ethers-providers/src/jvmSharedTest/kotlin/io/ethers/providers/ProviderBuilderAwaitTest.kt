package io.ethers.providers

import io.ethers.core.isFailure
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * The blocking terminal comes from the platform seam, so it can only be exercised where `runBlocking` exists.
 * Everything else about the builder is covered in commonTest.
 */
class ProviderBuilderAwaitTest : FunSpec({
    test("buildAwait reports the rpc failure rather than throwing") {
        // nothing is listening, so eth_chainId cannot succeed
        val result = Provider.builder("http://localhost:1").buildAwait()

        result.isFailure() shouldBe true
        result.unwrapError().shouldBeInstanceOf<Provider.UnableToGetChainId>()
    }

    test("buildAwait rejects an unsupported protocol before any RPC call") {
        Provider.builder("ftp://localhost").buildAwait()
            .unwrapError().shouldBeInstanceOf<Provider.UnsupportedUrlProtocol>()
    }
})
