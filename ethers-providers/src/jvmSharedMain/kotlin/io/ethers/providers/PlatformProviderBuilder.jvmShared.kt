package io.ethers.providers

import io.ethers.core.Result
import kotlinx.coroutines.runBlocking

actual abstract class PlatformProviderBuilder actual constructor() {
    actual abstract suspend fun build(): Result<Provider, Provider.Error>

    /**
     * Build a [Provider], resolving the chain id via an `eth_chainId` call and blocking the calling thread
     * until it completes.
     *
     * Follows `sendAwait`: the bare name suspends, the `Await` suffix blocks.
     * */
    fun buildAwait(): Result<Provider, Provider.Error> = runBlocking { build() }
}
