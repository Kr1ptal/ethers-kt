package io.ethers.providers.types

import io.ethers.core.Result
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.RpcError
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single-shot batch request.
 * */
class BatchRpcRequest @JvmOverloads constructor(defaultSize: Int = 10) {
    // Enable batchSent modification for batch execution via JsonRpcClient.requestBatch
    internal val batchSent = AtomicBoolean(false)

    private val _requests = ArrayList<RpcCall<*>>(defaultSize)
    internal val requests: List<RpcCall<*>> get() = _requests

    private val _responses = ArrayList<CompletableFuture<Result<*, RpcError>>>(defaultSize)
    internal val responses: List<CompletableFuture<Result<*, RpcError>>> get() = _responses

    private var client: JsonRpcClient? = null

    /**
     * Returns true if this batch has no requests.
     * */
    val isEmpty: Boolean get() = requests.isEmpty()

    /**
     * Add RPC request to this batch.
     *
     * NOTE: The returned CompletableFuture should not be awaited until the batch is sent.
     * Doing so will throw an exception to prevent blocking indefinitely.
     */
    fun <T> addRpcCall(request: RpcCall<T>): CompletableFuture<Result<T, RpcError>> {
        if (client == null) {
            client = request.client
        } else if (client !== request.client) {
            throw IllegalArgumentException("All requests must use the same client")
        }

        val future = ConditionalCompletableFuture<Result<T, RpcError>>(batchSent)

        _requests.add(request)
        _responses.add(future as CompletableFuture<Result<*, RpcError>>)

        return future
    }

    /**
     * Send batch request and await the result by blocking the calling thread.
     */
    fun sendAwait(): Boolean {
        return sendAsync().join()
    }

    /**
     * Asynchronously send batch request.
     */
    fun sendAsync(): CompletableFuture<Boolean> {
        if (client == null) {
            return CompletableFuture.completedFuture(false)
        }

        markAsSent()

        return client!!.requestBatch(this)
    }

    internal fun markAsSent() {
        if (!batchSent.compareAndSet(false, true)) {
            throw IllegalStateException("Batch already sent")
        }
    }

    companion object {
        // Provide custom JVM names for these function because the name gets mangled due to inline class return type
        @JvmStatic
        @JvmName("sendAwait")
        fun <T, E : Result.Error> sendAwait(requests: Iterable<RpcRequest<out T, E>>): BatchResponse<T, E> {
            return requests.sendAwait()
        }

        @JvmStatic
        @JvmName("sendAsync")
        fun <T, E : Result.Error> sendAsync(requests: Iterable<RpcRequest<T, E>>): BatchResponseAsync<T, E> {
            return requests.sendAsync()
        }
    }
}

/**
 * Unwrap all responses, throwing an exception if any of them is an error.
 * */
fun <T, E : Result.Error> Iterable<Result<T, E>>.unwrap(): UnwrappedBatchResponse<T> {
    val iter = this.iterator()
    if (!iter.hasNext()) {
        return UnwrappedBatchResponse(emptyList())
    }

    val size = if (this is Collection<*>) this.size else 10
    val ret = ArrayList<T>(size)
    while (iter.hasNext()) {
        ret.add(iter.next().unwrap())
    }

    return UnwrappedBatchResponse(ret)
}

/**
 * Batch-send all requests, awaiting the result by blocking the calling thread.
 */
fun <T, E : Result.Error> Iterable<RpcRequest<out T, E>>.sendAwait(): BatchResponse<T, E> {
    val async = sendAsync()
    val ret = ArrayList<Result<T, E>>(async.size)
    for (future in async) {
        ret.add(future.join())
    }

    return BatchResponse(ret)
}

/**
 * Batch-send all requests asynchronously.
 */
fun <T, E : Result.Error> Iterable<RpcRequest<out T, E>>.sendAsync(): BatchResponseAsync<T, E> {
    val iter = this.iterator()
    if (!iter.hasNext()) {
        return BatchResponseAsync(emptyList())
    }

    val size = if (this is Collection<*>) this.size else 10
    val futures = ArrayList<CompletableFuture<Result<T, E>>>(size)
    val batch = BatchRpcRequest()
    while (iter.hasNext()) {
        futures.add(iter.next().batch(batch) as CompletableFuture<Result<T, E>>)
    }

    batch.sendAsync()

    return BatchResponseAsync(futures)
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

/**
 * Unwrap all [Result]'s, throwing an exception if any of them is an error, otherwise returning a list
 * of success values.
 * */
fun <T, E : Result.Error> List<Result<T, E>>.unwrap(): List<T> {
    val ret = ArrayList<T>(size)
    for (result in this) {
        ret.add(result.unwrap())
    }
    return ret
}

// Zero-cost typed response classes to provide specialized "component" operators. In case it's used as a different type
// it gets boxed (e.g. `map`, `forEach`, etc...). But since we're just wrapping and delegating a `List`,
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

@JvmInline
value class BatchResponse<T, E : Result.Error>(
    private val responses: List<Result<T, E>>,
) : List<Result<T, E>> by responses {
    operator fun <O, U : Result.Error> component1() = responses[0] as Result<O, U>
    operator fun <O, U : Result.Error> component2() = responses[1] as Result<O, U>
    operator fun <O, U : Result.Error> component3() = responses[2] as Result<O, U>
    operator fun <O, U : Result.Error> component4() = responses[3] as Result<O, U>
    operator fun <O, U : Result.Error> component5() = responses[4] as Result<O, U>
    operator fun <O, U : Result.Error> component6() = responses[5] as Result<O, U>
    operator fun <O, U : Result.Error> component7() = responses[6] as Result<O, U>
    operator fun <O, U : Result.Error> component8() = responses[7] as Result<O, U>
    operator fun <O, U : Result.Error> component9() = responses[8] as Result<O, U>
    operator fun <O, U : Result.Error> component10() = responses[9] as Result<O, U>
    operator fun <O, U : Result.Error> component11() = responses[10] as Result<O, U>
    operator fun <O, U : Result.Error> component12() = responses[11] as Result<O, U>
}

@JvmInline
value class UnwrappedBatchResponse<T>(
    private val responses: List<T>,
) : List<T> by responses {
    operator fun <O> component1() = responses[0] as O
    operator fun <O> component2() = responses[1] as O
    operator fun <O> component3() = responses[2] as O
    operator fun <O> component4() = responses[3] as O
    operator fun <O> component5() = responses[4] as O
    operator fun <O> component6() = responses[5] as O
    operator fun <O> component7() = responses[6] as O
    operator fun <O> component8() = responses[7] as O
    operator fun <O> component9() = responses[8] as O
    operator fun <O> component10() = responses[9] as O
    operator fun <O> component11() = responses[10] as O
    operator fun <O> component12() = responses[11] as O
}
