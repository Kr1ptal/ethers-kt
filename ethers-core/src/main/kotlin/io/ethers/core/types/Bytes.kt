package io.ethers.core.types

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.ethers.core.FastHex
import io.ethers.core.readBytes
import io.ethers.rlp.RlpDecodable
import io.ethers.rlp.RlpDecoder
import io.ethers.rlp.RlpEncodable
import io.ethers.rlp.RlpEncoder

/**
 * Variable size byte array.
 * */
@JsonDeserialize(using = BytesDeserializer::class)
@JsonSerialize(using = BytesSerializer::class)
class Bytes(private val value: ByteArray) : RlpEncodable {
    constructor(value: CharSequence) : this(FastHex.decode(value))

    // cache of hex string for faster serialization if serializing the same instance multiple times
    private var stringCache: String? = null

    /**
     * Return the internal byte array.
     *
     * If you need to modify the array, use [toByteArray] instead which returns a new copy of the array.
     *
     * IMPORTANT: Do not modify the returned array, it will lead to undefined behavior.
     * */
    fun asByteArray() = value

    /**
     * Return a copy of internal byte array.
     *
     * If you do not need to modify the array, use [asByteArray] instead which returns the internal array
     * without copying.
     * */
    fun toByteArray() = value.copyOf()

    /**
     * Return the size of the internal byte array.
     * */
    val size: Int
        get() = value.size

    /**
     * Return `true` if the internal byte array is empty, `false` otherwise.
     * */
    val isEmpty: Boolean
        get() = value.isEmpty()

    /**
     * Create a new [Bytes] instance by copying the internal byte array.
     *
     * @param startIndex the start index of the new [Bytes] instance, inclusive.
     * @param endIndex the end index of the new [Bytes] instance, exclusive.
     * */
    fun copyOfRange(startIndex: Int, endIndex: Int): Bytes {
        return Bytes(value.copyOfRange(startIndex, endIndex))
    }

    /**
     * Return true if `this` contains the provided [Bytes], false otherwise.
     * */
    operator fun contains(other: Bytes): Boolean {
        return contains(other.value)
    }

    /**
     * Return true if `this` contains the provided hex [CharSequence], false otherwise.
     * */
    operator fun contains(other: CharSequence): Boolean {
        val otherSize = if (other.startsWith("0x")) (other.length - 2) else other.length
        return when {
            otherSize == 0 -> true
            (otherSize % 2) == 1 -> throw IllegalArgumentException("CharSequence need to be even length (2 hex char =  1 byte).")
            (otherSize / 2) > value.size -> false
            else -> contains(FastHex.decode(other))
        }
    }

    /**
     * Return true if `this` contains the provided [ByteArray], false otherwise.
     * */
    operator fun contains(other: ByteArray): Boolean {
        if (other.isEmpty()) {
            return true
        }
        if (other.size > value.size) {
            return false
        }

        val otherFirst = other[0]
        outer@ for (i in value.indices) {
            // check if it's possible to find a match
            if (i + other.size > value.size) {
                return false
            }

            // match possible, check the first character
            if (value[i] != otherFirst) {
                continue
            }

            // found first character, now find the rest
            var k = 1
            for (j in i + 1..<i + other.size) {
                if (value[j] != other[k++]) {
                    continue@outer
                }
            }

            return true
        }

        return false
    }

    /**
     * Check if `this` is starting with provided [Bytes].
     */
    fun startsWith(other: Bytes): Boolean {
        return startsWith(other.value)
    }

    /**
     * Check if `this` is starting with provided [CharSequence], skipping "0x" prefix if present.
     */
    fun startsWith(other: CharSequence): Boolean {
        val otherSize = if (other.startsWith("0x")) (other.length - 2) else other.length
        return when {
            otherSize == 0 -> true
            (otherSize % 2) == 1 -> throw IllegalArgumentException("CharSequence need to be even length (2 hex char =  1 byte).")
            (otherSize / 2) > value.size -> false
            else -> startsWith(FastHex.decode(other))
        }
    }

    /**
     * Check if `this` is starting with provided [ByteArray].
     */
    fun startsWith(other: ByteArray): Boolean {
        if (other.isEmpty()) {
            return true
        }
        if (other.size > value.size) {
            return false
        }

        for (i in other.indices) {
            if (other[i] != value[i]) {
                return false
            }
        }

        return true
    }

    /**
     * Return true if `this` is equal to the provided hex [CharSequence], false otherwise.
     * */
    infix fun equals(other: CharSequence): Boolean {
        val otherSize = if (other.startsWith("0x")) (other.length - 2) else other.length
        return when {
            otherSize == 0 -> value.isEmpty()
            (otherSize % 2) == 1 -> throw IllegalArgumentException("CharSequence need to be even length (2 hex char =  1 byte).")
            else -> equals(FastHex.decode(other))
        }
    }

    /**
     * Return true if `this` is equal to the provided [ByteArray], false otherwise.
     * */
    infix fun equals(other: ByteArray): Boolean {
        return value.contentEquals(other)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Bytes

        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }

    override fun toString(): String {
        return stringCache ?: FastHex.encodeWithPrefix(value).also { stringCache = it }
    }

    override fun rlpEncode(rlp: RlpEncoder) {
        rlp.encode(value)
    }

    companion object : RlpDecodable<Bytes> {
        @JvmField
        val EMPTY = Bytes(ByteArray(0))

        override fun rlpDecode(rlp: RlpDecoder): Bytes? {
            return rlp.decodeByteArray(::Bytes)
        }
    }
}

private class BytesDeserializer : JsonDeserializer<Bytes>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Bytes {
        return p.readBytes()
    }
}

private class BytesSerializer : JsonSerializer<Bytes>() {
    override fun serialize(value: Bytes, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.toString())
    }
}
