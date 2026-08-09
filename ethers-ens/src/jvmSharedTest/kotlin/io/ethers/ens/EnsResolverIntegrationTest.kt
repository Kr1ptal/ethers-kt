package io.ethers.ens

import io.ethers.core.isFailure
import io.ethers.core.types.Address
import io.ethers.providers.Provider
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
// NOTE: this suite makes live calls against a public mainnet endpoint, so it lives in jvmSharedTest rather than
// commonTest - it is an integration test, not a unit test, and should not run on every target.
private const val MAINNET_HTTP_RPC = "https://ethereum-rpc.publicnode.com"

class EnsResolverIntegrationTest : FunSpec({
    data class EnsNameTestData(
        val ensName: String = "",
        val nameHash: String = "",
        val resolverAddr: Address = Address.ZERO,
        val resolvedAddr: Address = Address.ZERO,
        val key: String = "",
        val resolvedRecord: String = "",
        val resolvedUri: String? = null,
    )

    context("Init provider and resolver") {
        val ensResolver = Provider.builder(MAINNET_HTTP_RPC).build().map(::EnsResolver).unwrap()

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
                    ensResolver.resolveAddress(it.ensName).send().unwrap() shouldBe it.resolvedAddr
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
                    ensResolver.resolveAddress(it.ensName).send().unwrap() shouldBe it.resolvedAddr
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
                            resolvedRecord = "luc@ens.domains",
                        ),
                    ),
                ) {
                    ensResolver.resolveText(it.ensName, it.key).send().unwrap() shouldBe it.resolvedRecord
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
                    ensResolver.resolveText(it.ensName, it.key).send().unwrap() shouldBe it.resolvedRecord
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
                    ensResolver.resolveEnsName(it.resolvedAddr).send().unwrap() shouldBe it.ensName
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
                            resolvedUri = "https://i.imgur.com/YW3Hzph.jpg",
                        ),
                        // IPFS
                        EnsNameTestData(
                            ensName = "cdixon.eth",
                            resolvedUri = "https://ipfs.io/ipfs/QmYA6ZpEARgHvRHZQdFPynMMX8NtdL2JCadvyuyG2oA88u",
                        ),
                        // TODO uncomment
                        // ERC-1155 with IPFS link
//                        EnsNameTestData(
//                            ensName = "vitalik.eth",
//                            resolvedUri = "https://ipfs.io/ipfs/QmSP4nq9fnN9dAiCj42ug9Wa79rqmQerZXZch82VqpiH7U/image.gif",
//                        ),
                        // Data
                        EnsNameTestData(
                            ensName = "0age.eth",
                            resolvedUri = "data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz48c3ZnIHN0eWxlPSJiYWNrZ3JvdW5kLWNvbG9yOmJsYWNrIiB2aWV3Qm94PSIwIDAgNTAwIDUwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB4PSIxNTUiIHk9IjYwIiB3aWR0aD0iMTkwIiBoZWlnaHQ9IjM5MCIgZmlsbD0iIzY5ZmYzNyIvPjwvc3ZnPg==",
                        ),
                    ),
                ) {
                    ensResolver.resolveAvatar(it.ensName).send().unwrap() shouldBe it.resolvedUri
                }
            }
            // TODO uncomment
//            context("Valid avatar - Address to avatar") {
//                withData(
//                    listOf(
//                        // ERC-721 - with IPFS link
//                        EnsNameTestData(
//                            resolvedAddr = Address("0x9Df11Fd2971eBD0d342d5f3E250A18bb7E6CFA3d"),
//                            resolvedUri = "https://ipfs.io/ipfs/QmaBHu7XS3Pk6hr5bXF52AuBSexX9X6LfeMgyfjKi3X8Xn/83b6379343d91f4d5178e8ba7cac1120",
//                        ),
//                    ),
//                ) {
//                    ensResolver.resolveAvatar(it.resolvedAddr).send().resultOrThrow() shouldBe it.resolvedUri
//                }
//            }
        }

        context("Testing errors") {
            val key = "email"

            // Testing [EnsResolver.Error.EnsNameInvalid]
            test("Invalid ENS names") {
                listOf("", "\t", ".", "\n.").forEach {
                    val resolveAddr = ensResolver.resolveAddress(it).send()
                    resolveAddr.isFailure() shouldBe true
                    resolveAddr.unwrapError().shouldBeInstanceOf<EnsResolver.Error.EnsNameInvalid>()

                    val resolveText = ensResolver.resolveText(it, key).send()
                    resolveText.isFailure() shouldBe true
                    resolveText.unwrapError().shouldBeInstanceOf<EnsResolver.Error.EnsNameInvalid>()
                }
            }

            // Testing [EnsResolver.Error.Normalisation]
            test("Failed normalisation") {
                val resolveAddr = ensResolver.resolveAddress("xn--u-ccb.com").send()
                resolveAddr.isFailure() shouldBe true
                resolveAddr.unwrapError().shouldBeInstanceOf<EnsResolver.Error.Normalisation>()

                val resolveText = ensResolver.resolveAddress("xn--u-ccb.com").send()
                resolveText.isFailure() shouldBe true
                resolveText.unwrapError().shouldBeInstanceOf<EnsResolver.Error.Normalisation>()
            }

            // Testing [EnsResolver.Error.UnknownResolver]
            context("Resolver not found") {
                withData(
                    listOf(
                        EnsNameTestData(
                            ensName = "123.kriptalABC.ethereum",
                        ),
                    ),
                ) {
                    val resolveAddr = ensResolver.resolveAddress(it.ensName).send()
                    resolveAddr.isFailure() shouldBe true
                    resolveAddr.unwrapError().shouldBeInstanceOf<EnsResolver.Error.UnknownResolver>()

                    val resolveText = ensResolver.resolveText(it.ensName, key).send()
                    resolveText.isFailure() shouldBe true
                    resolveText.unwrapError().shouldBeInstanceOf<EnsResolver.Error.UnknownResolver>()

                    val resolveEns = ensResolver.resolveEnsName(Address.ZERO).send()
                    resolveEns.isFailure() shouldBe true
                    resolveEns.unwrapError().shouldBeInstanceOf<EnsResolver.Error.EnsNameInvalid>()
                }
            }

            // Testing [EnsResolver.Error.UnsupportedSelector]
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
                    val resolveEns = ensResolver.resolveEnsName(addr).send()
                    resolveEns.isFailure() shouldBe true
                    resolveEns.unwrapError().shouldBeInstanceOf<EnsResolver.Error.UnsupportedSelector>()
                }
            }

            // Testing [EnsResolver.Error.IncorrectOwner]
            context("Incorrect owner") {
                /*context("Avatars - is not NFT owner") {
                    withData(
                        listOf(
                            // ERC-721 with IPFS link
                            EnsNameTestData(
                                ensName = "ikehaya-nft.eth",
                                resolvedUri = "https://ipfs.io/ipfs/QmdKkwCE8uVhgYd7tWBfhtHdQZDnbNukWJ8bvQmR6nZKsk",
                            ),
                        ),
                    ) {
                        val resolveAvatar = ensResolver.resolveAvatar(it.ensName).send()
                        resolveAvatar.isFailure() shouldBe true
                        resolveAvatar.unwrapError().shouldBeInstanceOf<EnsResolver.Error.IncorrectOwner>()
                    }
                }*/

                // TODO: when mocking
                context("Reverse resolve - Incorrect owner")
            }

            // Testing unknown ENS name - zero address, empty record (resolveAddress, resolveText)
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
                    val resolveAddress = ensResolver.resolveAddress(it.ensName).send()
                    resolveAddress.isFailure() shouldBe true
                    val error = resolveAddress.unwrapError()
                    error.shouldBeInstanceOf<EnsResolver.Error.UnknownEnsName>()
                    error.resolverAddr shouldBe it.resolverAddr
                    error.nameHash shouldBe it.nameHash

                    ensResolver.resolveText(it.ensName, "").send().unwrap() shouldBe ""
                }
            }

            // Testing [EnsResolver.Error.UnsupportedScheme]
            // TODO: when mocking
            context("Avatars - UnsupportedScheme")

            // Testing [EnsResolver.Error.AvatarParsing]
            // TODO: when mocking
            context("Avatars - AvatarParsing")
        }
    }
})
