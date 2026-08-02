package io.ethers.core

import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * RLP decoding, ABI decoding, signature parsing and secp256k1 all read byte payloads as unsigned magnitudes via
 * `BigInteger(1, bytes)`.
 *
 * The single-argument constructor reads two's-complement instead, so anything with the top bit set would come
 * back negative. These pin that distinction, since silently swapping one for the other corrupts values rather
 * than failing.
 */
class BigIntegerBytesTest : FunSpec({
    test("signum form reads bytes as an unsigned magnitude") {
        BigInteger(1, byteArrayOf(0x7F)) shouldBe bigIntegerOf(127)
        BigInteger(1, byteArrayOf(0xFF.toByte())) shouldBe bigIntegerOf(255)
        BigInteger(1, byteArrayOf(0x80.toByte())) shouldBe bigIntegerOf(128)
        BigInteger(1, byteArrayOf(0xFF.toByte(), 0xFE.toByte())) shouldBe bigIntegerOf(65534)
    }

    test("single-argument form really is two's-complement, so the two are not interchangeable") {
        BigInteger(byteArrayOf(0xFF.toByte())) shouldBe bigIntegerOf(-1)
        BigInteger(1, byteArrayOf(0xFF.toByte())) shouldBe bigIntegerOf(255)
    }

    test("a full 32-byte max value reads as 2^256 - 1") {
        val maxUint256 = ByteArray(32) { 0xFF.toByte() }
        val expected = bigIntegerOf(2).pow(256).subtract(bigIntegerOf(1))

        BigInteger(1, maxUint256) shouldBe expected
    }

    test("offset and length select the right window, still unsigned") {
        val bytes = byteArrayOf(0x00, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0x11)

        BigInteger(1, bytes, 2, 2) shouldBe bigIntegerOf(65535)
        BigInteger(1, bytes, 4, 1) shouldBe bigIntegerOf(17)
    }
})
