package io.ethers.ens

import io.ethers.core.types.Address
import io.ethers.ens.EnsResolver.Companion.resolveName
import io.ethers.providers.HttpClient
import io.ethers.providers.Provider
import io.ethers.providers.types.RpcResponse
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private const val MAINNET_HTTP_RPC = "https://ethereum.publicnode.com"

class EnsResolverTest : FunSpec({
    data class EnsNameTestData(
        val ensName: String,
        val nameHash: String = "",
        val resolvedAddr: Address = Address.ZERO,
        val resolverAddr: Address = Address.ZERO,
    )

    context("Ens resolving with instantiated EnsResolver") {
        val provider = Provider(HttpClient(MAINNET_HTTP_RPC))
        val ensResolver = EnsResolver(provider)

        context("Valid ENS names - No wildcard") {
            withData(
                listOf(
                    EnsNameTestData(
                        ensName = "resolver.eth",
                        nameHash = "0x469fbad6482d86a40a35d188cb7f8256302a5d6c50e9071c4f4e9f7604b2cac8",
                        resolvedAddr = Address("0x231b0Ee14048e9dCcD1d247744d114a4EB5E8E63"),
                    ),
                    EnsNameTestData(
                        ensName = "rEsoLvEr.ETh",
                        nameHash = "0x469fbad6482d86a40a35d188cb7f8256302a5d6c50e9071c4f4e9f7604b2cac8",
                        resolvedAddr = Address("0x231b0Ee14048e9dCcD1d247744d114a4EB5E8E63"),
                    ),
                    EnsNameTestData(
                        ensName = "kriptal.eth",
                        nameHash = "0x2c7e9ae2511488eb88232c2f80a48c962fa7e269e5ed5d020e365c9aa614e3de",
                        resolvedAddr = Address("0xefBEf8154B7C5cDB5d1A435bbbf1Adf54980D392"),
                    ),
                ),
            ) {
                ensResolver.resolveName(it.ensName).get().resultOrThrow() shouldBe it.resolvedAddr
            }
        }

        context("Valid ENS names - Offchain") {
            withData(
                listOf(
                    EnsNameTestData(
                        ensName = "1.offchainexample.eth",
                        resolvedAddr = Address("0x41563129cDbbD0c5D3e1c86cf9563926b243834d"),
                    ),
                ),
            ) {
                ensResolver.resolveName(it.ensName).get().resultOrThrow() shouldBe it.resolvedAddr
            }
        }

        // ERROR TESTING

        /**
         * Testing [EnsResolver.Error.EnsNameInvalid]
         */
        test("Invalid ENS names") {
            ensResolver.resolveName("").get().error.shouldBeInstanceOf<EnsResolver.Error.EnsNameInvalid>()
            ensResolver.resolveName("\t").get().error.shouldBeInstanceOf<EnsResolver.Error.EnsNameInvalid>()
            ensResolver.resolveName(".").get().error.shouldBeInstanceOf<EnsResolver.Error.EnsNameInvalid>()
            ensResolver.resolveName("\n.").get().error.shouldBeInstanceOf<EnsResolver.Error.EnsNameInvalid>()

            provider.resolveName("").get().error.shouldBeInstanceOf<EnsResolver.Error.EnsNameInvalid>()
            provider.resolveName("\t").get().error.shouldBeInstanceOf<EnsResolver.Error.EnsNameInvalid>()
            provider.resolveName(".").get().error.shouldBeInstanceOf<EnsResolver.Error.EnsNameInvalid>()
            provider.resolveName("\n.").get().error.shouldBeInstanceOf<EnsResolver.Error.EnsNameInvalid>()
        }

        /**
         * Testing [EnsResolver.Error.Normalisation]
         */
        test("Failed normalisation") {
            ensResolver.resolveName("xn--u-ccb.com").get().error.shouldBeInstanceOf<EnsResolver.Error.Normalisation>()

            provider.resolveName("xn--u-ccb.com").get().error.shouldBeInstanceOf<EnsResolver.Error.Normalisation>()
        }

        /**
         * Testing [EnsResolver.Error.UnknownResolver]
         */
        context("Resolver not found") {
            withData(
                listOf(
                    EnsNameTestData(
                        ensName = "123.kriptalABC.et",
                        nameHash = "0x469fbad6482d86a40a35d188cb7f8256302a5d6c50e9071c4f4e9f7604b2cac8",
                    ),
                ),
            ) {
                ensResolver.resolveName(it.ensName).get().error.shouldBeInstanceOf<EnsResolver.Error.UnknownResolver>()
                provider.resolveName(it.ensName).get().error.shouldBeInstanceOf<EnsResolver.Error.UnknownResolver>()
            }
        }

        /**
         * Testing [EnsResolver.Error.UnknownEnsName]
         */
        context("Resolver not found") {
            fun testError(error: RpcResponse.Error?, testData: EnsNameTestData) {
                error.shouldBeInstanceOf<EnsResolver.Error.UnknownEnsName>()
                error.resolverAddr shouldBe testData.resolverAddr
                error.nameHash shouldBe testData.nameHash
            }

            withData(
                listOf(
                    EnsNameTestData(
                        ensName = "123.kriptal.eth",
                        nameHash = "0x469fbad6482d86a40a35d188cb7f8256302a5d6c50e9071c4f4e9f7604b2cac8",
                        resolverAddr = Address("0x231b0Ee14048e9dCcD1d247744d114a4EB5E8E63"), // PublicResolver
                    ),
                ),
            ) {
                val res = ensResolver.resolveName(it.ensName).get()
                testError(res.error, it)
                testError(provider.resolveName(it.ensName).get().error, it)
            }
        }
    }
})
