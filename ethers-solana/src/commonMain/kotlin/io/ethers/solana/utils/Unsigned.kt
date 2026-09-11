package io.ethers.solana.utils

import io.github.artificialpb.bignum.BigInteger

internal const val U32_MAX = 4294967295L

internal val U64_MAX = BigInteger("18446744073709551615")
internal fun requireU64(value: BigInteger): BigInteger {
    require(value.signum() >= 0 && value <= U64_MAX) { "Value must fit an unsigned 64-bit integer" }
    return value
}

internal fun littleEndian(value: BigInteger, size: Int): ByteArray {
    require(value.signum() >= 0 && value.bitLength() <= size * 8) { "Value does not fit $size bytes" }
    val bytes = value.toByteArray().reversedArray()
    return ByteArray(size) { bytes.getOrElse(it) { 0 } }
}
