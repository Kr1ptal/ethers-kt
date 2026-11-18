package io.ethers.core

import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializerOrNull

@Suppress("UNCHECKED_CAST")
@OptIn(InternalSerializationApi::class)
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
    is BigInteger -> JsonPrimitive(this.toString())
    is BigDecimal -> JsonPrimitive(this.toString())
    is ByteArray -> JsonPrimitive(FastHex.encodeWithPrefix(this))
    is Array<*> -> JsonArray(this.map { it.toJsonElement() })
    is Iterable<*> -> JsonArray(this.map { it.toJsonElement() })
    is Map<*, *> -> JsonObject(this.entries.associate { (k, v) -> k.toString() to v.toJsonElement() })
    else -> {
        val ser = this::class.serializerOrNull() as KSerializer<Any>?
            ?: throw IllegalArgumentException(
                // qualifiedName is null for local and anonymous classes, so fall back to simpleName to keep the
                // type identifiable in the message
                "Cannot serialize JSON value of type ${this::class.qualifiedName ?: this::class.simpleName}: " +
                    "no kotlinx @Serializable serializer is registered. " +
                    "Convert it manually or pass a JsonElement.",
            )
        Kotlinx.DEFAULT.encodeToJsonElement(ser, this)
    }
}
