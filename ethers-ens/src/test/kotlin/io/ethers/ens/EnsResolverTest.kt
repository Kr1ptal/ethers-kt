package io.ethers.ens

import io.ethers.core.types.Address
import io.ethers.ens.EnsResolver.Companion.resolveName
import io.ethers.ens.EnsResolver.Companion.resolveText
import io.ethers.ens.EnsResolver.Companion.reverseLookup
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
        val ensName: String = "",
        val nameHash: String = "",
        val resolverAddr: Address = Address.ZERO,
        val resolvedAddr: Address = Address.ZERO,
        val key: String = "",
        val resolvedRecord: String = "",
    )

    context("Init provider and resolver") {
        val provider = Provider(HttpClient(MAINNET_HTTP_RPC))
        val ensResolver = EnsResolver(provider)

        context("To address") {
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
                    provider.resolveName(it.ensName).get().resultOrThrow() shouldBe it.resolvedAddr
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
                    provider.resolveName(it.ensName).get().resultOrThrow() shouldBe it.resolvedAddr
                }
            }
        }

        context("To text") {
            context("Valid ENS names - No wildcard") {
                withData(
                    listOf(
                        EnsNameTestData(
                            ensName = "luc.eth",
                            key = "email",
                            resolvedRecord = "luc@lucemans.nl",
                        ),
                    ),
                ) {
                    ensResolver.resolveText(it.ensName, it.key).get().resultOrThrow() shouldBe it.resolvedRecord
                }
            }

            context("Valid ENS names - Offchain") {
                withData(
                    listOf(
                        EnsNameTestData(
                            ensName = "1.offchainexample.eth",
                            key = "email",
                            resolvedRecord = "nick@ens.domains",
                        ),
                    ),
                ) {
                    ensResolver.resolveText(it.ensName, it.key).get().resultOrThrow() shouldBe it.resolvedRecord
                    provider.resolveText(it.ensName, it.key).get().resultOrThrow() shouldBe it.resolvedRecord
                }
            }
        }

        context("Reverse resolution") {
            context("Valid reverse lookup addresses") {
                withData(
                    listOf(
                        EnsNameTestData(
                            ensName = "registrar.firefly.eth",
                            resolvedAddr = Address("0x6fC21092DA55B392b045eD78F4732bff3C580e2c"),
                        ),
                    ),
                ) {
                    ensResolver.reverseLookup(it.resolvedAddr).get().resultOrThrow() shouldBe it.ensName
                    provider.reverseLookup(it.resolvedAddr).get().resultOrThrow() shouldBe it.ensName
                }
            }
        }

        context("Testing errors") {
            val key = "email"

            /**
             * Testing [EnsResolver.Error.EnsNameInvalid]
             */
            test("Invalid ENS names") {
                listOf("", "\t", ".", "\n.").forEach {
                    ensResolver.resolveName(it).get().error.shouldBeInstanceOf<EnsResolver.Error.EnsNameInvalid>()
                    provider.resolveName(it).get().error.shouldBeInstanceOf<EnsResolver.Error.EnsNameInvalid>()

                    ensResolver.resolveText(it, key).get().error.shouldBeInstanceOf<EnsResolver.Error.EnsNameInvalid>()
                    provider.resolveText(it, key).get().error.shouldBeInstanceOf<EnsResolver.Error.EnsNameInvalid>()
                }
            }

            /**
             * Testing [EnsResolver.Error.Normalisation]
             */
            test("Failed normalisation") {
                ensResolver.resolveName("xn--u-ccb.com")
                    .get().error.shouldBeInstanceOf<EnsResolver.Error.Normalisation>()
                provider.resolveName("xn--u-ccb.com")
                    .get().error.shouldBeInstanceOf<EnsResolver.Error.Normalisation>()

                ensResolver.resolveText("xn--u-ccb.com", key)
                    .get().error.shouldBeInstanceOf<EnsResolver.Error.Normalisation>()
                provider.resolveText("xn--u-ccb.com", key)
                    .get().error.shouldBeInstanceOf<EnsResolver.Error.Normalisation>()
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
                    ensResolver.resolveName(it.ensName)
                        .get().error.shouldBeInstanceOf<EnsResolver.Error.UnknownResolver>()
                    provider.resolveName(it.ensName)
                        .get().error.shouldBeInstanceOf<EnsResolver.Error.UnknownResolver>()

                    ensResolver.resolveText(it.ensName, key)
                        .get().error.shouldBeInstanceOf<EnsResolver.Error.UnknownResolver>()
                    provider.resolveText(it.ensName, key)
                        .get().error.shouldBeInstanceOf<EnsResolver.Error.UnknownResolver>()

                    ensResolver.reverseLookup(Address.ZERO).get().error.shouldBeInstanceOf<EnsResolver.Error.UnknownResolver>()
                    provider.reverseLookup(Address.ZERO).get().error.shouldBeInstanceOf<EnsResolver.Error.UnknownResolver>()
                }
            }

            /**
             * Testing [EnsResolver.Error.UnsupportedSelector]
             */
            context("Unsupported selector") {
                withData(
                    listOf(
                        EnsNameTestData(
                            nameHash = "0x469fbad6482d86a40a35d188cb7f8256302a5d6c50e9071c4f4e9f7604b2cac8",
                        ),
                    ),
                ) {
                    // TODO: tests for other resolutions when mocking
                    // address with invalid resolver (WETH token 0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2 as resolver)
                    val addr = Address("0x30c9223d9e3d23e0af1073a38e0834b055bf68ed")
                    ensResolver.reverseLookup(addr).get().error.shouldBeInstanceOf<EnsResolver.Error.UnsupportedSelector>()
                    provider.reverseLookup(addr).get().error.shouldBeInstanceOf<EnsResolver.Error.UnsupportedSelector>()
                }
            }

            /**
             * Testing unknown ENS name (zero address, empty record)
             */
            context("Resolve to NULL") {
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
                    testError(ensResolver.resolveName(it.ensName).get().error, it)
                    testError(provider.resolveName(it.ensName).get().error, it)

                    ensResolver.resolveText(it.ensName, "").get().resultOrThrow() shouldBe ""
                    provider.resolveText(it.ensName, "").get().resultOrThrow() shouldBe ""
                }
            }

            /**
             * Testing [EnsResolver.Error.InvalidReverseENSName]
             */
            // TODO: when mocking
            context("Invalid reverse ENS name")
        }
    }
})
