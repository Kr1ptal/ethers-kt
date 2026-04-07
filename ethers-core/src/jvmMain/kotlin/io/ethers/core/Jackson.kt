package io.ethers.core

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import java.io.InputStream
import java.math.BigInteger

/**
 * Static [JsonMapper] with default settings. Instance should be reused. Customized reading/writing can be achieved
 * using ObjectReader/ObjectWriter objects returned by the mapper.
 */
object Jackson {
    @JvmField
    val MAPPER: JsonMapper = JsonMapper.builder()
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        .addModule(
            SimpleModule()
                .addSerializer(ByteArray::class.java, ByteArrayHexSerializer())
                .addDeserializer(ByteArray::class.java, ByteArrayHexDeserializer()),
        )
        .build()

    /**
     * Create [JsonParser] and initialize it for reading.
     */
    fun JsonMapper.createAndInitParser(content: String): JsonParser {
        return this.createParser(content).initForReading()
    }

    /**
     * Create [JsonParser] and initialize it for reading.
     */
    fun JsonMapper.createAndInitParser(stream: InputStream): JsonParser {
        return this.createParser(stream).initForReading()
    }

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
}

/**
 * Serialize and deserialize Int as hex string with 0x prefix.
 * */
@JacksonAnnotationsInside
@JsonSerialize(using = IntHexSerializer::class)
@JsonDeserialize(using = IntHexDeserializer::class)
annotation class JsonHexInt

class IntHexSerializer : JsonSerializer<Int>() {
    override fun serialize(value: Int, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(FastHex.encodeWithPrefix(value))
    }
}

class IntHexDeserializer : JsonDeserializer<Int>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Int {
        return p.readHexInt()
    }
}

/**
 * Serialize and deserialize Long as hex string with 0x prefix.
 * */
@JacksonAnnotationsInside
@JsonSerialize(using = LongHexSerializer::class)
@JsonDeserialize(using = LongHexDeserializer::class)
annotation class JsonHexLong

class LongHexSerializer : JsonSerializer<Long>() {
    override fun serialize(value: Long, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(FastHex.encodeWithPrefix(value))
    }
}

class LongHexDeserializer : JsonDeserializer<Long>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Long {
        return p.readHexLong()
    }
}

/**
 * Serialize and deserialize BigInteger as hex string with 0x prefix.
 * */
@JacksonAnnotationsInside
@JsonSerialize(using = BigIntegerHexSerializer::class)
@JsonDeserialize(using = BigIntegerHexDeserializer::class)
annotation class JsonHexBigInteger

class BigIntegerHexSerializer : JsonSerializer<BigInteger>() {
    override fun serialize(value: BigInteger, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(FastHex.encodeWithPrefix(value))
    }
}

class BigIntegerHexDeserializer : JsonDeserializer<BigInteger>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): BigInteger {
        return p.readHexBigInteger()
    }
}
