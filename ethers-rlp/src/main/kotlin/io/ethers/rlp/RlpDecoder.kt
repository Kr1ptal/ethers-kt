package io.ethers.rlp

import java.math.BigInteger
import java.util.function.Supplier

class RlpDecoder(private val array: ByteArray) {
    private var startedListCount = 0

    var position: Int = 0
        private set

    /**
     * Read a byte from the array without advancing the position.
     *
     * @return the byte at [position].
     * */
    fun peekByte(): Byte {
        return array[position]
    }

    /**
     * Read a byte from the array and advance the position.
     *
     * @return the byte at [position] before advancing.
     * */
    fun readByte(): Byte {
        return array[position++]
    }

    /**
     * Decode and return the result using [decodable].
     *
     * @return result of [decodable].
     * */
    fun <T> decode(decodable: RlpDecodable<T>): T? {
        return decodable.rlpDecode(this)
    }

    /**
     * Decode the list, calling [decodable] on each list element, and returning the list. This function handles the
     * validation of list decoding for you and should be preferred over calling [startList] and
     * [finishList] directly.
     *
     * If the list elements are NOT all the same type, use [decodeList] instead.
     *
     * @return list of results returned by [decodable], or null if the list is empty.
     * @throws IllegalStateException if RLP element is not a list or if list was not decoded correctly.
     * */
    fun <T> decodeAsList(decodable: RlpDecodable<T>): List<T>? {
        return decodeAsList { decode(decodable) }
    }

    /**
     * Decode the list, calling [consumer] on each list element, and returning the list. This function handles the
     * validation of list decoding for you and should be preferred over calling [startList] and
     * [finishList] directly.
     *
     * If the list elements are NOT all the same type, use [decodeList] instead.
     *
     * @return list of results returned by [consumer], or null if the list is empty.
     * @throws IllegalStateException if RLP element is not a list or if list was not decoded correctly.
     * */
    inline fun <T> decodeAsList(consumer: RlpDecoder.() -> T?): List<T>? {
        val listEndPosition = startList()
        if (position == listEndPosition) {
            return null
        }

        val ret = ArrayList<T>()
        while (position < listEndPosition) {
            val v = consumer(this) ?: continue
            ret.add(v)
        }

        finishList(listEndPosition)

        if (ret.isEmpty()) {
            return null
        }

        return ret
    }

    /**
     * Decode the list via [supplier], returning the result of [supplier]. This function handles the validation of
     * list decoding for you and should be preferred over calling [startList] and [finishList] directly.
     *
     * If all the list elements are of the same type, prefer using [decodeAsList] instead.
     *
     * @return result of [supplier], or null if the list is empty.
     * @throws IllegalStateException if RLP element is not a list or if list was not decoded correctly.
     * */
    fun <T> decodeList(supplier: Supplier<T?>): T? {
        return decodeList { supplier.get() }
    }

    /**
     * Decode the list via [consumer], returning the result of [consumer]. This function handles the validation of
     * list decoding for you and should be preferred over calling [startList] and [finishList] directly.
     *
     * If all the list elements are of the same type, prefer using [decodeAsList] instead.
     *
     * @return result of [consumer], or null if the list is empty.
     * @throws IllegalStateException if RLP element is not a list or if list was not decoded correctly.
     * */
    inline fun <T> decodeList(consumer: RlpDecoder.() -> T?): T? {
        val listEndPosition = startList()
        if (position == listEndPosition) {
            return null
        }

        val r = consumer(this)

        finishList(listEndPosition)

        return r
    }

    /**
     * Start decoding a list, returning the end position of the list. The following needs to be done, in order,
     * after calling this function:
     *
     * 1. check if [position] is equal to the returned value. If it is, the list is empty,
     * 2. decode the list,
     * 3. call [finishList] with the returned value to validate that the list was correctly and fully consumed.
     *
     * Prefer using [decodeList] instead of this function directly as it handles the validation for you.
     *
     * @return end position of the list.
     * @throws IllegalStateException if element is not a list.
     * */
    fun startList(): Int {
        val flag = takeFlag()
        val listByteLength = when {
            flag == RLP_LIST_SHORT -> 0

            flag <= RLP_LIST_SHORT + MAX_SHORT_LENGTH -> flag - RLP_LIST_SHORT

            flag <= 0xff -> {
                val lengthOfSize = flag - RLP_LIST_LONG
                takeSizeFromLength(lengthOfSize)
            }

            else -> throw IllegalStateException("Not a list: $flag")
        }

        startedListCount++

        // IMPORTANT: get position last in this function, so it has correct index
        return position + listByteLength
    }

    /**
     * Finish decoding a list, validating that the list was decoded correctly. [listEndPosition] must be return value
     * from calling [startList].
     *
     * Prefer using [decodeList] instead of this function directly as it handles the validation for you.
     *
     * @throws IllegalStateException if list was not decoded correctly.
     * */
    fun finishList(listEndPosition: Int) {
        if (--startedListCount < 0) {
            throw IllegalStateException("Not all list decodings were finished. Need to close $startedListCount more.")
        }

        if (position != listEndPosition) {
            throw IllegalStateException("List not decoded correctly. Expected end position to be $listEndPosition, got $position")
        }
    }

    /**
     * Decode element as a [BigInteger], or [default] if RLP element is empty.
     *
     * @return decoded [BigInteger], or [default] if RLP element is empty.
     * */
    fun decodeBigIntegerElse(default: BigInteger): BigInteger {
        return decodeBigInteger() ?: default
    }

    /**
     * Decode element as a [BigInteger], or null if element is empty.
     *
     * @return decoded [BigInteger], or null if RLP element is empty.
     * @throws IllegalStateException if element is not a [BigInteger].
     * */
    fun decodeBigInteger(): BigInteger? {
        val flag = takeFlag()
        if (flag == RLP_NULL) {
            return null
        }

        if (flag < RLP_STRING_SHORT) {
            return BigInteger.valueOf(flag.toLong())
        }

        when {
            flag <= RLP_STRING_SHORT + MAX_SHORT_LENGTH -> {
                val size = flag - RLP_STRING_SHORT
                return BigInteger(1, takeByteArray(size))
            }

            else -> throw IllegalStateException("Not a BigInteger: $flag")
        }
    }

    /**
     * Decode element as a primitive [Long].
     *
     * @return decoded [Long], or 0 if RLP element is empty.
     * @throws IllegalStateException if element is not a [Long].
     * */
    fun decodeLong(): Long {
        val flag = takeFlag()
        if (flag == RLP_NULL) {
            return 0
        }

        if (flag < RLP_NULL) {
            return flag.toLong()
        }

        when {
            flag <= RLP_STRING_SHORT + MAX_SHORT_LENGTH -> {
                val size = flag - RLP_STRING_SHORT
                return takeLong(size)
            }

            else -> throw IllegalStateException("Not a long: $flag")
        }
    }

    /**
     * Decode element as a byte array, and apply [consumer] on the non-null result.
     *
     * @return result from [consumer], or null if byte array is empty.
     * @throws IllegalStateException if element is not a byte array.
     * */
    inline fun <T> decodeByteArray(consumer: (ByteArray) -> T): T? {
        return decodeByteArray()?.let(consumer)
    }

    /**
     * Decode element as a byte array.
     *
     * @return a byte array, or null if empty.
     * @throws IllegalStateException if element is not a byte array.
     * */
    fun decodeByteArray(): ByteArray? {
        val flag = takeFlag()
        if (flag == RLP_NULL) {
            return null
        }

        when {
            flag < RLP_STRING_SHORT -> {
                val result = ByteArray(1)
                result[0] = flag.toByte()
                return result
            }

            flag <= RLP_STRING_SHORT + MAX_SHORT_LENGTH -> {
                val size = flag - RLP_STRING_SHORT
                return takeByteArray(size)
            }

            flag <= RLP_LIST_SHORT -> {
                val lengthOfSize = flag - RLP_STRING_LONG
                val size = takeSizeFromLength(lengthOfSize)
                return takeByteArray(size)
            }

            else -> throw IllegalStateException("Not a byte array: $flag")
        }
    }

    private fun takeFlag(): Int {
        return array[position++].toUByte().toInt()
    }

    private fun takeLong(size: Int): Long {
        var result = 0L
        for (i in 0..<size) {
            result = result shl 8 or (array[position++].toLong() and 0xff)
        }
        return result
    }

    private fun takeByteArray(size: Int): ByteArray {
        val result = ByteArray(size)
        System.arraycopy(array, position, result, 0, size)
        position += size
        return result
    }

    private fun takeSizeFromLength(size: Int): Int {
        return when (size) {
            1 -> array[position++].toInt() and 0xff
            2 -> (array[position++].toInt() and 0xff) shl 8 or (array[position++].toInt() and 0xff)
            3 -> (array[position++].toInt() and 0xff) shl 16 or ((array[position++].toInt() and 0xff) shl 8) or (array[position++].toInt() and 0xff)
            4 -> (array[position++].toInt() and 0xff) shl 24 or ((array[position++].toInt() and 0xff) shl 16) or ((array[position++].toInt() and 0xff) shl 8) or (array[position++].toInt() and 0xff)
            else -> throw IllegalArgumentException("Size not supported: $size")
        }
    }
}
