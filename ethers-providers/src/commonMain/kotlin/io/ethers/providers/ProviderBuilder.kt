package io.ethers.providers

import io.ethers.core.Result
import io.ethers.core.asHexLong
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.unwrapOrReturn
import io.ethers.providers.types.RpcCall
import io.ethers.providers.types.RpcRequest
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration
import io.ktor.client.HttpClient as KtorHttpClient

/**
 * Seam through which each platform adds its own conveniences to [ProviderBuilder].
 *
 * It exists so platform helpers can be inherited *members* rather than extension functions - Java callers get
 * `builder.buildAwait()` instead of a static call taking the companion. All implementation stays in ordinary
 * common code in [ProviderBuilder].
 *
 * JVM and Android actualize this with a blocking terminal. A platform without those primitives actualizes it
 * with no extra members.
 */
expect abstract class PlatformProviderBuilder() {
    abstract suspend fun build(): Result<Provider, Provider.Error>
}

/**
 * Builds a [Provider] for [url], created via [Provider.builder].
 *
 * Supported URL protocols:
 * - http/https
 * - ws/wss
 *
 * Which terminal you call decides what the construction costs:
 * - [build] with a known chain id makes no RPC call, so it neither suspends nor blocks
 * - [build] without one resolves the chain id via `eth_chainId`
 * - `buildAwait` does the same, blocking the calling thread (JVM and Android only)
 */
class ProviderBuilder internal constructor(private val url: String) : PlatformProviderBuilder() {
    private var config = RpcClientConfig()

    /**
     * Use a pre-built [RpcClientConfig], replacing anything set on this builder so far.
     *
     * The individual setters below cover the same ground; this is for passing a config you already hold.
     * */
    fun config(config: RpcClientConfig) = apply { this.config = config }

    /** Client to use for making JSON-RPC requests. If not set, a default client will be used. */
    fun httpClient(client: KtorHttpClient) = apply { config.client(client) }

    /** Headers to include with each RPC request. Can be used to set authorization headers, etc... */
    fun headers(headers: Map<String, String>) = apply { config.requestHeaders(headers) }

    /**
     * If true, automatically resubscribes existing subscription streams on WebSocket reconnection.
     * If false, closes the streams instead. Default is true. Only used for `ws`/`wss` urls.
     * */
    fun resubscribeOnReconnect(resubscribe: Boolean) = apply { config.resubscribeOnReconnect(resubscribe) }

    /** WebSocket connection timeout. Only used for `ws`/`wss` urls. */
    fun connectTimeout(timeout: Duration) = apply { config.connectTimeoutMs(timeout.inWholeMilliseconds) }

    /** Request read timeout, used to expire in-flight requests. Only used for `ws`/`wss` urls. */
    fun readTimeout(timeout: Duration) = apply { config.readTimeoutMs(timeout.inWholeMilliseconds) }

    /**
     * Build a [Provider] with a known [chainId].
     *
     * Makes no RPC call at all, so it neither suspends nor blocks. [chainId] is a parameter rather than a
     * builder setting on purpose, so that "no network round-trip" is guaranteed by the signature instead of
     * being a runtime check.
     * */
    fun build(chainId: Long): Result<Provider, Provider.Error> {
        val client = clientOrNull() ?: return failure(Provider.UnsupportedUrlProtocol(url))
        return success(Provider(client, chainId))
    }

    /**
     * Build a [Provider], resolving the chain id via an `eth_chainId` call.
     * */
    override suspend fun build(): Result<Provider, Provider.Error> {
        val client = clientOrNull() ?: return failure(Provider.UnsupportedUrlProtocol(url))
        val chainId = getChainId(client).send()
            .unwrapOrReturn { return failure(Provider.UnableToGetChainId(url, it)) }

        return success(Provider(client, chainId))
    }

    private fun getChainId(client: JsonRpcClient): RpcRequest<Long, RpcError> {
        return RpcCall(client, "eth_chainId", EMPTY_ARRAY, { it.jsonPrimitive.asHexLong() })
    }

    private fun clientOrNull(): JsonRpcClient? = when {
        url.matches(PROTO_HTTPS) -> HttpClient(url, config)
        url.matches(PROTO_WSS) -> WsClient(url, config)
        else -> null
    }

    companion object {
        private val PROTO_HTTPS = "^(https?)://.+$".toRegex()
        private val PROTO_WSS = "^(wss?)://.+$".toRegex()
        private val EMPTY_ARRAY = emptyArray<Any>()
    }
}
