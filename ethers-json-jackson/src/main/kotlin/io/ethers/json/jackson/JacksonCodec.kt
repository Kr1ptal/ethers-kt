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
import io.ethers.json.JsonCodec
import java.io.InputStream
import java.math.BigInteger

/**
 * Jackson implementation of [JsonCodec].
 */
class JacksonCodec : JsonCodec {
    internal val mapper: JsonMapper = JsonMapper.builder()
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
                // Ethereum type serializers
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

    override fun <T> encode(value: T): String {
        return mapper.writeValueAsString(value)
    }

    override fun <T> encodeToBytes(value: T): ByteArray {
        return mapper.writeValueAsBytes(value)
    }

    override fun <T> decode(json: String, type: Class<T>): T {
        return mapper.readValue(json, type)
    }

    override fun <T> decode(json: ByteArray, type: Class<T>): T {
        return mapper.readValue(json, type)
    }

    override fun <T> decode(json: InputStream, type: Class<T>): T {
        return mapper.readValue(json, type)
    }

    // Serializers
    private class ByteArrayHexSerializer : StdSerializer<ByteArray>(ByteArray::class.java) {
        override fun serialize(value: ByteArray, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeString(FastHex.encodeWithPrefix(value))
        }
    }

    private class IntHexSerializer : StdSerializer<Int>(Int::class.java) {
        override fun serialize(value: Int, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeString(FastHex.encodeWithPrefix(value))
        }
    }

    private class LongHexSerializer : StdSerializer<Long>(Long::class.java) {
        override fun serialize(value: Long, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeString(FastHex.encodeWithPrefix(value))
        }
    }

    private class BigIntegerHexSerializer : StdSerializer<BigInteger>(BigInteger::class.java) {
        override fun serialize(value: BigInteger, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeString(FastHex.encodeWithPrefix(value))
        }
    }

    // Deserializers
    private class ByteArrayHexDeserializer : StdDeserializer<ByteArray>(ByteArray::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ByteArray {
            return readHexByteArray(p)
        }
    }

    private class IntHexDeserializer : StdDeserializer<Int>(Int::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Int {
            return readHexByteArray(p).toInt()
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

    private class LongHexDeserializer : StdDeserializer<Long>(Long::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Long {
            return readHexByteArray(p).toLong()
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

    private class BigIntegerHexDeserializer : StdDeserializer<BigInteger>(BigInteger::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): BigInteger {
            val decoded = readHexByteArray(p)
            if (decoded.isEmpty()) {
                return BigInteger.ZERO
            }
            // hex numbers are unsigned
            return BigInteger(1, decoded)
        }
    }

    companion object {
        private val EMPTY_BYTES = ByteArray(0)

        /**
         * Uses most optimal way to read and decode hex string from [JsonParser].
         */
        internal fun readHexByteArray(p: JsonParser): ByteArray {
            if (!p.hasTextCharacters()) {
                val text = p.text
                if (text.isEmpty()) {
                    return EMPTY_BYTES
                }
                if (text.length == 2 && (text == "0x" || text == "0X")) {
                    return EMPTY_BYTES
                }
                return FastHex.decode(text)
            }

            val len = p.textLength
            if (len == 0) {
                return EMPTY_BYTES
            }

            val chars = p.textCharacters
            val offset = p.textOffset
            if (len == 2 && chars[offset] == '0' && (chars[offset + 1] == 'x' || (chars[offset + 1] == 'X'))) {
                return EMPTY_BYTES
            }

            return FastHex.decode(chars, offset, len)
        }
    }
}

/**
 * Read hex-encoded byte array from JsonParser. This is exposed for internal use by deprecated code.
 */
internal fun readHexByteArray(p: JsonParser): ByteArray = JacksonCodec.readHexByteArray(p)
