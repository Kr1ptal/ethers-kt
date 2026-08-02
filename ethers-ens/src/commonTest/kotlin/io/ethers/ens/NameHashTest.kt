package io.ethers.ens

import io.ethers.core.FastHex
import io.ethers.core.types.Bytes
import io.ethers.ens.normalize.InvalidLabelException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class NameHashTest : FunSpec({
    context("Expected nameHash") {
        withData(
            listOf(
                "" to "0x0000000000000000000000000000000000000000000000000000000000000000",
                "eth" to "0x93cdeb708b7545dc668eb9280176169d1c33cfd8ed6f04690a0bcc88a93fc4ae",
                "foo.eth" to "0xde9b09fd7c5f901e23a3f19fecc54828e9c848539801e86591bd9801b019f84f",
                "\uD83D\uDC8E.test.eth" to "0x47cc6ab7edfed1938183b144966298c1742fd9261c12fed859471364f7b8e364",
            ),
        ) {
            FastHex.encodeWithPrefix(NameHash.nameHash(it.first)) shouldBe it.second
        }
    }

    context("Expected DNS encode") {
        withData(
            listOf(
                "1.offchainexample.eth" to "0x01310f6f6666636861696e6578616d706c650365746800",
                // each label is length-prefixed with its UTF-8 *byte* length, not its UTF-16 length. 💎 (U+1F48E)
                // is 2 UTF-16 code units but 4 UTF-8 bytes, so the prefix must be 0x04.
                "💎.eth" to "0x04f09f928e0365746800",
                // é (U+00E9) is 1 UTF-16 code unit but 2 UTF-8 bytes: "café" is 4 chars / 5 bytes -> 0x05
                "café.eth" to "0x05636166c3a90365746800",
            ),
        ) {
            NameHash.dnsEncode(it.first) shouldBe Bytes(it.second)
        }
    }

    test("DNS encode length prefix matches the UTF-8 byte length of each label") {
        // walks the encoding and checks every prefix against the bytes that follow it, so a UTF-16/UTF-8
        // mismatch is caught for any label rather than only the hard-coded vectors above
        for (name in listOf("a.eth", "café.eth", "💎.eth", "日本語.eth", "mixed-café-💎.eth")) {
            val encoded = NameHash.dnsEncode(name).asByteArray()
            val expectedLabels = name.split(".").map { it.encodeToByteArray() }

            var offset = 0
            for (expected in expectedLabels) {
                val declaredLength = encoded[offset].toInt() and 0xff
                declaredLength shouldBe expected.size
                offset++
                encoded.copyOfRange(offset, offset + declaredLength) shouldBe expected
                offset += declaredLength
            }
            offset shouldBe encoded.size - 1
            encoded[offset] shouldBe 0.toByte()
        }
    }

    test("Normalisation error") {
        shouldThrow<InvalidLabelException> {
            NameHash.nameHash(".")
        }
        shouldThrow<InvalidLabelException> {
            NameHash.nameHash(".eth")
        }
    }
})
