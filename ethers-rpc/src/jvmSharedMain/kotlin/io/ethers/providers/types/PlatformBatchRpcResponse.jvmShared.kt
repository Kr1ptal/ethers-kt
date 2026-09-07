package io.ethers.providers.types

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture

actual abstract class PlatformBatchRpcResponse<T> actual constructor() {
    actual abstract suspend fun await(): T

    /**
     * Await the response after its batch has been sent, by blocking the calling thread.
     *
     * Throws if the batch has not been sent yet, rather than blocking forever.
     */
    fun get(): T = runBlocking { await() }

    /**
     * Convert this pending batch response to a JVM [CompletableFuture].
     *
     * Started undispatched, so a batch that has not been sent yet fails the future immediately instead of on a
     * later dispatch.
     */
    fun toFuture(): CompletableFuture<T> {
        return CoroutineScope(Dispatchers.Default)
            .async(start = CoroutineStart.UNDISPATCHED) { await() }
            .asCompletableFuture()
    }
}
