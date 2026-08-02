package io.ethers.providers.types

/**
 * Native targets have no blocking or [java.util.concurrent.CompletableFuture] primitives to expose, so this adds
 * no members beyond the suspending [await] that [BatchRpcResponse] implements.
 */
actual abstract class PlatformBatchRpcResponse<T> actual constructor() {
    actual abstract suspend fun await(): T
}
