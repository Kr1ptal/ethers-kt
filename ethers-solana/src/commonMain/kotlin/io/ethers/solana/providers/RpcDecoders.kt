package io.ethers.solana.providers

import io.ethers.core.Kotlinx
import io.ethers.solana.types.AccountInfo
import io.ethers.solana.types.ContextValue
import io.ethers.solana.types.PublicKey
import io.ethers.solana.types.RpcContext
import io.ethers.solana.types.U64Serializer
import io.ethers.solana.utils.requireU64
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

internal fun u64(value: BigInteger): JsonElement = Kotlinx.DEFAULT.encodeToJsonElement(U64Serializer, requireU64(value))
internal fun decodeU64(value: JsonElement): BigInteger = Kotlinx.DEFAULT.decodeFromJsonElement(U64Serializer, value)
internal inline fun <reified T> decode(value: JsonElement): T = Kotlinx.DEFAULT.decodeFromJsonElement(value)
internal fun <T> decodeContext(value: JsonElement, decoder: (JsonElement) -> T): ContextValue<T> {
    val obj = value.jsonObject
    return ContextValue(decode<RpcContext>(obj.getValue("context")), decoder(obj.getValue("value")))
}
internal fun decodeAccount(element: JsonElement): AccountInfo? {
    if (element == JsonNull) return null
    val obj = element.jsonObject
    val data = obj.getValue("data").jsonArray
    require(data.size == 2 && data[1].jsonPrimitive.content == "base64") { "Expected base64 account data" }
    val bytes = Base64.decode(data[0].jsonPrimitive.content)
    return AccountInfo(bytes, obj.getValue("executable").jsonPrimitive.boolean, decodeU64(obj.getValue("lamports")), PublicKey(obj.getValue("owner").jsonPrimitive.content), decodeU64(obj.getValue("rentEpoch")), obj["space"]?.takeUnless { it == JsonNull }?.let(::decodeU64) ?: bigIntegerOf(bytes.size))
}
