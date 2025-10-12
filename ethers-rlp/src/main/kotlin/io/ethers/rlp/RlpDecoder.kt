package io.ethers.rlp

import java.math.BigInteger
import java.util.function.Supplier
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
class RlpDecoder(private val array: ByteArray) {
    private var startedListCount = 0

    var position: Int = 0
        private set

    val isDone: Boolean
        get() = position >= array.size

    private val remaining: Int
        get() = array.size - position

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
        if (!isNextElementList()) {
            return null
        }

        val listEndPosition = startList()
        if (position == listEndPosition) {
            return emptyList()
        }

        val ret = ArrayList<T>()
        while (position < listEndPosition) {
            val v = consumer(this) ?: return null
            ret.add(v)
        }

        finishList(listEndPosition)
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
        contract {
            callsInPlace(consumer, InvocationKind.AT_MOST_ONCE)
        }

        if (!isNextElementList()) {
            return null
        }

        val listEndPosition = startList()
        if (position == listEndPosition) {
            return null
        }

        val r = consumer(this)

        finishList(listEndPosition)

        return r
    }

    /**
     * Returns true if the next element is a list, false otherwise.
     * */
    fun isNextElementList(): Boolean {
        if (isDone) return false

        val flag = peekFlag()
        return when {
            flag < RLP_LIST_SHORT -> false
            flag == RLP_LIST_SHORT -> true
            flag <= RLP_LIST_SHORT + MAX_SHORT_LENGTH -> true
            flag <= 0xff -> {
                val lengthOfSize = flag - RLP_LIST_LONG
                return lengthOfSize <= MAX_LENGTH_OF_SIZE
            }
            else -> false
        }
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
                takeSizeWithLength(lengthOfSize)
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
     * Decode element as a [BigInteger], or return result of [default] if element cannot be decoded.
     *
     * @return decoded [BigInteger], or return result of [default] if element cannot be decoded as [BigInteger].
     * */
    inline fun decodeBigIntegerOrElse(default: () -> BigInteger): BigInteger {
        contract {
            callsInPlace(default, InvocationKind.AT_MOST_ONCE)
        }
        return if (isNextElementBigInteger()) decodeBigInteger() else default()
    }

    /**
     * Check if the next element can be decoded as a [BigInteger] without consuming it.
     *
     * @return true if the next element is a valid [BigInteger], false otherwise.
     * */
    fun isNextElementBigInteger(): Boolean {
        if (isDone) return false

        val flag = peekFlag()
        return when {
            flag == RLP_NULL -> true
            flag < RLP_STRING_SHORT -> true
            flag <= RLP_STRING_SHORT + MAX_SHORT_LENGTH -> {
                val size = flag - RLP_STRING_SHORT
                size <= 32 && remaining >= size
            }
            else -> false
        }
    }

    /**
     * Decode element as a [BigInteger], or null if element is empty.
     *
     * @return decoded [BigInteger], or null if RLP element is empty.
     * @throws IllegalStateException if element is not a [BigInteger].
     * */
    fun decodeBigInteger(): BigInteger {
        val flag = takeFlag()
        if (flag == RLP_NULL) {
            return BigInteger.ZERO
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
     * Decode element as a primitive [Long], or return result of [default] if element cannot be decoded.
     *
     * @return decoded [Long], or result of [default] if element cannot be decoded as [Long].
     * */
    inline fun decodeLongOrElse(default: () -> Long): Long {
        contract {
            callsInPlace(default, InvocationKind.AT_MOST_ONCE)
        }
        return if (isNextElementLong()) decodeLong() else default()
    }

    /**
     * Check if the next element can be decoded as a [Long] without consuming it.
     *
     * @return true if the next element is a valid [Long], false otherwise.
     * */
    fun isNextElementLong(): Boolean {
        if (isDone) return false

        val flag = peekFlag()
        return when {
            flag == RLP_NULL -> true
            flag < RLP_NULL -> true
            flag <= RLP_STRING_SHORT + MAX_SHORT_LENGTH -> {
                val size = flag - RLP_STRING_SHORT
                size <= 8 && remaining >= size
            }
            else -> false
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
        contract {
            callsInPlace(consumer, InvocationKind.AT_MOST_ONCE)
        }
        return decodeByteArrayOrElse { return null }.let(consumer)
    }

    /**
     * Decode element as a [ByteArray], or return result of [default] if element cannot be decoded.
     *
     * @return decoded [ByteArray], or result of [default] if element cannot be decoded as [ByteArray].
     * */
    inline fun decodeByteArrayOrElse(default: () -> ByteArray): ByteArray {
        contract {
            callsInPlace(default, InvocationKind.AT_MOST_ONCE)
        }
        return if (isNextElementByteArray()) decodeByteArray() else default()
    }

    /**
     * Check if the next element can be decoded as a byte array without consuming it.
     *
     * @return true if the next element is a valid byte array, false otherwise.
     * */
    fun isNextElementByteArray(): Boolean {
        if (isDone) return false

        val flag = peekFlag()
        if (flag == RLP_NULL) return true

        when {
            flag < RLP_STRING_SHORT -> remaining >= 1
            flag <= RLP_STRING_SHORT + MAX_SHORT_LENGTH -> {
                val size = flag - RLP_STRING_SHORT
                remaining >= size
            }
            flag <= RLP_LIST_SHORT -> {
                val lengthOfSize = flag - RLP_STRING_LONG
                if (remaining < lengthOfSize) return false

                val size = peekSizeWithLength(lengthOfSize)
                remaining >= (lengthOfSize + size)
            }
        }

        return false
    }

    /**
     * Decode element as a byte array.
     *
     * @return a byte array, or null if empty.
     * @throws IllegalStateException if element is not a byte array.
     * */
    fun decodeByteArray(): ByteArray {
        val flag = takeFlag()
        if (flag == RLP_NULL) {
            return EMPTY_BYTE_ARRAY
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
                val size = takeSizeWithLength(lengthOfSize)
                return takeByteArray(size)
            }

            else -> throw IllegalStateException("Not a byte array: $flag")
        }
    }

    private fun peekFlag(): Int {
        return array[position].toUByte().toInt()
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

    private fun takeSizeWithLength(lengthOfSize: Int): Int {
        val size = peekSizeWithLength(lengthOfSize)
        position += lengthOfSize
        return size
    }

    private fun peekSizeWithLength(lengthOfSize: Int): Int {
        return when (lengthOfSize) {
            1 -> array[position].toInt() and 0xff
            2 -> (array[position].toInt() and 0xff) shl 8 or (array[position + 1].toInt() and 0xff)
            3 -> (array[position].toInt() and 0xff) shl 16 or ((array[position + 1].toInt() and 0xff) shl 8) or (array[position + 2].toInt() and 0xff)
            4 -> (array[position].toInt() and 0xff) shl 24 or ((array[position + 1].toInt() and 0xff) shl 16) or ((array[position + 2].toInt() and 0xff) shl 8) or (array[position + 3].toInt() and 0xff)
            else -> throw IllegalArgumentException("Size is encoded with $lengthOfSize bytes. Max supported length is $MAX_LENGTH_OF_SIZE")
        }
    }

    companion object {
        private const val MAX_LENGTH_OF_SIZE = 4
        private val EMPTY_BYTE_ARRAY = ByteArray(0)
    }
}
