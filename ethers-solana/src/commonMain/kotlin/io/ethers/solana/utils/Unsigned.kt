package io.ethers.solana.utils

import io.github.artificialpb.bignum.BigInteger

internal const val U32_MAX = 4294967295L

internal val U64_MAX = BigInteger("18446744073709551615")
internal fun requireU64(value: BigInteger): BigInteger {
    require(value.signum() >= 0 && value <= U64_MAX) { "Value must fit an unsigned 64-bit integer" }
    return value
}

/**
 * Fixed-width little-endian encoding, as the instruction payloads and the v1 header use.
 *
 * Fills the result directly rather than reversing and padding an intermediate: the values written
 * here are almost always lamports, compute limits or lengths, which fit a Long and never need the
 * BigInteger's own byte array at all.
 */
internal fun littleEndian(value: BigInteger, size: Int): ByteArray {
    require(value.signum() >= 0 && value.bitLength() <= size * 8) { "Value does not fit $size bytes" }

    // a Long holds the whole value below 2^63, where its bit pattern needs no interpreting
    if (value.bitLength() <= 63) return littleEndianBits(value.toLong(), size)

    // wider values are read straight out of the big-endian bytes, back to front
    val source = value.toByteArray()
    val out = ByteArray(size)
    var from = source.size - 1
    var into = 0
    while (from >= 0 && into < size) out[into++] = source[from--]
    return out
}

/** As [littleEndian], for a value already held as a Long, which skips the BigInteger entirely. */
internal fun littleEndian(value: Long, size: Int): ByteArray {
    require(size in 1..8) { "A Long spans at most 8 bytes, got $size" }
    require(value >= 0 && (size == 8 || (value ushr (size * 8)) == 0L)) { "Value does not fit $size bytes" }
    return littleEndianBits(value, size)
}

private fun littleEndianBits(bits: Long, size: Int): ByteArray {
    val out = ByteArray(size)
    var remaining = bits
    var i = 0
    while (i < size && remaining != 0L) {
        out[i++] = remaining.toByte()
        remaining = remaining ushr 8
    }
    return out
}
