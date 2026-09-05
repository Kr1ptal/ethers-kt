@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.ethers.solana.types

import io.ethers.core.types.Bytes
import io.ethers.crypto.Base58
import io.github.artificialpb.bignum.BigDecimal
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.io.encoding.Base64

/**
 * Adds forward-compatible fields to a generated object serializer without hand-decoding its properties.
 * Unknown keys are flattened on the wire; they cannot override known properties.
 */
abstract class ExtensibleJsonSerializer<T>(
    private val delegate: KSerializer<T>,
    private val otherFields: (T) -> Map<String, JsonElement>,
) : KSerializer<T> {
    override val descriptor = delegate.descriptor
    private val knownFields = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }.toSet() - "otherFields"

    override fun deserialize(decoder: Decoder): T {
        val input = decoder as JsonDecoder
        val obj = input.decodeJsonElement().jsonObject
        val fields = obj.filterKeys { it in knownFields } +
            ("otherFields" to JsonObject(obj.filterKeys { it !in knownFields }))
        return input.json.decodeFromJsonElement(delegate, JsonObject(fields))
    }

    override fun serialize(encoder: Encoder, value: T) {
        val output = encoder as JsonEncoder
        val extras = otherFields(value)
        require(extras.keys.none { it in knownFields }) { "Unknown fields cannot override known RPC properties" }
        val fields = output.json.encodeToJsonElement(delegate, value).jsonObject - "otherFields"
        output.encodeJsonElement(exactJson(JsonObject(extras + fields)))
    }
}

/** A Rust externally tagged enum variant, delegating the payload to its own serializer. */
abstract class TaggedJsonSerializer<T>(private val tag: String, delegate: KSerializer<T>) : JsonTransformingSerializer<T>(delegate) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val obj = element.jsonObject
        require(obj.size == 1 && tag in obj) { "Expected error variant $tag" }
        return obj.getValue(tag)
    }
    override fun transformSerialize(element: JsonElement): JsonElement = JsonObject(mapOf(tag to element))
}

open class MappedSerializer<W, T>(private val wire: KSerializer<W>, private val fromWire: (W) -> T, private val toWire: (T) -> W) : KSerializer<T> {
    override val descriptor = wire.descriptor
    override fun deserialize(decoder: Decoder): T = fromWire(decoder.decodeSerializableValue(wire))
    override fun serialize(encoder: Encoder, value: T) = encoder.encodeSerializableValue(wire, toWire(value))
}

object ExactJsonSerializer : KSerializer<JsonElement> {
    override val descriptor = JsonElement.serializer().descriptor
    override fun deserialize(decoder: Decoder): JsonElement = (decoder as JsonDecoder).decodeJsonElement()
    override fun serialize(encoder: Encoder, value: JsonElement) = (encoder as JsonEncoder).encodeJsonElement(exactJson(value))
}

object OtherFieldsSerializer : KSerializer<Map<String, JsonElement>> by kotlinx.serialization.builtins.MapSerializer(String.serializer(), ExactJsonSerializer)
object U8ListSerializer : KSerializer<List<Int>> by kotlinx.serialization.builtins.ListSerializer(U8Serializer)

/** Preserve decimal literals in unknown fields without passing them through Double. */
internal fun exactJson(value: JsonElement): JsonElement = when (value) {
    is JsonObject -> JsonObject(value.mapValues { exactJson(it.value) })
    is JsonArray -> JsonArray(value.map(::exactJson))
    is JsonPrimitive -> if (value.isString || value == JsonNull || value.content == "true" || value.content == "false") value else JsonUnquotedLiteral(value.content)
}

object Base58BytesSerializer : KSerializer<Bytes> {
    override val descriptor = PrimitiveSerialDescriptor("SolanaBase58Bytes", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Bytes = Bytes(Base58.decode(decoder.decodeString()))
    override fun serialize(encoder: Encoder, value: Bytes) = encoder.encodeString(Base58.encode(value.asByteArray()))
}

/** Solana's [base64 data, encoding] tuple, shared by accounts and program return data. */
object Base64BytesSerializer : KSerializer<Bytes> {
    override val descriptor = kotlinx.serialization.builtins.ListSerializer(String.serializer()).descriptor
    override fun deserialize(decoder: Decoder): Bytes {
        val data = (decoder as JsonDecoder).decodeJsonElement().jsonArray
        require(data.size == 2 && data.all { it is JsonPrimitive && it.isString } && data[1].jsonPrimitive.content == "base64") { "Expected [data, base64]" }
        return Bytes(Base64.decode(data[0].jsonPrimitive.content))
    }
    override fun serialize(encoder: Encoder, value: Bytes) = (encoder as JsonEncoder).encodeJsonElement(JsonArray(listOf(JsonPrimitive(Base64.encode(value.asByteArray())), JsonPrimitive("base64"))))
}

object DecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("SolanaDecimal", PrimitiveKind.DOUBLE)
    override fun deserialize(decoder: Decoder): BigDecimal {
        val value = (decoder as JsonDecoder).decodeJsonElement().jsonPrimitive
        require(!value.isString && value != JsonNull) { "Expected a decimal JSON number" }
        return BigDecimal(value.content)
    }
    override fun serialize(encoder: Encoder, value: BigDecimal) = (encoder as JsonEncoder).encodeJsonElement(JsonUnquotedLiteral(value.toString()))
}

object U8Serializer : KSerializer<Int> {
    override val descriptor = PrimitiveSerialDescriptor("SolanaU8", PrimitiveKind.INT)
    override fun deserialize(decoder: Decoder): Int {
        val value = (decoder as JsonDecoder).decodeJsonElement().jsonPrimitive
        require(!value.isString) { "Expected an unsigned byte number" }
        return value.int.also { require(it in 0..255) { "Expected an unsigned byte" } }
    }
    override fun serialize(encoder: Encoder, value: Int) {
        require(value in 0..255) { "Expected an unsigned byte" }
        encoder.encodeInt(value)
    }
}

object U32Serializer : KSerializer<Long> {
    override val descriptor = PrimitiveSerialDescriptor("SolanaU32", PrimitiveKind.LONG)
    override fun deserialize(decoder: Decoder): Long {
        val value = (decoder as JsonDecoder).decodeJsonElement().jsonPrimitive
        require(!value.isString) { "Expected an unsigned 32-bit number" }
        return value.long.also { require(it in 0..4294967295L) { "Expected an unsigned 32-bit number" } }
    }
    override fun serialize(encoder: Encoder, value: Long) {
        require(value in 0..4294967295L) { "Expected an unsigned 32-bit number" }
        encoder.encodeLong(value)
    }
}
