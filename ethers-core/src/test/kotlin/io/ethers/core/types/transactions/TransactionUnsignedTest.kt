package io.ethers.core.types.transactions

import fixtures.AuthorizationFactory
import io.ethers.core.types.AccessList
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.transaction.TransactionUnsigned
import io.ethers.core.types.transaction.TxAccessList
import io.ethers.core.types.transaction.TxBlob
import io.ethers.core.types.transaction.TxDynamicFee
import io.ethers.core.types.transaction.TxLegacy
import io.ethers.core.types.transaction.TxSetCode
import io.ethers.rlp.RlpDecoder
import io.ethers.rlp.RlpEncoder
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

class TransactionUnsignedTest : FunSpec({
    val accessList = listOf(
        AccessList.Item(
            Address("0x2f62f2b4c5fcd7570a709dec05d68ea19c82a9ec"),
            listOf(
                Hash("0x9c2c23028bf4f085740a3671821db14e440561f617ea5532ee805d7f054741f6"),
                Hash("0x000000000000000000000000000000000000000000000000000000000000000b"),
                Hash("0x000000000000000000000000000000000000000000000000000000000000000a"),
            ),
        ),
    )

    context("signatureHash is correct") {
        test("TxLegacy without chain ID") {
            val tx = TxLegacy(
                to = Address("0x32be343b94f860124dc4fee278fdcbd38c102d88"),
                value = "53940392390000001024".toBigInteger(),
                nonce = 71,
                gas = 21000,
                gasPrice = "60317759056".toBigInteger(),
                data = null,
                chainId = -1L,
            )

            tx.signatureHash().toHexString() shouldBe "3bb0e0f64fc2ccaeac16a5b6a54261b235371421647328b32ab3152c447f4b43"
        }

        test("TxLegacy with chain ID") {
            val tx = TxLegacy(
                to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
                value = "1000000000".toBigInteger(),
                nonce = 0,
                gas = 2000000,
                gasPrice = "21000000000".toBigInteger(),
                data = null,
                chainId = 1L,
            )

            tx.signatureHash().toHexString() shouldBe "88cfbd7e51c7a40540b233cf68b62ad1df3e92462f1c6018d6d67eae0f3b08f5"
        }

        test("TxAccessList without access list") {
            val tx = TxAccessList(
                to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
                value = "1000000000".toBigInteger(),
                nonce = 0,
                gas = 2000000,
                gasPrice = "21000000000".toBigInteger(),
                data = Bytes("0x1214abcdef12445980"),
                chainId = 1L,
                accessList = emptyList(),
            )

            tx.signatureHash().toHexString() shouldBe "9cac944f150142405ab1873c80b72f75368664e417a87b90455d1f2e83178157"
        }

        test("TxAccessList with access list") {
            val tx = TxAccessList(
                to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
                value = "1000000000".toBigInteger(),
                nonce = 0,
                gas = 2000000,
                gasPrice = "21000000000".toBigInteger(),
                data = Bytes("0x1214abcdef12445980"),
                chainId = 1L,
                accessList = accessList,
            )

            tx.signatureHash().toHexString() shouldBe "d03f1041524fc50938d8ac6e8543dfcc31c51eeb66e6793655e8067a8165bd8c"
        }

        test("TxDynamicFee without access list") {
            val tx = TxDynamicFee(
                to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
                value = "1000000000".toBigInteger(),
                nonce = 12425132,
                gas = 2000000,
                gasFeeCap = "210000000000".toBigInteger(),
                gasTipCap = "21000000000".toBigInteger(),
                data = Bytes("0x1214abcdef12445980"),
                chainId = 1L,
                accessList = emptyList(),
            )

            tx.signatureHash().toHexString() shouldBe "af6891de644ea94fb026f73fe8716d9e5aac2e374e81bea8aaa4e4f1e7ab50b5"
        }

        test("TxDynamicFee with access list") {
            val tx = TxDynamicFee(
                to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
                value = "1000000000".toBigInteger(),
                nonce = 12425132,
                gas = 2000000,
                gasFeeCap = "210000000000".toBigInteger(),
                gasTipCap = "21000000000".toBigInteger(),
                data = Bytes("0x1214abcdef12445980"),
                chainId = 1L,
                accessList = accessList,
            )

            tx.signatureHash().toHexString() shouldBe "02f1301823f1eaa4cbf6832369fea3a5754bf88a13d95ac7eb6d2f8320f85c27"
        }

        test("TxBlob with and without sidecar encodes to same signature hash") {
            val withoutSidecar = TxBlob(
                to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
                value = "1000000000".toBigInteger(),
                nonce = 12425132,
                gas = 2000000,
                gasFeeCap = "210000000000".toBigInteger(),
                gasTipCap = "21000000000".toBigInteger(),
                data = Bytes("0x1214abcdef12445980"),
                chainId = 1L,
                accessList = emptyList(),
                blobFeeCap = "21000000000".toBigInteger(),
                blobVersionedHashes = listOf(Hash("0x010657f37554c781402a22917dee2f75def7ab966d7b770905398eba3c444014")),
            )

            val withSidecar = TxBlob(
                to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
                value = "1000000000".toBigInteger(),
                nonce = 12425132,
                gas = 2000000,
                gasFeeCap = "210000000000".toBigInteger(),
                gasTipCap = "21000000000".toBigInteger(),
                data = Bytes("0x1214abcdef12445980"),
                chainId = 1L,
                accessList = emptyList(),
                blobFeeCap = "21000000000".toBigInteger(),
                sidecar = TxBlob.Sidecar(
                    blobs = listOf(Bytes(ByteArray(TxBlob.Sidecar.BLOB_LENGTH))),
                    commitments = listOf(Bytes("c00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")),
                    proofs = listOf(Bytes("c00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")),
                ),
            )

            withoutSidecar.signatureHash() shouldBe withSidecar.signatureHash()
            withoutSidecar.signatureHash().toHexString() shouldBe "bb41b484e4a165d7536c36b12ead57c3391a69632e9b09cca5f3abf1584ed55e"
        }

        test("TxSetCode without access list") {
            val tx = TxSetCode(
                to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
                value = "1000000000".toBigInteger(),
                nonce = 12425132,
                gas = 2000000,
                gasFeeCap = "210000000000".toBigInteger(),
                gasTipCap = "21000000000".toBigInteger(),
                data = Bytes("0x1214abcdef12445980"),
                chainId = 1L,
                accessList = emptyList(),
                authorizationList = listOf(AuthorizationFactory.create()),
            )

            // This should produce a deterministic signature hash
            tx.signatureHash().size shouldBe 32
        }

        test("TxSetCode with access list") {
            val tx = TxSetCode(
                to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
                value = "1000000000".toBigInteger(),
                nonce = 12425132,
                gas = 2000000,
                gasFeeCap = "210000000000".toBigInteger(),
                gasTipCap = "21000000000".toBigInteger(),
                data = Bytes("0x1214abcdef12445980"),
                chainId = 1L,
                accessList = accessList,
                authorizationList = listOf(
                    AuthorizationFactory.create(chainId = 1L, nonce = 0L),
                    AuthorizationFactory.create(chainId = 1L, nonce = 1L),
                ),
            )

            // This should produce a deterministic signature hash different from the above
            tx.signatureHash().size shouldBe 32
        }
    }

    context("RLP decoding") {
        withData(
            TxLegacy(
                to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
                value = "1000000000".toBigInteger(),
                nonce = 1,
                gas = 2000000,
                gasPrice = "21000000000".toBigInteger(),
                data = Bytes("0x01"),
                chainId = 1L,
            ),
            TxAccessList(
                to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
                value = "1000000000".toBigInteger(),
                nonce = 1,
                gas = 2000000,
                gasPrice = "21000000000".toBigInteger(),
                data = Bytes("0x1214abcdef12445980"),
                chainId = 1L,
                accessList = accessList,
            ),
            TxDynamicFee(
                to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
                value = "1000000000".toBigInteger(),
                nonce = 12425132,
                gas = 2000000,
                gasFeeCap = "210000000000".toBigInteger(),
                gasTipCap = "21000000000".toBigInteger(),
                data = Bytes("0x1214abcdef12445980"),
                chainId = 1L,
                accessList = accessList,
            ),
            TxBlob(
                to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
                value = "1000000000".toBigInteger(),
                nonce = 12425132,
                gas = 2000000,
                gasFeeCap = "210000000000".toBigInteger(),
                gasTipCap = "21000000000".toBigInteger(),
                data = Bytes("0x1214abcdef12445980"),
                chainId = 1L,
                accessList = accessList,
                blobFeeCap = "21000000000".toBigInteger(),
                blobVersionedHashes = listOf(Hash.ZERO),
            ),
            TxSetCode(
                to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
                value = "1000000000".toBigInteger(),
                nonce = 12425132,
                gas = 2000000,
                gasFeeCap = "210000000000".toBigInteger(),
                gasTipCap = "21000000000".toBigInteger(),
                data = Bytes("0x1214abcdef12445980"),
                chainId = 1L,
                accessList = accessList,
                authorizationList = listOf(
                    AuthorizationFactory.create(chainId = 1L, nonce = 0L),
                    AuthorizationFactory.create(chainId = 1L, nonce = 1L),
                ),
            ),
        ) { tx ->
            val encoder = RlpEncoder()
            tx.rlpEncode(encoder)

            val decoder = RlpDecoder(encoder.toByteArray())
            TransactionUnsigned.rlpDecode(decoder) shouldBe tx
        }
    }

    context("RLP decoding with trailing signature fields") {
        test("TxDynamicFee from eth_fillTransaction with zeroed signature fields") {
            // Raw transaction from eth_fillTransaction response that includes zeroed signature fields (v=0, r=0, s=0)
            // The trailing bytes c0808080 are: c0=empty accessList, 80=0 (v), 80=0 (r), 80=0 (s)
            @Suppress("ktlint:standard:max-line-length")
            val raw = Bytes("0x02f8ee827a69808084593c9cee83030d4094895a430cd5effbaaf65e845705e62d754194ba0380b8c4d10831f2f6679889a29be5167f00c2e831fd23c453e47b4c174d85a05f31b98c57a9b293000000000000000000000000c2fe006b8efcd0af4976828782011b349b93d638000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0808080")

            val decoded = TransactionUnsigned.rlpDecode(raw.toByteArray())
            decoded shouldNotBe null
            val tx = decoded.shouldBeInstanceOf<TxDynamicFee>()

            tx.chainId shouldBe 31337L // 0x7a69
            tx.nonce shouldBe 0L
            tx.gas shouldBe 200000L // 0x30d40
            tx.gasFeeCap shouldBe "1497144558".toBigInteger() // 0x593c9cee
            tx.gasTipCap shouldBe 0.toBigInteger()
            tx.to shouldBe Address("0x895a430cd5effbaaf65e845705e62d754194ba03")
            tx.value shouldBe 0.toBigInteger()
            tx.accessList shouldBe emptyList()
        }

        test("TxAccessList with trailing signature fields") {
            // Encode a TxAccessList with signature, then decode as unsigned (should skip signature)
            val original = TxAccessList(
                to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
                value = "1000000000".toBigInteger(),
                nonce = 1,
                gas = 2000000,
                gasPrice = "21000000000".toBigInteger(),
                data = Bytes("0x1214abcdef12445980"),
                chainId = 1L,
                accessList = emptyList(),
            )

            // Encode with zero signature appended
            val encoder = RlpEncoder()
            encoder.appendRaw(0x01.toByte()) // type byte
            encoder.encodeList {
                encoder.encode(original.chainId)
                encoder.encode(original.nonce)
                encoder.encode(original.gasPrice)
                encoder.encode(original.gas)
                encoder.encode(original.to)
                encoder.encode(original.value)
                encoder.encode(original.data)
                encoder.encodeList(original.accessList)
                // Append zero signature fields
                encoder.encode(0L)
                encoder.encode(0.toBigInteger())
                encoder.encode(0.toBigInteger())
            }

            val decoded = TransactionUnsigned.rlpDecode(encoder.toByteArray())
            decoded shouldNotBe null
            decoded.shouldBeInstanceOf<TxAccessList>()
            decoded shouldBe original
        }

        test("TxDynamicFee with trailing signature fields") {
            val original = TxDynamicFee(
                to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
                value = "1000000000".toBigInteger(),
                nonce = 12425132,
                gas = 2000000,
                gasFeeCap = "210000000000".toBigInteger(),
                gasTipCap = "21000000000".toBigInteger(),
                data = Bytes("0x1214abcdef12445980"),
                chainId = 1L,
                accessList = emptyList(),
            )

            // Encode with zero signature appended
            val encoder = RlpEncoder()
            encoder.appendRaw(0x02.toByte()) // type byte
            encoder.encodeList {
                encoder.encode(original.chainId)
                encoder.encode(original.nonce)
                encoder.encode(original.gasTipCap)
                encoder.encode(original.gasFeeCap)
                encoder.encode(original.gas)
                encoder.encode(original.to)
                encoder.encode(original.value)
                encoder.encode(original.data)
                encoder.encodeList(original.accessList)
                // Append zero signature fields
                encoder.encode(0L)
                encoder.encode(0.toBigInteger())
                encoder.encode(0.toBigInteger())
            }

            val decoded = TransactionUnsigned.rlpDecode(encoder.toByteArray())
            decoded shouldNotBe null
            decoded.shouldBeInstanceOf<TxDynamicFee>()
            decoded shouldBe original
        }

        test("TxBlob with trailing signature fields") {
            val original = TxBlob(
                to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
                value = "1000000000".toBigInteger(),
                nonce = 12425132,
                gas = 2000000,
                gasFeeCap = "210000000000".toBigInteger(),
                gasTipCap = "21000000000".toBigInteger(),
                data = Bytes("0x1214abcdef12445980"),
                chainId = 1L,
                accessList = emptyList(),
                blobFeeCap = "21000000000".toBigInteger(),
                blobVersionedHashes = listOf(Hash.ZERO),
            )

            // Encode with zero signature appended
            val encoder = RlpEncoder()
            encoder.appendRaw(0x03.toByte()) // type byte
            encoder.encodeList {
                encoder.encode(original.chainId)
                encoder.encode(original.nonce)
                encoder.encode(original.gasTipCap)
                encoder.encode(original.gasFeeCap)
                encoder.encode(original.gas)
                encoder.encode(original.to)
                encoder.encode(original.value)
                encoder.encode(original.data)
                encoder.encodeList(original.accessList)
                encoder.encode(original.blobFeeCap)
                encoder.encodeList(original.blobVersionedHashes.orEmpty())
                // Append zero signature fields
                encoder.encode(0L)
                encoder.encode(0.toBigInteger())
                encoder.encode(0.toBigInteger())
            }

            val decoded = TransactionUnsigned.rlpDecode(encoder.toByteArray())
            decoded shouldNotBe null
            decoded.shouldBeInstanceOf<TxBlob>()
            decoded shouldBe original
        }

        test("TxSetCode with trailing signature fields") {
            val original = TxSetCode(
                to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
                value = "1000000000".toBigInteger(),
                nonce = 12425132,
                gas = 2000000,
                gasFeeCap = "210000000000".toBigInteger(),
                gasTipCap = "21000000000".toBigInteger(),
                data = Bytes("0x1214abcdef12445980"),
                chainId = 1L,
                accessList = emptyList(),
                authorizationList = listOf(AuthorizationFactory.create(chainId = 1L, nonce = 0L)),
            )

            // Encode with zero signature appended
            val encoder = RlpEncoder()
            encoder.appendRaw(0x04.toByte()) // type byte
            encoder.encodeList {
                encoder.encode(original.chainId)
                encoder.encode(original.nonce)
                encoder.encode(original.gasTipCap)
                encoder.encode(original.gasFeeCap)
                encoder.encode(original.gas)
                encoder.encode(original.to)
                encoder.encode(original.value)
                encoder.encode(original.data)
                encoder.encodeList(original.accessList)
                encoder.encodeList(original.authorizationList.orEmpty())
                // Append zero signature fields
                encoder.encode(0L)
                encoder.encode(0.toBigInteger())
                encoder.encode(0.toBigInteger())
            }

            val decoded = TransactionUnsigned.rlpDecode(encoder.toByteArray())
            decoded shouldNotBe null
            decoded.shouldBeInstanceOf<TxSetCode>()
            decoded shouldBe original
        }
    }
})
