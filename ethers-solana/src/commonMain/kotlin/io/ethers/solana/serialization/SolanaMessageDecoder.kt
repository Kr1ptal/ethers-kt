package io.ethers.solana.serialization

import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf

/**
 * Bounded primitives for Solana transaction wire formats and fixed-width instruction values, not Borsh.
 *
 * Malformed input is reported by setting [error] rather than by throwing, so decoding untrusted bytes
 * never pays for a stack trace. The first failure sticks: every later read returns a harmless zero
 * value and reads no further, leaving the caller free to finish the shape it is building and check
 * [error] once at the end. Counts read after a failure are zero, so no list is sized from bytes that
 * were never there.
 */
internal class SolanaMessageDecoder(private val bytes: ByteArray) {
    /** Offset of the next byte to read. */
    var position: Int = 0
        private set

    /** The first malformation seen, or null while the input is still readable. */
    var error: String? = null
        private set

    val failed: Boolean get() = error != null
    val remaining: Int get() = bytes.size - position

    private fun fail(message: String) {
        if (error == null) error = message
    }

    /** Whether [count] more bytes can be read, failing the decoder if they cannot. */
    private fun available(count: Int): Boolean {
        if (failed) return false
        if (count !in 0..remaining) {
            fail("Truncated binary payload")
            return false
        }
        return true
    }

    fun readByte(): Int {
        if (!available(1)) return 0
        return bytes[position++].toInt() and 0xff
    }

    fun readBytes(count: Int): ByteArray {
        if (!available(count)) return ByteArray(if (count in 0..MAX_ZERO_FILL) count else 0)
        val result = bytes.copyOfRange(position, position + count)
        position += count
        return result
    }

    /** Read [count] unsigned bytes as a list of indices, stored unboxed. */
    fun readU8List(count: Int): List<Int> {
        if (count == 0 || !available(count)) return emptyList()
        return U8List(readBytes(count))
    }

    /** Copy the bytes already read from [start] up to the current position. */
    fun copyFrom(start: Int): ByteArray = bytes.copyOfRange(start, position)

    /** Whether the next [count] bytes are all zero, without consuming them. */
    fun peekZeros(count: Int): Boolean {
        if (failed || count > remaining) return false
        for (i in position until position + count) {
            if (bytes[i].toInt() != 0) return false
        }
        return true
    }

    /** Skip [count] bytes, failing the decoder if they are not there. */
    fun skip(count: Int) {
        if (available(count)) position += count
    }

    /** Read a canonical shortvec (compact-u16) length, rejecting overflow and overlong encodings. */
    fun readShortVecLength(): Int {
        if (failed) return 0
        var result = 0
        for (i in 0..2) {
            val next = readByte()
            if (failed) return 0
            if (i == 2 && next > 3) {
                fail("Shortvec overflow")
                return 0
            }
            if (i != 0 && next == 0) {
                fail("Non-canonical shortvec")
                return 0
            }
            result = result or ((next and 127) shl (i * 7))
            if (next and 128 == 0) return result
        }
        fail("Invalid shortvec")
        return 0
    }

    /** Read an unsigned little-endian value of at most 8 bytes, which is negative only for a full 8-byte value above [Long.MAX_VALUE]. */
    fun readLittleEndianLong(size: Int): Long {
        require(size in 1..8) { "A Long spans at most 8 bytes, got $size" }
        if (!available(size)) return 0
        var result = 0L
        for (i in size - 1 downTo 0) {
            result = (result shl 8) or (bytes[position + i].toLong() and 0xff)
        }
        position += size
        return result
    }

    fun readUnsignedLittleEndian(size: Int): BigInteger {
        if (size in 1..8) {
            val start = position
            val value = readLittleEndianLong(size)
            if (failed) return bigIntegerOf(0)
            if (value >= 0) return bigIntegerOf(value)
            // above Long.MAX_VALUE, so fall back to the magnitude bytes
            return BigInteger(1, bytes.copyOfRange(start, start + size).reversedArray())
        }
        val bytes = readBytes(size)
        if (failed) return bigIntegerOf(0)
        return BigInteger(1, bytes.reversedArray())
    }

    /**
     * Read [count] items, stopping at the first malformation rather than running the reader against
     * a length the remaining bytes cannot support.
     */
    inline fun <T> readList(count: Int, read: (Int) -> T): List<T> {
        if (failed || count <= 0) return emptyList()
        val items = ArrayList<T>(minOf(count, remaining + 1))
        for (i in 0 until count) {
            if (failed) return emptyList()
            items.add(read(i))
        }
        return if (failed) emptyList() else items
    }

    fun requireDone() {
        if (!failed && remaining != 0) fail("Trailing binary data")
    }

    companion object {
        /** A failed read still returns a correctly sized buffer up to this length, so callers building fixed-width values do not themselves fail. */
        const val MAX_ZERO_FILL: Int = 64
    }
}
