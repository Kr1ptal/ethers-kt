package io.ethers.solana.codec

import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes
import io.github.artificialpb.bignum.BigInteger
import kotlin.jvm.JvmOverloads

/**
 * Growable Borsh output buffer.
 *
 * A value outside its declared width throws [IllegalArgumentException]: encoding validates the
 * caller's own values, so a failure there is a programming error rather than untrusted input.
 */
class BorshWriter @JvmOverloads constructor(initialCapacity: Int = 64) {
    private var buffer = ByteArray(initialCapacity.coerceAtLeast(8))

    /** Number of bytes written so far. */
    var size: Int = 0
        private set

    private fun reserve(count: Int): Int {
        val start = size
        val required = start + count
        if (required > buffer.size) buffer = buffer.copyOf(maxOf(required, buffer.size * 2))
        size = required
        return start
    }

    private fun byte(value: Int) {
        val start = reserve(1)
        buffer[start] = value.toByte()
    }

    private fun short(value: Int) {
        val start = reserve(2)
        buffer[start] = value.toByte()
        buffer[start + 1] = (value ushr 8).toByte()
    }

    private fun int(value: Int) = intAt(reserve(4), value)

    private fun long(value: Long) = longAt(reserve(8), value)

    private fun intAt(start: Int, value: Int) {
        val buffer = buffer
        buffer[start] = value.toByte()
        buffer[start + 1] = (value ushr 8).toByte()
        buffer[start + 2] = (value ushr 16).toByte()
        buffer[start + 3] = (value ushr 24).toByte()
    }

    private fun longAt(start: Int, value: Long) {
        intAt(start, value.toInt())
        intAt(start + 4, (value ushr 32).toInt())
    }

    /** Write [value] across [width] bytes, two's complement when [signed], after checking it fits. */
    private fun writeBig(value: BigInteger, width: Int, signed: Boolean) {
        val bitLength = value.bitLength()
        val negative = value.signum() < 0
        if (signed) {
            require(bitLength < width * 8) { "Value does not fit a signed ${width * 8}-bit integer" }
        } else {
            require(!negative && bitLength <= width * 8) { "Value does not fit an unsigned ${width * 8}-bit integer" }
        }
        val start = reserve(width)
        if (bitLength < Long.SIZE_BITS) {
            // fits a Long exactly, so skip the big-endian copy; the bytes above the low 8 are sign fill
            longAt(start, value.toLong())
            if (negative) buffer.fill(-1, start + 8, start + width)
            // bytes past the written size are always zero, so a non-negative value needs no fill
            return
        }
        val bigEndian = value.toByteArray()
        val fill: Byte = if (negative) -1 else 0
        for (i in 0 until width) {
            val from = bigEndian.size - 1 - i
            buffer[start + i] = if (from >= 0) bigEndian[from] else fill
        }
    }

    fun writeU8(value: Int) {
        require(value and 0xff.inv() == 0) { "Value $value does not fit an unsigned 8-bit integer" }
        byte(value)
    }

    fun writeU16(value: Int) {
        require(value and 0xffff.inv() == 0) { "Value $value does not fit an unsigned 16-bit integer" }
        short(value)
    }

    fun writeU32(value: Long) {
        require(value and 0xffffffffL.inv() == 0L) { "Value $value does not fit an unsigned 32-bit integer" }
        int(value.toInt())
    }

    fun writeU64(value: BigInteger) = writeBig(value, 8, false)
    fun writeU128(value: BigInteger) = writeBig(value, 16, false)
    fun writeU256(value: BigInteger) = writeBig(value, 32, false)

    fun writeI8(value: Byte) = byte(value.toInt())
    fun writeI16(value: Short) = short(value.toInt())
    fun writeI32(value: Int) = int(value)
    fun writeI64(value: Long) = long(value)
    fun writeI128(value: BigInteger) = writeBig(value, 16, true)
    fun writeI256(value: BigInteger) = writeBig(value, 32, true)

    fun writeF32(value: Float) {
        require(!value.isNaN()) { "NaN is not a valid Borsh f32" }
        int(value.toRawBits())
    }

    fun writeF64(value: Double) {
        require(!value.isNaN()) { "NaN is not a valid Borsh f64" }
        long(value.toRawBits())
    }

    fun writeBool(value: Boolean) = byte(if (value) 1 else 0)

    fun writeOptionTag(present: Boolean) = writeBool(present)

    /** Write a u32 collection length. */
    fun writeLength(length: Int) {
        require(length >= 0) { "Length must not be negative" }
        int(length)
    }

    /** Write exactly [size] bytes, without a length prefix. */
    fun writeFixedBytes(value: SolanaBytes, size: Int) {
        require(value.size == size) { "Expected $size bytes, got ${value.size}" }
        writeRaw(value.asByteArray())
    }

    /** Write u32-length-prefixed bytes. */
    fun writeBytes(value: SolanaBytes) {
        writeLength(value.size)
        writeRaw(value.asByteArray())
    }

    /**
     * Write u32-length-prefixed UTF-8. An unpaired surrogate throws [CharacterCodingException] and
     * leaves the writer unchanged.
     */
    fun writeString(value: String) {
        val length = value.length
        // reserve the prefix and one byte per char, which is exact for ASCII, the common case
        val prefix = reserve(4 + length)
        var buffer = buffer
        var out = prefix + 4
        var i = 0
        while (i < length) {
            val c = value[i].code
            if (c >= 0x80) break
            buffer[out++] = c.toByte()
            i++
        }
        if (i < length) {
            // a char takes at most 3 bytes (a surrogate pair takes 4 for 2 chars), 1 of which is reserved
            reserve((length - i) * 2)
            buffer = this.buffer
            while (i < length) {
                val c = value[i++].code
                when {
                    c < 0x80 -> buffer[out++] = c.toByte()
                    c < 0x800 -> {
                        buffer[out++] = (0xc0 or (c shr 6)).toByte()
                        buffer[out++] = (0x80 or (c and 0x3f)).toByte()
                    }
                    c !in 0xd800..0xdfff -> {
                        buffer[out++] = (0xe0 or (c shr 12)).toByte()
                        buffer[out++] = (0x80 or ((c shr 6) and 0x3f)).toByte()
                        buffer[out++] = (0x80 or (c and 0x3f)).toByte()
                    }
                    c < 0xdc00 && i < length && value[i].code in 0xdc00..0xdfff -> {
                        val codePoint = 0x10000 + ((c - 0xd800) shl 10) + (value[i++].code - 0xdc00)
                        buffer[out++] = (0xf0 or (codePoint shr 18)).toByte()
                        buffer[out++] = (0x80 or ((codePoint shr 12) and 0x3f)).toByte()
                        buffer[out++] = (0x80 or ((codePoint shr 6) and 0x3f)).toByte()
                        buffer[out++] = (0x80 or (codePoint and 0x3f)).toByte()
                    }
                    else -> {
                        // an unpaired surrogate: restore the zeroed tail this writer relies on, then let the
                        // stdlib throw its usual exception for it
                        buffer.fill(0, prefix, out)
                        size = prefix
                        value.encodeToByteArray(throwOnInvalidSequence = true)
                        error("Unpaired surrogate at $i was not rejected")
                    }
                }
            }
        }
        // the unused part of the reservation is still zero, so dropping it keeps the tail zeroed
        size = out
        intAt(prefix, out - prefix - 4)
    }

    fun writeAddress(value: SolanaAddress) = writeRaw(value.asByteArray())

    /** Write a u32-length-prefixed vector, each element through [element]. */
    inline fun <T> writeVec(values: List<T>, element: BorshWriter.(T) -> Unit) {
        writeLength(values.size)
        for (value in values) element(value)
    }

    /** Write exactly [size] elements without a length prefix. */
    inline fun <T> writeArray(values: List<T>, size: Int, element: BorshWriter.(T) -> Unit) {
        require(values.size == size) { "Expected $size elements, got ${values.size}" }
        for (value in values) element(value)
    }

    /** Write an option from a nullable value. */
    inline fun <T : Any> writeOption(value: T?, present: BorshWriter.(T) -> Unit) {
        writeOptionTag(value != null)
        if (value != null) present(value)
    }

    /** Write an explicit option, which keeps nesting distinct. */
    inline fun <T> writeExplicitOption(value: SolanaOption<T>, present: BorshWriter.(T) -> Unit) {
        writeOptionTag(value is SolanaOption.Some)
        if (value is SolanaOption.Some) present(value.value)
    }

    /** Write [count] zero bytes of padding, which a zero-copy layout places between and after fields. */
    fun writePadding(count: Int) {
        require(count >= 0) { "Count must not be negative" }
        // bytes past the written size are always zero, in the initial buffer and in each grown copy
        reserve(count)
    }

    /** Write [bytes] verbatim, such as a discriminator. */
    fun writeRaw(bytes: ByteArray) {
        // reserve before reading the buffer, which reserving replaces when it grows
        val start = reserve(bytes.size)
        bytes.copyInto(buffer, start)
    }

    /**
     * The bytes written so far. Returns the buffer itself when it is exactly full; later writes grow
     * into a new buffer, so the returned array is never modified by this writer.
     */
    fun toByteArray(): ByteArray = if (size == buffer.size) buffer else buffer.copyOf(size)
}
