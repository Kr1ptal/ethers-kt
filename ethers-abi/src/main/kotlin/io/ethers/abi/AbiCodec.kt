package io.ethers.abi

import io.ethers.core.FastHex
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import java.math.BigInteger
import java.nio.ByteBuffer

object AbiCodec {
    private val TWOS_COMPLEMENT_PADDING = (0..<32).map { ByteArray(it) { 0xff.toByte() } }.toTypedArray()

    const val WORD_SIZE_BYTES = 32

    /**
     * Maximum recursion depth for nested ABI types during decoding.
     *
     * This limit matches the EVM's 16-slot stack access constraint (DUP16/SWAP16 opcodes).
     * While the ABI specification doesn't mandate this limit, practical Solidity contracts
     * cannot produce structures deeper than 16 levels due to EVM constraints.
     *
     * This protects against:
     * - Stack overflow attacks from maliciously crafted deeply nested data
     * - "Billion laughs" style DoS attacks via recursive structures
     * - OOM conditions from excessive memory allocation
     */
    private const val MAX_RECURSION_DEPTH = 16

    /**
     * Checks if a type is a zero-sized type (ZST).
     *
     * Zero-sized types can be exploited for DoS attacks where tiny payloads
     * claim to contain billions of elements, causing excessive memory allocation.
     *
     * Modern Solidity and Vyper versions disallow defining ZSTs.
     *
     * Examples of ZSTs:
     * - Empty tuples: ()
     * - Empty structs
     * - Zero-length fixed arrays: T[0]
     * - Arrays of ZSTs: ()[], T[0][]
     */
    private fun isZeroSizedType(type: AbiType<*>): Boolean {
        return when (type) {
            // Empty tuple/struct - has no fields
            is AbiType.Struct<*>,
            is AbiType.Tuple,
            -> type.types.isEmpty()

            // Fixed array with zero length
            is AbiType.FixedArray<*> -> type.length == 0 || isZeroSizedType(type.type)

            // Dynamic/fixed array containing ZST elements
            is AbiType.Array<*> -> isZeroSizedType(type.type)

            // All primitive types (address, bool, int, uint, bytes, string, fixedBytes) are non-zero
            else -> false
        }
    }

    /**
     * Encode [data] as [types], prepended with [prefix]. Prefix is usually one of:
     * - `method selector`
     * - `deploy bytecode`
     *
     * @throws [AbiCodecException] if encoding failed.
     * */
    @JvmStatic
    fun <T : Any> encodeWithPrefix(
        prefix: Bytes,
        types: List<AbiType<out T>>,
        data: List<T>,
    ): ByteArray {
        if (types.isEmpty()) {
            return prefix.asByteArray()
        }

        if (types.size != data.size) {
            throw AbiCodecException("Expected ${types.size} arguments, got ${data.size}")
        }

        withHeadTailLengths(types, data) { head, tail ->
            val ret = ByteBuffer.allocate(prefix.size + head + tail)
            ret.put(prefix.asByteArray())
            encodeTokensHeadTail(ret, types, data, head)
            return ret.array()
        }
    }

    /**
     * Encode [data] as [types].
     *
     * @throws [AbiCodecException] if encoding failed.
     * */
    @JvmStatic
    fun <T : Any> encode(types: List<AbiType<out T>>, data: List<T>): ByteArray {
        if (types.isEmpty()) {
            throw AbiCodecException("Cannot encode empty tokens")
        }

        if (types.size != data.size) {
            throw AbiCodecException("Expected ${types.size} arguments, got ${data.size}")
        }

        withHeadTailLengths(types, data) { head, tail ->
            val ret = ByteBuffer.allocate(head + tail)
            encodeTokensHeadTail(ret, types, data, head)
            return ret.array()
        }
    }

    /**
     * Encode [data] as a single [type].
     *
     * @throws [AbiCodecException] if encoding failed.
     * */
    @JvmStatic
    fun <T : Any> encode(type: AbiType<out T>, data: T): ByteArray {
        val head = getTokenHeadLength(type, data)
        val tail = getTokenTailLength(type, data)

        val ret = ByteBuffer.allocate(head + tail)
        encodeTokenHead(ret, type, data, head)
        encodeTokenTail(ret, type, data)

        return ret.array()
    }

    /**
     * Decode [data] as [types], skipping [prefixSize] bytes. Prefix is usually one of:
     * - `method selector`
     * - `deploy bytecode`
     *
     * @throws [AbiCodecException] if decoding failed.
     * */
    @JvmStatic
    fun <T : Any> decodeWithPrefix(prefixSize: Int, types: List<AbiType<out T>>, data: ByteArray): List<T> {
        if (data.size < prefixSize) {
            throw AbiCodecException("Data is too short: ${data.size}")
        }

        if (types.isEmpty() && data.size > prefixSize) {
            throw AbiCodecException("Expected empty input, got: ${FastHex.encodeWithoutPrefix(data)}")
        }

        if (types.isNotEmpty() && data.size == prefixSize) {
            throw AbiCodecException("Expected input, got empty data")
        }

        if (types.isEmpty()) {
            @Suppress("UNCHECKED_CAST")
            return emptyList<Any>() as List<T>
        }

        @Suppress("UNCHECKED_CAST")
        return decodeTokens(types, ByteBuffer.wrap(data).position(prefixSize)) as List<T>
    }

    /**
     * Decode [data] as [types].
     *
     * @throws [AbiCodecException] if decoding failed.
     * */
    @JvmStatic
    fun <T : Any> decode(types: List<AbiType<out T>>, data: ByteArray): List<T> {
        if (types.isEmpty()) {
            throw AbiCodecException("Cannot decode empty tokens")
        }

        if (data.size < WORD_SIZE_BYTES) {
            throw AbiCodecException("Cannot decode empty data: ${FastHex.encodeWithoutPrefix(data)}")
        }

        @Suppress("UNCHECKED_CAST")
        return decodeTokens(types, ByteBuffer.wrap(data)) as List<T>
    }

    /**
     * Decode [data] as a single [type].
     *
     * @throws [AbiCodecException] if decoding failed.
     * */
    @JvmStatic
    fun <T : Any> decode(type: AbiType<T>, data: ByteArray): T {
        // if we don't have at least one word, throw
        if (data.size < WORD_SIZE_BYTES) {
            throw AbiCodecException("Cannot decode empty data: ${FastHex.encodeWithoutPrefix(data)}")
        }

        @Suppress("UNCHECKED_CAST")
        return decodeToken(type, ByteBuffer.wrap(data), 0) as T
    }

    private fun encodeTokensHeadTail(buff: ByteBuffer, types: List<AbiType<*>>, data: List<Any>, headLength: Int) {
        var headOffset = headLength
        for (i in types.indices) {
            encodeTokenHead(buff, types[i], data[i], headOffset)
            headOffset += getTokenTailLength(types[i], data[i])
        }

        for (i in types.indices) {
            encodeTokenTail(buff, types[i], data[i])
        }
    }

    private fun encodeTokensHeadTail(buff: ByteBuffer, type: AbiType<*>, data: List<Any>, headLength: Int) {
        var headOffset = headLength
        for (i in data.indices) {
            encodeTokenHead(buff, type, data[i], headOffset)
            headOffset += getTokenTailLength(type, data[i])
        }

        for (i in data.indices) {
            encodeTokenTail(buff, type, data[i])
        }
    }

    private fun encodeTokenHead(buff: ByteBuffer, type: AbiType<*>, data: Any, headOffset: Int) {
        when (type) {
            // head-only, right-padded
            AbiType.Address -> {
                val value = data as Address
                buff.position(buff.position() + 12)
                buff.put(value.asByteArray())
            }

            AbiType.Bool -> {
                val value = data as Boolean
                buff.position(buff.position() + 31)
                buff.put(if (value) 1 else 0)
            }

            is AbiType.Int -> {
                val v = data as BigInteger
                if (v.bitLength() > type.bitSize - 1) {
                    throw AbiCodecException("Provided INT value has more than ${type.bitSize - 1} bits: ${v.bitLength()}")
                }

                // if value is negative, convert to two's complement 256-bit number
                if (v.signum() == -1) {
                    val arr = v.toByteArray()

                    buff.put(TWOS_COMPLEMENT_PADDING[32 - arr.size])
                    buff.put(arr)
                    return
                }

                // TODO this could be optimized by accessing the underlying array directly (e.g. method handle to BigInteger#getInt(index))
                //      and writing directly to buff. This would avoid the intermediate array allocation and copy.
                val arr = v.toByteArray()
                if (arr.size == 33 && arr[0].toInt() == 0) {
                    buff.put(arr, 1, 32)
                } else {
                    buff.position(buff.position() + (32 - arr.size))
                    buff.put(arr)
                }
            }

            is AbiType.UInt -> {
                val v = data as BigInteger
                if (v.signum() == -1) {
                    throw AbiCodecException("Expected UINT, got INT: $v")
                }

                if (v.bitLength() > type.bitSize) {
                    throw AbiCodecException("Provided UINT value has more than ${type.bitSize} bits: ${v.bitLength()}")
                }

                // TODO this could be optimized by accessing the underlying array directly (e.g. method handle to BigInteger#getInt(index))
                //      and writing directly to buff. This would avoid the intermediate array allocation and copy.
                val arr = v.toByteArray()
                if (arr.size > 33 || arr.size == 33 && arr[0].toInt() != 0) {
                    throw AbiCodecException("Provided value has more than 256 bits: $v")
                }

                if (arr.size == 33 && arr[0].toInt() == 0) {
                    buff.put(arr, 1, 32)
                } else {
                    buff.position(buff.position() + (32 - arr.size))
                    buff.put(arr)
                }
            }

            // head-only, left-padded
            is AbiType.FixedBytes -> {
                val value = data as Bytes
                if (value.size != type.length) {
                    throw AbiCodecException("Provided value has length ${value.size}, expected ${type.length}")
                }

                val rem = WORD_SIZE_BYTES - value.size

                buff.put(value.asByteArray())
                if (rem > 0) {
                    buff.position(buff.position() + rem)
                }
            }

            // head-only, bytes/string left-padded
            AbiType.Bytes, AbiType.String, is AbiType.Array<*> -> {
                buff.position(buff.position() + 28)
                buff.putInt(headOffset)
            }

            // tail-only if dynamic, else all elements are encoded as head
            is AbiType.FixedArray<*> -> {
                if (type.isDynamic) {
                    buff.position(buff.position() + 28)
                    buff.putInt(headOffset)
                    return
                }

                val value = data as List<*>
                if (value.size != type.length) {
                    throw AbiCodecException("Provided value has length ${value.size}, expected ${type.length}")
                }

                for (i in value.indices) {
                    encodeTokenHead(buff, type.type, value[i]!!, headOffset)
                }
            }

            is AbiType.Struct<*>,
            is AbiType.Tuple<*>,
            -> {
                if (type.isDynamic) {
                    buff.position(buff.position() + 28)
                    buff.putInt(headOffset)
                    return
                }

                val value = type.dataAsTuple(data)
                if (value.size != type.types.size) {
                    throw AbiCodecException("Provided value has length ${value.size}, expected ${type.types.size}")
                }

                for (i in type.types.indices) {
                    encodeTokenHead(buff, type.types[i], value[i], headOffset)
                }
            }
        }
    }

    private fun encodeTokenTail(buff: ByteBuffer, type: AbiType<*>, data: Any) {
        when (type) {
            // head-only
            AbiType.Address, AbiType.Bool, is AbiType.Int, is AbiType.UInt, is AbiType.FixedBytes -> {}

            // head-only, bytes/string left-padded
            AbiType.Bytes -> {
                val value = (data as Bytes).asByteArray()
                buff.position(buff.position() + 28)
                buff.putInt(value.size)

                val numOfWords = (value.size + WORD_SIZE_BYTES - 1) / WORD_SIZE_BYTES
                for (i in 0..<numOfWords) {
                    val start = i * WORD_SIZE_BYTES
                    val end = minOf(start + WORD_SIZE_BYTES, value.size)
                    val length = end - start
                    buff.put(value, start, length)

                    if (length < WORD_SIZE_BYTES) {
                        buff.position(buff.position() + (WORD_SIZE_BYTES - length))
                    }
                }
            }

            AbiType.String -> {
                val value = (data as String).toByteArray(Charsets.UTF_8)
                buff.position(buff.position() + 28)
                buff.putInt(value.size)

                val numOfWords = (value.size + WORD_SIZE_BYTES - 1) / WORD_SIZE_BYTES
                for (i in 0..<numOfWords) {
                    val start = i * WORD_SIZE_BYTES
                    val end = minOf(start + WORD_SIZE_BYTES, value.size)
                    val length = end - start
                    buff.put(value, start, length)

                    if (length < WORD_SIZE_BYTES) {
                        buff.position(buff.position() + (WORD_SIZE_BYTES - length))
                    }
                }
            }

            is AbiType.Array<*> -> {
                @Suppress("UNCHECKED_CAST")
                val value = data as List<Any>
                buff.position(buff.position() + 28)
                buff.putInt(value.size)

                if (value.isNotEmpty()) {
                    var headLength = 0
                    for (i in value.indices) {
                        headLength += getTokenHeadLength(type.type, value[i])
                    }

                    encodeTokensHeadTail(buff, type.type, value, headLength)
                }
            }

            // tail-only if dynamic, else all elements are encoded as head
            is AbiType.FixedArray<*> -> {
                if (!type.isDynamic) {
                    return
                }

                @Suppress("UNCHECKED_CAST")
                val value = data as List<Any>
                if (value.size != type.length) {
                    throw AbiCodecException("Provided value has length ${value.size}, expected ${type.length}")
                }

                var headLength = 0
                for (i in value.indices) {
                    headLength += getTokenHeadLength(type.type, value[i])
                }
                encodeTokensHeadTail(buff, type.type, value, headLength)
            }

            is AbiType.Struct<*>,
            is AbiType.Tuple<*>,
            -> {
                if (!type.isDynamic) {
                    return
                }
                val value = type.dataAsTuple(data)
                if (value.size != type.types.size) {
                    throw AbiCodecException("Provided value has length ${value.size}, expected ${type.types.size}")
                }
                var headLength = 0
                for (i in value.indices) {
                    headLength += getTokenHeadLength(type.types[i], value[i])
                }
                encodeTokensHeadTail(buff, type.types, value, headLength)
            }
        }
    }

    private inline fun <R> withHeadTailLengths(
        types: List<AbiType<*>>,
        data: List<Any>,
        consumer: (Int, Int) -> R,
    ): R {
        var head = 0
        var tail = 0
        for (i in types.indices) {
            head += getTokenHeadLength(types[i], data[i])
            tail += getTokenTailLength(types[i], data[i])
        }
        return consumer(head, tail)
    }

    private fun getTokenHeadLength(type: AbiType<*>, data: Any): Int {
        return when (type) {
            // head-only
            AbiType.Address -> WORD_SIZE_BYTES
            AbiType.Bool -> WORD_SIZE_BYTES
            is AbiType.FixedBytes -> WORD_SIZE_BYTES
            is AbiType.Int, is AbiType.UInt -> WORD_SIZE_BYTES

            // offset-only
            AbiType.Bytes -> WORD_SIZE_BYTES
            AbiType.String -> WORD_SIZE_BYTES
            is AbiType.Array<*> -> WORD_SIZE_BYTES

            // offset-only if dynamic, else all elements are encoded as head
            is AbiType.FixedArray<*> -> {
                if (type.isDynamic) {
                    return WORD_SIZE_BYTES
                }

                var headLength = 0
                val values = data as List<*>
                for (i in values.indices) {
                    headLength += getTokenHeadLength(type.type, values[i]!!)
                }

                headLength
            }

            is AbiType.Struct<*>,
            is AbiType.Tuple,
            -> {
                if (type.isDynamic) {
                    return WORD_SIZE_BYTES
                }

                var headLength = 0
                val value = type.dataAsTuple(data)
                for (i in type.types.indices) {
                    headLength += getTokenHeadLength(type.types[i], value[i])
                }

                headLength
            }
        }
    }

    private fun getTokenTailLength(type: AbiType<*>, data: Any): Int {
        return when (type) {
            AbiType.Address -> 0
            AbiType.Bool -> 0
            is AbiType.FixedBytes -> 0
            is AbiType.Int, is AbiType.UInt -> 0

            AbiType.Bytes -> {
                val value = data as Bytes
                val numOfWords = (value.size + WORD_SIZE_BYTES - 1) / WORD_SIZE_BYTES
                WORD_SIZE_BYTES + (numOfWords * WORD_SIZE_BYTES)
            }

            AbiType.String -> {
                val utf8Length = Utf8.encodedLength(data as String)
                val numOfWords = (utf8Length + WORD_SIZE_BYTES - 1) / WORD_SIZE_BYTES
                WORD_SIZE_BYTES + (numOfWords * WORD_SIZE_BYTES)
            }

            is AbiType.Array<*> -> {
                var length = 0
                val value = data as List<*>
                for (i in value.indices) {
                    val v = value[i]!!
                    length += getTokenHeadLength(type.type, v) + getTokenTailLength(type.type, v)
                }

                // length + encoded element length
                WORD_SIZE_BYTES + length
            }

            is AbiType.FixedArray<*> -> {
                if (!type.isDynamic) {
                    return 0
                }

                var length = 0
                val value = data as List<*>
                for (i in value.indices) {
                    val v = value[i]!!
                    length += getTokenHeadLength(type.type, v) + getTokenTailLength(type.type, v)
                }

                length
            }

            is AbiType.Struct<*>,
            is AbiType.Tuple<*>,
            -> {
                if (!type.isDynamic) {
                    return 0
                }

                var length = 0
                val value = type.dataAsTuple(data)
                for (i in type.types.indices) {
                    val v = value[i]
                    length += getTokenHeadLength(type.types[i], v) + getTokenTailLength(type.types[i], v)
                }

                length
            }
        }
    }

    private fun decodeTokens(types: List<AbiType<*>>, buff: ByteBuffer): List<Any> {
        val ret = ArrayList<Any>(types.size)

        // to account for 4byte selector
        val offset = buff.position()
        for (i in types.indices) {
            ret.add(decodeToken(types[i], buff, offset))
        }
        return ret
    }

    private fun decodeToken(type: AbiType<*>, buff: ByteBuffer, currOffset: Int, depth: Int = 1): Any {
        if (depth > MAX_RECURSION_DEPTH) {
            throw AbiCodecException("Recursion depth $depth exceeds maximum: $MAX_RECURSION_DEPTH")
        }

        // Reject zero-sized types to prevent DoS attacks
        if (isZeroSizedType(type)) {
            throw AbiCodecException(
                "Zero-sized type detected: $type. " +
                    "ZSTs are not allowed per modern Solidity/Vyper and may indicate " +
                    "a malicious payload or corrupted data.",
            )
        }

        when (type) {
            AbiType.Address -> {
                buff.ensureRemaining(WORD_SIZE_BYTES)

                val arr = ByteArray(20)
                buff.skip(12).get(arr)
                return Address(arr)
            }

            AbiType.Bool -> return buff.skip(31).get().toInt() == 1

            // left-padded
            is AbiType.FixedBytes -> {
                buff.ensureRemaining(WORD_SIZE_BYTES)

                val arr = ByteArray(type.length)
                buff.get(arr).skip(32 - type.length)
                return Bytes(arr)
            }

            is AbiType.Int -> {
                buff.ensureRemaining(WORD_SIZE_BYTES)

                val ret = BigInteger(buff.array(), buff.position(), 32)
                buff.skip(32)
                return ret
            }

            is AbiType.UInt -> {
                buff.ensureRemaining(WORD_SIZE_BYTES)

                val ret = BigInteger(1, buff.array(), buff.position(), 32)
                buff.skip(32)
                return ret
            }

            AbiType.Bytes -> {
                buff.ensureRemaining(WORD_SIZE_BYTES)

                val offset = currOffset + buff.skip(28).getInt()
                val endPosition = buff.position()

                buff.ensureValidOffset(offset, currOffset).ensureRemaining(WORD_SIZE_BYTES)
                val length = buff.position(offset).skip(28).getInt()
                if (length < 0) {
                    throw AbiCodecException("Bytes length must be greater than zero, got: $length")
                }

                buff.ensureRemaining(length)

                val arr = ByteArray(length)
                buff.get(arr).position(endPosition)

                return Bytes(arr)
            }

            AbiType.String -> {
                buff.ensureRemaining(WORD_SIZE_BYTES)

                val offset = currOffset + buff.skip(28).getInt()
                val endPosition = buff.position()

                buff.ensureValidOffset(offset, currOffset).ensureRemaining(WORD_SIZE_BYTES)
                val length = buff.position(offset).skip(28).getInt()
                if (length < 0) {
                    throw AbiCodecException("String length must be greater than zero, got: $length")
                }

                buff.ensureRemaining(length)

                val ret = String(buff.array(), buff.position(), length, Charsets.UTF_8)
                buff.position(endPosition)

                return ret
            }

            is AbiType.Array<*> -> {
                buff.ensureRemaining(WORD_SIZE_BYTES)

                var offset = currOffset + buff.skip(28).getInt()
                val endPosition = buff.position()

                buff.ensureValidOffset(offset, currOffset).ensureRemaining(WORD_SIZE_BYTES)
                val length = buff.position(offset).skip(28).getInt()
                if (length < 0) {
                    throw AbiCodecException("Array length must be greater than zero, got: $length")
                }

                offset += WORD_SIZE_BYTES

                val arr = ArrayList<Any>(length)
                for (i in 0..<length) {
                    arr.add(decodeToken(type.type, buff, offset, depth + 1))
                }

                buff.position(endPosition)
                return arr
            }

            is AbiType.FixedArray<*> -> {
                val arr = ArrayList<Any>(type.length)

                if (type.isDynamic) {
                    buff.ensureRemaining(WORD_SIZE_BYTES)

                    val offset = currOffset + buff.skip(28).getInt()
                    val endPosition = buff.position()

                    buff.ensureValidOffset(offset, currOffset)
                    buff.position(offset)

                    for (i in 0..<type.length) {
                        arr.add(decodeToken(type.type, buff, offset, depth + 1))
                    }

                    buff.position(endPosition)
                } else {
                    for (i in 0..<type.length) {
                        arr.add(decodeToken(type.type, buff, currOffset, depth + 1))
                    }
                }

                return arr
            }

            is AbiType.Struct<*>,
            is AbiType.Tuple,
            -> {
                val arr = ArrayList<Any>(type.types.size)

                if (type.isDynamic) {
                    buff.ensureRemaining(WORD_SIZE_BYTES)
                    val offset = currOffset + buff.skip(28).getInt()
                    val endPosition = buff.position()

                    buff.ensureValidOffset(offset, currOffset)
                    buff.position(offset)

                    for (i in type.types.indices) {
                        arr.add(decodeToken(type.types[i], buff, offset, depth + 1))
                    }

                    buff.position(endPosition)
                } else {
                    for (i in type.types.indices) {
                        arr.add(decodeToken(type.types[i], buff, currOffset, depth + 1))
                    }
                }

                return type.factory.apply(arr)
            }
        }
    }

    /**
     * Encode [data] as [types], according to the non-standard packed encoding rules:
     * - types shorter than 32 bytes are concatenated directly, without padding or sign extension
     * - dynamic types are encoded in-place and without the length.
     * - array elements are padded, but still encoded in-place
     *
     * Furthermore, structs as well as nested arrays are not supported.
     *
     * In general, the encoding is ambiguous as soon as there are two dynamically-sized elements, because of the
     * missing length field. Since the encoding is ambiguous, there is no decoding function.
     *
     * See: [docs](https://docs.soliditylang.org/en/latest/abi-spec.html#non-standard-packed-mode)
     * */
    @JvmStatic
    fun <T : Any> encodePacked(types: List<AbiType<out T>>, data: List<T>): Bytes {
        var encodedSize = 0
        for (i in types.indices) {
            encodedSize += packEncodedSize(types[i], data[i], false)
        }

        val ret = ByteBuffer.allocate(encodedSize)
        for (i in types.indices) {
            encodePacked(ret, types[i], data[i], false)
        }
        return Bytes(ret.array())
    }

    private fun packEncodedSize(type: AbiType<*>, data: Any, inArray: Boolean): Int {
        return when (type) {
            AbiType.Address -> if (inArray) WORD_SIZE_BYTES else 20
            AbiType.Bool -> if (inArray) WORD_SIZE_BYTES else 1
            is AbiType.FixedBytes -> if (inArray) WORD_SIZE_BYTES else type.length
            is AbiType.Int -> if (inArray) WORD_SIZE_BYTES else type.bitSize / 8
            is AbiType.UInt -> if (inArray) WORD_SIZE_BYTES else type.bitSize / 8
            AbiType.Bytes -> (data as Bytes).size
            AbiType.String -> Utf8.encodedLength(data as String)
            is AbiType.Array<*> -> {
                if (type.type.isDynamic || type.type is AbiType.Array<*>) {
                    throw AbiCodecException("Cannot encode dynamic or nested arrays in packed format")
                }

                var size = 0
                val values = data as List<*>
                for (i in values.indices) {
                    size += packEncodedSize(type.type, values[i]!!, true)
                }

                size
            }

            is AbiType.FixedArray<*> -> {
                if (type.type.isDynamic || type.type is AbiType.Array<*>) {
                    throw AbiCodecException("Cannot encode dynamic or nested arrays in packed format")
                }

                var size = 0
                val values = data as List<*>
                for (i in values.indices) {
                    size += packEncodedSize(type.type, values[i]!!, true)
                }

                size
            }

            is AbiType.Struct<*>,
            is AbiType.Tuple,
            -> throw AbiCodecException("Cannot encode tuple in packed format")
        }
    }

    private fun encodePacked(buff: ByteBuffer, type: AbiType<*>, data: Any, inArray: Boolean) {
        when (type) {
            AbiType.Address -> {
                if (inArray) {
                    buff.skip(12)
                }
                buff.put((data as Address).asByteArray())
            }

            AbiType.Bool -> {
                if (inArray) {
                    buff.skip(31)
                }
                buff.put(if (data as Boolean) 1 else 0)
            }

            is AbiType.FixedBytes -> {
                val value = data as Bytes
                if (value.size != type.length) {
                    throw AbiCodecException("Provided value has length ${value.size}, expected ${type.length}")
                }

                buff.put(value.asByteArray())

                // padded at the end
                if (inArray) {
                    buff.skip(type.length - value.size)
                }
            }

            is AbiType.Int -> {
                val v = data as BigInteger
                if (v.bitLength() > type.bitSize - 1) {
                    throw AbiCodecException("Provided INT value has more than ${type.bitSize - 1} bits: ${v.bitLength()}")
                }

                // TODO this could be optimized by accessing the underlying array directly (e.g. method handle to BigInteger#getInt(index))
                //      and writing directly to buff. This would avoid the intermediate array allocation and copy.
                val arr = v.toByteArray()

                // if value is negative, convert to two's complement 256-bit number
                if (v.signum() == -1) {
                    // if in array, fully extend the sign, otherwise just extend it, so it fills full byte size of the type
                    if (inArray) {
                        buff.put(TWOS_COMPLEMENT_PADDING[32 - arr.size])
                    } else {
                        buff.put(TWOS_COMPLEMENT_PADDING[(type.bitSize / 8) - arr.size])
                    }

                    buff.put(arr)
                    return
                }

                if (inArray) {
                    buff.skip(32 - arr.size)
                } else {
                    buff.skip((type.bitSize / 8) - arr.size)
                }

                buff.put(arr)
            }

            is AbiType.UInt -> {
                val v = data as BigInteger
                if (v.signum() == -1) {
                    throw AbiCodecException("Expected UINT, got INT: $v")
                }

                if (v.bitLength() > type.bitSize) {
                    throw AbiCodecException("Provided UINT value has more than ${type.bitSize} bits: ${v.bitLength()}")
                }

                // TODO this could be optimized by accessing the underlying array directly (e.g. method handle to BigInteger#getInt(index))
                //      and writing directly to buff. This would avoid the intermediate array allocation and copy.
                val arr = v.toByteArray()
                if (arr.size > 33 || arr.size == 33 && arr[0].toInt() != 0) {
                    throw AbiCodecException("Provided value has more than 256 bits: $v")
                }

                if (arr.size == 33 && arr[0].toInt() == 0) {
                    buff.put(arr, 1, 32)
                } else {
                    if (inArray) {
                        buff.skip(32 - arr.size)
                    } else {
                        buff.skip((type.bitSize / 8) - arr.size)
                    }
                    buff.put(arr)
                }
            }

            AbiType.Bytes -> buff.put((data as Bytes).asByteArray())
            AbiType.String -> buff.put((data as String).toByteArray(Charsets.UTF_8))
            is AbiType.Array<*> -> {
                val values = data as List<*>
                for (i in values.indices) {
                    encodePacked(buff, type.type, values[i]!!, true)
                }
            }

            is AbiType.FixedArray<*> -> {
                val values = data as List<*>
                if (values.size != type.length) {
                    throw AbiCodecException("Provided value has length ${values.size}, expected ${type.length}")
                }

                for (i in values.indices) {
                    encodePacked(buff, type.type, values[i]!!, true)
                }
            }

            is AbiType.Struct<*>,
            is AbiType.Tuple,
            -> throw AbiCodecException("Cannot encode tuple in packed format")
        }
    }
}

/**
 * Exception thrown when decoding/encoding of data in [AbiCodec] fails.
 *  */
data class AbiCodecException(override val message: String) : RuntimeException()

private fun ByteBuffer.skip(n: Int): ByteBuffer {
    if (n <= 0) {
        return this
    }
    return position(position() + n)
}

private fun ByteBuffer.ensureRemaining(n: Int): ByteBuffer {
    if (this.remaining() < n) {
        throw AbiCodecException("Not enough bytes left. Wanted $n, have ${this.remaining()}")
    }
    return this
}

private fun ByteBuffer.ensureValidOffset(offset: Int, currentOffset: Int): ByteBuffer {
    // can only move forward
    if (offset < currentOffset) {
        throw AbiCodecException("Invalid backwards offset: $offset (currentOffset: $currentOffset)")
    }

    // subtract current offset in case we're decoding data with prefix, which needs to be ignored
    val wordRemainder = (offset - currentOffset) % AbiCodec.WORD_SIZE_BYTES

    if (wordRemainder != 0) {
        throw AbiCodecException("Offset is not 32 byte word-aligned: $offset")
    }

    if (this.capacity() < offset) {
        throw AbiCodecException("Invalid offset '$offset'. Buffer capacity: ${this.capacity()}")
    }
    return this
}
