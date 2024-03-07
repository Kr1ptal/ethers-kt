package io.ethers.ens

import io.ethers.core.isFailure
import io.ethers.core.types.Address
import io.ethers.providers.Provider
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.net.URI

private const val MAINNET_HTTP_RPC = "https://ethereum.publicnode.com"

class EnsMiddlewareTest : FunSpec({
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
        val ensMiddleware = Provider.fromUrl(MAINNET_HTTP_RPC).map(::EnsMiddleware).unwrap()

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
                    ensMiddleware.resolveAddress(it.ensName).get().unwrap() shouldBe it.resolvedAddr
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
                    ensMiddleware.resolveAddress(it.ensName).get().unwrap() shouldBe it.resolvedAddr
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
                    ensMiddleware.resolveText(it.ensName, it.key).get().unwrap() shouldBe it.resolvedRecord
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
                    ensMiddleware.resolveText(it.ensName, it.key).get().unwrap() shouldBe it.resolvedRecord
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
                    ensMiddleware.resolveEnsName(it.resolvedAddr).get().unwrap() shouldBe it.ensName
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
                        // TODO uncomment
                        // ERC-1155 with IPFS link
//                        EnsNameTestData(
//                            ensName = "vitalik.eth",
//                            resolvedUri = URI("https://ipfs.io/ipfs/QmSP4nq9fnN9dAiCj42ug9Wa79rqmQerZXZch82VqpiH7U/image.gif"),
//                        ),
                        // Data
                        EnsNameTestData(
                            ensName = "0age.eth",
                            resolvedUri = URI("data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz48c3ZnIHN0eWxlPSJiYWNrZ3JvdW5kLWNvbG9yOmJsYWNrIiB2aWV3Qm94PSIwIDAgNTAwIDUwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB4PSIxNTUiIHk9IjYwIiB3aWR0aD0iMTkwIiBoZWlnaHQ9IjM5MCIgZmlsbD0iIzY5ZmYzNyIvPjwvc3ZnPg=="),
                        ),
                    ),
                ) {
                    ensMiddleware.resolveAvatar(it.ensName).get().unwrap() shouldBe it.resolvedUri
                }
            }
            // TODO uncomment
//            context("Valid avatar - Address to avatar") {
//                withData(
//                    listOf(
//                        // ERC-721 - with IPFS link
//                        EnsNameTestData(
//                            resolvedAddr = Address("0x9Df11Fd2971eBD0d342d5f3E250A18bb7E6CFA3d"),
//                            resolvedUri = URI("https://ipfs.io/ipfs/QmaBHu7XS3Pk6hr5bXF52AuBSexX9X6LfeMgyfjKi3X8Xn/83b6379343d91f4d5178e8ba7cac1120"),
//                        ),
//                    ),
//                ) {
//                    ensMiddleware.resolveAvatar(it.resolvedAddr).get().resultOrThrow() shouldBe it.resolvedUri
//                }
//            }
        }

        context("Testing errors") {
            val key = "email"

            /**
             * Testing [EnsMiddleware.Error.EnsNameInvalid]
             */
            test("Invalid ENS names") {
                listOf("", "\t", ".", "\n.").forEach {
                    val resolveAddr = ensMiddleware.resolveAddress(it).get()
                    resolveAddr.isFailure() shouldBe true
                    resolveAddr.unwrapError().shouldBeInstanceOf<EnsMiddleware.Error.EnsNameInvalid>()

                    val resolveText = ensMiddleware.resolveText(it, key).get()
                    resolveText.isFailure() shouldBe true
                    resolveText.unwrapError().shouldBeInstanceOf<EnsMiddleware.Error.EnsNameInvalid>()
                }
            }

            /**
             * Testing [EnsMiddleware.Error.Normalisation]
             */
            test("Failed normalisation") {
                val resolveAddr = ensMiddleware.resolveAddress("xn--u-ccb.com").get()
                resolveAddr.isFailure() shouldBe true
                resolveAddr.unwrapError().shouldBeInstanceOf<EnsMiddleware.Error.Normalisation>()

                val resolveText = ensMiddleware.resolveAddress("xn--u-ccb.com").get()
                resolveText.isFailure() shouldBe true
                resolveText.unwrapError().shouldBeInstanceOf<EnsMiddleware.Error.Normalisation>()
            }

            /**
             * Testing [EnsMiddleware.Error.UnknownResolver]
             */
            context("Resolver not found") {
                withData(
                    listOf(
                        EnsNameTestData(
                            ensName = "123.kriptalABC.ethereum",
                        ),
                    ),
                ) {
                    val resolveAddr = ensMiddleware.resolveAddress(it.ensName).get()
                    resolveAddr.isFailure() shouldBe true
                    resolveAddr.unwrapError().shouldBeInstanceOf<EnsMiddleware.Error.UnknownResolver>()

                    val resolveText = ensMiddleware.resolveText(it.ensName, key).get()
                    resolveText.isFailure() shouldBe true
                    resolveText.unwrapError().shouldBeInstanceOf<EnsMiddleware.Error.UnknownResolver>()

                    val resolveEns = ensMiddleware.resolveEnsName(Address.ZERO).get()
                    resolveEns.isFailure() shouldBe true
                    resolveEns.unwrapError().shouldBeInstanceOf<EnsMiddleware.Error.UnknownResolver>()
                }
            }

            /**
             * Testing [EnsMiddleware.Error.UnsupportedSelector]
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
                    val resolveEns = ensMiddleware.resolveEnsName(addr).get()
                    resolveEns.isFailure() shouldBe true
                    resolveEns.unwrapError().shouldBeInstanceOf<EnsMiddleware.Error.UnsupportedSelector>()
                }
            }

            /**
             * Testing [EnsMiddleware.Error.IncorrectOwner]
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
                        val resolveAvatar = ensMiddleware.resolveAvatar(it.ensName).get()
                        resolveAvatar.isFailure() shouldBe true
                        resolveAvatar.unwrapError().shouldBeInstanceOf<EnsMiddleware.Error.IncorrectOwner>()
                    }
                }

                // TODO: when mocking
                context("Reverse resolve - Incorrect owner")
            }

            /**
             * Testing unknown ENS name - zero address, empty record (resolveAddress, resolveText)
             */
            context("Resolve to NULL") {
                withData(
                    listOf(
                        EnsNameTestData(
                            ensName = "123.kriptal.eth",
                            nameHash = "0x469fbad6482d86a40a35d188cb7f8256302a5d6c50e9071c4f4e9f7604b2cac8",
                            resolverAddr = Address("0x231b0Ee14048e9dCcD1d247744d114a4EB5E8E63"), // PublicResolver
                        ),
                    ),
                ) {
                    val resolveAddress = ensMiddleware.resolveAddress(it.ensName).get()
                    resolveAddress.isFailure() shouldBe true
                    val error = resolveAddress.unwrapError()
                    error.shouldBeInstanceOf<EnsMiddleware.Error.UnknownEnsName>()
                    error.resolverAddr shouldBe it.resolverAddr
                    error.nameHash shouldBe it.nameHash

                    ensMiddleware.resolveText(it.ensName, "").get().unwrap() shouldBe ""
                }
            }

            /**
             * Testing [EnsMiddleware.Error.UnsupportedScheme]
             */
            // TODO: when mocking
            context("Avatars - UnsupportedScheme")

            /**
             * Testing [EnsMiddleware.Error.AvatarParsing]
             */
            // TODO: when mocking
            context("Avatars - AvatarParsing")
        }
    }
})
