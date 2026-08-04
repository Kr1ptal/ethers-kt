package io.ethers.abi

import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary

/**
 * Common replacement for kotest's `Arb.bigInt`, which is JVM-only because it generates `java.math.BigInteger`.
 *
 * Generates non-negative values whose bit length falls in `minNumBits..maxNumBits`, matching the shape of the
 * kotest generator these tests previously used.
 *
 * NOTE: duplicated in the commonTest source sets of ethers-core and ethers-crypto. Test source sets are not shared
 * between gradle modules, so the alternative would be a dedicated test-only module for ~20 lines.
 */
fun Arb.Companion.bigInteger(maxNumBits: Int): Arb<BigInteger> = bigInteger(0, maxNumBits)

fun Arb.Companion.bigInteger(minNumBits: Int, maxNumBits: Int): Arb<BigInteger> = arbitrary { rs ->
    val bits = rs.random.nextInt(minNumBits, maxNumBits + 1)
    if (bits == 0) {
        return@arbitrary bigIntegerOf(0)
    }

    val bytes = ByteArray((bits + 7) / 8) { rs.random.nextInt().toByte() }

    // drop the surplus high bits of the leading byte, so the value never exceeds `bits` bits
    val surplus = bytes.size * 8 - bits
    bytes[0] = (bytes[0].toInt() and (0xFF ushr surplus)).toByte()

    BigInteger(1, bytes)
}
