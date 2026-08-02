package io.ethers.providers.types

import io.ethers.core.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture

actual abstract class PlatformRpcRequest<T, E : Result.Error> actual constructor() {
    actual abstract suspend fun send(): Result<T, E>

    /**
     * Send the RPC request and await the result by blocking the calling thread.
     */
    fun sendAwait(): Result<T, E> = runBlocking { send() }

    /**
     * Asynchronously send the RPC request as a [CompletableFuture].
     */
    fun sendAsync(): CompletableFuture<Result<T, E>> {
        return CoroutineScope(Dispatchers.Default).async { send() }.asCompletableFuture()
    }
}
