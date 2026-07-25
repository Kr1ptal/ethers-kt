package io.ethers.providers

import io.ktor.client.engine.HttpClientEngineFactory

/**
 * Platform-specific engine used to build the default ktor [io.ktor.client.HttpClient].
 *
 * Engines are not available on every ktor target, so the choice is left to each platform instead of hard-coding
 * one in common code.
 */
internal expect val defaultHttpClientEngineFactory: HttpClientEngineFactory<*>
