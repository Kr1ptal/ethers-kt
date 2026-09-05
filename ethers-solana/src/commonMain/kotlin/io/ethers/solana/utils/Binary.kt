package io.ethers.solana.utils

import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf

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

internal class BinaryWriter {
    private var bytes = ByteArray(128)
    private var size = 0
    fun byte(value: Int): BinaryWriter {
        require(value in 0..255) { "Byte out of range" }
        ensure(1)
        bytes[size++] = value.toByte()
        return this
    }
    fun bytes(value: ByteArray): BinaryWriter {
        ensure(value.size)
        value.copyInto(bytes, size)
        size += value.size
        return this
    }
    fun length(value: Int): BinaryWriter {
        require(value in 0..65535) { "Shortvec length out of range" }
        var rest = value
        do {
            val low = rest and 127
            rest = rest ushr 7
            byte(low or if (rest != 0) 128 else 0)
        } while (rest != 0)
        return this
    }
    fun toByteArray(): ByteArray = bytes.copyOf(size)
    private fun ensure(count: Int) {
        require(count >= 0 && size <= Int.MAX_VALUE - count) { "Binary payload too large" }
        if (size + count > bytes.size) bytes = bytes.copyOf(maxOf(size + count, bytes.size * 2))
    }
}

internal class BinaryReader(private val bytes: ByteArray) {
    private var offset = 0
    val remaining: Int get() = bytes.size - offset
    fun byte(): Int {
        require(remaining > 0) { "Truncated binary payload" }
        return bytes[offset++].toInt() and 255
    }
    fun bytes(count: Int): ByteArray {
        require(count in 0..remaining) { "Truncated binary payload" }
        return bytes.copyOfRange(offset, offset + count).also { offset += count }
    }
    fun length(): Int {
        var result = 0
        for (i in 0..2) {
            val next = byte()
            require(i != 2 || next <= 3) { "Shortvec overflow" }
            require(i == 0 || next != 0) { "Non-canonical shortvec" }
            result = result or ((next and 127) shl (i * 7))
            if (next and 128 == 0) return result
        }
        error("Invalid shortvec")
    }
    fun unsigned(size: Int): BigInteger = BigInteger(1, bytes(size).reversedArray())
    fun requireDone() = require(remaining == 0) { "Trailing binary data" }
}
