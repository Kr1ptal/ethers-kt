package io.ethers.providers.types

import io.ethers.core.Result
import io.ethers.core.ThrowableError

/**
 * Native targets have no blocking or [java.util.concurrent.CompletableFuture] primitives to expose, so this adds
 * no members beyond the suspending [send] that [RpcRequest] implements.
 */
actual abstract class PlatformRpcRequest<T, E : ThrowableError> actual constructor() {
    actual abstract suspend fun send(): Result<T, E>
}
