package io.ethers.core

import kotlinx.serialization.json.Json
import kotlin.jvm.JvmField

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
