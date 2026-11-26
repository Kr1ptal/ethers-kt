package io.ethers.providers.types

import io.ethers.core.FastHex
import io.ethers.core.Kotlinx
import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.serializerOrNull

/**
 * Convert a value to JSON by its runtime type. Used for [RpcParams] whose static type has no serializer (`Any`, maps of
 * mixed values) and for [RpcParams.ofDynamic].
 */
@Suppress("UNCHECKED_CAST")
@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
internal fun Any?.toDynamicJson(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Byte -> JsonPrimitive(this)
    is Short -> JsonPrimitive(this)
    is Int -> JsonPrimitive(this)
    is Long -> JsonPrimitive(this)
    is Float -> JsonPrimitive(this)
    is Double -> JsonPrimitive(this)
    // arbitrary-precision numbers are written as unquoted JSON number literals, so they keep full precision while
    // still being typed as numbers (JSON-RPC clients reject a quoted string where the spec defines a number)
    is BigInteger -> JsonUnquotedLiteral(this.toString())
    is BigDecimal -> JsonUnquotedLiteral(this.toPlainString())
    is ByteArray -> JsonPrimitive(FastHex.encodeWithPrefix(this))
    is Array<*> -> JsonArray(this.map { it.toDynamicJson() })
    is Iterable<*> -> JsonArray(this.map { it.toDynamicJson() })
    is Map<*, *> -> JsonObject(this.entries.associate { (k, v) -> k.toString() to v.toDynamicJson() })
    else -> {
        val ser = lookupSerializer(this) ?: throw IllegalArgumentException(
            // qualifiedName is null for local and anonymous classes, so fall back to simpleName to keep the
            // type identifiable in the message
            "Cannot serialize JSON value of type ${this::class.qualifiedName ?: this::class.simpleName}: " +
                "it has no kotlinx @Serializable serializer (an annotated class gets one only if the module declaring " +
                "it applies the kotlinx serialization plugin), or it is a generic class, which can't be encoded from " +
                "a runtime value. Pass it with its static type or serializer, or as a JsonElement.",
        )
        Kotlinx.DEFAULT.encodeToJsonElement(ser, this)
    }
}

@Suppress("UNCHECKED_CAST")
@OptIn(InternalSerializationApi::class)
private fun lookupSerializer(value: Any): KSerializer<Any>? = try {
    value::class.serializerOrNull() as KSerializer<Any>?
} catch (_: IndexOutOfBoundsException) {
    // on Kotlin/Native a generic class's generated serializer factory reads type argument serializers that a class
    // lookup can't supply, and throws instead of returning null like the JVM does
    null
}
