package io.ethers.providers.types

import io.channels.core.ChannelReceiver
import io.ethers.core.Result
import io.ethers.core.ThrowableError

/**
 * Native targets have no blocking primitives to expose, so this adds no members beyond the suspending [send] that
 * [RpcSubscribe] implements.
 */
actual interface PlatformRpcSubscribe<T : Any, E : ThrowableError> {
    actual suspend fun send(): Result<ChannelReceiver<T>, E>
}
