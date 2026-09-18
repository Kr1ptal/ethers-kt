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
 * [littleEndianInto] is the primitive: an instruction builds its whole payload in one array and
 * writes each field into place, rather than concatenating a piece at a time.
 */
internal fun littleEndian(value: BigInteger, size: Int): ByteArray = ByteArray(size).also { littleEndianInto(it, 0, value, size) }

/** As [littleEndian], for a value already held as a Long, which skips the BigInteger entirely. */
internal fun littleEndian(value: Long, size: Int): ByteArray = ByteArray(size).also { littleEndianInto(it, 0, value, size) }

/** Write [value] little-endian across [size] bytes of [dest], starting at [offset]. */
internal fun littleEndianInto(dest: ByteArray, offset: Int, value: BigInteger, size: Int) {
    require(value.signum() >= 0 && value.bitLength() <= size * 8) { "Value does not fit $size bytes" }

    // a Long holds the whole value below 2^63, where its bit pattern needs no interpreting
    if (value.bitLength() <= 63) return writeLittleEndian(dest, offset, value.toLong(), size)

    // wider values are read straight out of the big-endian bytes, back to front
    val source = value.toByteArray()
    var from = source.size - 1
    var i = 0
    while (i < size) dest[offset + i++] = if (from >= 0) source[from--] else 0
}

/** As [littleEndianInto], for a value already held as a Long. */
internal fun littleEndianInto(dest: ByteArray, offset: Int, value: Long, size: Int) {
    require(size in 1..8) { "A Long spans at most 8 bytes, got $size" }
    require(value >= 0 && (size == 8 || (value ushr (size * 8)) == 0L)) { "Value does not fit $size bytes" }
    writeLittleEndian(dest, offset, value, size)
}

private fun writeLittleEndian(dest: ByteArray, offset: Int, bits: Long, size: Int) {
    var remaining = bits
    for (i in 0 until size) {
        dest[offset + i] = remaining.toByte()
        remaining = remaining ushr 8
    }
}
