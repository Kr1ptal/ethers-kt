@file:JvmName("Providers")

package io.ethers.providers

import io.ethers.core.Result
import kotlinx.coroutines.runBlocking

/**
 * Create a new [Provider] for [url], resolving the chain id via an `eth_chainId` call, blocking the calling
 * thread until it completes.
 *
 * Supported URL protocols:
 * - http/https
 * - ws/wss
 *
 * JVM/Android-only: `runBlocking` has no common equivalent. The suspending [Provider.Companion.fromUrl] and the
 * [chainId][Provider.Companion.fromUrl]-taking overload are both available in common code.
 *
 * Named `fromUrlAwait` to match `sendAwait` and friends, where the bare name suspends and the `Await` suffix
 * blocks. The distinct name is also load-bearing: an extension with the same signature as a member is silently
 * shadowed by that member, so a `fromUrl` extension here would resolve to the suspending overload instead.
 * */
@JvmOverloads
fun Provider.Companion.fromUrlAwait(
    url: String,
    config: RpcClientConfig = RpcClientConfig(),
): Result<Provider, Provider.Error> = runBlocking { fromUrl(url, config) }
