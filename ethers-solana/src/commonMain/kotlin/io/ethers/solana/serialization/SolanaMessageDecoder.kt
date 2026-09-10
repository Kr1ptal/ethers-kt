package io.ethers.solana.serialization

import com.ditchoom.buffer.BufferFactory
import com.ditchoom.buffer.Default
import com.ditchoom.buffer.PlatformBuffer
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
internal class SolanaMessageDecoder(bytes: ByteArray) {
    private val buffer: PlatformBuffer = BufferFactory.Default.wrap(bytes)

    /** The first malformation seen, or null while the input is still readable. */
    var error: String? = null
        private set

    val failed: Boolean get() = error != null
    val remaining: Int get() = buffer.remaining()

    private fun fail(message: String) {
        if (error == null) error = message
    }

    fun readByte(): Int {
        if (failed) return 0
        if (remaining <= 0) {
            fail("Truncated binary payload")
            return 0
        }
        return buffer.readUnsignedByte().toInt()
    }

    fun readBytes(count: Int): ByteArray {
        if (failed) return ByteArray(if (count in 0..MAX_ZERO_FILL) count else 0)
        if (count !in 0..remaining) {
            fail("Truncated binary payload")
            return ByteArray(if (count in 0..MAX_ZERO_FILL) count else 0)
        }
        return buffer.copyToByteArray(count)
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

    fun readUnsignedLittleEndian(size: Int): BigInteger {
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
