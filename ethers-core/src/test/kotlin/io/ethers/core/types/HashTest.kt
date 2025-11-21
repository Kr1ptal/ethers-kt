package io.ethers.core.types

import io.ethers.core.FastHex
import io.ethers.json.jackson.Jackson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bigInt
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import java.math.BigInteger

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

    context("constructors") {
        context("BigInteger") {
            withData(
                nameFn = { it.toString() },
                BigInteger.ZERO to "0x0000000000000000000000000000000000000000000000000000000000000000",
                BigInteger.ONE to "0x0000000000000000000000000000000000000000000000000000000000000001",
                BigInteger.TEN to "0x000000000000000000000000000000000000000000000000000000000000000a",
                BigInteger("765456789032412362757890124973865712381") to "0x000000000000000000000000000000023fdd9d780f1088c8e7adeca0bdec3afd",
                BigInteger.TWO.pow(255) - BigInteger.ONE to "0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
            ) { (value, expected) ->
                Hash(value) shouldBe Hash(expected)
            }

            test("throws exception for values with more than 256 bits") {
                shouldThrow<IllegalArgumentException> { Hash(BigInteger.TWO.pow(256)) }
                shouldThrow<IllegalArgumentException> { Hash(BigInteger.TWO.pow(300)) }
            }

            test("correct hash for random bits between 0 and 256") {
                Arb.int(1..256).checkAll {
                    val value = BigInteger.TWO.pow(it) - BigInteger.ONE
                    val expected = value.toString(16).padStart(64, '0')
                    Hash(value) shouldBe Hash(expected)
                }
            }
        }
    }
})
