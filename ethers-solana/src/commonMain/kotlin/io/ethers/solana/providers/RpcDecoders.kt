package io.ethers.solana.providers

import io.ethers.core.Kotlinx
import io.ethers.solana.types.U64Serializer
import io.ethers.solana.types.rpc.AccountInfo
import io.ethers.solana.types.rpc.ContextValue
import io.ethers.solana.types.rpc.RpcContext
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/** Preserve the caller's exact integer; range validation belongs to the RPC node. */
@OptIn(ExperimentalSerializationApi::class)
internal fun rpcInteger(value: BigInteger): JsonElement = JsonUnquotedLiteral(value.toString())
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
