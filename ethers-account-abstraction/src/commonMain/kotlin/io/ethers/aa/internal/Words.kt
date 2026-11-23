package io.ethers.aa.internal

import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf

internal val ZERO: BigInteger = bigIntegerOf(0)

/**
 * Encode the unsigned [value] as a big-endian, zero-padded array of exactly [size] bytes.
 *
 * @throws IllegalArgumentException if [value] is negative or does not fit into [size] bytes.
 */
internal fun uintToBytes(value: BigInteger, size: Int, name: String = "value"): ByteArray {
    require(value.signum() >= 0) { "$name must not be negative: $value" }
    val raw = value.toByteArray()
    // toByteArray is two's complement, so a positive value may carry a single leading zero sign byte
    val offset = if (raw.size > 1 && raw[0].toInt() == 0) 1 else 0
    val length = raw.size - offset
    require(length <= size) { "$name does not fit into ${size * 8} bits: $value" }

    val ret = ByteArray(size)
    raw.copyInto(ret, size - length, offset, raw.size)
    return ret
}

/**
 * Decode an unsigned big-endian integer from [data] in range [[from], [to]).
 */
internal fun bytesToUint(data: ByteArray, from: Int = 0, to: Int = data.size): BigInteger {
    if (from == to) return ZERO
    return BigInteger(1, data.copyOfRange(from, to))
}

internal fun concatBytes(vararg parts: ByteArray): ByteArray {
    var size = 0
    for (part in parts) size += part.size

    val ret = ByteArray(size)
    var offset = 0
    for (part in parts) {
        part.copyInto(ret, offset)
        offset += part.size
    }
    return ret
}

/**
 * Minimal writer of 32-byte ABI words, enough to ABI-encode tuples of static types without going through the
 * generic, reflection-free but [Any]-typed [io.ethers.abi.AbiCodec].
 */
internal class WordWriter(words: Int) {
    private val buffer = ByteArray(words * 32)
    private var offset = 0

    fun uint(value: BigInteger): WordWriter = raw(uintToBytes(value, 32))

    fun address(value: Address): WordWriter {
        value.asByteArray().copyInto(buffer, offset + 12)
        offset += 32
        return this
    }

    fun raw(word: ByteArray): WordWriter {
        require(word.size == 32) { "Word must be 32 bytes, got ${word.size}" }
        word.copyInto(buffer, offset)
        offset += 32
        return this
    }

    fun toByteArray(): ByteArray {
        check(offset == buffer.size) { "Not all words written: $offset of ${buffer.size} bytes" }
        return buffer
    }
}

internal fun Bytes?.orEmpty(): Bytes = this ?: Bytes.EMPTY
