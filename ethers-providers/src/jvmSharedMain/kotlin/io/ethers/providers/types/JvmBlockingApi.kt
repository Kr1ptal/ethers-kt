@file:JvmName("BlockingRequests")
@file:Suppress("UNCHECKED_CAST")

package io.ethers.providers.types

import io.channels.core.ChannelReceiver
import io.ethers.core.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration

// Blocking and CompletableFuture-based conveniences over the suspending provider API.
//
// These are JVM/Android-only: `runBlocking` and CompletableFuture have no common equivalent, so they cannot live
// in `commonMain` alongside the rest of the module.

// -------------------------------------------------------------------------------------------------------------
// RpcRequest
// -------------------------------------------------------------------------------------------------------------

/**
 * Send the RPC request and await the result by blocking the calling thread.
 */
fun <T, E : Result.Error> RpcRequest<T, E>.sendAwait(): Result<T, E> = runBlocking { send() }

/**
 * Asynchronously send the RPC request as a [CompletableFuture].
 */
fun <T, E : Result.Error> RpcRequest<T, E>.sendAsync(): CompletableFuture<Result<T, E>> {
    return CoroutineScope(Dispatchers.Default).async { send() }.asCompletableFuture()
}

// -------------------------------------------------------------------------------------------------------------
// RpcSubscribe
// -------------------------------------------------------------------------------------------------------------

/**
 * Subscribe to a stream via RPC and await the subscription response by blocking the calling thread.
 */
fun <T : Any, E : Result.Error> RpcSubscribe<T, E>.sendAwait(): Result<ChannelReceiver<T>, E> = runBlocking { send() }

/**
 * Asynchronously subscribe to a stream via RPC as a [CompletableFuture].
 */
fun <T : Any, E : Result.Error> RpcSubscribe<T, E>.sendAsync(): CompletableFuture<Result<ChannelReceiver<T>, E>> {
    return CoroutineScope(Dispatchers.Default).async { send() }.asCompletableFuture()
}

// -------------------------------------------------------------------------------------------------------------
// BatchRpcRequest / BatchRpcResponse
// -------------------------------------------------------------------------------------------------------------

/**
 * Send the batch request and await the result by blocking the calling thread.
 */
fun BatchRpcRequest.sendAwait(): Boolean = runBlocking { send() }

/**
 * Asynchronously send the batch request as a [CompletableFuture].
 */
fun BatchRpcRequest.sendAsync(): CompletableFuture<Boolean> = sendDeferred().asCompletableFuture()

/**
 * Convert this pending batch response to a JVM [CompletableFuture].
 */
fun <T> BatchRpcResponse<T>.toFuture(): CompletableFuture<T> {
    return CoroutineScope(Dispatchers.Default)
        .async(start = CoroutineStart.UNDISPATCHED) { await() }
        .asCompletableFuture()
}

/**
 * Batch-send all requests, awaiting the result by blocking the calling thread.
 */
fun <T, E : Result.Error> Iterable<RpcRequest<out T, E>>.sendAwait(): BatchResponse<T, E> = runBlocking { send() }

/**
 * Batch-send all requests asynchronously.
 */
fun <T, E : Result.Error> Iterable<RpcRequest<out T, E>>.sendAsync(): BatchResponseAsync<T, E> {
    val iter = this.iterator()
    if (!iter.hasNext()) {
        return BatchResponseAsync(emptyList())
    }

    val size = if (this is Collection<*>) this.size else 10
    val pendingResponses = ArrayList<BatchRpcResponse<Result<T, E>>>(size)
    val batch = BatchRpcRequest()
    while (iter.hasNext()) {
        pendingResponses.add(iter.next().batch(batch) as BatchRpcResponse<Result<T, E>>)
    }

    batch.sendAsync()

    return BatchResponseAsync(pendingResponses.map { it.toFuture() })
}

/**
 * Await all [CompletableFuture]s in the list, returning a list of results.
 * */
fun <T> List<CompletableFuture<T>>.await(): List<T> {
    val ret = ArrayList<T>(size)
    for (future in this) {
        ret.add(future.join())
    }
    return ret
}

// Zero-cost typed response class to provide specialized "component" operators. In case it's used as a different
// type, it gets boxed (e.g. `map`, `forEach`, etc...). But since we're just wrapping and delegating a `List`,
// it's still pretty cheap.
@JvmInline
value class BatchResponseAsync<T, E : Result.Error>(
    private val responses: List<CompletableFuture<Result<T, E>>>,
) : List<CompletableFuture<Result<T, E>>> by responses {
    operator fun <O, U : Result.Error> component1() = responses[0] as CompletableFuture<Result<O, U>>
    operator fun <O, U : Result.Error> component2() = responses[1] as CompletableFuture<Result<O, U>>
    operator fun <O, U : Result.Error> component3() = responses[2] as CompletableFuture<Result<O, U>>
    operator fun <O, U : Result.Error> component4() = responses[3] as CompletableFuture<Result<O, U>>
    operator fun <O, U : Result.Error> component5() = responses[4] as CompletableFuture<Result<O, U>>
    operator fun <O, U : Result.Error> component6() = responses[5] as CompletableFuture<Result<O, U>>
    operator fun <O, U : Result.Error> component7() = responses[6] as CompletableFuture<Result<O, U>>
    operator fun <O, U : Result.Error> component8() = responses[7] as CompletableFuture<Result<O, U>>
    operator fun <O, U : Result.Error> component9() = responses[8] as CompletableFuture<Result<O, U>>
    operator fun <O, U : Result.Error> component10() = responses[9] as CompletableFuture<Result<O, U>>
    operator fun <O, U : Result.Error> component11() = responses[10] as CompletableFuture<Result<O, U>>
    operator fun <O, U : Result.Error> component12() = responses[11] as CompletableFuture<Result<O, U>>
}

// -------------------------------------------------------------------------------------------------------------
// PendingInclusion
// -------------------------------------------------------------------------------------------------------------

/**
 * Await for pending transaction to be included in a block (= mined) by blocking the calling thread.
 *
 * @param retries number of attempts to receive a transaction inclusion response
 * @param interval time to wait between retries
 * @param confirmations number of mined blocks required to announce inclusion of the pending transaction
 */
@JvmOverloads
fun <T> PendingInclusion<T>.awaitInclusion(
    retries: Int = DEFAULT_RETRIES,
    interval: Duration = DEFAULT_INCLUSION_INTERVAL,
    confirmations: Int = DEFAULT_CONFIRMATIONS,
): Result<T, PendingInclusion.Error> = runBlocking { inclusion(retries, interval, confirmations) }

/**
 * Asynchronously wait for pending transaction to be included in a block (= mined), as a [CompletableFuture].
 *
 * @param retries number of attempts to receive a transaction inclusion response
 * @param interval time to wait between retries
 * @param confirmations number of mined blocks required to announce inclusion of the pending transaction
 */
@JvmOverloads
fun <T> PendingInclusion<T>.inclusionAsync(
    retries: Int = DEFAULT_RETRIES,
    interval: Duration = DEFAULT_INCLUSION_INTERVAL,
    confirmations: Int = DEFAULT_CONFIRMATIONS,
): CompletableFuture<Result<T, PendingInclusion.Error>> {
    return CoroutineScope(Dispatchers.Default)
        .async { inclusion(retries, interval, confirmations) }
        .asCompletableFuture()
}
