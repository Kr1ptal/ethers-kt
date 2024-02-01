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
class Bytes(val value: ByteArray) : RlpEncodable {
    constructor(value: CharSequence) : this(FastHex.decode(value))

    val size: Int
        get() = value.size

    val isEmpty: Boolean
        get() = value.isEmpty()

    override fun rlpEncode(rlp: RlpEncoder) {
        rlp.encode(value)
    }

    infix fun equals(other: CharSequence): Boolean {
        val otherSize = if (other.startsWith("0x")) (other.length - 2) else other.length
        return when {
            otherSize == 0 -> value.isEmpty()
            (otherSize % 2) == 1 -> throw IllegalArgumentException("CharSequence need to be even length (2 hex char =  1 byte).")
            else -> equals(FastHex.decode(other))
        }
    }

    infix fun equals(other: ByteArray): Boolean {
        return value.contentEquals(other)
    }

    operator fun contains(other: Bytes): Boolean {
        return contains(other.value)
    }

    operator fun contains(other: CharSequence): Boolean {
        val otherSize = if (other.startsWith("0x")) (other.length - 2) else other.length
        return when {
            otherSize == 0 -> true
            (otherSize % 2) == 1 -> throw IllegalArgumentException("CharSequence need to be even length (2 hex char =  1 byte).")
            (otherSize / 2) > value.size -> false
            else -> contains(FastHex.decode(other))
        }
    }

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
        return FastHex.encodeWithPrefix(value)
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
