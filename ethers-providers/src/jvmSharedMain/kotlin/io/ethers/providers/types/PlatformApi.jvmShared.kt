package io.ethers.providers.types

import io.channels.core.ChannelReceiver
import io.ethers.core.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration

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

actual interface PlatformPendingInclusion<T> {
    actual suspend fun inclusion(
        retries: Int,
        interval: Duration,
        confirmations: Int,
    ): Result<T, PendingInclusion.Error>

    /**
     * Await for pending transaction to be included in a block (= mined) by blocking the calling thread.
     */
    fun awaitInclusion(): Result<T, PendingInclusion.Error> = awaitInclusion(DEFAULT_RETRIES, DEFAULT_INCLUSION_INTERVAL, DEFAULT_CONFIRMATIONS)

    /**
     * Await for pending transaction to be included in a block (= mined) by blocking the calling thread.
     *
     * @param retries number of attempts to receive a transaction inclusion response
     */
    fun awaitInclusion(retries: Int): Result<T, PendingInclusion.Error> = awaitInclusion(retries, DEFAULT_INCLUSION_INTERVAL, DEFAULT_CONFIRMATIONS)

    /**
     * Await for pending transaction to be included in a block (= mined) by blocking the calling thread.
     *
     * @param retries number of attempts to receive a transaction inclusion response
     * @param interval time to wait between retries
     */
    fun awaitInclusion(retries: Int, interval: Duration): Result<T, PendingInclusion.Error> = awaitInclusion(retries, interval, DEFAULT_CONFIRMATIONS)

    /**
     * Await for pending transaction to be included in a block (= mined) by blocking the calling thread.
     *
     * @param retries number of attempts to receive a transaction inclusion response
     * @param interval time to wait between retries
     * @param confirmations number of mined blocks required to announce inclusion of the pending transaction
     */
    fun awaitInclusion(
        retries: Int,
        interval: Duration,
        confirmations: Int,
    ): Result<T, PendingInclusion.Error> = runBlocking { inclusion(retries, interval, confirmations) }

    /**
     * Asynchronously wait for pending transaction to be included in a block (= mined), as a [CompletableFuture].
     */
    fun inclusionAsync(): CompletableFuture<Result<T, PendingInclusion.Error>> = inclusionAsync(DEFAULT_RETRIES, DEFAULT_INCLUSION_INTERVAL, DEFAULT_CONFIRMATIONS)

    /**
     * Asynchronously wait for pending transaction to be included in a block (= mined), as a [CompletableFuture].
     *
     * @param retries number of attempts to receive a transaction inclusion response
     */
    fun inclusionAsync(retries: Int): CompletableFuture<Result<T, PendingInclusion.Error>> = inclusionAsync(retries, DEFAULT_INCLUSION_INTERVAL, DEFAULT_CONFIRMATIONS)

    /**
     * Asynchronously wait for pending transaction to be included in a block (= mined), as a [CompletableFuture].
     *
     * @param retries number of attempts to receive a transaction inclusion response
     * @param interval time to wait between retries
     */
    fun inclusionAsync(retries: Int, interval: Duration): CompletableFuture<Result<T, PendingInclusion.Error>> = inclusionAsync(retries, interval, DEFAULT_CONFIRMATIONS)

    /**
     * Asynchronously wait for pending transaction to be included in a block (= mined), as a [CompletableFuture].
     *
     * @param retries number of attempts to receive a transaction inclusion response
     * @param interval time to wait between retries
     * @param confirmations number of mined blocks required to announce inclusion of the pending transaction
     */
    fun inclusionAsync(
        retries: Int,
        interval: Duration,
        confirmations: Int,
    ): CompletableFuture<Result<T, PendingInclusion.Error>> {
        return CoroutineScope(Dispatchers.Default)
            .async { inclusion(retries, interval, confirmations) }
            .asCompletableFuture()
    }
}
