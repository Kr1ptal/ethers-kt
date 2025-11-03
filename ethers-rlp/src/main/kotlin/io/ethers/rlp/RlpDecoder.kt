package io.ethers.rlp

import java.math.BigInteger
import java.util.function.Supplier
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.absoluteValue

@OptIn(ExperimentalContracts::class)
class RlpDecoder(private val array: ByteArray) {
    var error: Error? = null
        private set

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
                lengthOfSize <= MAX_LENGTH_OF_SIZE && remaining >= (lengthOfSize + 1)
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
     * @throws RlpDecoderException if element is not a list.
     * */
    fun startList(): Int {
        val listByteLength = getNextElement(true) { it }
            ?: throw RlpDecoderException("Not a list")

        startedListCount++

        // IMPORTANT: get position last in this function, so it has a correct index
        return position + listByteLength
    }

    /**
     * Finish decoding a list, validating that the list was decoded correctly. [listEndPosition] must be return value
     * from calling [startList].
     *
     * Prefer using [decodeList] instead of this function directly as it handles the validation for you.
     *
     * @throws RlpDecoderException if list was not decoded correctly.
     * */
    fun finishList(listEndPosition: Int) {
        if (--startedListCount < 0) {
            throw RlpDecoderException("Not all list decodings were finished. Need to close ${startedListCount.absoluteValue} more.")
        }

        if (position != listEndPosition) {
            throw RlpDecoderException("List not decoded correctly. Expected end position to be $listEndPosition, got $position")
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

        val ret = decodeBigIntegerOrNull()
        return if (error == null) ret!! else default()
    }

    /**
     * Decode the element as a [BigInteger], or ZERO if an element
     *
     * @return decoded [BigInteger], or ZERO if RLP element is empty.
     * @throws RlpDecoderException if element is not a [BigInteger].
     * */
    fun decodeBigInteger(): BigInteger {
        val ret = decodeBigIntegerOrNull()
        error?.doThrow()
        return ret!!
    }

    fun decodeBigIntegerOrNull(): BigInteger? {
        return getNextElement(false, ::decodeBigIntegerOrNull)
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
        val ret = decodeLongOrNull()
        return if (error == null) ret!! else default()
    }

    /**
     * Decode element as a primitive [Long].
     *
     * @return decoded [Long], or 0 if RLP element is empty.
     * @throws RlpDecoderException if element is not a [Long].
     * */
    fun decodeLong(): Long {
        val ret = decodeLongOrNull()
        error?.doThrow()
        return ret!!
    }

    fun decodeLongOrNull(): Long? {
        return getNextElement(false, ::takeLong)
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
     * Decode the [ByteArray] of next element, or null if it cannot be decoded.
     *
     * @return the decoded [T], or null if the element cannot be decoded.
     * */
    inline fun <T> decodeByteArray(consumer: (ByteArray) -> T): T? {
        contract {
            callsInPlace(consumer, InvocationKind.AT_MOST_ONCE)
        }
        val ret = decodeByteArrayOrNull()
        return if (error == null) consumer(ret!!) else null
    }

    /**
     * Decode the next element as a [ByteArray], or return result of [default] if it cannot be decoded.
     *
     * @return decoded [ByteArray], or result of [default] if the next element cannot be decoded as [ByteArray].
     * */
    inline fun decodeByteArrayOrElse(default: () -> ByteArray): ByteArray {
        contract {
            callsInPlace(default, InvocationKind.AT_MOST_ONCE)
        }
        val ret = decodeByteArrayOrNull()
        return if (error == null) ret!! else default()
    }

    /**
     * Decode the next element as a [ByteArray], throwing if the element could not be read.
     *
     * @return the decoded [ByteArray].
     * @throws RlpDecoderException if the next element could not be read as a [ByteArray].
     * */
    fun decodeByteArray(): ByteArray {
        val ret = decodeByteArrayOrNull()
        error?.doThrow()
        return ret!!
    }

    /**
     * Decode the next element as a [ByteArray], returning null if the element could not be read.
     *
     * @return the decoded [ByteArray], or null if the element cannot be decoded.
     * */
    fun decodeByteArrayOrNull(): ByteArray? {
        return getNextElement(false, ::takeByteArray)
    }

    // loosely based on: https://github.com/alloy-rs/rlp/blob/323dcd751ecec18a88690f744c3a7c389b924236/crates/rlp/src/header.rs#L21-L21
    private inline fun <T> getNextElement(
        expectList: Boolean,
        decoder: (payloadSize: Int) -> T,
    ): T? {
        if (error != null) return null

        if (remaining < 1) {
            this.error = Error.inputTooShort(position)
            return null
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
                        this.error = Error.inputTooShort(position)
                        return null
                    }

                    if (peekFlag() < RLP_STRING_SHORT) {
                        this.error = Error("Invalid single byte value at position $position")
                        return null
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
                    this.error = Error.inputTooShort(position)
                    return null
                }

                if (lengthOfSize !in 1..4) {
                    this.error = Error("Invalid length of size at position $position: $lengthOfSize")
                    return null
                }

                val size = takeSizeWithLength(lengthOfSize)
                if (size < 56) {
                    this.error = Error("Encoded size too short at position $position: $size")
                    return null
                }

                payloadSize = size
            }

            flag in RLP_LIST_SHORT..RLP_LIST_LONG -> {
                position++

                isList = true
                payloadSize = flag - RLP_LIST_SHORT
            }

            else -> throw IllegalStateException("Impossible flag value: $flag")
        }

        if (expectList != isList) {
            val expectation = if (expectList) "Expected" else "Unexpected"
            this.error = Error("$expectation list at position $position")
            return null
        }

        if (remaining < payloadSize) {
            this.error = Error.inputTooShort(position)
            return null
        }

        return decoder(payloadSize)
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
            else -> throw IllegalStateException("Impossible length value: $lengthOfSize")
        }
    }

    // an error wrapper to avoid capturing a stacktrace unless actually throwing an exception
    data class Error(val message: String) {
        fun doThrow(): Nothing {
            throw RlpDecoderException(message)
        }

        companion object {
            fun inputTooShort(position: Int): Error {
                return Error("Remaining input too short at position $position")
            }
        }
    }

    companion object {
        private const val MAX_LENGTH_OF_SIZE = 4
        private val EMPTY_BYTE_ARRAY = ByteArray(0)
    }
}
