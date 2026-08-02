package io.ethers.core

import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * bignum-kt's `ByteArray` constructor reads two's-complement, so anything with the top bit set comes back
 * negative. These pin the unsigned reading that `java.math.BigInteger(1, bytes)` used to give us.
 */
class BigIntegersTest : FunSpec({
    test("leading byte below 0x80 is unchanged by the two's-complement reading") {
        bigIntegerFromUnsigned(byteArrayOf(0x7F)) shouldBe bigIntegerOf(127)
        bigIntegerFromUnsigned(byteArrayOf(0x01, 0x00)) shouldBe bigIntegerOf(256)
    }

    test("leading byte at or above 0x80 stays positive") {
        // the whole point: two's-complement would read these as -1, -128 and -2
        bigIntegerFromUnsigned(byteArrayOf(0xFF.toByte())) shouldBe bigIntegerOf(255)
        bigIntegerFromUnsigned(byteArrayOf(0x80.toByte())) shouldBe bigIntegerOf(128)
        bigIntegerFromUnsigned(byteArrayOf(0xFF.toByte(), 0xFE.toByte())) shouldBe bigIntegerOf(65534)
    }

    test("a full 32-byte max value reads as 2^256 - 1") {
        val maxUint256 = ByteArray(32) { 0xFF.toByte() }
        val expected = bigIntegerOf(2).pow(256).subtract(bigIntegerOf(1))

        bigIntegerFromUnsigned(maxUint256) shouldBe expected
    }

    test("offset and length select the right window, still unsigned") {
        val bytes = byteArrayOf(0x00, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0x11)

        bigIntegerFromUnsigned(bytes, 2, 2) shouldBe bigIntegerOf(65535)
        bigIntegerFromUnsigned(bytes, 4, 1) shouldBe bigIntegerOf(17)
    }

    test("padding does not disturb the source array") {
        val bytes = byteArrayOf(0xFF.toByte(), 0x01)
        bigIntegerFromUnsigned(bytes) shouldBe bigIntegerOf(65281)

        bytes shouldBe byteArrayOf(0xFF.toByte(), 0x01)
    }

    test("signed reading really does differ, so the helper is not redundant") {
        BigInteger(byteArrayOf(0xFF.toByte())) shouldBe bigIntegerOf(-1)
        bigIntegerFromUnsigned(byteArrayOf(0xFF.toByte())) shouldBe bigIntegerOf(255)
    }
})
