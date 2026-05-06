package io.ethers.core

import kotlinx.serialization.json.Json

/**
 * Shared [Json] instance with default settings for deserializing Ethereum JSON-RPC responses.
 */
object Kotlinx {
    @JvmField
    val DEFAULT: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
}
