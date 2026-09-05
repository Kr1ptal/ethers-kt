package io.ethers.solana.types

import io.ethers.core.types.Bytes
import io.ethers.crypto.Base58
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.io.encoding.Base64

/** Read fields independently, without interpreting a transaction version or validating a message. */
internal class RPCFields(raw: JsonElement) {
    private val obj = raw as? JsonObject ?: JsonObject(emptyMap())
    private val known = mutableSetOf<String>()
    fun value(name: String): JsonElement? {
        known.add(name)
        return obj[name]?.takeUnless { it == JsonNull }
    }
    fun string(name: String): String? = value(name)?.let(::rpcString)
    fun u64(name: String): BigInteger? = value(name)?.let(::rpcU64)
    fun u8(name: String): Int? = value(name)?.let(::rpcU8)
    fun u32(name: String): Long? = value(name)?.let(::rpcU32)
    fun i64(name: String): Long? = value(name)?.let(::rpcI64)
    fun bool(name: String): Boolean? = value(name)?.jsonPrimitive?.also { require(!it.isString) }?.boolean
    fun <T> list(name: String, decode: (JsonElement) -> T): List<T>? = value(name)?.jsonArray?.map(decode)
    fun otherFields(): Map<String, JsonElement> = obj.filterKeys { it !in known }
    fun contains(name: String): Boolean = name in obj
}

internal fun rpcString(value: JsonElement): String = value.jsonPrimitive.also { require(it.isString) { "Expected a JSON string" } }.content
internal fun rpcU64(value: JsonElement): BigInteger = Json.decodeFromJsonElement(U64Serializer, value)
internal fun rpcI64(value: JsonElement): Long = value.jsonPrimitive.also { require(!it.isString) { "Expected a JSON number" } }.long
internal fun rpcU8(value: JsonElement): Int = rpcI64(value).also { require(it in 0..255) { "Expected an unsigned byte" } }.toInt()
internal fun rpcU32(value: JsonElement): Long = rpcI64(value).also { require(it in 0..4294967295) { "Expected a u32" } }
internal fun rpcAddress(value: JsonElement): SolanaAddress = SolanaAddress(rpcString(value))

internal fun rpcEncoding(raw: JsonElement): String? = when {
    raw is JsonPrimitive && raw.isString -> "base58"
    raw is JsonArray && raw.size == 2 && raw.all { it is JsonPrimitive && it.isString } -> rpcString(raw[1])
    else -> null
}

/** Unknown encodings remain raw; malformed data using a known encoding is not silently accepted. */
internal fun rpcEncodedBytes(raw: JsonElement, encoding: String?): Bytes? {
    if (encoding != "base58" && encoding != "base64") return null
    val text = when (raw) {
        is JsonPrimitive -> raw.content
        is JsonArray -> raw.firstOrNull()?.let(::rpcString)
        else -> null
    } ?: return null
    return when (encoding) {
        "base58" -> Bytes(Base58.decode(text))
        "base64" -> Bytes(Base64.decode(text))
        else -> null
    }
}

/** JsonElement's default serializer can round decimal literals through Double; retain their exact text. */
@OptIn(ExperimentalSerializationApi::class)
internal fun rpcExactJson(raw: JsonElement): JsonElement = when (raw) {
    is JsonObject -> JsonObject(raw.mapValues { rpcExactJson(it.value) })
    is JsonArray -> JsonArray(raw.map(::rpcExactJson))
    is JsonPrimitive -> if (raw.isString || raw == JsonNull || raw.content == "true" || raw.content == "false") raw else JsonUnquotedLiteral(raw.content)
}
