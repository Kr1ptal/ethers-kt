package io.ethers.rlp

import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.function.Consumer
import kotlin.math.max

/**
 * RLP encoder.
 *
 * Docs: [RLP](https://ethereum.org/en/developers/docs/data-structures-and-encoding/rlp/)
 * */
class RlpEncoder @JvmOverloads constructor(
    array: ByteArray,
    private val isExactSize: Boolean = false,
) {
    private var buffer: ByteBuffer = ByteBuffer.wrap(array)
    private var startedListCount = 0

    @JvmOverloads
    constructor(size: Int = 512, isExactSize: Boolean = false) : this(ByteArray(size), isExactSize)

    fun toByteArray(): ByteArray {
        if (startedListCount != 0) {
            throw IllegalStateException("Not all list encodings were finished. Need to close $startedListCount more.")
        }
        if (isExactSize || buffer.position() == buffer.capacity()) {
            if (buffer.position() != buffer.capacity()) {
                throw IllegalStateException("Incorrectly sized RLP Encoder: got size ${buffer.position()}, expected ${buffer.capacity()}")
            }

            return buffer.array()
        }

        return buffer.array().copyOfRange(0, buffer.position())
    }

    fun appendRaw(byte: Byte): RlpEncoder {
        buffer.ensureCapacity(1).put(byte)
        return this
    }

    /**
     * RLP encode list of [T] objects.
     */
    fun <T : RlpEncodable> encodeList(list: List<T>): RlpEncoder {
        return encodeList(RlpSizer.sizeOfListBody(list)) {
            for (i in list.indices) {
                list[i].rlpEncode(this)
            }
        }
    }

    /**
     * RLP encode list of values provided via [action].
     */
    @JvmOverloads
    fun encodeList(bodySize: Int = -1, action: Consumer<RlpEncoder>): RlpEncoder {
        return encodeList(bodySize) { action.accept(this) }
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
                val lengthOfSize = RlpSizer.lengthOfSizeInBytes(bodySize)
                buffer.ensureCapacity(1 + lengthOfSize + bodySize)
                buffer.put((RLP_LIST_LONG + lengthOfSize).toByte())
                encodeSize(lengthOfSize, bodySize)
            }
            // body size is unknown or short enough to not require length encoding, just reserve space for the prefix
            else -> buffer.ensureCapacity(1).put(RLP_LIST_SHORT.toByte())
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
                buffer.put(bufferStartPosition, (RLP_LIST_SHORT + size).toByte())
            }

            // if prefix does not already contain the size, we need to encode it
            bodySize <= 0 -> {
                val lengthOfSize = RlpSizer.lengthOfSizeInBytes(size)
                buffer.put(bufferStartPosition, (RLP_LIST_LONG + lengthOfSize).toByte())

                val startIndex = bufferStartPosition + 1
                buffer.ensureCapacity(lengthOfSize)
                buffer.array().copyInto(buffer.array(), startIndex + lengthOfSize, startIndex, bufferEndPosition)

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
        if (value == null) {
            buffer.ensureCapacity(1).put(RLP_NULL.toByte())
            return this
        }

        if (value < BigInteger.ZERO) {
            throw IllegalArgumentException("Negative values are not supported: $value")
        }

        if (value == BigInteger.ZERO) {
            buffer.ensureCapacity(1).put(RLP_NULL.toByte())
            return this
        }

        val bytes = value.toByteArray()
        val offset = max(0, bytes.indexOfFirst { it != 0.toByte() })
        val nonZeroLength = bytes.size - offset

        if (nonZeroLength > 32) {
            throw IllegalArgumentException("Value too big, max 32 bytes are supported: $value")
        }

        when {
            nonZeroLength == 1 && bytes[offset].toUByte().toInt() < RLP_STRING_SHORT -> {
                buffer.ensureCapacity(1).put(bytes[offset])
            }

            // always true, number can have max 32 bytes (uint256):
            // bytes.size <= MAX_SHORT_LENGTH -> {
            else -> {
                buffer.ensureCapacity(1 + nonZeroLength)
                buffer.put((RLP_STRING_SHORT + nonZeroLength).toByte())
                buffer.put(bytes, offset, nonZeroLength)
            }
        }

        return this
    }

    fun encode(value: Long): RlpEncoder {
        if (value < 0L) {
            throw IllegalArgumentException("Negative values are not supported: $value")
        }

        if (value == 0L) {
            buffer.ensureCapacity(1).put(RLP_NULL.toByte())
            return this
        }

        var bitShift = 56
        while (bitShift >= 0 && value shr bitShift == 0L) {
            bitShift -= 8
        }

        val nonZeroLength = bitShift / 8 + 1

        when {
            nonZeroLength == 1 && value < RLP_STRING_SHORT -> {
                buffer.ensureCapacity(1).put(value.toByte())
            }

            // always true, long == 8 bytes:
            // nonZeroLength <= MAX_SHORT_LENGTH -> {
            else -> {
                buffer.ensureCapacity(1 + nonZeroLength)

                buffer.put((RLP_STRING_SHORT + nonZeroLength).toByte())
                while (bitShift >= 0) {
                    buffer.put((value shr bitShift and 0xff).toByte())
                    bitShift -= 8
                }
            }
        }

        return this
    }

    fun encode(bytes: ByteArray?): RlpEncoder {
        if (bytes == null || bytes.isEmpty()) {
            buffer.ensureCapacity(1).put(RLP_NULL.toByte())
            return this
        }

        when {
            bytes.size == 1 && bytes[0].toUByte().toInt() < RLP_STRING_SHORT -> {
                buffer.ensureCapacity(1).put(bytes[0])
            }

            bytes.size <= MAX_SHORT_LENGTH -> {
                buffer.ensureCapacity(1 + bytes.size)
                buffer.put((RLP_STRING_SHORT + bytes.size).toByte())
                buffer.put(bytes)
            }

            else -> {
                val lengthOfSize = RlpSizer.lengthOfSizeInBytes(bytes.size)
                buffer.ensureCapacity(1 + lengthOfSize + bytes.size)

                buffer.put((RLP_STRING_LONG + lengthOfSize).toByte())
                encodeSize(lengthOfSize, bytes.size)
                buffer.put(bytes)
            }
        }
        return this
    }

    private fun encodeSize(lengthOfSize: Int, size: Int) {
        when (lengthOfSize) {
            1 -> buffer.put((size and 0xff).toByte())

            2 -> {
                buffer.put((size shr 8 and 0xff).toByte())
                buffer.put((size and 0xff).toByte())
            }

            3 -> {
                buffer.put((size shr 16 and 0xff).toByte())
                buffer.put((size shr 8 and 0xff).toByte())
                buffer.put((size and 0xff).toByte())
            }

            4 -> {
                buffer.put((size shr 24 and 0xff).toByte())
                buffer.put((size shr 16 and 0xff).toByte())
                buffer.put((size shr 8 and 0xff).toByte())
                buffer.put((size and 0xff).toByte())
            }
        }
    }

    private fun ByteBuffer.ensureCapacity(sizeIncrement: Int): ByteBuffer {
        val spaceLeft = remaining() - sizeIncrement
        when {
            isExactSize && spaceLeft < 0 -> throw IllegalStateException("Buffer is full - exact size of encoder is incorrect")
            spaceLeft >= 0 -> return this
        }

        val newCapacity = max(capacity() + sizeIncrement, (capacity() * BUFFER_GROWTH_FACTOR).toInt())
        val newBuffer = buffer.array().copyOf(newCapacity)

        val originalPosition = position()

        buffer = ByteBuffer.wrap(newBuffer)
        buffer.position(originalPosition)

        return buffer
    }

    companion object {
        private const val BUFFER_GROWTH_FACTOR = 1.5
    }
}
