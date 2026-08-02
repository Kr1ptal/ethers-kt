package io.ethers.providers.types

import io.channels.core.ChannelReceiver
import io.ethers.core.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture

actual interface PlatformRpcSubscribe<T : Any, E : Result.Error> {
    actual suspend fun send(): Result<ChannelReceiver<T>, E>

    /**
     * Subscribe to a stream via RPC and await the subscription response by blocking the calling thread.
     */
    fun sendAwait(): Result<ChannelReceiver<T>, E> = runBlocking { send() }

    /**
     * Asynchronously subscribe to a stream via RPC as a [CompletableFuture].
     */
    fun sendAsync(): CompletableFuture<Result<ChannelReceiver<T>, E>> {
        return CoroutineScope(Dispatchers.Default).async { send() }.asCompletableFuture()
    }
}
