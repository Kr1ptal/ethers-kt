package io.ethers.solana

import io.ethers.solana.utils.U64_MAX
import io.ethers.solana.utils.littleEndian
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.random.Random

/** The previous implementation, kept as the reference these bytes must still match. */
private fun reference(value: BigInteger, size: Int): ByteArray {
    require(value.signum() >= 0 && value.bitLength() <= size * 8) { "Value does not fit $size bytes" }
    val bytes = value.toByteArray().reversedArray()
    return ByteArray(size) { bytes.getOrElse(it) { 0 } }
}

class LittleEndianTest : FunSpec({
    test("agrees with the previous implementation across widths and boundaries") {
        val boundaries = listOf(
            BigInteger("0"), BigInteger("1"), BigInteger("127"), BigInteger("128"), BigInteger("255"),
            BigInteger("256"), BigInteger("65535"), BigInteger("65536"), BigInteger("4294967295"),
            BigInteger("4294967296"), BigInteger("9223372036854775807"), // Long.MAX, the fast-path edge
            BigInteger("9223372036854775808"), // one past it, where the wide path takes over
            U64_MAX,
        )
        for (value in boundaries) {
            for (size in 1..16) {
                if (value.bitLength() > size * 8) continue
                littleEndian(value, size) shouldBe reference(value, size)
            }
        }
    }

    test("agrees with the previous implementation on random values") {
        val random = Random(0xE1)
        repeat(500) {
            val size = random.nextInt(1, 17)
            val value = BigInteger(1, ByteArray(random.nextInt(1, size + 1)) { random.nextInt().toByte() })
            if (value.bitLength() > size * 8) return@repeat
            littleEndian(value, size) shouldBe reference(value, size)
        }
    }

    test("the Long overload matches the BigInteger one") {
        for (value in listOf(0L, 1L, 255L, 256L, 65535L, 4294967295L, Long.MAX_VALUE)) {
            for (size in 1..8) {
                if (size < 8 && (value ushr (size * 8)) != 0L) continue
                littleEndian(value, size) shouldBe littleEndian(bigIntegerOf(value), size)
            }
        }
    }

    test("values that do not fit their width are rejected, as before") {
        shouldThrow<IllegalArgumentException> { littleEndian(bigIntegerOf(256), 1) }
        shouldThrow<IllegalArgumentException> { littleEndian(BigInteger("-1"), 8) }
        shouldThrow<IllegalArgumentException> { littleEndian(256L, 1) }
        shouldThrow<IllegalArgumentException> { littleEndian(-1L, 8) }
        shouldThrow<IllegalArgumentException> { littleEndian(1L, 9) }
        // u64 max still encodes, which is the widest value the instructions carry
        littleEndian(U64_MAX, 8) shouldBe ByteArray(8) { -1 }
    }
})
