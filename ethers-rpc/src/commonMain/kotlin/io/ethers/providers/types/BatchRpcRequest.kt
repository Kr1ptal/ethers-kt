@file:Suppress("JavaDefaultMethodsNotOverriddenByDelegation", "UNCHECKED_CAST")

package io.ethers.providers.types

import io.ethers.core.Result
import io.ethers.core.ThrowableError
import io.ethers.core.failure
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.RpcError
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmOverloads

/**
 * Seam through which each platform adds its own conveniences to [BatchRpcRequest].
 *
 * It exists so platform helpers can be inherited *members* rather than extension functions - Java callers get
 * `batch.sendAwait()` instead of a static call. All implementation stays in ordinary common code in
 * [BatchRpcRequest].
 *
 * JVM and Android actualize this with blocking and `CompletableFuture` variants. A platform without those
 * primitives actualizes it with no extra members.
 */
expect abstract class PlatformBatchRpcRequest() {
    abstract suspend fun send(): Boolean
}

/**
 * Single-shot batch request.
 * */
class BatchRpcRequest @JvmOverloads constructor(defaultSize: Int = 10) : PlatformBatchRpcRequest() {
    // Enable batchSent modification for batch execution via JsonRpcClient.requestBatch
    internal val batchSent = atomic(false)

    /**
     * The calls in this batch, in the order they were added. [JsonRpcClient.requestBatch] sends them and completes
     * each one's response with [complete].
     */
    val requests: List<RpcCall<*>>
        field = ArrayList(defaultSize)

    internal val responses: List<CompletableDeferred<Result<*, RpcError>>>
        field = ArrayList(defaultSize)

    private var client: JsonRpcClient? = null

    /**
     * Returns true if this batch has no requests.
     * */
    val isEmpty: Boolean get() = requests.isEmpty()

    /**
     * Add a [RpcCall] to this batch.
     *
     * NOTE: The returned response should not be awaited until the batch is sent.
     * Doing so will throw an exception to prevent suspending indefinitely.
     */
    fun <T> addRpcCall(request: RpcCall<T>): BatchRpcResponse<Result<T, RpcError>> {
        if (client == null) {
            client = request.client
        } else if (client !== request.client) {
            throw IllegalArgumentException("All requests must use the same client")
        }

        val response = CompletableDeferred<Result<T, RpcError>>()

        requests.add(request)
        responses.add(response as CompletableDeferred<Result<*, RpcError>>)

        return BatchRpcResponse(response) { batchSent.value }
    }

    /**
     * Send the batch request without blocking the calling thread.
     */
    override suspend fun send(): Boolean {
        val client = client ?: return false
        markAsSent()
        return client.requestBatch(this)
    }

    /**
     * Send the batch through [client] rather than the client its calls were made with, without blocking the calling
     * thread. A [JsonRpcClient] that wraps another uses this to forward a batch, or a new batch it builds, to the
     * wrapped client.
     */
    suspend fun sendWith(client: JsonRpcClient): Boolean {
        markAsSent()
        return client.requestBatch(this)
    }

    /**
     * Complete the response of the call at [index] in [requests] with [result], which must be a result of that
     * call's type. Returns false if the response was already completed.
     */
    fun complete(index: Int, result: Result<*, RpcError>): Boolean = responses[index].complete(result)

    /**
     * Complete each call the batch response did not answer with [error]. Returns true if there was any, so a client
     * can report the batch as failed.
     */
    internal fun failUnanswered(error: Result<*, RpcError>): Boolean {
        var any = false
        for (response in responses) {
            if (response.complete(error)) any = true
        }
        return any
    }

    /**
     * Encode the params of every call in [requests], each as the UTF-8 text of its JSON array, returned in the same
     * order. A batch is sent whole or not at all, so if the params of any call can't be encoded, every call in the batch
     * fails with an error naming that call before the exception is rethrown to the sender, as [JsonRpcClient.request]
     * does for a single call. Without that, a caller awaiting a call's response would wait forever.
     */
    internal fun encodeParams(): List<ByteArray> {
        val params = ArrayList<ByteArray>(requests.size)
        try {
            for (call in requests) {
                params.add(call.params.encode())
            }
        } catch (e: Exception) {
            // every call before the failing one was encoded, so its index is the number encoded so far
            val index = params.size
            val call = requests[index]
            val message = "Batch not sent, params of call $index (${call.method}) can't be encoded: ${e.message}"
            failUnanswered(failure(RpcError(RpcError.CODE_CALL_FAILED, message, null, e)))
            throw e
        }
        return params
    }

    /** Whether some call has not been completed yet. */
    internal fun hasUnanswered(): Boolean = responses.any { !it.isCompleted }

    internal fun sendDeferred(): Deferred<Boolean> = CoroutineScope(Dispatchers.Default)
        .async(start = CoroutineStart.UNDISPATCHED) { send() }

    internal fun markAsSent() {
        if (!batchSent.compareAndSet(expect = false, update = true)) {
            throw IllegalStateException("Batch already sent")
        }
    }
}

/**
 * Unwrap all responses, throwing an exception if any of them is an error.
 * */
fun <T, E : ThrowableError> Iterable<Result<T, E>>.unwrap(): UnwrappedBatchResponse<T> {
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
 * Batch-send all requests without blocking the calling thread.
 */
suspend fun <T, E : ThrowableError> Iterable<RpcRequest<out T, E>>.send(): BatchResponse<T, E> {
    val iter = iterator()
    if (!iter.hasNext()) {
        return BatchResponse(emptyList())
    }

    val size = if (this is Collection<*>) this.size else 10
    val pendingResponses = ArrayList<BatchRpcResponse<Result<T, E>>>(size)
    val batch = BatchRpcRequest(size)
    while (iter.hasNext()) {
        pendingResponses.add(iter.next().batch(batch) as BatchRpcResponse<Result<T, E>>)
    }

    batch.send()

    val responses = ArrayList<Result<T, E>>(pendingResponses.size)
    for (response in pendingResponses) {
        responses.add(response.await())
    }
    return BatchResponse(responses)
}

/**
 * Unwrap all [Result]'s, throwing an exception if any of them is an error, otherwise returning a list
 * of success values.
 * */
fun <T, E : ThrowableError> List<Result<T, E>>.unwrap(): List<T> {
    val ret = ArrayList<T>(size)
    for (result in this) {
        ret.add(result.unwrap())
    }
    return ret
}

// Zero-cost typed response classes to provide specialized "component" operators. In case it's used as a different
// type, it gets boxed (e.g. `map`, `forEach`, etc...). But since we're just wrapping and delegating a `List`,
// it's still pretty cheap.
@JvmInline
value class BatchResponse<T, E : ThrowableError>(
    private val responses: List<Result<T, E>>,
) : List<Result<T, E>> by responses {
    operator fun <O, U : ThrowableError> component1() = responses[0] as Result<O, U>
    operator fun <O, U : ThrowableError> component2() = responses[1] as Result<O, U>
    operator fun <O, U : ThrowableError> component3() = responses[2] as Result<O, U>
    operator fun <O, U : ThrowableError> component4() = responses[3] as Result<O, U>
    operator fun <O, U : ThrowableError> component5() = responses[4] as Result<O, U>
    operator fun <O, U : ThrowableError> component6() = responses[5] as Result<O, U>
    operator fun <O, U : ThrowableError> component7() = responses[6] as Result<O, U>
    operator fun <O, U : ThrowableError> component8() = responses[7] as Result<O, U>
    operator fun <O, U : ThrowableError> component9() = responses[8] as Result<O, U>
    operator fun <O, U : ThrowableError> component10() = responses[9] as Result<O, U>
    operator fun <O, U : ThrowableError> component11() = responses[10] as Result<O, U>
    operator fun <O, U : ThrowableError> component12() = responses[11] as Result<O, U>
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
