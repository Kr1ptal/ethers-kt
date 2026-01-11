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
import io.ethers.core.HexDecodingError
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.readHash
import io.ethers.core.success
import io.ethers.rlp.RlpDecodable
import io.ethers.rlp.RlpDecoder
import io.ethers.rlp.RlpEncodable
import io.ethers.rlp.RlpEncoder
import java.math.BigInteger

/**
 * 32-byte hash.
 * */
@JsonDeserialize(using = HashDeserializer::class)
@JsonSerialize(using = HashSerializer::class)
class Hash(private val value: ByteArray) : RlpEncodable {
    constructor(value: CharSequence) : this(FastHex.decode(value))
    constructor(value: Address) : this(value.asByteArray().copyInto(ByteArray(32), 12))
    constructor(value: BigInteger) : this(bigIntegerToBytes(value))

    // cache of hex string for faster serialization if serializing the same instance multiple times
    private var stringCache: String? = null

    init {
        require(value.size == 32) { "Hash must be 32 bytes long" }
    }

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

    override fun rlpEncode(rlp: RlpEncoder) {
        rlp.encode(value)
    }

    override fun rlpSize(): Int {
        return RlpEncoder.sizeOf(value)
    }

    infix fun equals(other: CharSequence): Boolean {
        return value.contentEquals(FastHex.decode(other))
    }

    infix fun equals(other: ByteArray): Boolean {
        return value.contentEquals(other)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Hash

        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }

    override fun toString(): String {
        return stringCache ?: FastHex.encodeWithPrefix(value).also { stringCache = it }
    }

    companion object : RlpDecodable<Hash> {
        @JvmField
        val ZERO = Hash(ByteArray(32))

        @JvmStatic
        override fun rlpDecode(rlp: RlpDecoder): Hash? {
            val arr = rlp.decodeByteArrayOrNull() ?: return null
            return when {
                arr.size != 32 -> rlp.error("Invalid hash length: ${arr.size}")
                else -> Hash(arr)
            }
        }

        /**
         * Create a new [Hash] from hex string with validation.
         */
        @JvmStatic
        fun fromHex(hex: String): Result<Hash, HexDecodingError> {
            if (!FastHex.isValidHex(hex)) {
                return failure(HexDecodingError("Invalid hex format: $hex"))
            }

            val bytes = FastHex.decodeUnsafe(hex)
            if (bytes.size != 32) {
                return failure(HexDecodingError("Hash must be 32 bytes long, got ${bytes.size} bytes"))
            }

            return success(Hash(bytes))
        }

        /**
         * Create a new [Hash] from hex string without validation.
         */
        @JvmStatic
        fun fromHexUnsafe(hex: String): Hash {
            return Hash(FastHex.decodeUnsafe(hex))
        }

        /**
         * Convert [BigInteger] to [ByteArray] of length 32, padded from start with zeros if needed.
         *
         * @throws [IllegalArgumentException] if [BigInteger] has more than 256 bits.
         * */
        private fun bigIntegerToBytes(value: BigInteger): ByteArray {
            val bytes = value.toByteArray()
            if (bytes.size > 33 || bytes.size == 33 && bytes[0].toInt() != 0) {
                throw IllegalArgumentException("Provided value has more than 256 bits: $value")
            }

            val ret = ByteArray(32)
            bytes.copyInto(
                ret,
                if (bytes.size > 32) 0 else 32 - bytes.size,
                if (bytes.size > 32) 1 else 0,
                bytes.size,
            )

            return ret
        }
    }
}

private class HashDeserializer : JsonDeserializer<Hash>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Hash {
        return p.readHash()
    }
}

private class HashSerializer : JsonSerializer<Hash>() {
    override fun serialize(value: Hash, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.toString())
    }
}
