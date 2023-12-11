package io.ethers.ens

import io.ethers.EnsResolver
import io.ethers.core.types.Address
import io.ethers.providers.HttpClient
import io.ethers.providers.Provider
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private const val MAINNET_HTTP_RPC = "https://ethereum.publicnode.com"

class EnsResolverTest : FunSpec({

    context("Ens resolving with instantiated EnsResolver") {

        val provider = Provider(HttpClient(MAINNET_HTTP_RPC))
        val ensResolver = EnsResolver(provider)

        context("Valid ENS names") {
            withData(
                listOf(
                    "resolver.eth" to Address("0x231b0Ee14048e9dCcD1d247744d114a4EB5E8E63"),
                    "RESOLVER.eth" to Address("0x231b0Ee14048e9dCcD1d247744d114a4EB5E8E63"),
                ),
            ) {
                ensResolver.resolveName(it.first).resultOrThrow() shouldBe it.second
            }
        }

        test("Invalid ENS names") {
            ensResolver.resolveName("").error.shouldBeInstanceOf<EnsResolver.Error.EnsNameInvalid>()
            ensResolver.resolveName("\t").error.shouldBeInstanceOf<EnsResolver.Error.EnsNameInvalid>()
            ensResolver.resolveName(".").error.shouldBeInstanceOf<EnsResolver.Error.EnsNameInvalid>()
            ensResolver.resolveName("\n.").error.shouldBeInstanceOf<EnsResolver.Error.EnsNameInvalid>()
        }

        context("Non-existent ens names") {
            withData(
                listOf(
                    "123.kriptal.eth",
                ),
            ) {
                ensResolver.resolveName(it).error.shouldBeInstanceOf<EnsResolver.Error.FailedToResolve>()
            }
        }
    }
})
