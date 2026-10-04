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

    private fun writeLittleEndian(value: Long, width: Int) {
        val start = reserve(width)
        var remaining = value
        for (i in 0 until width) {
            buffer[start + i] = remaining.toByte()
            remaining = remaining ushr 8
        }
    }

    /** Write [value] across [width] bytes, two's complement when [signed], after checking it fits. */
    private fun writeBig(value: BigInteger, width: Int, signed: Boolean) {
        if (signed) {
            require(value.bitLength() < width * 8) { "Value does not fit a signed ${width * 8}-bit integer" }
        } else {
            require(value.signum() >= 0 && value.bitLength() <= width * 8) { "Value does not fit an unsigned ${width * 8}-bit integer" }
        }
        val bigEndian = value.toByteArray()
        val fill = if (value.signum() < 0) -1 else 0
        val start = reserve(width)
        for (i in 0 until width) {
            val from = bigEndian.size - 1 - i
            buffer[start + i] = if (from >= 0) bigEndian[from] else fill.toByte()
        }
    }

    private fun writeUnsigned(value: Long, width: Int) {
        require(value >= 0 && value ushr (width * 8) == 0L) { "Value $value does not fit an unsigned ${width * 8}-bit integer" }
        writeLittleEndian(value, width)
    }

    fun writeU8(value: Int) = writeUnsigned(value.toLong(), 1)
    fun writeU16(value: Int) = writeUnsigned(value.toLong(), 2)
    fun writeU32(value: Long) = writeUnsigned(value, 4)
    fun writeU64(value: BigInteger) = writeBig(value, 8, false)
    fun writeU128(value: BigInteger) = writeBig(value, 16, false)
    fun writeU256(value: BigInteger) = writeBig(value, 32, false)

    fun writeI8(value: Byte) = writeLittleEndian(value.toLong(), 1)
    fun writeI16(value: Short) = writeLittleEndian(value.toLong(), 2)
    fun writeI32(value: Int) = writeLittleEndian(value.toLong(), 4)
    fun writeI64(value: Long) = writeLittleEndian(value, 8)
    fun writeI128(value: BigInteger) = writeBig(value, 16, true)
    fun writeI256(value: BigInteger) = writeBig(value, 32, true)

    fun writeF32(value: Float) {
        require(!value.isNaN()) { "NaN is not a valid Borsh f32" }
        writeLittleEndian(value.toRawBits().toLong(), 4)
    }

    fun writeF64(value: Double) {
        require(!value.isNaN()) { "NaN is not a valid Borsh f64" }
        writeLittleEndian(value.toRawBits(), 8)
    }

    fun writeBool(value: Boolean) = writeU8(if (value) 1 else 0)

    fun writeOptionTag(present: Boolean) = writeBool(present)

    /** Write a u32 collection length. */
    fun writeLength(length: Int) {
        require(length >= 0) { "Length must not be negative" }
        writeU32(length.toLong())
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

    /** Write u32-length-prefixed UTF-8. */
    fun writeString(value: String) {
        val bytes = value.encodeToByteArray(throwOnInvalidSequence = true)
        writeLength(bytes.size)
        writeRaw(bytes)
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
