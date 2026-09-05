package io.ethers.solana.serialization

import com.ditchoom.buffer.BufferFactory
import com.ditchoom.buffer.Default
import com.ditchoom.buffer.PlatformBuffer
import io.github.artificialpb.bignum.BigInteger

/** Bounded primitives for Solana's legacy/v0 wire format and fixed-width instruction values, not Borsh. */
internal class SolanaMessageDecoder(bytes: ByteArray) {
    private val buffer: PlatformBuffer = BufferFactory.Default.wrap(bytes)
    val remaining: Int get() = buffer.remaining()

    fun readByte(): Int {
        require(remaining > 0) { "Truncated binary payload" }
        return buffer.readUnsignedByte().toInt()
    }

    fun readBytes(count: Int): ByteArray {
        require(count in 0..remaining) { "Truncated binary payload" }
        return buffer.copyToByteArray(count)
    }

    /** Read a canonical shortvec (compact-u16) length, rejecting overflow and overlong encodings. */
    fun readShortVecLength(): Int {
        var result = 0
        for (i in 0..2) {
            val next = readByte()
            require(i != 2 || next <= 3) { "Shortvec overflow" }
            require(i == 0 || next != 0) { "Non-canonical shortvec" }
            result = result or ((next and 127) shl (i * 7))
            if (next and 128 == 0) return result
        }
        error("Invalid shortvec")
    }

    fun readUnsignedLittleEndian(size: Int): BigInteger = BigInteger(1, readBytes(size).reversedArray())

    fun requireDone() = require(remaining == 0) { "Trailing binary data" }
}
