package io.ethers.providers.types

/**
 * Native targets have no blocking primitives to expose, so this adds no members beyond the suspending [send] that
 * [BatchRpcRequest] implements.
 */
actual abstract class PlatformBatchRpcRequest actual constructor() {
    actual abstract suspend fun send(): Boolean
}
