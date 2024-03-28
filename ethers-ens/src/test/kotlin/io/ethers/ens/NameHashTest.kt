package io.ethers.ens

import io.ethers.core.FastHex
import io.ethers.core.types.Bytes
import io.github.adraffy.ens.InvalidLabelException
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
            ),
        ) {
            NameHash.dnsEncode(it.first) shouldBe Bytes(it.second)
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
