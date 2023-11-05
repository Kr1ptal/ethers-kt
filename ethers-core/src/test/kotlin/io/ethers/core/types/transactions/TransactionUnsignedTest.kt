package io.ethers.core.types.transactions

import io.ethers.core.types.AccessList
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.transaction.TransactionUnsigned
import io.ethers.core.types.transaction.TxAccessList
import io.ethers.core.types.transaction.TxBlob
import io.ethers.core.types.transaction.TxDynamicFee
import io.ethers.core.types.transaction.TxLegacy
import io.ethers.rlp.RlpDecoder
import io.ethers.rlp.RlpEncoder
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

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
                accessList = null,
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
                accessList = null,
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
                accessList = null,
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
                accessList = null,
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
        ) { tx ->
            val encoder = RlpEncoder()
            tx.rlpEncode(encoder)

            val decoder = RlpDecoder(encoder.toByteArray())
            TransactionUnsigned.rlpDecode(decoder, tx.chainId) shouldBe tx
        }
    }
})
