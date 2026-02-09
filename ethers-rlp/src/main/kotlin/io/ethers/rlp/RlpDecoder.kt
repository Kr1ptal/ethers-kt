package io.ethers.rlp

import java.math.BigInteger
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.absoluteValue

@OptIn(ExperimentalContracts::class)
class RlpDecoder(private val array: ByteArray) {
    var error: String? = null
        private set

    private var startedListCount = 0

    var position: Int = 0
        private set

    val isDone: Boolean
        get() = position >= array.size

    private val remaining: Int
        get() = array.size - position

    /**
     * Sets the [error] - if not already set - and returns `null`.
     * */
    fun <T> error(error: String): T? {
        this.error = this.error ?: error
        return null
    }

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
     * Decode the list, calling [decodable] on each list element, and returning the list. This function handles the
     * validation of list decoding for you and should be preferred over calling [startList] and [finishList] directly.
     *
     * If the list elements are NOT all the same type, use [decodeListOrNull] instead.
     *
     * @return list of results returned by [decodable] or `null` if the list is empty.
     * */
    fun <T> decodeAsListOrNull(decodable: RlpDecodable<T>): List<T>? {
        return decodeAsListOrNull { decodeOrNull(decodable) }
    }

    /**
     * Decode the list, calling [consumer] on each list element, and returning the list. This function handles the
     * validation of list decoding for you and should be preferred over calling [startList] and
     * [finishList] directly.
     *
     * If the list elements are NOT all the same type, use [decodeListOrNull] instead.
     *
     * @return list of results returned by [consumer], or null if the list is empty.
     * @throws RlpDecoderException if RLP element is not a list or if list was not decoded correctly.
     * */
    inline fun <T> decodeAsListOrNull(consumer: RlpDecoder.() -> T?): List<T>? {
        val listEndPosition = startListOrMinusOne()
        if (listEndPosition == -1) return null

        if (position == listEndPosition) {
            return emptyList()
        }

        val ret = ArrayList<T>()
        while (position < listEndPosition) {
            val v = consumer(this) ?: return null
            ret.add(v)
        }

        if (finishListOrMinusOne(listEndPosition) == -1) {
            return null
        }

        return ret
    }

    /**
     * Decode the list via [consumer], returning the result of [consumer]. This function handles the validation of
     * list decoding for you and should be preferred over calling [startList] and [finishList] directly.
     *
     * If all the list elements are of the same type, prefer using [decodeAsListOrNull] instead.
     *
     * @return result of [consumer], or null if the list is empty or cannot be decoded.
     * */
    inline fun <T> decodeListOrNull(consumer: RlpDecoder.() -> T): T? {
        contract {
            callsInPlace(consumer, InvocationKind.AT_MOST_ONCE)
        }

        val listEndPosition = startListOrMinusOne()
        if (position == listEndPosition) {
            return error("List cannot be empty")
        }

        val r = consumer(this)
        if (finishListOrMinusOne(listEndPosition) == -1) {
            return null
        }

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
            flag <= 0xff -> true
            else -> false
        }
    }

    /**
     * Start decoding a list, returning the end position of the list. The following needs to be done, in order,
     * after calling this function:
     *
     * 1. check if [position] is equal to the returned value. If it is, the list is empty,
     * 2. decode the list body,
     * 3. call [finishList] with the returned value to validate that the list was fully decoded.
     *
     * Prefer using [decodeListOrNull] instead of this function directly as it handles the validation for you.
     *
     * @return end position of the list.
     * @throws RlpDecoderException if element is not a list.
     * */
    fun startList(): Int {
        val ret = startListOrMinusOne()
        this.error?.throwDecoderException()
        return ret
    }

    /**
     * Start decoding a list, returning the end position of the list. The following needs to be done, in order,
     * after calling this function:
     *
     * 1. check if [position] is equal to the returned value. If it is, the list is empty,
     * 2. decode the list body,
     * 3. call [finishListOrMinusOne] with the returned value to validate that the list was fully decoded.
     *
     * Prefer using [decodeListOrNull] instead of this function directly as it handles the validation for you.
     *
     * @return end position of the list.
     * */
    fun startListOrMinusOne(): Int {
        val listByteLength = getNextElement(true, -1) { it }
        if (this.error != null) {
            return -1
        }

        startedListCount++

        // IMPORTANT: get position last in this function, so it has a correct index
        return position + listByteLength
    }

    /**
     * Finish decoding a list, validating that the list was decoded correctly. [listEndPosition] must be return value
     * from calling [startList].
     *
     * Prefer using [decodeListOrNull] instead of this function directly as it handles the validation for you.
     *
     * @throws RlpDecoderException if list was not decoded correctly.
     * */
    fun finishList(listEndPosition: Int) {
        finishListOrMinusOne(listEndPosition)
        this.error?.throwDecoderException()
    }

    /**
     * Finish decoding a list, validating that the list was decoded correctly. [listEndPosition] must be return value
     * from calling [startList].
     *
     * Prefer using [decodeListOrNull] instead of this function directly as it handles the validation for you.
     *
     * @throws RlpDecoderException if list was not decoded correctly.
     * */
    fun finishListOrMinusOne(listEndPosition: Int): Int {
        if (--startedListCount < 0) {
            this.error = "Not all list were finished decoding. Need to close ${startedListCount.absoluteValue} more."
            return -1
        }

        if (position != listEndPosition) {
            this.error = "List not decoded correctly. Expected end position to be $listEndPosition, got $position"
            return -1
        }

        return listEndPosition
    }

    /**
     * Decode the element as a [BigInteger].
     *
     * @return decoded [BigInteger].
     * @throws RlpDecoderException if element is not a [BigInteger].
     * */
    fun decodeBigInteger(): BigInteger {
        val ret = decodeBigIntegerOrNull()
        error?.throwDecoderException()
        return ret!!
    }

    /**
     * Decode the element as a [BigInteger], or `null` if it could not be decoded.
     *
     * @return decoded [BigInteger], or `null` if the element could not be decoded.
     * */
    fun decodeBigIntegerOrNull(): BigInteger? {
        return getNextElement(false, null, ::decodeBigIntegerOrNull)
    }

    /**
     * Decode the element as a primitive [Long], or return the result of [default] if it cannot be decoded.
     *
     * @return decoded [Long], or result of [default] if the element is not a valid [Long].
     * */
    inline fun decodeLongOrElse(default: () -> Long): Long {
        contract {
            callsInPlace(default, InvocationKind.AT_MOST_ONCE)
        }

        val ret = decodeLongOrMinusOne()
        return if (error == null) ret else default()
    }

    /**
     * Decode element as a primitive [Long].
     *
     * @return decoded [Long].
     * @throws RlpDecoderException if element is not a valid [Long].
     * */
    fun decodeLong(): Long {
        val ret = decodeLongOrMinusOne()
        error?.throwDecoderException()
        return ret
    }

    /**
     * Decode the element as a primitive [Long], or `-1` if it cannot be decoded.
     *
     * @return decoded [Long], or `-1` if the element is not a valid [Long].
     * */
    fun decodeLongOrMinusOne(): Long {
        return getNextElement(false, -1L, ::takeLong)
    }

    /**
     * Decode the element as a primitive [Long], or `null` if it cannot be decoded.
     *
     * NOTE: This will box the returned [Long] - prefer [decodeLongOrMinusOne] / [decodeLongOrElse] in
     * high-performance scenarios.
     *
     * @return decoded [Long], or `null` if the element is not a valid [Long].
     * */
    fun decodeLongOrNull(): Long? {
        return getNextElement(false, null, ::takeLong)
    }

    /**
     * Decode and return the result using [decodable].
     *
     * @return result of [decodable].
     * */
    fun <T> decodeOrNull(decodable: RlpDecodable<T>): T? {
        val ret = decodable.rlpDecode(this)
        return if (error == null) ret else null
    }

    /**
     * Decode and return the optional result of [decodable], or return the result of [default] if it cannot be decoded.
     *
     * @return optional decoded result from [decodable], or result of [default] if the element is not a valid [T].
     * */
    inline fun <T : Any> decodeOptionalOrElse(decodable: RlpDecodable<T>, default: () -> T?): T? {
        val ret = decodable.rlpDecode(this)
        return when {
            error != null -> default()
            else -> ret
        }
    }

    /**
     * Decode the next element as a [ByteArray], returning `null` if it cannot be decoded.
     *
     * @return the decoded [ByteArray].
     * @throws RlpDecoderException if the next element could not be read as a [ByteArray].
     * */
    fun decodeByteArray(): ByteArray {
        val ret = decodeByteArrayOrNull()
        error?.throwDecoderException()
        return ret!!
    }

    /**
     * Decode the next element as a [ByteArray], returning `null` if it cannot be decoded.
     *
     * @return the decoded [ByteArray], or null if the element cannot be decoded.
     * */
    fun decodeByteArrayOrNull(): ByteArray? {
        return getNextElement(false, null, ::takeByteArray)
    }

    // loosely based on: https://github.com/alloy-rs/rlp/blob/323dcd751ecec18a88690f744c3a7c389b924236/crates/rlp/src/header.rs#L21-L21
    private inline fun <T> getNextElement(
        expectList: Boolean,
        errorValue: T,
        decoder: (payloadSize: Int) -> T,
    ): T {
        if (error != null) return errorValue

        if (remaining < 1) {
            this.error = inputTooShort(position)
            return errorValue
        }

        var isList: Boolean
        var payloadSize: Int

        val flag = peekFlag()
        when {
            flag < RLP_STRING_SHORT -> {
                isList = false
                payloadSize = 1
            }

            flag in RLP_STRING_SHORT..RLP_STRING_LONG -> {
                position++

                val size = flag - RLP_STRING_SHORT
                if (size == 1) {
                    if (remaining < size) {
                        this.error = inputTooShort(position)
                        return errorValue
                    }

                    if (peekFlag() < RLP_STRING_SHORT) {
                        this.error = "Invalid single byte value at position $position"
                        return errorValue
                    }
                }

                isList = false
                payloadSize = size
            }

            flag in (RLP_STRING_LONG + 1)..<RLP_LIST_SHORT || flag in (RLP_LIST_LONG + 1)..0xff -> {
                position++

                isList = flag > RLP_LIST_LONG

                val lengthOfSize = flag - if (isList) RLP_LIST_LONG else RLP_STRING_LONG
                if (remaining < lengthOfSize) {
                    this.error = inputTooShort(position)
                    return errorValue
                }

                if (lengthOfSize !in 1..MAX_LENGTH_OF_SIZE) {
                    this.error = "Invalid length of size at position $position: $lengthOfSize"
                    return errorValue
                }

                val size = takeSizeWithLength(lengthOfSize)
                if (size < 56) {
                    this.error = "Encoded size too short at position $position: $size"
                    return errorValue
                }

                payloadSize = size
            }

            flag in RLP_LIST_SHORT..RLP_LIST_LONG -> {
                position++

                isList = true
                payloadSize = flag - RLP_LIST_SHORT
            }

            else -> throw RlpDecoderException("Impossible flag value: $flag")
        }

        if (expectList != isList) {
            val expectation = if (expectList) "Expected" else "Unexpected"
            this.error = "$expectation list at position $position"
            return errorValue
        }

        if (remaining < payloadSize) {
            this.error = inputTooShort(position)
            return errorValue
        }

        return decoder(payloadSize)
    }

    private fun inputTooShort(position: Int): String {
        return "Remaining input too short at position $position"
    }

    private fun peekFlag(): Int {
        return array[position].toUByte().toInt()
    }

    private fun decodeBigIntegerOrNull(size: Int): BigInteger {
        if (size == 0) return BigInteger.ZERO

        return BigInteger(1, takeByteArray(size))
    }

    private fun takeLong(size: Int): Long {
        if (size == 0) return 0L

        var result = 0L
        for (i in 0..<size) {
            result = result shl 8 or (array[position++].toLong() and 0xff)
        }
        return result
    }

    private fun takeByteArray(size: Int): ByteArray {
        if (size == 0) return EMPTY_BYTE_ARRAY

        val result = ByteArray(size)
        System.arraycopy(array, position, result, 0, size)
        position += size
        return result
    }

    private fun takeSizeWithLength(lengthOfSize: Int): Int {
        return when (lengthOfSize) {
            1 -> array[position++].toInt() and 0xff
            2 -> (array[position++].toInt() and 0xff) shl 8 or (array[position++].toInt() and 0xff)
            3 -> (array[position++].toInt() and 0xff) shl 16 or ((array[position++].toInt() and 0xff) shl 8) or (array[position++].toInt() and 0xff)
            4 -> (array[position++].toInt() and 0xff) shl 24 or ((array[position++].toInt() and 0xff) shl 16) or ((array[position++].toInt() and 0xff) shl 8) or (array[position++].toInt() and 0xff)
            else -> throw RlpDecoderException("Impossible length value: $lengthOfSize")
        }
    }

    private fun String.throwDecoderException(): Nothing {
        throw RlpDecoderException(this)
    }

    companion object {
        private const val MAX_LENGTH_OF_SIZE = 4
        private val EMPTY_BYTE_ARRAY = ByteArray(0)
    }
}
