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
import io.ethers.core.readBloom
import io.ethers.crypto.Hashing
import kotlin.experimental.and
import kotlin.experimental.or

private const val BLOOM_SIZE = 256

/**
 * Bloom filter.
 *
 * Space-efficient probabilistic data structure that is used to test whether an element is a member of a set.
 * False positive matches are possible, but false negatives are not – in other words, a query returns either
 * "possibly in set" or "definitely not in set".
 */
@JsonDeserialize(using = BloomDeserializer::class)
@JsonSerialize(using = BloomSerializer::class)
class Bloom(val value: ByteArray) {
    constructor() : this(ByteArray(BLOOM_SIZE))
    constructor(value: CharSequence) : this(FastHex.decode(value))

    init {
        require(value.size == BLOOM_SIZE) { "Bloom must be exactly $BLOOM_SIZE bytes long" }
    }

    operator fun contains(hash: Hash) = contains(hash.value)

    operator fun contains(address: Address) = contains(address.value)

    operator fun contains(input: ByteArray): Boolean {
        withBloomValues(input) { b1, b2, b3, i1, i2, i3 ->
            if (value[i1] and b1 != b1) return false
            if (value[i2] and b2 != b2) return false
            return value[i3] and b3 == b3
        }
    }

    /**
     * Add [Hash] value to the Bloom filter.
     */
    fun add(hash: Hash) = add(hash.value)

    /**
     * Add [Address] value to the Bloom filter.
     */
    fun add(address: Address) = add(address.value)

    /**
     * Add [ByteArray] value to the Bloom filter.
     */
    fun add(input: ByteArray) {
        withBloomValues(input) { b1, b2, b3, i1, i2, i3 ->
            value[i1] = (value[i1] or b1)
            value[i2] = (value[i2] or b2)
            value[i3] = (value[i3] or b3)
        }
    }

    private inline fun <R> withBloomValues(item: ByteArray, consumer: (Byte, Byte, Byte, Int, Int, Int) -> R): R {
        val hash = Hashing.keccak256(item)
        val b1 = (1 shl (hash[1].toInt() and 0x7)).toByte()
        val b2 = (1 shl (hash[3].toInt() and 0x7)).toByte()
        val b3 = (1 shl (hash[5].toInt() and 0x7)).toByte()

        val i1 = BLOOM_SIZE - (hash.getUShort(0) and 0x7ff shr 3) - 1
        val i2 = BLOOM_SIZE - (hash.getUShort(2) and 0x7ff shr 3) - 1
        val i3 = BLOOM_SIZE - (hash.getUShort(4) and 0x7ff shr 3) - 1

        return consumer(b1, b2, b3, i1, i2, i3)
    }

    private fun ByteArray.getUShort(index: Int): Int {
        return ((this[index].toInt() and 0xff) shl 8) or (this[index + 1].toInt() and 0xff)
    }

    override fun toString(): String {
        return FastHex.encodeWithPrefix(value)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Bloom

        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }
}

private class BloomDeserializer : JsonDeserializer<Bloom>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Bloom {
        return p.readBloom()
    }
}

private class BloomSerializer : JsonSerializer<Bloom>() {
    override fun serialize(value: Bloom, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.toString())
    }
}
