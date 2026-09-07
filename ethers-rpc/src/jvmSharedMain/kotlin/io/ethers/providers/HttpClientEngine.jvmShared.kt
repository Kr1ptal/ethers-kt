package io.ethers.providers

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

internal actual val defaultHttpClientEngineFactory: HttpClientEngineFactory<*>
    get() = CIO
