package io.ethers.ens

import io.ethers.EnsResolver
import io.ethers.EnsResolver.Companion.resolveName
import io.ethers.core.types.Address
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
        val nameHash: String,
        val address: Address = Address.ZERO,
    )

    context("Ens resolving with instantiated EnsResolver") {
        // Todo mock provider
        val provider = Provider(HttpClient(MAINNET_HTTP_RPC))
        val ensResolver = EnsResolver(provider)

        context("Valid ENS names") {
            withData(
                listOf(
                    EnsNameTestData(
                        ensName = "resolver.eth",
                        nameHash = "0x469fbad6482d86a40a35d188cb7f8256302a5d6c50e9071c4f4e9f7604b2cac8",
                        address = Address("0x231b0Ee14048e9dCcD1d247744d114a4EB5E8E63"),
                    ),
                    EnsNameTestData(
                        ensName = "rEsoLvEr.ETh",
                        nameHash = "0x469fbad6482d86a40a35d188cb7f8256302a5d6c50e9071c4f4e9f7604b2cac8",
                        address = Address("0x231b0Ee14048e9dCcD1d247744d114a4EB5E8E63"),
                    ),
                ),
            ) {
                ensResolver.resolveName(it.ensName).get().resultOrThrow() shouldBe it.address

                provider.resolveName(it.ensName).get().resultOrThrow() shouldBe it.address
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
        context("Non-existent ens names") {
            fun testError(error: RpcResponse.Error?, testData: EnsNameTestData) {
                error.shouldBeInstanceOf<EnsResolver.Error.UnknownResolver>()
                error.nameHash shouldBe testData.nameHash
                error.registryAddress shouldBe EnsResolver.getRegistryAddress(provider.chainId)
            }

            withData(
                listOf(
                    EnsNameTestData(
                        ensName = "123.kriptal.eth",
                        nameHash = "0x469fbad6482d86a40a35d188cb7f8256302a5d6c50e9071c4f4e9f7604b2cac8",
                    ),
                ),
            ) {
                testError(ensResolver.resolveName(it.ensName).get().error, it)
                testError(provider.resolveName(it.ensName).get().error, it)
            }
        }
    }
})
