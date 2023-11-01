package io.ethers.core.types

import io.ethers.core.FastHex
import io.ethers.core.Jackson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bigInt
import io.kotest.property.checkAll

class HashTest : FunSpec({

    test("Hash must be 32 bytes long") {
        Arb.bigInt(0, 247).checkAll {
            shouldThrow<IllegalArgumentException> {
                Hash(it.toByteArray())
            }
        }
    }

    context("equals") {
        val hashString = "8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925"
        val hash = Hash(hashString)

        test("same as CharSequence") {
            hash.equals(hashString) shouldBe true
        }

        test("same as ByteArray") {
            hash.equals(FastHex.decode(hashString)) shouldBe true
        }

        test("other CharSequence does not equal") {
            hash.equals("0x2c00f9fd0fcdeb1ccaf7a31d05702b578ea1b8f8feccd2cd63423cdd41e4149c") shouldBe false
        }
    }

    test("serialization / deserialization") {
        val hash = Hash("0x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925")
        val json = Jackson.MAPPER.writeValueAsString(hash)
        val deserialized = Jackson.MAPPER.readValue(json, Hash::class.java)

        deserialized shouldBe hash
    }
})
