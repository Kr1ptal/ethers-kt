package io.ethers.providers.types

import kotlinx.coroutines.Deferred

/**
 * Seam through which each platform adds its own conveniences to [BatchRpcResponse].
 *
 * It exists so platform helpers can be inherited *members* rather than extension functions. Without it the only
 * accessor is the suspending [BatchRpcResponse.await], which Java cannot call at all. All implementation stays in
 * ordinary common code in [BatchRpcResponse].
 *
 * JVM and Android actualize this with blocking and `CompletableFuture` variants. A platform without those
 * primitives actualizes it with no extra members.
 */
expect abstract class PlatformBatchRpcResponse<T>() {
    abstract suspend fun await(): T
}

/**
 * A pending response for a request added to a [BatchRpcRequest].
 */
class BatchRpcResponse<T> internal constructor(
    private val awaitResponse: suspend () -> T,
    private val canAwait: () -> Boolean,
) : PlatformBatchRpcResponse<T>() {
    internal constructor(response: Deferred<T>, canAwait: () -> Boolean) :
        this({ response.await() }, canAwait)

    /**
     * Await the response after its batch has been sent.
     */
    override suspend fun await(): T {
        check(canAwait()) {
            "Request has not been sent yet. Awaiting would suspend indefinitely."
        }
        return awaitResponse()
    }

    internal fun <R> map(mapper: suspend (T) -> R): BatchRpcResponse<R> {
        return BatchRpcResponse({ mapper(await()) }, canAwait)
    }
}
