package io.ethers.ens

import io.ethers.EnsResolver
import io.ethers.NameHash
import io.ethers.core.FastHex
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

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
            FastHex.encodeWithPrefix(NameHash.nameHash(it.first).resultOrThrow()) shouldBe it.second
        }
    }

    test("Normalisation error") {
        NameHash.nameHash(".").error.shouldBeInstanceOf<EnsResolver.Error.Normalisation>()
        NameHash.nameHash(".eth").error.shouldBeInstanceOf<EnsResolver.Error.Normalisation>()
    }
})
