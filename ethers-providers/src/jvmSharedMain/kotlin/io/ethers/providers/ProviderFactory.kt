@file:JvmName("Providers")

package io.ethers.providers

import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.unwrapOrReturn
import io.ethers.providers.types.sendAwait

private val PROTO_HTTPS = "^(https?)://.+$".toRegex()
private val PROTO_WSS = "^(wss?)://.+$".toRegex()

/**
 * Create a new [Provider] from the given [url] and optional [RpcClientConfig]. If no [chainId] is provided,
 * it tries to fetch it via `eth_chainId` RPC call.
 *
 * Supported URL protocols:
 * - http/https
 * - ws/wss
 *
 * JVM/Android-only: it blocks while resolving the chain id, and [WsClient] is not available in common code.
 * */
@JvmOverloads
fun Provider.Companion.fromUrl(
    url: String,
    config: RpcClientConfig = RpcClientConfig(),
    chainId: Long = -1L,
): Result<Provider, Provider.Error> {
    val client = when {
        url.matches(PROTO_HTTPS) -> HttpClient(url, config)
        url.matches(PROTO_WSS) -> WsClient(url, config)
        else -> return failure(Provider.UnsupportedUrlProtocol(url))
    }

    @Suppress("NAME_SHADOWING")
    var chainId = chainId
    if (chainId == -1L) {
        chainId = Provider.getChainId(client).sendAwait()
            .unwrapOrReturn { return failure(Provider.UnableToGetChainId(url, it)) }
    }

    return success(Provider(client, chainId))
}
