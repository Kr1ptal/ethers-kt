package io.ethers.providers

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

/**
 * Darwin is the ktor engine backed by NSURLSession, and is the only one available on Apple targets. It supports
 * both HTTP and the WebSocket upgrade, which [WsClient] needs.
 */
internal actual val defaultHttpClientEngineFactory: HttpClientEngineFactory<*>
    get() = Darwin
