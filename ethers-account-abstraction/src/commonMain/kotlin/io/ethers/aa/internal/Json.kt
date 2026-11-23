package io.ethers.aa.internal

import io.ethers.core.asHexBigInteger
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parse a JSON-RPC quantity. Bundlers are not consistent about quantities: most return `0x`-prefixed hex strings,
 * which [asHexBigInteger] parses, but some return JSON numbers or decimal strings, so those are accepted too.
 */
internal fun JsonElement.asQuantity(): BigInteger {
    val primitive = jsonPrimitive
    val content = primitive.content
    if (primitive.isString && (content.startsWith("0x") || content.startsWith("0X") || content.isEmpty())) {
        return primitive.asHexBigInteger()
    }
    return BigInteger(content)
}

/**
 * Return [value] of a required response field, or fail decoding if the response did not have it.
 */
internal fun <T : Any> required(value: T?, field: String): T {
    return value ?: throw SerializationException("Missing field '$field'")
}
