package io.ethers.solana.providers

import io.ethers.solana.types.SolanaAddress
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed class LogsFilter {
    data object All : LogsFilter()
    data object AllWithVotes : LogsFilter()
    data class Mentions(val account: SolanaAddress) : LogsFilter()
    internal fun toJson(): JsonElement = when (this) {
        All -> JsonPrimitive("all")
        AllWithVotes -> JsonPrimitive("allWithVotes")
        is Mentions -> buildJsonObject { put("mentions", JsonArray(listOf(JsonPrimitive(account.toString())))) }
    }
}

sealed class AccountFilter {
    data class DataSize(val bytes: Long) : AccountFilter()
    data class Memcmp(val offset: Long, val base58: String) : AccountFilter()
    internal fun toJson(): JsonElement = when (this) {
        is DataSize -> buildJsonObject { put("dataSize", bytes) }
        is Memcmp -> buildJsonObject {
            put(
                "memcmp",
                buildJsonObject {
                    put("offset", offset)
                    put("bytes", base58)
                    put("encoding", "base58")
                },
            )
        }
    }
}
