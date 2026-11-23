package io.ethers.aa.internal

import io.ethers.core.FastHex
import io.ethers.core.types.Address
import io.ethers.core.types.Authorization
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parse a JSON-RPC quantity. Bundlers are not consistent about quantities: most return `0x`-prefixed hex strings,
 * but some return JSON numbers or decimal strings, so all three are accepted.
 */
internal fun JsonElement.asQuantity(): BigInteger {
    val primitive = jsonPrimitive
    val content = primitive.content
    if (primitive.isString) {
        if (content.startsWith("0x") || content.startsWith("0X")) {
            if (content.length == 2) return ZERO
            return BigInteger(1, FastHex.decode(if (content.length % 2 == 0) content else "0x0" + content.substring(2)))
        }
        if (content.isEmpty()) return ZERO
    }
    return BigInteger(content)
}

internal fun JsonElement.asQuantityLong(): Long = asQuantity().toLong()

internal fun JsonElement.asHexBytes(): Bytes {
    val content = jsonPrimitive.content
    if (content.isEmpty() || content == "0x" || content == "0X") return Bytes.EMPTY
    return Bytes(content)
}

internal fun JsonElement.asAddressValue(): Address = Address(jsonPrimitive.content)

internal fun JsonElement.asHashValue(): Hash = Hash(jsonPrimitive.content)

internal fun JsonObject.field(key: String): JsonElement {
    val value = this[key]
    require(value != null && value !is JsonNull) { "Missing field '$key'" }
    return value
}

internal fun JsonObject.fieldOrNull(key: String): JsonElement? {
    val value = this[key]
    return if (value == null || value is JsonNull) null else value
}

internal fun quantity(value: BigInteger): JsonPrimitive = JsonPrimitive(FastHex.encodeWithPrefix(value))

internal fun quantity(value: Long): JsonPrimitive = JsonPrimitive(FastHex.encodeWithPrefix(value))

internal fun JsonObjectBuilder.put(key: String, value: JsonElement?) {
    if (value != null) put(key, value)
}

internal fun hex(value: Bytes): JsonPrimitive = JsonPrimitive(value.toString())

internal fun hex(value: Address): JsonPrimitive = JsonPrimitive(value.toString())

internal fun Authorization.toRpcJson(): JsonObject = buildJsonObject {
    put("chainId", quantity(chainId))
    put("address", hex(address))
    put("nonce", quantity(nonce))
    put("yParity", quantity(yParity))
    put("r", JsonPrimitive(FastHex.encodeWithPrefix(uintToBytes(r, 32, "r"))))
    put("s", JsonPrimitive(FastHex.encodeWithPrefix(uintToBytes(s, 32, "s"))))
}

internal fun JsonElement.asAuthorization(): Authorization {
    val obj = this as? JsonObject ?: throw IllegalArgumentException("Authorization must be a JSON object")
    return Authorization(
        chainId = obj.field("chainId").asQuantityLong(),
        address = obj.field("address").asAddressValue(),
        nonce = obj.field("nonce").asQuantityLong(),
        yParity = (obj.fieldOrNull("yParity") ?: obj.fieldOrNull("v") ?: JsonPrimitive("0x0")).asQuantityLong(),
        r = obj.field("r").asQuantity(),
        s = obj.field("s").asQuantity(),
    )
}

internal val BIG_ZERO_JSON = quantity(bigIntegerOf(0))
