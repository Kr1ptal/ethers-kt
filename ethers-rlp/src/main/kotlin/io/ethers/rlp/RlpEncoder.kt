package io.ethers.rlp

import java.math.BigInteger

/**
 * RLP encoder.
 *
 * Docs: [RLP](https://ethereum.org/en/developers/docs/data-structures-and-encoding/rlp/)
 * */
abstract class RlpEncoder {
    /**
     * Return RLP-encoded [ByteArray] of this encoder.
     * */
    abstract fun toByteArray(): ByteArray

    /**
     * Appends a raw byte to the buffer. Useful when encoding typed transactions, so we don't have to copy the result
     * to a new byte array just to prepend the type byte.
     * */
    abstract fun appendRaw(byte: Byte): RlpEncoder

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
     * Start list encoding, and return the list start position. Each call to this function needs to be followed
     * by [finishList].
     *
     * Prefer using [encodeList] instead of this function directly as it handles all of the above for you.
     *
     * @return start position of the list.
     */
    abstract fun startList(): Int

    /**
     * End list encoding. Each call to this function needs to be preceded by [startList].
     *
     * Prefer using [encodeList] instead of this function directly as it handles all of the above for you.
     */
    abstract fun finishList(bufferStartPosition: Int)

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

    /**
     * RLP encode [BigInteger].
     *
     * NOTE:
     * Integers are encoded as big-endian byte arrays, without leading zeros. The value `0` is encoded as an
     * empty byte array. Negative values are prohibited.
     */
    abstract fun encode(value: BigInteger?): RlpEncoder

    /**
     * RLP encode [Long].
     */
    abstract fun encode(value: Long): RlpEncoder

    /**
     * RLP encode [ByteArray].
     */
    abstract fun encode(bytes: ByteArray?): RlpEncoder

    companion object {
        @JvmStatic
        fun sized(size: Int): RlpEncoder = RlpBufferEncoder(ByteArray(size), isCorrectlySized = false)

        @JvmStatic
        fun unsized(): RlpEncoder = sized(512)
    }
}
