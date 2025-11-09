package io.ethers.json.jackson

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import io.ethers.core.FastHex
import io.ethers.core.types.Address
import io.ethers.core.types.Bloom
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import java.math.BigInteger

/**
 * Provides Jackson mapper for backwards compatibility with code that uses Jackson from ethers-core.
 */
object Jackson {
    @JvmField
    val MAPPER: JsonMapper = JsonMapper.builder()
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        .addModule(
            SimpleModule()
                // Primitive hex serializers
                .addSerializer(ByteArray::class.java, ByteArrayHexSerializer())
                .addDeserializer(ByteArray::class.java, ByteArrayHexDeserializer())
                .addSerializer(Int::class.java, IntHexSerializer())
                .addDeserializer(Int::class.javaPrimitiveType, IntHexDeserializer())
                .addDeserializer(Int::class.javaObjectType, IntHexDeserializer())
                .addSerializer(Long::class.java, LongHexSerializer())
                .addDeserializer(Long::class.javaPrimitiveType, LongHexDeserializer())
                .addDeserializer(Long::class.javaObjectType, LongHexDeserializer())
                .addSerializer(BigInteger::class.java, BigIntegerHexSerializer())
                .addDeserializer(BigInteger::class.java, BigIntegerHexDeserializer())
                // Basic Ethereum types
                .addSerializer(Address::class.java, AddressSerializer())
                .addDeserializer(Address::class.java, AddressDeserializer())
                .addSerializer(Hash::class.java, HashSerializer())
                .addDeserializer(Hash::class.java, HashDeserializer())
                .addSerializer(Bytes::class.java, BytesSerializer())
                .addDeserializer(Bytes::class.java, BytesDeserializer())
                .addSerializer(Bloom::class.java, BloomSerializer())
                .addDeserializer(Bloom::class.java, BloomDeserializer()),
        )
        .build()

    private class ByteArrayHexSerializer : StdSerializer<ByteArray>(ByteArray::class.java) {
        override fun serialize(value: ByteArray, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeString(FastHex.encodeWithPrefix(value))
        }
    }

    private class ByteArrayHexDeserializer : StdDeserializer<ByteArray>(ByteArray::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ByteArray {
            return p.readHexByteArray()
        }
    }

    private class IntHexSerializer : StdSerializer<Int>(Int::class.java) {
        override fun serialize(value: Int, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeString(FastHex.encodeWithPrefix(value))
        }
    }

    private class IntHexDeserializer : StdDeserializer<Int>(Int::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Int {
            return p.readHexByteArray().toInt()
        }

        private fun ByteArray.toInt(): Int {
            if (isEmpty()) return 0
            var value = 0
            for (b in this) {
                value = (value shl 8) + (b.toInt() and 255)
            }
            return value
        }
    }

    private class LongHexSerializer : StdSerializer<Long>(Long::class.java) {
        override fun serialize(value: Long, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeString(FastHex.encodeWithPrefix(value))
        }
    }

    private class LongHexDeserializer : StdDeserializer<Long>(Long::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Long {
            return p.readHexByteArray().toLong()
        }

        private fun ByteArray.toLong(): Long {
            if (isEmpty()) return 0L
            var value = 0L
            for (b in this) {
                value = (value shl 8) + (b.toInt() and 255)
            }
            return value
        }
    }

    private class BigIntegerHexSerializer : StdSerializer<BigInteger>(BigInteger::class.java) {
        override fun serialize(value: BigInteger, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeString(FastHex.encodeWithPrefix(value))
        }
    }

    private class BigIntegerHexDeserializer : StdDeserializer<BigInteger>(BigInteger::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): BigInteger {
            val decoded = p.readHexByteArray()
            if (decoded.isEmpty()) {
                return BigInteger.ZERO
            }
            // hex numbers are unsigned
            return BigInteger(1, decoded)
        }
    }

    // Basic Ethereum type serializers
    private class AddressSerializer : JsonSerializer<Address>() {
        override fun serialize(value: Address, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.writeString(value.toString())
        }
    }

    private class AddressDeserializer : JsonDeserializer<Address>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Address {
            return p.readAddress()
        }
    }

    private class HashSerializer : JsonSerializer<Hash>() {
        override fun serialize(value: Hash, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.writeString(value.toString())
        }
    }

    private class HashDeserializer : JsonDeserializer<Hash>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Hash {
            return p.readHash()
        }
    }

    private class BytesSerializer : JsonSerializer<Bytes>() {
        override fun serialize(value: Bytes, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.writeString(value.toString())
        }
    }

    private class BytesDeserializer : JsonDeserializer<Bytes>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Bytes {
            return p.readBytes()
        }
    }

    private class BloomSerializer : JsonSerializer<Bloom>() {
        override fun serialize(value: Bloom, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.writeString(value.toString())
        }
    }

    private class BloomDeserializer : JsonDeserializer<Bloom>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Bloom {
            return p.readBloom()
        }
    }
}
