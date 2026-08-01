package io.ethers.providers.types

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture

actual abstract class PlatformBatchRpcRequest actual constructor() {
    actual abstract suspend fun send(): Boolean

    /**
     * Send the batch request and await the result by blocking the calling thread.
     */
    fun sendAwait(): Boolean = runBlocking { send() }

    /**
     * Asynchronously send the batch request as a [CompletableFuture].
     *
     * Started undispatched so the batch is marked as sent before this returns, matching [sendDeferred].
     */
    fun sendAsync(): CompletableFuture<Boolean> {
        return CoroutineScope(Dispatchers.Default)
            .async(start = CoroutineStart.UNDISPATCHED) { send() }
            .asCompletableFuture()
    }
}
