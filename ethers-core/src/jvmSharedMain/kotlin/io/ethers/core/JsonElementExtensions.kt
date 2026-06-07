package io.ethers.core

import io.ethers.core.types.Address
import io.ethers.core.types.Bloom
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.math.BigInteger

private val EMPTY_BYTES = ByteArray(0)

/**
 * Decode hex string from [JsonPrimitive] content into a [ByteArray].
 */
fun JsonPrimitive.asHexByteArray(): ByteArray {
    val text = content
    if (text.isEmpty()) return EMPTY_BYTES
    if (text.length == 2 && (text == "0x" || text == "0X")) return EMPTY_BYTES
    return FastHex.decode(text)
}

/**
 * Decode hex string from [JsonPrimitive] content as unsigned [BigInteger].
 */
fun JsonPrimitive.asHexBigInteger(): BigInteger {
    val decoded = asHexByteArray()
    if (decoded.isEmpty()) return BigInteger.ZERO
    return BigInteger(1, decoded)
}

/**
 * Decode hex string from [JsonPrimitive] content as [Long].
 */
fun JsonPrimitive.asHexLong(): Long = asHexByteArray().toHexLong()

/**
 * Decode hex string from [JsonPrimitive] content as [Int].
 */
fun JsonPrimitive.asHexInt(): Int = asHexByteArray().toHexInt()

/**
 * Decode [JsonPrimitive] content as [Long], accepting either a hex string (0x-prefixed) or a plain number.
 */
fun JsonPrimitive.asAnyLong(): Long {
    val text = content
    return if (text.length >= 2 && text[0] == '0' && (text[1] == 'x' || text[1] == 'X')) {
        asHexLong()
    } else {
        long
    }
}

/**
 * Decode hex string from [JsonPrimitive] as [Address].
 */
fun JsonPrimitive.asAddress(): Address {
    val arr = asHexByteArray()
    return if (arr.isEmpty()) Address.ZERO else Address(arr)
}

/**
 * Decode hex string from [JsonPrimitive] as [Hash].
 */
fun JsonPrimitive.asHash(): Hash = Hash(asHexByteArray())

/**
 * Decode hex string from [JsonPrimitive] as [Bytes].
 */
fun JsonPrimitive.asBytes(): Bytes {
    val arr = asHexByteArray()
    return if (arr.isEmpty()) Bytes.EMPTY else Bytes(arr)
}

/**
 * Decode hex string from [JsonPrimitive] as [Bytes], returning null for empty hex strings.
 */
fun JsonPrimitive.asBytesOrNull(): Bytes? {
    val arr = asHexByteArray()
    return if (arr.isEmpty()) null else Bytes(arr)
}

/**
 * Decode hex string from [JsonPrimitive] as [Bloom].
 */
fun JsonPrimitive.asBloom(): Bloom = Bloom(asHexByteArray())

/**
 * Return null if this [JsonElement] is [JsonNull], otherwise apply [block] on the primitive content.
 */
inline fun <T> JsonElement.ifNotNull(block: JsonPrimitive.() -> T): T? {
    if (this is JsonNull) return null
    return jsonPrimitive.block()
}

/**
 * Get a field from this [JsonObject] and apply [block] if present and non-null, otherwise return null.
 */
inline fun <T> JsonObject.getOrNull(key: String, block: JsonElement.() -> T): T? {
    val element = this[key] ?: return null
    if (element is JsonNull) return null
    return element.block()
}

private fun ByteArray.toHexLong(): Long {
    if (isEmpty()) return 0L
    var value = 0L
    for (b in this) {
        value = (value shl 8) + (b.toInt() and 255)
    }
    return value
}

private fun ByteArray.toHexInt(): Int {
    if (isEmpty()) return 0
    var value = 0
    for (b in this) {
        value = (value shl 8) + (b.toInt() and 255)
    }
    return value
}

/**
 * Read [JsonPrimitive.boolean] value.
 */
val JsonPrimitive.asBoolean: Boolean
    get() = boolean

/**
 * Read [JsonPrimitive.boolean] value, or null.
 */
val JsonPrimitive.asBooleanOrNull: Boolean?
    get() = booleanOrNull

/**
 * Read [JsonPrimitive.double] value.
 */
val JsonPrimitive.asDouble: Double
    get() = double
