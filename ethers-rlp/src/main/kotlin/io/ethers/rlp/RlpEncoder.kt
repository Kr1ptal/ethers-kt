package io.ethers.rlp

import java.math.BigInteger
import java.nio.ByteBuffer
import kotlin.math.max

private const val BUFFER_GROWTH_FACTOR = 1.5

/**
 * RLP encoder.
 *
 * Docs: [RLP](https://ethereum.org/en/developers/docs/data-structures-and-encoding/rlp/)
 * */
class RlpEncoder(array: ByteArray) {
    @JvmOverloads
    constructor(initialCapacity: Int = 512) : this(ByteArray(initialCapacity))

    private var buffer: ByteBuffer = ByteBuffer.wrap(array)
    private var startedListCount = 0

    fun toByteArray(): ByteArray {
        if (startedListCount != 0) {
            throw IllegalStateException("Not all list encodings were finished. Need to close $startedListCount more.")
        }
        return buffer.array().copyOfRange(0, buffer.position())
    }

    /**
     * Appends a raw byte to the buffer. Useful when encoding typed transactions, so we don't have to copy the result
     * to a new byte array just to prepend the type byte.
     * */
    fun appendRaw(byte: Byte): RlpEncoder {
        buffer.ensureCapacity(1).put(byte)
        return this
    }

    /**
     * RLP encode list of [T] objects.
     */
    fun <T : RlpEncodable> encodeList(list: List<T>): RlpEncoder {
        return encodeList {
            for (i in list.indices) {
                list[i].rlpEncode(this)
            }
        }
    }

    /**
     * RLP encode list of values provided via [action].
     */
    fun encodeList(action: Runnable): RlpEncoder {
        return encodeList { action.run() }
    }

    /**
     * RLP encode list of values provided via [action].
     *
     * This function should be preferred over calling [startList] and [finishList] directly.
     */
    inline fun <T> encodeList(list: List<T>, action: RlpEncoder.(T) -> Unit): RlpEncoder {
        val bufferStartPosition = startList()
        for (i in list.indices) {
            action(this, list[i])
        }
        finishList(bufferStartPosition)

        return this
    }

    /**
     * RLP encode list of values provided via [action].
     *
     * This function should be preferred over calling [startList] and [finishList] directly.
     */
    inline fun encodeList(action: RlpEncoder.() -> Unit): RlpEncoder {
        val bufferStartPosition = startList()
        action(this)
        finishList(bufferStartPosition)

        return this
    }

    /**
     * Start list encoding, incrementing the [startedListCount], and returning the list start position. Each call to
     * this function needs to be followed by [finishList].
     *
     * Prefer using [encodeList] instead of this function directly as it handles all of the above for you.
     *
     * @return start position of the list.
     */
    fun startList(): Int {
        val bufferStartPosition = buffer.position()

        // reserve space for the list prefix, so we can avoid copying the bytes later
        buffer.ensureCapacity(1).put(RLP_LIST_SHORT.toByte())
        startedListCount++

        return bufferStartPosition
    }

    /**
     * End list encoding and decrementing the [startedListCount]. Each call to this function needs to be
     * preceded by [startList].
     *
     * Prefer using [encodeList] instead of this function directly as it handles all of the above for you.
     */
    fun finishList(bufferStartPosition: Int) {
        val bufferEndPosition = buffer.position()
        val size = bufferEndPosition - bufferStartPosition - 1
        when {
            size == 0 -> {}
            size <= MAX_SHORT_LENGTH -> {
                buffer.put(bufferStartPosition, (RLP_LIST_SHORT + size).toByte())
            }

            else -> {
                val lengthOfSize = lengthOfSizeInBytes(size)
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
            buffer.ensureCapacity(1).put(RLP_NULL.toByte())
        } else {
            value.rlpEncode(this)
        }

        return this
    }

    /**
     * RLP encode [BigInteger].
     *
     * NOTE:
     * Integers are encoded as big-endian byte arrays, without leading zeros. The value `0` is encoded as an
     * empty byte array. Negative values are prohibited.
     */
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

    /**
     * RLP encode [Long].
     */
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

    /**
     * RLP encode [ByteArray].
     */
    fun encode(bytes: ByteArray?): RlpEncoder {
        if (bytes == null || bytes.isEmpty()) {
            buffer.ensureCapacity(1).put(RLP_NULL.toByte())
            return this
        }

        when {
            bytes.size == 1 && bytes[0] == 0.toByte() -> {
                buffer.ensureCapacity(1).put(RLP_NULL.toByte())
            }

            bytes.size == 1 && bytes[0].toUByte().toInt() < RLP_STRING_SHORT -> {
                buffer.ensureCapacity(1).put(bytes[0])
            }

            bytes.size <= MAX_SHORT_LENGTH -> {
                buffer.ensureCapacity(1 + bytes.size)
                buffer.put((RLP_STRING_SHORT + bytes.size).toByte())
                buffer.put(bytes)
            }

            else -> {
                val lengthOfSize = lengthOfSizeInBytes(bytes.size)
                buffer.ensureCapacity(1 + lengthOfSize + bytes.size)

                buffer.put((RLP_STRING_LONG + lengthOfSize).toByte())
                encodeSize(lengthOfSize, bytes.size)
                buffer.put(bytes)
            }
        }
        return this
    }

    private fun lengthOfSizeInBytes(size: Int): Int {
        return when {
            size <= 0xff -> 1
            size <= 0xffff -> 2
            size <= 0xffffff -> 3
            else -> 4
        }
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
        if (remaining() >= sizeIncrement) {
            return this
        }

        val newCapacity = max(capacity() + sizeIncrement, (capacity() * BUFFER_GROWTH_FACTOR).toInt())
        val newBuffer = buffer.array().copyOf(newCapacity)

        val originalPosition = position()

        buffer = ByteBuffer.wrap(newBuffer)
        buffer.position(originalPosition)

        return buffer
    }
}
