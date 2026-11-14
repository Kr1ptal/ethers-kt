package io.ethers.solana.codec

import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlin.jvm.JvmOverloads

/**
 * Bounds-checked cursor over Borsh data.
 *
 * Reads advance the cursor, so nested codecs each consume exactly their own value. Invalid input
 * throws [SolanaDecodeException] carrying a [SolanaDecodeError.Malformed] that records the offset into
 * the whole input and the input itself; the `decode` entry points convert it back into a
 * [Result]. Lengths are checked against the remaining bytes before anything is allocated.
 *
 * Unsigned integers widen to the next signed Kotlin type that holds them (u8 and u16 to [Int], u32 to
 * [Long], u64 and wider to [BigInteger]); signed integers up to 64 bits use the matching Kotlin type.
 * Collection lengths are u32, as Borsh defines them.
 */
class BorshReader @JvmOverloads constructor(private val data: ByteArray, offset: Int = 0) {
    init {
        if (offset !in 0..data.size) throw IndexOutOfBoundsException("Offset $offset is outside 0..${data.size}")
    }

    /** Offset of the next byte to read. */
    var position: Int = offset
        private set

    val remaining: Int get() = data.size - position

    /** Throw a [SolanaDecodeError.Malformed] for the value that starts at [offset]. */
    @JvmOverloads
    fun fail(reason: SolanaDecodeError.Reason, message: String, offset: Int = position): Nothing {
        throw SolanaDecodeException(SolanaDecodeError.Malformed(reason, message, offset, SolanaBytes.fromBytes(data)))
    }

    /** Fail unless every byte has been read. */
    fun requireDone() {
        if (remaining != 0) fail(SolanaDecodeError.Reason.TRAILING_DATA, "$remaining trailing bytes")
    }

    private fun take(count: Int): Int {
        if (count > remaining) fail(SolanaDecodeError.Reason.TRUNCATED, "Expected $count bytes, $remaining remain")
        val start = position
        position += count
        return start
    }

    private fun byte(): Int = data[take(1)].toInt() and 0xff

    private fun short(): Int {
        val start = take(2)
        return (data[start].toInt() and 0xff) or ((data[start + 1].toInt() and 0xff) shl 8)
    }

    private fun int(): Int = intAt(take(4))

    private fun long(): Long = longAt(take(8))

    private fun intAt(start: Int): Int {
        return (data[start].toInt() and 0xff) or
            ((data[start + 1].toInt() and 0xff) shl 8) or
            ((data[start + 2].toInt() and 0xff) shl 16) or
            ((data[start + 3].toInt() and 0xff) shl 24)
    }

    private fun longAt(start: Int): Long = (intAt(start).toLong() and 0xffffffffL) or (intAt(start + 4).toLong() shl 32)

    /** Read [size] bytes as a little-endian integer, two's complement when [signed]. */
    private fun littleEndianBig(size: Int, signed: Boolean): BigInteger {
        val start = take(size)
        val low = longAt(start)
        // most values fit a Long: build those directly instead of parsing a reversed copy of the bytes.
        // That holds when every byte above the low 8 repeats the sign of the low Long, which for unsigned
        // values means they are zero and the low Long is not negative.
        val fill: Byte = if (signed && low < 0) -1 else 0
        if (signed || low >= 0) {
            var i = start + 8
            val end = start + size
            while (i < end && data[i] == fill) i++
            if (i == end) return bigIntegerOf(low)
        }
        val bigEndian = ByteArray(size) { data[start + size - 1 - it] }
        return if (signed) BigInteger(bigEndian) else BigInteger(1, bigEndian)
    }

    fun readU8(): Int = byte()
    fun readU16(): Int = short()
    fun readU32(): Long = int().toLong() and 0xffffffffL
    fun readU64(): BigInteger = littleEndianBig(8, false)
    fun readU128(): BigInteger = littleEndianBig(16, false)
    fun readU256(): BigInteger = littleEndianBig(32, false)

    fun readI8(): Byte = data[take(1)]
    fun readI16(): Short = short().toShort()
    fun readI32(): Int = int()
    fun readI64(): Long = long()
    fun readI128(): BigInteger = littleEndianBig(16, true)
    fun readI256(): BigInteger = littleEndianBig(32, true)

    /** Borsh rejects NaN, which has no canonical encoding. */
    fun readF32(): Float {
        val start = position
        return Float.fromBits(int()).also {
            if (it.isNaN()) fail(SolanaDecodeError.Reason.INVALID_VALUE, "NaN is not a valid Borsh f32", start)
        }
    }

    /** Borsh rejects NaN, which has no canonical encoding. */
    fun readF64(): Double {
        val start = position
        return Double.fromBits(long()).also {
            if (it.isNaN()) fail(SolanaDecodeError.Reason.INVALID_VALUE, "NaN is not a valid Borsh f64", start)
        }
    }

    fun readBool(): Boolean = when (val value = readU8()) {
        0 -> false
        1 -> true
        else -> fail(SolanaDecodeError.Reason.INVALID_BOOL, "Invalid bool byte $value", position - 1)
    }

    /** Read an option tag: true when a value follows. */
    fun readOptionTag(): Boolean = when (val tag = readU8()) {
        0 -> false
        1 -> true
        else -> fail(SolanaDecodeError.Reason.INVALID_OPTION_TAG, "Invalid option tag $tag", position - 1)
    }

    /**
     * Read a u32 collection length. A length is rejected when it exceeds the remaining bytes, which
     * bounds every allocation by the input size; this also rejects vectors of zero-sized elements
     * longer than the rest of the payload.
     */
    fun readLength(): Int {
        val start = position
        val length = readU32()
        if (length > remaining) fail(SolanaDecodeError.Reason.LENGTH_OUT_OF_RANGE, "Length $length exceeds the $remaining remaining bytes", start)
        return length.toInt()
    }

    /** Skip [count] bytes of padding, which a zero-copy layout places between and after fields. */
    fun skipPadding(count: Int) {
        require(count >= 0) { "Count must not be negative" }
        take(count)
    }

    fun readFixedBytes(size: Int): SolanaBytes {
        require(size >= 0) { "Size must not be negative" }
        val start = take(size)
        return SolanaBytes.fromBytes(data.copyOfRange(start, start + size))
    }

    /** Read u32-length-prefixed bytes. */
    fun readBytes(): SolanaBytes = readFixedBytes(readLength())

    /** Read u32-length-prefixed UTF-8, rejecting invalid sequences. */
    fun readString(): String {
        val length = readLength()
        val start = take(length)
        // ASCII cannot hold an invalid sequence, and the lenient decoder is much cheaper than the validating one
        if (isAscii(start, start + length)) return data.decodeToString(start, start + length)
        return try {
            data.decodeToString(start, start + length, throwOnInvalidSequence = true)
        } catch (e: CharacterCodingException) {
            fail(SolanaDecodeError.Reason.INVALID_UTF8, "Invalid UTF-8 string", start)
        }
    }

    private fun isAscii(start: Int, end: Int): Boolean {
        var i = start
        // eight bytes per step: any byte with its high bit set is non-ASCII
        while (i + 8 <= end) {
            if (longAt(i) and ASCII_MASK != 0L) return false
            i += 8
        }
        while (i < end) {
            if (data[i++] < 0) return false
        }
        return true
    }

    fun readAddress(): SolanaAddress {
        val start = take(32)
        return SolanaAddress(data.copyOfRange(start, start + 32))
    }

    /** Read a u32-length-prefixed vector, each element through [element]. */
    inline fun <T> readVec(element: BorshReader.() -> T): List<T> = List(readLength()) { element() }

    /** Read exactly [size] elements without a length prefix. */
    inline fun <T> readArray(size: Int, element: BorshReader.() -> T): List<T> {
        require(size >= 0) { "Size must not be negative" }
        return List(size) { element() }
    }

    /**
     * Read an option as a nullable value. [value] must not itself read a nullable value, which would
     * make `Some(None)` indistinguishable from `None`; use [readExplicitOption] for nested options.
     */
    inline fun <T : Any> readOption(value: BorshReader.() -> T): T? = if (readOptionTag()) value() else null

    /** Read an option that keeps nesting distinct, for `Option<Option<T>>` and other nullable values. */
    inline fun <T> readExplicitOption(value: BorshReader.() -> T): SolanaOption<T> {
        return if (readOptionTag()) SolanaOption.Some(value()) else SolanaOption.None
    }

    /** Fail for an enum variant index [type] does not declare; [offset] defaults to the tag just read. */
    @JvmOverloads
    fun invalidVariant(type: String, variant: Int, offset: Int = position - 1): Nothing {
        fail(SolanaDecodeError.Reason.INVALID_VARIANT, "Invalid $type variant $variant", offset)
    }

    internal companion object {
        private const val ASCII_MASK = -0x7f7f7f7f7f7f7f80L // 0x8080808080808080

        /** Run [read] over [data], converting a thrown [SolanaDecodeException] into a failure. */
        inline fun <T> decode(data: ByteArray, offset: Int, requireDone: Boolean, read: (BorshReader) -> T): Result<T, SolanaDecodeError> {
            return try {
                val reader = BorshReader(data, offset)
                val value = read(reader)
                if (requireDone) reader.requireDone()
                success(value)
            } catch (e: SolanaDecodeException) {
                failure(e.error)
            }
        }
    }
}
