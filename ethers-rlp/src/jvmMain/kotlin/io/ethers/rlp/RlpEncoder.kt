package io.ethers.rlp

import com.ditchoom.buffer.PlatformBuffer
import com.ditchoom.buffer.wrap
import java.math.BigInteger
import kotlin.math.max

/**
 * RLP encoder.
 *
 * Docs: [RLP](https://ethereum.org/en/developers/docs/data-structures-and-encoding/rlp/)
 * */
class RlpEncoder @JvmOverloads constructor(
    private var array: ByteArray,
    private val isExactSize: Boolean = false,
) {
    private var buffer: PlatformBuffer = PlatformBuffer.wrap(array)
    private var startedListCount = 0

    @JvmOverloads
    constructor(size: Int = 512, isExactSize: Boolean = false) : this(ByteArray(size), isExactSize)

    fun toByteArray(): ByteArray {
        if (startedListCount != 0) {
            throw IllegalStateException("Not all list encodings were finished. Need to close $startedListCount more.")
        }
        val writtenBytes = buffer.position()
        if (isExactSize || writtenBytes == array.size) {
            if (writtenBytes != array.size) {
                throw IllegalStateException("Incorrectly sized RLP Encoder: got size $writtenBytes, expected ${array.size}")
            }

            return array
        }

        return array.copyOf(writtenBytes)
    }

    /**
     * Appends a raw byte to the buffer. Useful when encoding typed transactions, so we don't have to copy the result
     * to a new byte array just to prepend the type byte.
     * */
    fun appendRaw(byte: Byte): RlpEncoder {
        ensureCapacity(1)
        buffer.writeByte(byte)
        return this
    }

    /**
     * RLP encode list of [T] objects.
     */
    fun <T : RlpEncodable> encodeList(list: List<T>): RlpEncoder {
        return encodeList(sizeOfListBody(list)) {
            for (i in list.indices) {
                list[i].rlpEncode(this)
            }
        }
    }

    /**
     * RLP encode list of values provided via [action].
     */
    @JvmOverloads
    fun encodeList(bodySize: Int = -1, action: Runnable): RlpEncoder {
        return encodeList(bodySize) { action.run() }
    }

    /**
     * RLP encode list of values provided via [action].
     *
     * This function should be preferred over calling [startList] and [finishList] directly.
     */
    inline fun encodeList(bodySize: Int = -1, action: RlpEncoder.() -> Unit): RlpEncoder {
        val bufferStartPosition = startList(bodySize)
        action(this)
        finishList(bufferStartPosition, bodySize)

        return this
    }

    @JvmOverloads
    fun startList(bodySize: Int = -1): Int {
        val bufferStartPosition = buffer.position()

        when {
            // if we know the size of the list body in advance, we encode it here and avoid copying the bytes later
            bodySize > MAX_SHORT_LENGTH -> {
                val lengthOfSize = lengthOfSizeInBytes(bodySize)
                ensureCapacity(1 + lengthOfSize + bodySize)
                buffer.writeByte((RLP_LIST_LONG + lengthOfSize).toByte())
                encodeSize(lengthOfSize, bodySize)
            }
            // body size is unknown or short enough to not require length encoding, just reserve space for the prefix
            else -> {
                ensureCapacity(1)
                buffer.writeByte(RLP_LIST_SHORT.toByte())
            }
        }

        startedListCount++
        return bufferStartPosition
    }

    @JvmOverloads
    fun finishList(bufferStartPosition: Int, bodySize: Int = -1) {
        val bufferEndPosition = buffer.position()
        val size = bufferEndPosition - bufferStartPosition - 1

        when {
            size == 0 -> {}
            size <= MAX_SHORT_LENGTH -> {
                buffer.set(bufferStartPosition, (RLP_LIST_SHORT + size).toByte())
            }

            // if prefix does not already contain the size, we need to encode it
            bodySize <= 0 -> {
                val lengthOfSize = lengthOfSizeInBytes(size)
                array[bufferStartPosition] = (RLP_LIST_LONG + lengthOfSize).toByte()

                val startIndex = bufferStartPosition + 1
                ensureCapacity(lengthOfSize)

                // Shift bytes forward in the array to make room for size encoding
                array.copyInto(array, startIndex + lengthOfSize, startIndex, bufferEndPosition)

                buffer.position(startIndex)
                encodeSize(lengthOfSize, size)

                buffer.position(bufferEndPosition + lengthOfSize)
            }
        }

        startedListCount--
    }

    /**
     * RLP encode [T].
     */
    fun <T : RlpEncodable> encode(value: T?): RlpEncoder {
        if (value == null) {
            appendRaw(RLP_NULL.toByte())
        } else {
            value.rlpEncode(this)
        }

        return this
    }

    fun encode(value: BigInteger?): RlpEncoder {
        if (value == null || value == BigInteger.ZERO) {
            ensureCapacity(1)
            buffer.writeByte(RLP_NULL.toByte())
            return this
        }

        if (value < BigInteger.ZERO) {
            throw IllegalArgumentException("Negative values are not supported: $value")
        }

        val bytes = value.toByteArray()
        val offset = max(0, bytes.indexOfFirst { it != 0.toByte() })
        val nonZeroLength = bytes.size - offset

        if (nonZeroLength > 32) {
            throw IllegalArgumentException("Value too big, max 32 bytes are supported: $value")
        }

        when {
            nonZeroLength == 1 && bytes[offset].toUByte().toInt() < RLP_STRING_SHORT -> {
                ensureCapacity(1)
                buffer.writeByte(bytes[offset])
            }

            // always true, number can have max 32 bytes (uint256):
            // bytes.size <= MAX_SHORT_LENGTH -> {
            else -> {
                ensureCapacity(1 + nonZeroLength)
                buffer.writeByte((RLP_STRING_SHORT + nonZeroLength).toByte())
                buffer.writeBytes(bytes, offset, nonZeroLength)
            }
        }

        return this
    }

    fun encode(value: Long): RlpEncoder {
        if (value < 0L) {
            throw IllegalArgumentException("Negative values are not supported: $value")
        }

        if (value == 0L) {
            ensureCapacity(1)
            buffer.writeByte(RLP_NULL.toByte())
            return this
        }

        var bitShift = 56
        while (bitShift >= 0 && value shr bitShift == 0L) {
            bitShift -= 8
        }

        val nonZeroLength = bitShift / 8 + 1

        when {
            nonZeroLength == 1 && value < RLP_STRING_SHORT -> {
                ensureCapacity(1)
                buffer.writeByte(value.toByte())
            }

            // always true, long == 8 bytes:
            // nonZeroLength <= MAX_SHORT_LENGTH -> {
            else -> {
                ensureCapacity(1 + nonZeroLength)

                buffer.writeByte((RLP_STRING_SHORT + nonZeroLength).toByte())
                while (bitShift >= 0) {
                    buffer.writeByte((value shr bitShift and 0xff).toByte())
                    bitShift -= 8
                }
            }
        }

        return this
    }

    fun encode(value: ByteArray?): RlpEncoder {
        if (value == null || value.isEmpty()) {
            ensureCapacity(1)
            buffer.writeByte(RLP_NULL.toByte())
            return this
        }

        when {
            value.size == 1 && value[0].toUByte().toInt() < RLP_STRING_SHORT -> {
                ensureCapacity(1)
                buffer.writeByte(value[0])
            }

            value.size <= MAX_SHORT_LENGTH -> {
                ensureCapacity(1 + value.size)
                buffer.writeByte((RLP_STRING_SHORT + value.size).toByte())
                buffer.writeBytes(value)
            }

            else -> {
                val lengthOfSize = lengthOfSizeInBytes(value.size)
                ensureCapacity(1 + lengthOfSize + value.size)

                buffer.writeByte((RLP_STRING_LONG + lengthOfSize).toByte())
                encodeSize(lengthOfSize, value.size)
                buffer.writeBytes(value)
            }
        }
        return this
    }

    private fun encodeSize(lengthOfSize: Int, size: Int) {
        when (lengthOfSize) {
            1 -> buffer.writeByte((size and 0xff).toByte())

            2 -> {
                buffer.writeByte((size shr 8 and 0xff).toByte())
                buffer.writeByte((size and 0xff).toByte())
            }

            3 -> {
                buffer.writeByte((size shr 16 and 0xff).toByte())
                buffer.writeByte((size shr 8 and 0xff).toByte())
                buffer.writeByte((size and 0xff).toByte())
            }

            4 -> {
                buffer.writeByte((size shr 24 and 0xff).toByte())
                buffer.writeByte((size shr 16 and 0xff).toByte())
                buffer.writeByte((size shr 8 and 0xff).toByte())
                buffer.writeByte((size and 0xff).toByte())
            }
        }
    }

    private fun ensureCapacity(sizeIncrement: Int) {
        val remaining = array.size - buffer.position()
        if (isExactSize || remaining >= sizeIncrement) {
            return
        }

        val originalPosition = buffer.position()
        val newCapacity = max(array.size + sizeIncrement, (array.size * BUFFER_GROWTH_FACTOR).toInt())

        // Resize the array in place using copyOf
        array = array.copyOf(newCapacity)

        buffer = PlatformBuffer.wrap(array)
        buffer.position(originalPosition)
    }

    companion object {
        private const val BUFFER_GROWTH_FACTOR = 1.5
        private val RLP_STRING_SHORT_BIGINT = BigInteger.valueOf(0x80)

        /**
         * Return the size of the RLP encoding of [value], without actually encoding it.
         * */
        @JvmStatic
        fun sizeOf(value: RlpEncodable?): Int {
            return value?.rlpSize() ?: 1
        }

        /**
         * Return the size of the RLP encoding of [value], without actually encoding it.
         * */
        @JvmStatic
        fun sizeOf(value: BigInteger?): Int {
            if (value == null) {
                return 1
            }

            if (value < BigInteger.ZERO) {
                throw IllegalArgumentException("Negative values are not supported: $value")
            }

            if (value == BigInteger.ZERO) {
                return 1
            }

            val nonZeroLength = (value.bitLength() + 7) / 8
            if (nonZeroLength > 32) {
                throw IllegalArgumentException("Value too big, max 32 bytes are supported: $value")
            }

            return when {
                nonZeroLength == 1 && value < RLP_STRING_SHORT_BIGINT -> 1

                // always true, number can have max 32 bytes (uint256):
                // bytes.size <= MAX_SHORT_LENGTH -> {
                else -> 1 + nonZeroLength
            }
        }

        /**
         * Return the size of the RLP encoding of [value], without actually encoding it.
         * */
        @JvmStatic
        fun sizeOf(value: Long): Int {
            if (value < 0L) {
                throw IllegalArgumentException("Negative values are not supported: $value")
            }

            if (value == 0L) {
                return 1
            }

            var bitShift = 56
            while (bitShift >= 0 && value shr bitShift == 0L) {
                bitShift -= 8
            }

            val nonZeroLength = bitShift / 8 + 1

            return when {
                nonZeroLength == 1 && value < RLP_STRING_SHORT -> 1

                // always true, long == 8 bytes:
                // nonZeroLength <= MAX_SHORT_LENGTH -> {
                else -> 1 + nonZeroLength
            }
        }

        /**
         * Return the size of the RLP encoding of [value], without actually encoding it.
         * */
        @JvmStatic
        fun sizeOf(value: ByteArray?): Int {
            if (value == null || value.isEmpty()) {
                return 1
            }

            when {
                value.size == 1 && value[0].toUByte().toInt() < RLP_STRING_SHORT -> {
                    return 1
                }

                value.size <= MAX_SHORT_LENGTH -> {
                    return 1 + value.size
                }

                else -> {
                    val lengthOfSize = lengthOfSizeInBytes(value.size)
                    return 1 + lengthOfSize + value.size
                }
            }
        }

        /**
         * Return the size of the RLP encoding of [list], without actually encoding it.
         *
         * **This returns list header size + list body size.**
         * */
        @JvmStatic
        fun <T : RlpEncodable> sizeOfList(list: List<T>): Int {
            return sizeOfList(sizeOfListBody(list))
        }

        /**
         * Return the size of the RLP encoding of list with given [listBodySize].
         *
         * **This returns list header size + list body size.**
         * */
        @JvmStatic
        fun sizeOfList(listBodySize: Int): Int {
            return listBodySize + when {
                listBodySize == 0 -> 1
                listBodySize <= MAX_SHORT_LENGTH -> 1
                else -> 1 + lengthOfSizeInBytes(listBodySize)
            }
        }

        /**
         * Return the size of the RLP encoding of [list] body, without actually encoding it.
         *
         * **This returns only list body size.**
         * */
        @JvmStatic
        fun sizeOfListBody(list: List<RlpEncodable>): Int {
            var size = 0
            for (i in list.indices) {
                size += sizeOf(list[i])
            }
            return size
        }

        /**
         * Return the RLP length of encoding given [size].
         * */
        @JvmStatic
        fun lengthOfSizeInBytes(size: Int): Int {
            return when {
                size <= 0xff -> 1
                size <= 0xffff -> 2
                size <= 0xffffff -> 3
                else -> 4
            }
        }
    }
}
