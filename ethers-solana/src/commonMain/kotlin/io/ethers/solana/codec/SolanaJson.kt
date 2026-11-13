@file:OptIn(ExperimentalSerializationApi::class)

package io.ethers.solana.codec

import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The JSON form of a value of a generated program binding, entirely separate from its binary [SolanaCodec].
 *
 * Program bindings generated with kotlinx.serialization support enabled give each struct, enum and event a nested
 * `JsonSerializer` object extending this class, and annotate the type with `@Serializable(with = ...)`, so with the
 * kotlinx.serialization compiler plugin applied `Json.encodeToString(value)` finds it. Without the plugin, pass the
 * serializer explicitly: `Json.encodeToString(Type.JsonSerializer, value)`.
 *
 * The representation is JSON only, and lossless, see [SolanaJson]. Unknown object keys are rejected unless the [Json]
 * instance ignores unknown keys.
 */
abstract class SolanaJsonSerializer<T>(serialName: String) : KSerializer<T> {
    final override val descriptor: SerialDescriptor = SerialDescriptor(serialName, JsonElement.serializer().descriptor)

    /** The JSON form of [value]. A value its IDL type cannot represent, such as an out of range integer, throws. */
    abstract fun toJson(value: T): JsonElement

    /**
     * The value [element] represents. Invalid input throws [SolanaJsonException], whose path locates the offending
     * value. Object keys the type does not declare are rejected unless [ignoreUnknownKeys].
     */
    abstract fun fromJson(element: JsonElement, ignoreUnknownKeys: Boolean): T

    /** The value [element] represents, rejecting object keys the type does not declare. */
    fun fromJson(element: JsonElement): T = fromJson(element, false)

    final override fun serialize(encoder: Encoder, value: T) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("${descriptor.serialName} can only be encoded as JSON")
        json.encodeJsonElement(toJson(value))
    }

    final override fun deserialize(decoder: Decoder): T {
        val json = decoder as? JsonDecoder ?: throw SerializationException("${descriptor.serialName} can only be decoded from JSON")
        return fromJson(json.decodeJsonElement(), json.json.configuration.ignoreUnknownKeys)
    }
}

/**
 * The JSON form of an event, which holds the payload fields only, as the binary encoding does. [context] is not
 * represented, so [fromJson] gives each decoded event a standalone context of [programId], while [withContext] decodes
 * with a context of the caller's choosing.
 */
abstract class SolanaEventJsonSerializer<T : SolanaEvent>(serialName: String, private val programId: SolanaAddress) : SolanaJsonSerializer<T>(serialName) {
    /** The event [element] represents, emitted in [context]. */
    abstract fun fromJson(element: JsonElement, ignoreUnknownKeys: Boolean, context: SolanaEventContext): T

    override fun fromJson(element: JsonElement, ignoreUnknownKeys: Boolean): T = fromJson(element, ignoreUnknownKeys, SolanaEventContext(programId))

    /** A serializer of the same JSON form, which gives each decoded event [context]. */
    fun withContext(context: SolanaEventContext): SolanaJsonSerializer<T> {
        val events = this
        return object : SolanaJsonSerializer<T>(descriptor.serialName) {
            override fun toJson(value: T): JsonElement = events.toJson(value)
            override fun fromJson(element: JsonElement, ignoreUnknownKeys: Boolean): T = events.fromJson(element, ignoreUnknownKeys, context)
        }
    }
}

/** Invalid JSON for a program value. [path] locates it within the decoded value, such as `config.weights[2]`. */
class SolanaJsonException(val path: String, val reason: String, cause: Throwable? = null) :
    SerializationException(if (path.isEmpty()) reason else "$path: $reason", cause)

/**
 * Conversions between IDL values and JSON, which generated `JsonSerializer`s are built from.
 *
 * The representation, which never routes a value through a JSON double:
 *
 * - `bool`: a JSON boolean.
 * - `u8`, `u16`, `u32`, `i8`, `i16`, `i32`: a JSON integer, which any JSON parser reads exactly.
 * - `u64`, `i64`, `u128`, `i128`, `u256`, `i256`: a canonical decimal string, such as `"-42"`, since these exceed the
 *   integers a double holds exactly. A JSON number, a leading `+` or zero, or `-0` is rejected.
 * - `f32`, `f64`: a JSON number, written with the fewest digits that read back as the same value, or the string
 *   `"NaN"`, `"Infinity"` or `"-Infinity"`, which JSON numbers cannot represent.
 * - `string`: a JSON string.
 * - `pubkey`: a base58 string.
 * - `bytes` and `[u8; N]`: a standard, padded base64 string. A fixed array must decode to exactly N bytes.
 *   `Vec<u8>` is an array of integers, like every other `Vec`.
 * - `Vec<T>` and `[T; N]`: a JSON array. A fixed array must have exactly N elements.
 * - `Option<T>`: `null` for none, otherwise the value. A struct field whose key is absent is none as well. A nested
 *   option, `Option<Option<T>>`, writes the outer some as a one-element array, so `Some(None)` is `[null]` and
 *   `Some(Some(1))` is `[1]`.
 * - A struct with named fields: an object keyed by the field names exactly as the IDL declares them, in declaration
 *   order. A tuple struct: an array of its fields. A struct without fields: `{}`.
 * - An enum, externally tagged by the variant name the IDL declares: a variant without fields is the string `"Name"`,
 *   one with named fields `{"Name": {...}}`, and a tuple variant `{"Name": [...]}`. Variants of the same shape stay
 *   distinct.
 * - A type alias: its target's form. Zero-copy padding is not represented.
 */
object SolanaJson {
    private val INTEGER = Regex("-?(0|[1-9][0-9]*)")
    private val NUMBER = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
    private val EMPTY_OBJECT = JsonObject(emptyMap())

    // --- structure ---

    /** Run [decode] for the value at [segment] of the current path, a field name or an `[index]`. */
    fun <T> at(segment: String, decode: () -> T): T = try {
        decode()
    } catch (e: SolanaJsonException) {
        val path = when {
            e.path.isEmpty() -> segment
            e.path.startsWith("[") -> segment + e.path
            else -> "$segment.${e.path}"
        }
        throw SolanaJsonException(path, e.reason, e.cause)
    } catch (e: IllegalArgumentException) {
        throw SolanaJsonException(segment, e.message ?: "invalid value", e)
    }

    /** Encode a struct's [fields], in order. */
    fun encodeStruct(vararg fields: Pair<String, JsonElement>): JsonObject {
        if (fields.isEmpty()) return EMPTY_OBJECT
        val map = LinkedHashMap<String, JsonElement>(fields.size * 2)
        for ((name, value) in fields) map[name] = value
        return JsonObject(map)
    }

    /** [element] as the object of struct [type], whose fields are [names]. Unknown keys fail unless [ignoreUnknownKeys]. */
    fun decodeStruct(element: JsonElement, type: String, names: Set<String>, ignoreUnknownKeys: Boolean): JsonObject {
        val obj = element as? JsonObject ?: fail("Expected a JSON object for $type, got ${describe(element)}")
        if (!ignoreUnknownKeys) {
            for (key in obj.keys) if (key !in names) fail("Unknown field '$key' of $type")
        }
        return obj
    }

    /** Decode field [name] of [fields]. A missing [optional] field decodes as `null`. */
    fun <T> field(fields: JsonObject, name: String, optional: Boolean, decode: (JsonElement) -> T): T {
        val element = fields[name] ?: if (optional) JsonNull else throw SolanaJsonException(name, "Missing field")
        return at(name) { decode(element) }
    }

    /** Encode a tuple's [fields], in order. */
    fun encodeTuple(vararg fields: JsonElement): JsonArray = JsonArray(fields.asList())

    /** [element] as the array of tuple [type], which has [size] fields. */
    fun decodeTuple(element: JsonElement, type: String, size: Int): JsonArray {
        val array = element as? JsonArray ?: fail("Expected a JSON array for $type, got ${describe(element)}")
        if (array.size != size) fail("Expected $size fields for $type, got ${array.size}")
        return array
    }

    /** Decode the element at [index] of [elements]. */
    fun <T> element(elements: JsonArray, index: Int, decode: (JsonElement) -> T): T = at("[$index]") { decode(elements[index]) }

    /** Encode enum variant [name] without fields. */
    fun encodeVariant(name: String): JsonElement = JsonPrimitive(name)

    /** Encode enum variant [name] with its fields, [payload]. */
    fun encodeVariant(name: String, payload: JsonElement): JsonElement = JsonObject(mapOf(name to payload))

    /** The variant of enum [type] that [element] encodes: its name, and its fields, or null for a bare name. */
    fun decodeVariant(element: JsonElement, type: String): Variant {
        if (element is JsonPrimitive && element.isString) return Variant(type, element.content, null)
        if (element is JsonObject && element.size == 1) {
            val (name, payload) = element.entries.first()
            return Variant(type, name, payload)
        }
        fail("Expected a variant name or an object with a single variant key for $type, got ${describe(element)}")
    }

    /** An enum variant as decoded from JSON, see [decodeVariant]. */
    class Variant internal constructor(private val type: String, val name: String, private val payload: JsonElement?) {
        /** The variant [value], which must have been a bare name. */
        fun <T> unit(value: T): T {
            if (payload != null) throw SolanaJsonException(name, "Variant $name of $type has no fields, so is written as the string \"$name\"")
            return value
        }

        /** Decode the variant's fields with [decode]. */
        fun <T> fields(decode: (JsonElement) -> T): T {
            val fields = payload ?: fail("Variant $name of $type has fields, so is written as {\"$name\": ...}")
            return at(name) { decode(fields) }
        }

        /** Fail for a variant [type] does not declare. */
        fun unknown(): Nothing = fail("Unknown variant '$name' of $type")
    }

    // --- options and sequences ---

    fun <T : Any> encodeOption(value: T?, encode: (T) -> JsonElement): JsonElement = if (value == null) JsonNull else encode(value)

    fun <T : Any> decodeOption(element: JsonElement, decode: (JsonElement) -> T): T? = if (element is JsonNull) null else decode(element)

    /** Encode a nested option: [SolanaOption.Some] as a one-element array, since its value may itself be `null`. */
    fun <T> encodeExplicitOption(value: SolanaOption<T>, encode: (T) -> JsonElement): JsonElement = when (value) {
        SolanaOption.None -> JsonNull
        is SolanaOption.Some -> JsonArray(listOf(encode(value.value)))
    }

    fun <T> decodeExplicitOption(element: JsonElement, decode: (JsonElement) -> T): SolanaOption<T> {
        if (element is JsonNull) return SolanaOption.None
        val array = element as? JsonArray
        if (array == null || array.size != 1) fail("Expected null or a one-element array for a nested option, got ${describe(element)}")
        return SolanaOption.Some(element(array, 0, decode))
    }

    fun <T> encodeVec(values: List<T>, encode: (T) -> JsonElement): JsonArray = JsonArray(values.map(encode))

    fun <T> decodeVec(element: JsonElement, decode: (JsonElement) -> T): List<T> {
        val array = element as? JsonArray ?: fail("Expected a JSON array, got ${describe(element)}")
        return List(array.size) { element(array, it, decode) }
    }

    fun <T> encodeArray(values: List<T>, size: Int, encode: (T) -> JsonElement): JsonArray {
        require(values.size == size) { "Expected $size elements, got ${values.size}" }
        return encodeVec(values, encode)
    }

    fun <T> decodeArray(element: JsonElement, size: Int, decode: (JsonElement) -> T): List<T> {
        val array = element as? JsonArray ?: fail("Expected a JSON array, got ${describe(element)}")
        if (array.size != size) fail("Expected $size elements, got ${array.size}")
        return List(size) { element(array, it, decode) }
    }

    // --- primitives ---

    fun encodeBool(value: Boolean): JsonElement = JsonPrimitive(value)

    fun decodeBool(element: JsonElement): Boolean {
        val primitive = element as? JsonPrimitive
        return when {
            primitive == null || primitive.isString -> null
            primitive.content == "true" -> true
            primitive.content == "false" -> false
            else -> null
        } ?: fail("Expected a JSON boolean, got ${describe(element)}")
    }

    fun encodeU8(value: Int): JsonElement = JsonPrimitive(checkRange(value.toLong(), 0, 255, "u8"))
    fun encodeU16(value: Int): JsonElement = JsonPrimitive(checkRange(value.toLong(), 0, 65535, "u16"))
    fun encodeU32(value: Long): JsonElement = JsonPrimitive(checkRange(value, 0, 4294967295L, "u32"))
    fun encodeI8(value: Byte): JsonElement = JsonPrimitive(value)
    fun encodeI16(value: Short): JsonElement = JsonPrimitive(value)
    fun encodeI32(value: Int): JsonElement = JsonPrimitive(value)

    fun decodeU8(element: JsonElement): Int = smallInteger(element, 0, 255, "u8").toInt()
    fun decodeU16(element: JsonElement): Int = smallInteger(element, 0, 65535, "u16").toInt()
    fun decodeU32(element: JsonElement): Long = smallInteger(element, 0, 4294967295L, "u32")
    fun decodeI8(element: JsonElement): Byte = smallInteger(element, Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong(), "i8").toByte()
    fun decodeI16(element: JsonElement): Short = smallInteger(element, Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong(), "i16").toShort()
    fun decodeI32(element: JsonElement): Int = smallInteger(element, Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong(), "i32").toInt()

    fun encodeI64(value: Long): JsonElement = JsonPrimitive(value.toString())

    fun decodeI64(element: JsonElement): Long = decimal(element, "i64").toLongOrNull() ?: fail("Value is out of range for i64")

    fun encodeU64(value: BigInteger): JsonElement = encodeBig(value, 64, false)
    fun encodeU128(value: BigInteger): JsonElement = encodeBig(value, 128, false)
    fun encodeU256(value: BigInteger): JsonElement = encodeBig(value, 256, false)
    fun encodeI128(value: BigInteger): JsonElement = encodeBig(value, 128, true)
    fun encodeI256(value: BigInteger): JsonElement = encodeBig(value, 256, true)

    fun decodeU64(element: JsonElement): BigInteger = decodeBig(element, 64, false)
    fun decodeU128(element: JsonElement): BigInteger = decodeBig(element, 128, false)
    fun decodeU256(element: JsonElement): BigInteger = decodeBig(element, 256, false)
    fun decodeI128(element: JsonElement): BigInteger = decodeBig(element, 128, true)
    fun decodeI256(element: JsonElement): BigInteger = decodeBig(element, 256, true)

    fun encodeF32(value: Float): JsonElement = if (value.isFinite()) JsonPrimitive(value) else JsonPrimitive(value.toString())
    fun encodeF64(value: Double): JsonElement = if (value.isFinite()) JsonPrimitive(value) else JsonPrimitive(value.toString())

    fun decodeF32(element: JsonElement): Float {
        nonFinite(element, "f32")?.let { return it.toFloat() }
        // a number beyond the range reads as infinity, which the JSON did not say
        return number(element, "f32").toFloat().also { if (it.isInfinite()) fail("Value is out of range for f32") }
    }

    fun decodeF64(element: JsonElement): Double {
        nonFinite(element, "f64")?.let { return it }
        return number(element, "f64").toDouble().also { if (it.isInfinite()) fail("Value is out of range for f64") }
    }

    fun encodeString(value: String): JsonElement = JsonPrimitive(value)

    fun decodeString(element: JsonElement): String = string(element, "a JSON string")

    fun encodeAddress(value: SolanaAddress): JsonElement = JsonPrimitive(value.toBase58())

    fun decodeAddress(element: JsonElement): SolanaAddress {
        val base58 = string(element, "a base58 public key")
        return try {
            SolanaAddress(base58)
        } catch (e: IllegalArgumentException) {
            throw SolanaJsonException("", "Invalid base58 public key: ${e.message}", e)
        }
    }

    fun encodeBytes(value: SolanaBytes): JsonElement = JsonPrimitive(value.toBase64())

    fun decodeBytes(element: JsonElement): SolanaBytes {
        val base64 = string(element, "a base64 string")
        return try {
            SolanaBytes.fromBase64(base64)
        } catch (e: IllegalArgumentException) {
            throw SolanaJsonException("", "Invalid base64: ${e.message}", e)
        }
    }

    fun encodeFixedBytes(value: SolanaBytes, size: Int): JsonElement {
        require(value.size == size) { "Expected $size bytes, got ${value.size}" }
        return encodeBytes(value)
    }

    fun decodeFixedBytes(element: JsonElement, size: Int): SolanaBytes {
        val bytes = decodeBytes(element)
        if (bytes.size != size) fail("Expected $size bytes, got ${bytes.size}")
        return bytes
    }

    // --- helpers ---

    private fun fail(reason: String): Nothing = throw SolanaJsonException("", reason)

    private fun describe(element: JsonElement): String = when (element) {
        is JsonNull -> "null"
        is JsonObject -> "an object"
        is JsonArray -> "an array"
        is JsonPrimitive -> if (element.isString) "a string" else element.content
    }

    private fun checkRange(value: Long, min: Long, max: Long, type: String): Long {
        require(value in min..max) { "Value $value is out of range for $type" }
        return value
    }

    private fun smallInteger(element: JsonElement, min: Long, max: Long, type: String): Long {
        val primitive = element as? JsonPrimitive
        if (primitive == null || primitive.isString || !INTEGER.matches(primitive.content)) fail("Expected a JSON integer for $type, got ${describe(element)}")
        val value = primitive.content.toLongOrNull()
        if (value == null || value !in min..max) fail("Value ${primitive.content} is out of range for $type")
        return value
    }

    private fun string(element: JsonElement, expected: String): String {
        val primitive = element as? JsonPrimitive
        if (primitive == null || !primitive.isString) fail("Expected $expected, got ${describe(element)}")
        return primitive.content
    }

    private fun decimal(element: JsonElement, type: String): String {
        val primitive = element as? JsonPrimitive
        if (primitive == null || !primitive.isString) fail("Expected a decimal string for $type, got ${describe(element)}")
        val content = primitive.content
        if (!INTEGER.matches(content) || content == "-0") fail("Expected a canonical decimal string for $type, got \"$content\"")
        return content
    }

    private fun encodeBig(value: BigInteger, bits: Int, signed: Boolean): JsonElement {
        require(fits(value, bits, signed)) { "Value $value is out of range for ${if (signed) "i" else "u"}$bits" }
        return JsonPrimitive(value.toString())
    }

    private fun decodeBig(element: JsonElement, bits: Int, signed: Boolean): BigInteger {
        val value = BigInteger(decimal(element, "${if (signed) "i" else "u"}$bits"))
        if (!fits(value, bits, signed)) fail("Value $value is out of range for ${if (signed) "i" else "u"}$bits")
        return value
    }

    private fun fits(value: BigInteger, bits: Int, signed: Boolean): Boolean = if (signed) value.bitLength() < bits else value.signum() >= 0 && value.bitLength() <= bits

    private fun nonFinite(element: JsonElement, type: String): Double? {
        val primitive = element as? JsonPrimitive
        if (primitive == null || !primitive.isString) return null
        return when (primitive.content) {
            "NaN" -> Double.NaN
            "Infinity" -> Double.POSITIVE_INFINITY
            "-Infinity" -> Double.NEGATIVE_INFINITY
            else -> fail("Expected a JSON number, \"NaN\", \"Infinity\" or \"-Infinity\" for $type, got \"${primitive.content}\"")
        }
    }

    private fun number(element: JsonElement, type: String): String {
        val primitive = element as? JsonPrimitive
        if (primitive == null || !NUMBER.matches(primitive.content)) fail("Expected a JSON number for $type, got ${describe(element)}")
        return primitive.content
    }
}
