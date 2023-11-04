package io.ethers.signers

import io.ethers.core.types.Address
import io.ethers.crypto.bip32.HDPath
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

class MnemonicKeySourceTest : FunSpec({
    data class TestCase(
        val mnemonic: String,
        val passphrase: String,
        val index: Int,
        val expectedAddress: String,
    )

    listOf(
        TestCase(
            "work man father plunge mystery proud hollow address reunion sauce theory bonus",
            "TREZOR123",
            0,
            "0x431a00DA1D54c281AeF638A73121B3D153e0b0F6",
        ),
        TestCase(
            "inject danger program federal spice bitter term garbage coyote breeze thought funny",
            "LEDGER321",
            1,
            "0x231a3D0a05d13FAf93078C779FeeD3752ea1350C",
        ),
        TestCase(
            "fire evolve buddy tenant talent favorite ankle stem regret myth dream fresh",
            "",
            2,
            "0x1D86AD5eBb2380dAdEAF52f61f4F428C485460E9",
        ),
        TestCase(
            "thumb soda tape crunch maple fresh imitate cancel order blind denial giraffe",
            "",
            3,
            "0xFB78b25f69A8e941036fEE2A5EeAf349D81D4ccc",
        ),
    ).forAll { (mnemonic, passphrase, index, expectedAddress) ->
        test("mnemonic=$mnemonic, passphrase=$passphrase, index=$index") {
            val source = MnemonicKeySource(mnemonic, passphrase, HDPath.ETHEREUM)

            source.getAccount(index).address shouldBe Address(expectedAddress)
        }
    }
})
