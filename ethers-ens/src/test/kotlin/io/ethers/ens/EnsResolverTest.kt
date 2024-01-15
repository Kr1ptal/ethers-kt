package io.ethers.ens

import io.ethers.core.types.Address
import io.ethers.ens.EnsResolver.Companion.resolveAddress
import io.ethers.ens.EnsResolver.Companion.resolveEnsName
import io.ethers.ens.EnsResolver.Companion.resolveText
import io.ethers.providers.HttpClient
import io.ethers.providers.Provider
import io.ethers.providers.types.RpcResponse
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.net.URI

private const val MAINNET_HTTP_RPC = "https://ethereum.publicnode.com"

class EnsResolverTest : FunSpec({
    data class EnsNameTestData(
        val ensName: String = "",
        val nameHash: String = "",
        val resolverAddr: Address = Address.ZERO,
        val resolvedAddr: Address = Address.ZERO,
        val key: String = "",
        val resolvedRecord: String = "",
        val resolvedUri: URI? = null,
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
                    ensResolver.resolveAddress(it.ensName).get().resultOrThrow() shouldBe it.resolvedAddr
                    provider.resolveAddress(it.ensName).get().resultOrThrow() shouldBe it.resolvedAddr
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
                    ensResolver.resolveAddress(it.ensName).get().resultOrThrow() shouldBe it.resolvedAddr
                    provider.resolveAddress(it.ensName).get().resultOrThrow() shouldBe it.resolvedAddr
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
            context("Valid - No wildcard") {
                withData(
                    listOf(
                        EnsNameTestData(
                            ensName = "registrar.firefly.eth",
                            resolvedAddr = Address("0x6fC21092DA55B392b045eD78F4732bff3C580e2c"),
                        ),
                    ),
                ) {
                    ensResolver.resolveEnsName(it.resolvedAddr).get().resultOrThrow() shouldBe it.ensName
                    provider.resolveEnsName(it.resolvedAddr).get().resultOrThrow() shouldBe it.ensName
                }
            }
        }

        context("Avatars") {
            context("Valid avatar - ENS to Avatar") {
                withData(
                    listOf(
                        // HTTPS
                        EnsNameTestData(
                            ensName = "parishilton.eth",
                            resolvedUri = URI("https://i.imgur.com/YW3Hzph.jpg"),
                        ),
                        // IPFS
                        EnsNameTestData(
                            ensName = "cdixon.eth",
                            resolvedUri = URI("https://ipfs.io/ipfs/QmYA6ZpEARgHvRHZQdFPynMMX8NtdL2JCadvyuyG2oA88u"),
                        ),
                        // ERC-1155 with IPFS link
                        EnsNameTestData(
                            ensName = "vitalik.eth",
                            resolvedUri = URI("https://ipfs.io/ipfs/QmSP4nq9fnN9dAiCj42ug9Wa79rqmQerZXZch82VqpiH7U/image.gif"),
                        ),
                        // Data
                        EnsNameTestData(
                            ensName = "0age.eth",
                            resolvedUri = URI("data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz48c3ZnIHN0eWxlPSJiYWNrZ3JvdW5kLWNvbG9yOmJsYWNrIiB2aWV3Qm94PSIwIDAgNTAwIDUwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB4PSIxNTUiIHk9IjYwIiB3aWR0aD0iMTkwIiBoZWlnaHQ9IjM5MCIgZmlsbD0iIzY5ZmYzNyIvPjwvc3ZnPg=="),
                        ),
                    ),
                ) {
                    ensResolver.resolveAvatar(it.ensName).get().resultOrThrow() shouldBe it.resolvedUri
                }
            }
            context("Valid avatar - Address to avatar") {
                withData(
                    listOf(
                        // ERC-721 - with IPFS link
                        EnsNameTestData(
                            resolvedAddr = Address("0x9Df11Fd2971eBD0d342d5f3E250A18bb7E6CFA3d"),
                            resolvedUri = URI("https://ipfs.io/ipfs/QmaBHu7XS3Pk6hr5bXF52AuBSexX9X6LfeMgyfjKi3X8Xn/83b6379343d91f4d5178e8ba7cac1120"),
                        ),
                    ),
                ) {
                    ensResolver.resolveAvatar(it.resolvedAddr).get().resultOrThrow() shouldBe it.resolvedUri
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
                    ensResolver.resolveAddress(it).get().error.shouldBeInstanceOf<EnsResolver.Error.EnsNameInvalid>()
                    provider.resolveAddress(it).get().error.shouldBeInstanceOf<EnsResolver.Error.EnsNameInvalid>()

                    ensResolver.resolveText(it, key).get().error.shouldBeInstanceOf<EnsResolver.Error.EnsNameInvalid>()
                    provider.resolveText(it, key).get().error.shouldBeInstanceOf<EnsResolver.Error.EnsNameInvalid>()
                }
            }

            /**
             * Testing [EnsResolver.Error.Normalisation]
             */
            test("Failed normalisation") {
                ensResolver.resolveAddress("xn--u-ccb.com")
                    .get().error.shouldBeInstanceOf<EnsResolver.Error.Normalisation>()
                provider.resolveAddress("xn--u-ccb.com")
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
                    ensResolver.resolveAddress(it.ensName)
                        .get().error.shouldBeInstanceOf<EnsResolver.Error.UnknownResolver>()
                    provider.resolveAddress(it.ensName)
                        .get().error.shouldBeInstanceOf<EnsResolver.Error.UnknownResolver>()

                    ensResolver.resolveText(it.ensName, key)
                        .get().error.shouldBeInstanceOf<EnsResolver.Error.UnknownResolver>()
                    provider.resolveText(it.ensName, key)
                        .get().error.shouldBeInstanceOf<EnsResolver.Error.UnknownResolver>()

                    ensResolver.resolveEnsName(Address.ZERO).get().error.shouldBeInstanceOf<EnsResolver.Error.UnknownResolver>()
                    provider.resolveEnsName(Address.ZERO).get().error.shouldBeInstanceOf<EnsResolver.Error.UnknownResolver>()
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
                    ensResolver.resolveEnsName(addr).get().error.shouldBeInstanceOf<EnsResolver.Error.UnsupportedSelector>()
                    provider.resolveEnsName(addr).get().error.shouldBeInstanceOf<EnsResolver.Error.UnsupportedSelector>()
                }
            }

            /**
             * Testing [EnsResolver.Error.IncorrectOwner]
             */
            context("Incorrect owner") {
                context("Avatars - is not NFT owner") {
                    withData(
                        listOf(
                            // ERC-721 with IPFS link
                            EnsNameTestData(
                                ensName = "ikehaya-nft.eth",
                                resolvedUri = URI("https://ipfs.io/ipfs/QmdKkwCE8uVhgYd7tWBfhtHdQZDnbNukWJ8bvQmR6nZKsk"),
                            ),
                        ),
                    ) {
                        ensResolver.resolveAvatar(it.ensName)
                            .get().error.shouldBeInstanceOf<EnsResolver.Error.IncorrectOwner>()
                    }
                }

                // TODO: when mocking
                context("Reverse resolve - Incorrect owner")
            }

            /**
             * Testing unknown ENS name - zero address, empty record (resolveAddress, resolveText)
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
                    testError(ensResolver.resolveAddress(it.ensName).get().error, it)
                    testError(provider.resolveAddress(it.ensName).get().error, it)

                    ensResolver.resolveText(it.ensName, "").get().resultOrThrow() shouldBe ""
                    provider.resolveText(it.ensName, "").get().resultOrThrow() shouldBe ""
                }
            }

            /**
             * Testing [EnsResolver.Error.UnsupportedScheme]
             */
            // TODO: when mocking
            context("Avatars - UnsupportedScheme")

            /**
             * Testing [EnsResolver.Error.AvatarParsing]
             */
            // TODO: when mocking
            context("Avatars - AvatarParsing")
        }
    }
})
