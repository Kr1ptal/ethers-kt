package io.ethers.solana

import io.ethers.core.Kotlinx
import io.ethers.providers.HttpClient
import io.ethers.providers.RpcError
import io.ethers.providers.types.RpcRequest
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.providers.middleware.SolanaApi
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.rpc.Commitment
import io.ethers.solana.types.rpc.ContextValue
import io.ethers.solana.types.rpc.SolanaReadConfig
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.jsonObject
import io.ktor.client.HttpClient as KtorHttpClient

/** Doubles every balance, overriding all overloads so neither resolves against [inner]. */
private class DoublingApi(override val inner: SolanaApi) : SolanaApi by inner {
    override fun getBalance(address: SolanaAddress): RpcRequest<ContextValue<BigInteger>, RpcError> = getBalance(address, defaultCommitment)
    override fun getBalance(address: SolanaAddress, commitment: Commitment): RpcRequest<ContextValue<BigInteger>, RpcError> = getBalance(address, SolanaReadConfig(commitment = commitment))
    override fun getBalance(address: SolanaAddress, config: SolanaReadConfig): RpcRequest<ContextValue<BigInteger>, RpcError> = inner.getBalance(address, config).map { ContextValue(it.context, it.value.multiply(bigIntegerOf(2))) }
}

class SolanaMiddlewareTest : FunSpec({
    lateinit var provider: SolanaProvider
    lateinit var ktor: KtorHttpClient

    beforeEach {
        ktor = KtorHttpClient(
            MockEngine { request ->
                val id = Kotlinx.DEFAULT.parseToJsonElement((request.body as TextContent).text).jsonObject.getValue("id")
                respond(
                    """{"jsonrpc":"2.0","id":$id,"result":{"context":{"slot":7},"value":21}}""",
                    HttpStatusCode.OK,
                    headersOf("Content-Type", "application/json"),
                )
            },
        )
        provider = SolanaProvider(HttpClient("https://example.invalid", ktor), defaultCommitment = Commitment.CONFIRMED)
    }
    afterEach {
        provider.close()
        ktor.close()
    }

    test("a delegating layer keeps subscriptions, which used to be lost when wrapping") {
        val wrapped: SolanaApi = DoublingApi(provider)

        // declared on SolanaApi, so delegation carries them through to the provider that owns the socket
        listOf(
            wrapped.subscribeSlot(),
            wrapped.subscribeRoot(),
            wrapped.subscribeLogs(),
            wrapped.subscribeLogs(Commitment.FINALIZED),
            wrapped.subscribeAccount(Programs.SYSTEM),
            wrapped.subscribeProgram(Programs.SYSTEM),
            wrapped.subscribeSignature(SolanaSignature(ByteArray(64) { 1 })),
            wrapped.subscribeSignature(SolanaSignature(ByteArray(64) { 1 }), true),
        ).all { it != null } shouldBe true
    }

    test("an implementation with no provider beneath it fails loudly when subscribing") {
        val httpOnly = object : SolanaApi {
            override val client = provider.client
            override val defaultCommitment = Commitment.CONFIRMED
        }

        // the RPC surface is still fully usable without a provider
        httpOnly.getBalance(Programs.SYSTEM).send().unwrap().value shouldBe bigIntegerOf(21)
        shouldThrow<IllegalStateException> { httpOnly.subscribeSlot() }
    }

    test("a layer can walk to the provider at the bottom of the chain") {
        val wrapped = DoublingApi(provider)
        val twice = DoublingApi(wrapped)

        provider.inner shouldBe null
        provider.provider shouldBe provider
        wrapped.inner shouldBe provider
        wrapped.provider shouldBe provider
        twice.provider shouldBe provider
        twice.client shouldBe provider.client
    }

    test("overriding every overload intercepts the call, and delegated calls do not") {
        val wrapped: SolanaApi = DoublingApi(provider)

        wrapped.getBalance(Programs.SYSTEM).send().unwrap().value shouldBe bigIntegerOf(42)
        wrapped.getBalance(Programs.SYSTEM, Commitment.FINALIZED).send().unwrap().value shouldBe bigIntegerOf(42)
        // not overridden, so it reaches the provider untouched
        provider.getBalance(Programs.SYSTEM).send().unwrap().value shouldBe bigIntegerOf(21)
    }
})
