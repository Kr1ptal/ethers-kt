package io.ethers.rlp

import java.math.BigInteger

/**
 * A utility class to calculate the size of RLP-encoded values, without actually encoding them.
 * */
object RlpSizer {
    private val RLP_STRING_SHORT_BIGINT = BigInteger.valueOf(0x80)

    fun sizeOf(value: RlpEncodable?): Int {
        return value?.rlpSize() ?: 1
    }

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

    fun sizeOf(bytes: ByteArray?): Int {
        if (bytes == null || bytes.isEmpty()) {
            return 1
        }

        when {
            bytes.size == 1 && bytes[0].toUByte().toInt() < RLP_STRING_SHORT -> {
                return 1
            }

            bytes.size <= MAX_SHORT_LENGTH -> {
                return 1 + bytes.size
            }

            else -> {
                val lengthOfSize = lengthOfSizeInBytes(bytes.size)
                return 1 + lengthOfSize + bytes.size
            }
        }
    }

    fun <T : RlpEncodable> sizeOfList(list: List<T>): Int {
        return sizeOfListWithBody(sizeOfListBody(list))
    }

    fun sizeOfListBody(list: List<RlpEncodable>): Int {
        var size = 0
        for (item in list) {
            size += sizeOf(item)
        }
        return size
    }

    fun sizeOfListWithBody(listBodySize: Int): Int {
        return listBodySize + when {
            listBodySize == 0 -> 1
            listBodySize <= MAX_SHORT_LENGTH -> 1
            else -> 1 + lengthOfSizeInBytes(listBodySize)
        }
    }

    fun lengthOfSizeInBytes(size: Int): Int {
        return when {
            size <= 0xff -> 1
            size <= 0xffff -> 2
            size <= 0xffffff -> 3
            else -> 4
        }
    }
}
