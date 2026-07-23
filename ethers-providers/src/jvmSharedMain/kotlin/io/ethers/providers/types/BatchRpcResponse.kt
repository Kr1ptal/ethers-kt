package io.ethers.providers.types

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import java.util.concurrent.CompletableFuture

/**
 * A pending response for a request added to a [BatchRpcRequest].
 */
class BatchRpcResponse<T> internal constructor(
    private val awaitResponse: suspend () -> T,
    private val canAwait: () -> Boolean,
) {
    internal constructor(response: Deferred<T>, canAwait: () -> Boolean) :
        this({ response.await() }, canAwait)

    /**
     * Await the response after its batch has been sent.
     */
    suspend fun await(): T {
        check(canAwait()) {
            "Request has not been sent yet. Awaiting would suspend indefinitely."
        }
        return awaitResponse()
    }

    internal fun <R> map(mapper: (T) -> R): BatchRpcResponse<R> {
        return BatchRpcResponse({ mapper(await()) }, { true })
    }
}

/**
 * Convert this pending batch response to a JVM [CompletableFuture].
 */
fun <T> BatchRpcResponse<T>.toFuture(): CompletableFuture<T> {
    return CoroutineScope(Dispatchers.Default)
        .async(start = CoroutineStart.UNDISPATCHED) { await() }
        .asCompletableFuture()
}
