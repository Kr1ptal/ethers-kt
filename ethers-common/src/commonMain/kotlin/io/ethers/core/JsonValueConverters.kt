package io.ethers.core

import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.serializerOrNull

/**
 * A value that names its own serializer, which [toJsonElement] uses instead of finding one by class lookup.
 *
 * Implement it on generic classes, e.g. `Foo<T>`: the serialization plugin only generates
 * `Foo.serializer(typeSerial0)` for those, and a runtime value does not reveal the type argument, so they can't be
 * found by class lookup. It also skips the per-call reflective lookup on the JVM.
 */
interface JsonSerializable<T> {
    /**
     * Serializer that encodes this value.
     */
    val jsonSerializer: SerializationStrategy<T>
}

@Suppress("UNCHECKED_CAST")
@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
fun Any?.toJsonElement(): JsonElement = when (this) {
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
    is Array<*> -> JsonArray(this.map { it.toJsonElement() })
    is Iterable<*> -> JsonArray(this.map { it.toJsonElement() })
    is Map<*, *> -> JsonObject(this.entries.associate { (k, v) -> k.toString() to v.toJsonElement() })
    is JsonSerializable<*> -> Kotlinx.DEFAULT.encodeToJsonElement(jsonSerializer as SerializationStrategy<Any>, this)
    else -> {
        val ser = this::class.serializerOrNull() as KSerializer<Any>?
            ?: throw IllegalArgumentException(
                // qualifiedName is null for local and anonymous classes, so fall back to simpleName to keep the
                // type identifiable in the message
                "Cannot serialize JSON value of type ${this::class.qualifiedName ?: this::class.simpleName}: " +
                    "no kotlinx @Serializable serializer is registered, or the class is generic. " +
                    "Implement JsonSerializable, convert it manually or pass a JsonElement.",
            )
        Kotlinx.DEFAULT.encodeToJsonElement(ser, this)
    }
}
