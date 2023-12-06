package io.ethers.providers.types

import io.ethers.providers.JsonRpcClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single-shot batch request.
 * */
class BatchRpcRequest @JvmOverloads constructor(defaultSize: Int = 10) {
    private val batchSent = AtomicBoolean(false)

    private val _requests = ArrayList<RpcCall<*>>(defaultSize)
    internal val requests: List<RpcCall<*>> get() = _requests

    private val _responses = ArrayList<CompletableFuture<RpcResponse<*>>>(defaultSize)
    internal val responses: List<CompletableFuture<RpcResponse<*>>> get() = _responses

    private var client: JsonRpcClient? = null

    /**
     * Add RPC request to this batch.
     *
     * NOTE: The returned CompletableFuture should not be awaited until the batch is sent.
     * Doing so will throw an exception to prevent blocking indefinitely.
     */
    fun <T> addRpcCall(request: RpcCall<T>): CompletableFuture<RpcResponse<T>> {
        if (client == null) {
            client = request.client
        } else if (client !== request.client) {
            throw IllegalArgumentException("All requests must use the same client")
        }

        val future = BatchCompletableFuture<RpcResponse<T>>(this)

        _requests.add(request)
        _responses.add(future as CompletableFuture<RpcResponse<*>>)

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
            throw IllegalStateException("No requests added")
        }

        if (!batchSent.compareAndSet(false, true)) {
            throw IllegalStateException("Batch already sent")
        }

        return client!!.requestBatch(this)
    }

    /**
     * Helper class to prevent awaiting the response until the batch has been sent.
     * */
    private class BatchCompletableFuture<T>(private val batch: BatchRpcRequest) : CompletableFuture<T>() {
        override fun get(): T {
            verifyBatchSent()
            return super.get()
        }

        override fun get(timeout: Long, unit: TimeUnit): T {
            verifyBatchSent()
            return super.get(timeout, unit)
        }

        override fun getNow(valueIfAbsent: T): T {
            verifyBatchSent()
            return super.getNow(valueIfAbsent)
        }

        override fun join(): T {
            verifyBatchSent()
            return super.join()
        }

        private fun verifyBatchSent() {
            if (!batch.batchSent.get()) {
                throw IllegalStateException("Batch has not been sent yet. Awaiting would block indefinitely.")
            }
        }
    }

    companion object {
        // Provide custom JVM names for these function because the name gets mangled due to inline class return type
        @JvmStatic
        @JvmName("sendAwait")
        fun <T> sendAwait(requests: Iterable<RpcRequest<out T>>): BatchResponse<T> {
            return requests.sendAwait()
        }

        @JvmStatic
        @JvmName("sendAsync")
        fun <T> sendAsync(requests: Iterable<RpcRequest<out T>>): BatchResponseAsync<T> {
            return requests.sendAsync()
        }
    }
}

/**
 * Unwrap all responses, throwing an exception if any of them is an error.
 * */
fun <T> Iterable<RpcResponse<out T>>.resultOrThrow(): UnwrappedBatchResponse<T> {
    val iter = this.iterator()
    if (!iter.hasNext()) {
        return UnwrappedBatchResponse(emptyList())
    }

    val size = if (this is Collection<*>) this.size else 10
    val ret = ArrayList<T>(size)
    while (iter.hasNext()) {
        ret.add(iter.next().resultOrThrow())
    }

    return UnwrappedBatchResponse(ret)
}

/**
 * Batch-send all requests, awaiting the result by blocking the calling thread.
 */
fun <T> Iterable<RpcRequest<out T>>.sendAwait(): BatchResponse<T> {
    val async = sendAsync()
    val ret = ArrayList<RpcResponse<T>>(async.size)
    for (future in async) {
        ret.add(future.join())
    }

    return BatchResponse(ret)
}

/**
 * Batch-send all requests asynchronously.
 */
fun <T> Iterable<RpcRequest<out T>>.sendAsync(): BatchResponseAsync<T> {
    val iter = this.iterator()
    if (!iter.hasNext()) {
        return BatchResponseAsync(emptyList())
    }

    val size = if (this is Collection<*>) this.size else 10
    val futures = ArrayList<CompletableFuture<RpcResponse<T>>>(size)
    val batch = BatchRpcRequest()
    while (iter.hasNext()) {
        futures.add(iter.next().batch(batch) as CompletableFuture<RpcResponse<T>>)
    }

    batch.sendAsync()

    return BatchResponseAsync(futures)
}

// Zero-cost typed response classes to provide specialized "component" operators. In case it's used as a different type
// it gets boxed (e.g. `map`, `forEach`, etc...). But since we're just wrapping and delegating a `List`,
// it's still pretty cheap.
@JvmInline
value class BatchResponseAsync<T>(
    private val responses: List<CompletableFuture<RpcResponse<T>>>,
) : List<CompletableFuture<RpcResponse<T>>> by responses {
    operator fun <O> component1() = responses[0] as CompletableFuture<RpcResponse<O>>
    operator fun <O> component2() = responses[1] as CompletableFuture<RpcResponse<O>>
    operator fun <O> component3() = responses[2] as CompletableFuture<RpcResponse<O>>
    operator fun <O> component4() = responses[3] as CompletableFuture<RpcResponse<O>>
    operator fun <O> component5() = responses[4] as CompletableFuture<RpcResponse<O>>
    operator fun <O> component6() = responses[5] as CompletableFuture<RpcResponse<O>>
    operator fun <O> component7() = responses[6] as CompletableFuture<RpcResponse<O>>
    operator fun <O> component8() = responses[7] as CompletableFuture<RpcResponse<O>>
    operator fun <O> component9() = responses[8] as CompletableFuture<RpcResponse<O>>
    operator fun <O> component10() = responses[9] as CompletableFuture<RpcResponse<O>>
    operator fun <O> component11() = responses[10] as CompletableFuture<RpcResponse<O>>
    operator fun <O> component12() = responses[11] as CompletableFuture<RpcResponse<O>>
}

@JvmInline
value class BatchResponse<T>(
    private val responses: List<RpcResponse<T>>,
) : List<RpcResponse<T>> by responses {
    operator fun <O> component1() = responses[0] as RpcResponse<O>
    operator fun <O> component2() = responses[1] as RpcResponse<O>
    operator fun <O> component3() = responses[2] as RpcResponse<O>
    operator fun <O> component4() = responses[3] as RpcResponse<O>
    operator fun <O> component5() = responses[4] as RpcResponse<O>
    operator fun <O> component6() = responses[5] as RpcResponse<O>
    operator fun <O> component7() = responses[6] as RpcResponse<O>
    operator fun <O> component8() = responses[7] as RpcResponse<O>
    operator fun <O> component9() = responses[8] as RpcResponse<O>
    operator fun <O> component10() = responses[9] as RpcResponse<O>
    operator fun <O> component11() = responses[10] as RpcResponse<O>
    operator fun <O> component12() = responses[11] as RpcResponse<O>
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
