package io.ethers.providers.types

import kotlinx.coroutines.Deferred

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

    internal fun <R> map(mapper: suspend (T) -> R): BatchRpcResponse<R> {
        return BatchRpcResponse({ mapper(await()) }, canAwait)
    }
}
