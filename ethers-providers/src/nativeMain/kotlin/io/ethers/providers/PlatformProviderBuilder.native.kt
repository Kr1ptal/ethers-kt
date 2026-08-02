package io.ethers.providers

import io.ethers.core.Result

/**
 * Native targets have no blocking terminal to expose, so this adds no members beyond the suspending [build] that
 * [ProviderBuilder] implements.
 */
actual abstract class PlatformProviderBuilder actual constructor() {
    actual abstract suspend fun build(): Result<Provider, Provider.Error>
}
