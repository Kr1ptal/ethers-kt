package io.ethers.solana.serialization

import com.ditchoom.buffer.BufferFactory
import com.ditchoom.buffer.Default
import com.ditchoom.buffer.PlatformBuffer

/**
 * Byte and compact-u16 primitives for Solana transaction wire formats, not Borsh.
 *
 * Starts at the size of the largest legacy or v0 packet, so encoding one never grows the buffer. The
 * smallest message a transaction can have is 150 bytes - a single transfer, three accounts - so a
 * smaller start would reallocate and copy for every transaction, and four times over for a full one.
 * [toByteArray] returns an exactly sized copy, so the slack is never handed out or retained.
 */
internal class SolanaMessageEncoder(capacity: Int = MAX_PACKET_SIZE) {
    private var bytes = ByteArray(capacity)
    private var buffer: PlatformBuffer = BufferFactory.Default.wrap(bytes)

    fun writeByte(value: Int): SolanaMessageEncoder {
        require(value in 0..255) { "Byte out of range" }
        ensureCapacity(1)
        buffer.writeByte(value.toByte())
        return this
    }

    fun writeBytes(value: ByteArray): SolanaMessageEncoder {
        ensureCapacity(value.size)
        buffer.writeBytes(value)
        return this
    }

    /** Write a canonical shortvec (compact-u16) length in one to three bytes. */
    fun writeShortVecLength(value: Int): SolanaMessageEncoder {
        require(value in 0..65535) { "Shortvec length out of range" }
        var rest = value
        do {
            val low = rest and 127
            rest = rest ushr 7
            writeByte(low or if (rest != 0) 128 else 0)
        } while (rest != 0)
        return this
    }

    /** Return an independent copy of the written bytes without changing the write position. */
    fun toByteArray(): ByteArray = bytes.copyOf(buffer.position())

    companion object {
        /** The wire limit for legacy and v0 transactions; v1 allows more and may still grow once. */
        const val MAX_PACKET_SIZE: Int = 1232
    }

    private fun ensureCapacity(count: Int) {
        val position = buffer.position()
        require(count >= 0 && position <= Int.MAX_VALUE - count) { "Binary payload too large" }
        val required = position + count
        if (required <= bytes.size) return
        val doubled = (bytes.size.toLong() * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        bytes = bytes.copyOf(maxOf(required, doubled))
        buffer = BufferFactory.Default.wrap(bytes)
        buffer.position(position)
    }
}
