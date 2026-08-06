@file:JvmName("BlockingBatchRequests")

package io.ethers.providers.types

import io.ethers.core.Result
import io.ethers.core.ThrowableError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture

// Blocking and CompletableFuture-based conveniences over the suspending provider API.
//
// These are JVM/Android-only: `runBlocking` and CompletableFuture have no common equivalent, so they cannot live
// in `commonMain` alongside the rest of the module.

// -------------------------------------------------------------------------------------------------------------
// BatchRpcRequest / BatchRpcResponse
// -------------------------------------------------------------------------------------------------------------

/**
 * Batch-send all requests, awaiting the result by blocking the calling thread.
 */
fun <T, E : ThrowableError> Iterable<RpcRequest<out T, E>>.sendAwait(): BatchResponse<T, E> = runBlocking { send() }

/**
 * Batch-send all requests asynchronously.
 */
fun <T, E : ThrowableError> Iterable<RpcRequest<out T, E>>.sendAsync(): BatchResponseAsync<T, E> {
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

    val scope = CoroutineScope(Dispatchers.Default)
    return BatchResponseAsync(
        pendingResponses.map {
            scope.async(start = CoroutineStart.UNDISPATCHED) { it.await() }
                .asCompletableFuture()
        },
    )
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
value class BatchResponseAsync<T, E : ThrowableError>(
    private val responses: List<CompletableFuture<Result<T, E>>>,
) : List<CompletableFuture<Result<T, E>>> by responses {
    operator fun <O, U : ThrowableError> component1() = responses[0] as CompletableFuture<Result<O, U>>
    operator fun <O, U : ThrowableError> component2() = responses[1] as CompletableFuture<Result<O, U>>
    operator fun <O, U : ThrowableError> component3() = responses[2] as CompletableFuture<Result<O, U>>
    operator fun <O, U : ThrowableError> component4() = responses[3] as CompletableFuture<Result<O, U>>
    operator fun <O, U : ThrowableError> component5() = responses[4] as CompletableFuture<Result<O, U>>
    operator fun <O, U : ThrowableError> component6() = responses[5] as CompletableFuture<Result<O, U>>
    operator fun <O, U : ThrowableError> component7() = responses[6] as CompletableFuture<Result<O, U>>
    operator fun <O, U : ThrowableError> component8() = responses[7] as CompletableFuture<Result<O, U>>
    operator fun <O, U : ThrowableError> component9() = responses[8] as CompletableFuture<Result<O, U>>
    operator fun <O, U : ThrowableError> component10() = responses[9] as CompletableFuture<Result<O, U>>
    operator fun <O, U : ThrowableError> component11() = responses[10] as CompletableFuture<Result<O, U>>
    operator fun <O, U : ThrowableError> component12() = responses[11] as CompletableFuture<Result<O, U>>
}

// Blocking `await()` conveniences over the suspending `awaitSuspend()` batch API.
//
// JVM/Android-only: `runBlocking` has no common equivalent.

fun <R1, R2, E1 : ThrowableError, E2 : ThrowableError> BatchResponse2<PendingResponse<R1, E1>, PendingResponse<R2, E2>>.await() = runBlocking { awaitSuspend() }
fun <R1, R2, R3, E1 : ThrowableError, E2 : ThrowableError, E3 : ThrowableError> BatchResponse3<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>>.await() = runBlocking { awaitSuspend() }
fun <R1, R2, R3, R4, E1 : ThrowableError, E2 : ThrowableError, E3 : ThrowableError, E4 : ThrowableError> BatchResponse4<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>>.await() = runBlocking { awaitSuspend() }
fun <R1, R2, R3, R4, R5, E1 : ThrowableError, E2 : ThrowableError, E3 : ThrowableError, E4 : ThrowableError, E5 : ThrowableError> BatchResponse5<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>>.await() = runBlocking { awaitSuspend() }
fun <R1, R2, R3, R4, R5, R6, E1 : ThrowableError, E2 : ThrowableError, E3 : ThrowableError, E4 : ThrowableError, E5 : ThrowableError, E6 : ThrowableError> BatchResponse6<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>, PendingResponse<R6, E6>>.await() = runBlocking { awaitSuspend() }
fun <R1, R2, R3, R4, R5, R6, R7, E1 : ThrowableError, E2 : ThrowableError, E3 : ThrowableError, E4 : ThrowableError, E5 : ThrowableError, E6 : ThrowableError, E7 : ThrowableError> BatchResponse7<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>, PendingResponse<R6, E6>, PendingResponse<R7, E7>>.await() = runBlocking { awaitSuspend() }
fun <R1, R2, R3, R4, R5, R6, R7, R8, E1 : ThrowableError, E2 : ThrowableError, E3 : ThrowableError, E4 : ThrowableError, E5 : ThrowableError, E6 : ThrowableError, E7 : ThrowableError, E8 : ThrowableError> BatchResponse8<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>, PendingResponse<R6, E6>, PendingResponse<R7, E7>, PendingResponse<R8, E8>>.await() = runBlocking { awaitSuspend() }
fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, E1 : ThrowableError, E2 : ThrowableError, E3 : ThrowableError, E4 : ThrowableError, E5 : ThrowableError, E6 : ThrowableError, E7 : ThrowableError, E8 : ThrowableError, E9 : ThrowableError> BatchResponse9<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>, PendingResponse<R6, E6>, PendingResponse<R7, E7>, PendingResponse<R8, E8>, PendingResponse<R9, E9>>.await() = runBlocking { awaitSuspend() }
fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, E1 : ThrowableError, E2 : ThrowableError, E3 : ThrowableError, E4 : ThrowableError, E5 : ThrowableError, E6 : ThrowableError, E7 : ThrowableError, E8 : ThrowableError, E9 : ThrowableError, E10 : ThrowableError> BatchResponse10<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>, PendingResponse<R6, E6>, PendingResponse<R7, E7>, PendingResponse<R8, E8>, PendingResponse<R9, E9>, PendingResponse<R10, E10>>.await() = runBlocking { awaitSuspend() }
fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, E1 : ThrowableError, E2 : ThrowableError, E3 : ThrowableError, E4 : ThrowableError, E5 : ThrowableError, E6 : ThrowableError, E7 : ThrowableError, E8 : ThrowableError, E9 : ThrowableError, E10 : ThrowableError, E11 : ThrowableError> BatchResponse11<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>, PendingResponse<R6, E6>, PendingResponse<R7, E7>, PendingResponse<R8, E8>, PendingResponse<R9, E9>, PendingResponse<R10, E10>, PendingResponse<R11, E11>>.await() = runBlocking { awaitSuspend() }
fun <R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, E1 : ThrowableError, E2 : ThrowableError, E3 : ThrowableError, E4 : ThrowableError, E5 : ThrowableError, E6 : ThrowableError, E7 : ThrowableError, E8 : ThrowableError, E9 : ThrowableError, E10 : ThrowableError, E11 : ThrowableError, E12 : ThrowableError> BatchResponse12<PendingResponse<R1, E1>, PendingResponse<R2, E2>, PendingResponse<R3, E3>, PendingResponse<R4, E4>, PendingResponse<R5, E5>, PendingResponse<R6, E6>, PendingResponse<R7, E7>, PendingResponse<R8, E8>, PendingResponse<R9, E9>, PendingResponse<R10, E10>, PendingResponse<R11, E11>, PendingResponse<R12, E12>>.await() = runBlocking { awaitSuspend() }
