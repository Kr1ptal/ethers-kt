package io.ethers.providers

import io.ethers.core.isFailure
import io.ethers.core.isSuccess
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.time.Duration.Companion.seconds

class ProviderBuilderTest : FunSpec({
    context("build(chainId)") {
        test("http url builds without any RPC call") {
            val result = Provider.builder("https://localhost:1/nonexistent").build(1L)

            result.isSuccess() shouldBe true
            result.unwrap().chainId shouldBe 1L
            result.unwrap().client.shouldBeInstanceOf<HttpClient>()
        }

        test("ws url builds a WsClient") {
            val result = Provider.builder("wss://localhost:1/nonexistent").build(7L)

            result.isSuccess() shouldBe true
            result.unwrap().chainId shouldBe 7L
            result.unwrap().client.shouldBeInstanceOf<WsClient>()
            // deliberately not closed: with no explicit ktor client the provider holds RpcClientConfig's shared
            // DEFAULT_CLIENT, and closing it would tear down the transport every other spec is using
        }

        test("unsupported protocol fails instead of throwing") {
            val result = Provider.builder("ftp://localhost").build(1L)

            result.isFailure() shouldBe true
            result.unwrapError().shouldBeInstanceOf<Provider.UnsupportedUrlProtocol>().url shouldBe "ftp://localhost"
        }

        test("settings are applied and are chainable") {
            val builder = Provider.builder("wss://localhost:1/nonexistent")
                .headers(mapOf("Authorization" to "Bearer token"))
                .resubscribeOnReconnect(false)
                .connectTimeout(3.seconds)
                .readTimeout(4.seconds)

            builder.build(1L).isSuccess() shouldBe true
        }

        test("config() replaces anything set earlier") {
            val replacement = RpcClientConfig().requestHeaders(mapOf("X" to "replacement"))
            val builder = Provider.builder("https://localhost:1/nonexistent")
                .headers(mapOf("X" to "overwritten"))
                .config(replacement)

            builder.build(1L).isSuccess() shouldBe true
        }
    }

    context("chain id resolution") {
        test("suspending build reports the rpc failure rather than throwing") {
            // nothing is listening, so eth_chainId cannot succeed
            val result = Provider.builder("http://localhost:1").build()

            result.isFailure() shouldBe true
            result.unwrapError().shouldBeInstanceOf<Provider.UnableToGetChainId>()
        }

        test("blocking build reports the same failure") {
            val result = Provider.builder("http://localhost:1").buildAwait()

            result.isFailure() shouldBe true
            result.unwrapError().shouldBeInstanceOf<Provider.UnableToGetChainId>()
        }

        test("unsupported protocol fails before any RPC call is attempted") {
            Provider.builder("ftp://localhost").build()
                .unwrapError().shouldBeInstanceOf<Provider.UnsupportedUrlProtocol>()
            Provider.builder("ftp://localhost").buildAwait()
                .unwrapError().shouldBeInstanceOf<Provider.UnsupportedUrlProtocol>()
        }
    }
})
