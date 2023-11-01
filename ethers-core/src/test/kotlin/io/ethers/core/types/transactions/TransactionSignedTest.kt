package io.ethers.core.types.transactions

import io.ethers.core.types.AccessList
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.Signature
import io.ethers.core.types.transaction.TransactionSigned
import io.ethers.core.types.transaction.TxAccessList
import io.ethers.core.types.transaction.TxDynamicFee
import io.ethers.core.types.transaction.TxLegacy
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase

class TransactionSignedTest : FunSpec({
    test("rlp encode/decode TxLegacy without chain ID") {
        // based on: 0x8c09b3738ff35f814d1549dad5cb0a6d5858a4cd66a3ac8abec903a8ec7acf4f
        val tx = TxLegacy(
            to = Address("0x32be343b94f860124dc4fee278fdcbd38c102d88"),
            value = "53940392390000001024".toBigInteger(),
            nonce = 71,
            gas = 21000,
            gasPrice = "60317759056".toBigInteger(),
            data = null,
            chainId = -1L,
        )

        val signature = Signature(
            "19421212088719815271344666575303211260201938119335252342119094927553198774356".toBigInteger(),
            "31544167366976575860499615173798475590035610996395232018037175896014426317714".toBigInteger(),
            28,
        )

        val signed = TransactionSigned(tx, signature)
        val rlp = signed.toRlp()
        rlp.toHexString() shouldBeEqualIgnoringCase "f86d47850e0b37f6508252089432be343b94f860124dc4fee278fdcbd38c102d888902ec92c5171b224000801ca02af0043955354a8dcceefc492f464977d6eac51d7c94b41d86dd11ab5d0b5854a045bd5db428b629807524f4b973f820a4591b59055a6669f346039517f9fda392"

        val decoded = TransactionSigned.rlpDecode(rlp)
        decoded shouldBe signed
    }

    test("rlp encode/decode TxLegacy with chain ID") {
        val tx = TxLegacy(
            to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
            value = "1000000000".toBigInteger(),
            nonce = 0,
            gas = 2000000,
            gasPrice = "21000000000".toBigInteger(),
            data = null,
            chainId = 1L,
        )

        val signature = Signature(
            "79440387067578648643008012733832802856903723450045762070555193125330762627918".toBigInteger(),
            "24830859692978839778370969737964141525080645226171071268096642880992234125891".toBigInteger(),
            38,
        )

        val signed = TransactionSigned(tx, signature)
        val rlp = signed.toRlp()
        rlp.toHexString() shouldBeEqualIgnoringCase "f869808504e3b29200831e848094f0109fc8df283027b6285cc889f5aa624eac1f55843b9aca008026a0afa1aa6b3d92db3e76e0573281b81eee269a5d8ce864ab4e0e8b288658c87b4ea036e5c4bf00230dd93364784ca4e853ebff45d1b074c2539006f97c742af73a43"

        val decoded = TransactionSigned.rlpDecode(rlp)
        decoded shouldBe signed
    }

    test("rlp encode/decode TxAccessList") {
        val tx = TxAccessList(
            to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
            value = "1000000000".toBigInteger(),
            nonce = 0,
            gas = 2000000,
            gasPrice = "21000000000".toBigInteger(),
            data = Bytes("0x1214abcdef12445980"),
            chainId = 1L,
            accessList = listOf(
                AccessList.Item(
                    Address("0x2f62f2b4c5fcd7570a709dec05d68ea19c82a9ec"),
                    listOf(
                        Hash("0x9c2c23028bf4f085740a3671821db14e440561f617ea5532ee805d7f054741f6"),
                        Hash("0x000000000000000000000000000000000000000000000000000000000000000b"),
                        Hash("0x000000000000000000000000000000000000000000000000000000000000000a"),
                    ),
                ),
            ),
        )

        val signature = Signature(
            "79440387067578648643008012733832802856903723450045762070555193125330762627918".toBigInteger(),
            "24830859692978839778370969737964141525080645226171071268096642880992234125891".toBigInteger(),
            1,
        )

        val signed = TransactionSigned(tx, signature)
        val rlp = signed.toRlp()
        rlp.toHexString() shouldBeEqualIgnoringCase "01f8f101808504e3b29200831e848094f0109fc8df283027b6285cc889f5aa624eac1f55843b9aca00891214abcdef12445980f87cf87a942f62f2b4c5fcd7570a709dec05d68ea19c82a9ecf863a09c2c23028bf4f085740a3671821db14e440561f617ea5532ee805d7f054741f6a0000000000000000000000000000000000000000000000000000000000000000ba0000000000000000000000000000000000000000000000000000000000000000a01a0afa1aa6b3d92db3e76e0573281b81eee269a5d8ce864ab4e0e8b288658c87b4ea036e5c4bf00230dd93364784ca4e853ebff45d1b074c2539006f97c742af73a43"

        val decoded = TransactionSigned.rlpDecode(rlp)
        decoded shouldBe signed
    }

    test("rlp encode/decode TxDynamicFee") {
        val tx = TxDynamicFee(
            to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
            value = "1000000000".toBigInteger(),
            nonce = 12425132,
            gas = 2000000,
            gasFeeCap = "210000000000".toBigInteger(),
            gasTipCap = "21000000000".toBigInteger(),
            data = Bytes("0x1214abcdef12445980"),
            chainId = 1L,
            accessList = listOf(
                AccessList.Item(
                    Address("0x2f62f2b4c5fcd7570a709dec05d68ea19c82a9ec"),
                    listOf(
                        Hash("0x9c2c23028bf4f085740a3671821db14e440561f617ea5532ee805d7f054741f6"),
                        Hash("0x000000000000000000000000000000000000000000000000000000000000000b"),
                        Hash("0x000000000000000000000000000000000000000000000000000000000000000a"),
                    ),
                ),
            ),
        )

        val signature = Signature(
            "79440387067578648643008012733832802856903723450045762070555193125330762627918".toBigInteger(),
            "24830859692978839778370969737964141525080645226171071268096642880992234125891".toBigInteger(),
            1,
        )

        val signed = TransactionSigned(tx, signature)
        val rlp = signed.toRlp()
        rlp.toHexString() shouldBeEqualIgnoringCase "02f8fa0183bd97ac8504e3b292008530e4f9b400831e848094f0109fc8df283027b6285cc889f5aa624eac1f55843b9aca00891214abcdef12445980f87cf87a942f62f2b4c5fcd7570a709dec05d68ea19c82a9ecf863a09c2c23028bf4f085740a3671821db14e440561f617ea5532ee805d7f054741f6a0000000000000000000000000000000000000000000000000000000000000000ba0000000000000000000000000000000000000000000000000000000000000000a01a0afa1aa6b3d92db3e76e0573281b81eee269a5d8ce864ab4e0e8b288658c87b4ea036e5c4bf00230dd93364784ca4e853ebff45d1b074c2539006f97c742af73a43"

        val decoded = TransactionSigned.rlpDecode(rlp)
        decoded shouldBe signed
    }
})
