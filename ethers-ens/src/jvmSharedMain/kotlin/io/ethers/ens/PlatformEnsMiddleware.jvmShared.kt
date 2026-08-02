package io.ethers.ens

import io.ethers.core.Result
import io.ethers.core.types.Address
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import java.util.concurrent.CompletableFuture

actual abstract class PlatformEnsMiddleware actual constructor() {
    actual abstract suspend fun resolveAddress(ensName: String): Result<Address, EnsMiddleware.Error>

    actual abstract suspend fun resolveText(ensName: String, key: String): Result<String, EnsMiddleware.Error>

    actual abstract suspend fun resolveEnsName(address: Address): Result<String, EnsMiddleware.Error>

    actual abstract suspend fun resolveAvatar(ensName: String): Result<String, EnsMiddleware.Error>

    actual abstract suspend fun resolveAvatar(address: Address): Result<String, EnsMiddleware.Error>

    /** Resolve ENS name to [Address] as a [CompletableFuture]. */
    fun resolveAddressAsync(ensName: String): CompletableFuture<Result<Address, EnsMiddleware.Error>> = future { resolveAddress(ensName) }

    /** Resolve the text record under [key] as a [CompletableFuture]. */
    fun resolveTextAsync(ensName: String, key: String): CompletableFuture<Result<String, EnsMiddleware.Error>> = future { resolveText(ensName, key) }

    /** Reverse-resolve [address] to an ENS name as a [CompletableFuture]. */
    fun resolveEnsNameAsync(address: Address): CompletableFuture<Result<String, EnsMiddleware.Error>> = future { resolveEnsName(address) }

    /** Resolve the avatar of an ENS name as a [CompletableFuture]. */
    fun resolveAvatarAsync(ensName: String): CompletableFuture<Result<String, EnsMiddleware.Error>> = future { resolveAvatar(ensName) }

    /** Resolve the avatar of an [address] as a [CompletableFuture]. */
    fun resolveAvatarAsync(address: Address): CompletableFuture<Result<String, EnsMiddleware.Error>> = future { resolveAvatar(address) }

    private fun <T> future(block: suspend () -> T): CompletableFuture<T> = CoroutineScope(Dispatchers.Default).async { block() }.asCompletableFuture()
}
