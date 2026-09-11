@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.ethers.solana.types

import io.ethers.solana.utils.U32_MAX
import io.ethers.solana.utils.requireU64
import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.BigInteger
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
import io.ethers.core.json.JsonElement as RawJson

/**
 * Adds forward-compatible fields to a generated object serializer without hand-decoding its properties.
 * Unknown keys are flattened on the wire; they cannot override known properties.
 */
abstract class ExtensibleJsonSerializer<T>(
    private val delegate: KSerializer<T>,
    private val otherFields: (T) -> Map<String, RawJson>,
) : KSerializer<T> {
    override val descriptor = delegate.descriptor
    private val knownFields = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }.toSet() - OTHER_FIELDS

    override fun deserialize(decoder: Decoder): T {
        val input = decoder as JsonDecoder
        val obj = input.decodeJsonElement().jsonObject

        // split in one pass, allocating the unknown map only for responses that carry unknown fields,
        // which is none of them until the cluster is upgraded past this library
        val fields = LinkedHashMap<String, JsonElement>(obj.size + 1)
        var unknown: MutableMap<String, JsonElement>? = null
        for ((key, element) in obj) {
            if (key in knownFields) {
                fields[key] = element
            } else {
                var extras = unknown
                if (extras == null) {
                    extras = LinkedHashMap()
                    unknown = extras
                }
                extras[key] = element
            }
        }
        fields[OTHER_FIELDS] = unknown?.let(::JsonObject) ?: EMPTY_OTHER_FIELDS
        return input.json.decodeFromJsonElement(delegate, JsonObject(fields))
    }

    override fun serialize(encoder: Encoder, value: T) {
        val output = encoder as JsonEncoder
        val extras = otherFields(value)
        val encoded = output.json.encodeToJsonElement(delegate, value).jsonObject

        // an empty map is the property's default, which the encoder omits, so there is nothing to
        // strip and nothing to merge
        if (extras.isEmpty() && !encoded.containsKey(OTHER_FIELDS)) {
            output.encodeJsonElement(encoded)
            return
        }

        val fields = LinkedHashMap<String, JsonElement>(encoded.size + extras.size)
        for ((key, element) in extras) {
            require(key !in knownFields) { "Unknown fields cannot override known RPC properties" }
            // the unknown value is text already, so it is written back exactly as it arrived
            fields[key] = if (element.toString() == "null") JsonNull else JsonUnquotedLiteral(element.toString())
        }
        for ((key, element) in encoded) {
            if (key != OTHER_FIELDS) fields[key] = element
        }
        output.encodeJsonElement(JsonObject(fields))
    }

    private companion object {
        const val OTHER_FIELDS = "otherFields"
        val EMPTY_OTHER_FIELDS = JsonObject(emptyMap())
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

/**
 * A JSON value this library does not model, kept as the text it arrived as.
 *
 * Holding the raw text rather than a parsed tree is what keeps unknown numbers intact: re-encoding a
 * parsed literal routes it through Long or Double, which rounds long decimals and rejects values
 * outside Double's range. This is the same representation the EVM types use for their unknown fields.
 */
object RawJsonSerializer : KSerializer<RawJson> {
    override val descriptor = PrimitiveSerialDescriptor("SolanaRawJson", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): RawJson = RawJson((decoder as JsonDecoder).decodeJsonElement().toString())
    override fun serialize(encoder: Encoder, value: RawJson) {
        val json = value.toString()
        (encoder as JsonEncoder).encodeJsonElement(if (json == "null") JsonNull else JsonUnquotedLiteral(json))
    }
}

object OtherFieldsSerializer : KSerializer<Map<String, RawJson>> by kotlinx.serialization.builtins.MapSerializer(String.serializer(), RawJsonSerializer)
object U8ListSerializer : KSerializer<List<Int>> by kotlinx.serialization.builtins.ListSerializer(U8Serializer)

/** Solana's [base64 data, encoding] tuple, shared by accounts and program return data. */
object Base64TupleBytesSerializer : KSerializer<SolanaBytes> {
    override val descriptor = kotlinx.serialization.builtins.ListSerializer(String.serializer()).descriptor
    override fun deserialize(decoder: Decoder): SolanaBytes {
        val data = (decoder as JsonDecoder).decodeJsonElement().jsonArray
        require(data.size == 2 && data.all { it is JsonPrimitive && it.isString } && data[1].jsonPrimitive.content == "base64") { "Expected [data, base64]" }
        return SolanaBytes.fromBase64(data[0].jsonPrimitive.content)
    }
    override fun serialize(encoder: Encoder, value: SolanaBytes) = (encoder as JsonEncoder).encodeJsonElement(JsonArray(listOf(JsonPrimitive(value.toBase64()), JsonPrimitive("base64"))))
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
        return value.long.also { require(it in 0..U32_MAX) { "Expected an unsigned 32-bit number" } }
    }
    override fun serialize(encoder: Encoder, value: Long) {
        require(value in 0..U32_MAX) { "Expected an unsigned 32-bit number" }
        encoder.encodeLong(value)
    }
}

/** Decimal JSON number, unlike ethers-core's hexadecimal Ethereum quantities. */
object U64Serializer : KSerializer<BigInteger> {
    override val descriptor = PrimitiveSerialDescriptor("SolanaU64", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: BigInteger) {
        val checked = requireU64(value)
        (encoder as JsonEncoder).encodeJsonElement(JsonUnquotedLiteral(checked.toString()))
    }
    override fun deserialize(decoder: Decoder): BigInteger {
        val primitive = (decoder as JsonDecoder).decodeJsonElement().jsonPrimitive
        require(!primitive.isString) { "Expected a decimal JSON number" }
        return requireU64(BigInteger(primitive.content))
    }
}
