package io.ethers.solana.providers

import io.ethers.core.Kotlinx
import io.ethers.solana.types.AccountInfo
import io.ethers.solana.types.ContextValue
import io.ethers.solana.types.RpcContext
import io.ethers.solana.types.U64Serializer
import io.ethers.solana.utils.requireU64
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

internal fun u64(value: BigInteger): JsonElement = Kotlinx.DEFAULT.encodeToJsonElement(U64Serializer, requireU64(value))
internal fun decodeU64(value: JsonElement): BigInteger = Kotlinx.DEFAULT.decodeFromJsonElement(U64Serializer, value)
internal inline fun <reified T> decode(value: JsonElement): T = Kotlinx.DEFAULT.decodeFromJsonElement(value)
internal fun <T> decodeContext(value: JsonElement, decoder: (JsonElement) -> T): ContextValue<T> {
    val obj = value.jsonObject
    return ContextValue(decode<RpcContext>(obj.getValue("context")), decoder(obj.getValue("value")))
}
internal fun decodeAccount(element: JsonElement): AccountInfo? {
    if (element == JsonNull) return null
    return decode<AccountInfo>(element)
}
