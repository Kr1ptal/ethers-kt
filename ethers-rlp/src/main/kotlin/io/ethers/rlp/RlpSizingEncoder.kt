package io.ethers.rlp

import io.ethers.rlp.RlpSizer.lengthOfSizeInBytes
import java.math.BigInteger

/**
 * An [RlpEncoder] implementation that calculates the total size of the RLP-encoded data. This is useful for correctly
 * sizing the buffer before encoding the data, avoiding unnecessary copying of the byte array.
 * */
class RlpSizingEncoder : RlpEncoder() {
    private var size = 0
    private var startedListCount = 0

    /**
     * Returns the size of the RLP-encoded data.
     * */
    fun size(): Int {
        if (startedListCount != 0) {
            throw IllegalStateException("Not all list encodings were finished. Need to close $startedListCount more.")
        }

        return size
    }

    /**
     * This method is not supported for [RlpSizingEncoder] and will throw [UnsupportedOperationException].
     * */
    override fun toByteArray(): ByteArray {
        throw UnsupportedOperationException("Sizer cannot be converted to byte array")
    }

    override fun appendRaw(byte: Byte): RlpEncoder {
        size += 1
        return this
    }

    override fun startList(): Int {
        startedListCount++
        return size
    }

    override fun finishList(bufferStartPosition: Int) {
        val bufferEndPosition = size
        val listBodySize = bufferEndPosition - bufferStartPosition - 1

        size += when {
            listBodySize == 0 -> 1
            listBodySize <= MAX_SHORT_LENGTH -> 1
            else -> 1 + lengthOfSizeInBytes(listBodySize)
        }

        startedListCount--
    }

    override fun encode(value: BigInteger?): RlpEncoder {
        size += RlpSizer.sizeOf(value)
        return this
    }

    override fun encode(value: Long): RlpEncoder {
        size += RlpSizer.sizeOf(value)
        return this
    }

    override fun encode(bytes: ByteArray?): RlpEncoder {
        size += RlpSizer.sizeOf(bytes)
        return this
    }
}
