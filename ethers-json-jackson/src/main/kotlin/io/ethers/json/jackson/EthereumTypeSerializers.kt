package io.ethers.json.jackson

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import io.ethers.core.types.Address
import io.ethers.core.types.Bloom
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash

/**
 * Jackson serializers and deserializers for Ethereum core types.
 * These are registered globally in JacksonCodec.
 */

// Address
internal class AddressDeserializer : JsonDeserializer<Address>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Address {
        return p.readAddress()
    }
}

internal class AddressSerializer : JsonSerializer<Address>() {
    override fun serialize(value: Address, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.toString())
    }
}

// Hash
internal class HashDeserializer : JsonDeserializer<Hash>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Hash {
        return p.readHash()
    }
}

internal class HashSerializer : JsonSerializer<Hash>() {
    override fun serialize(value: Hash, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.toString())
    }
}

// Bytes
internal class BytesDeserializer : JsonDeserializer<Bytes>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Bytes {
        return p.readBytes()
    }
}

internal class BytesSerializer : JsonSerializer<Bytes>() {
    override fun serialize(value: Bytes, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.toString())
    }
}

// Bloom
internal class BloomDeserializer : JsonDeserializer<Bloom>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Bloom {
        return p.readBloom()
    }
}

internal class BloomSerializer : JsonSerializer<Bloom>() {
    override fun serialize(value: Bloom, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.toString())
    }
}
