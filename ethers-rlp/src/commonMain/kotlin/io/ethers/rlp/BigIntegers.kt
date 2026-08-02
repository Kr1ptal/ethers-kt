package io.ethers.rlp

import io.github.artificialpb.bignum.BigInteger

/**
 * Read [bytes] as an unsigned magnitude, the equivalent of `java.math.BigInteger(1, bytes)`.
 *
 * bignum-kt's `ByteArray` constructor reads two's-complement on every platform, so a leading byte of `0x80` or
 * above would come back negative. A zero byte is prepended in that case to force an unsigned reading.
 */
internal fun bigIntegerFromUnsigned(
    bytes: ByteArray,
    offset: Int = 0,
    length: Int = bytes.size - offset,
): BigInteger {
    if (length > 0 && bytes[offset] < 0) {
        val padded = ByteArray(length + 1)
        bytes.copyInto(padded, 1, offset, offset + length)
        return BigInteger(padded)
    }

    return BigInteger(bytes, offset, length)
}
