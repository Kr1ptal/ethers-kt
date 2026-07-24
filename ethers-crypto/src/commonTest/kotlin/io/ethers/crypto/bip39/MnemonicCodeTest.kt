package io.ethers.crypto.bip39

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class MnemonicCodeTest : FunSpec({
    test("constructor fails on invalid word list") {
        listOf(
            emptyList(),
            listOf("turtle", "front", "uncle", "idea"),
        ).forAll {
            shouldThrow<IllegalArgumentException> {
                MnemonicCode(it)
            }
        }
    }

    test("getEntropy fails on invalid word list") {
        listOf(
            listOf("turtle", "front", "randomInvalidWord"), // invalid word
            listOf("turtle", "front", "uncle", "idea", "write", "write"), // invalid checksum
        ).forAll {
            shouldThrow<RuntimeException> {
                MnemonicCode(it).getEntropy()
            }
        }
    }

    test("fromEntropy fails on invalid entropy length") {
        listOf(
            byteArrayOf(),
            byteArrayOf(1, 2),
            byteArrayOf(1, 2, 3, 4),
            ByteArray(12),
            ByteArray(36),
        ).forAll {
            shouldThrow<IllegalArgumentException> {
                MnemonicCode.fromEntropy(it, MnemonicWordListEnglish)
            }
        }
    }

    context("test vectors") {
        test("english") {
            Bip39EnglishTestVectorsData.VECTORS.forAll { vector ->
                val mnemonic = MnemonicCode.fromEntropy(vector.entropy.hexToByteArray())

                mnemonic.words shouldContainExactly vector.mnemonic.split(MnemonicWordListEnglish.separator)
                mnemonic.getSeed(vector.passphrase) shouldBe vector.seed.hexToByteArray()
                mnemonic.getEntropy() shouldBe vector.entropy.hexToByteArray()
            }
        }
    }
})
