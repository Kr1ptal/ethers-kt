package io.ethers.providers.types

import io.ethers.core.Result
import io.channels.core.ChannelReceiver

/**
 * Native targets have no blocking primitives to expose, so this adds no members beyond the suspending [send] that
 * [RpcSubscribe] implements.
 */
actual interface PlatformRpcSubscribe<T : Any, E : Result.Error> {
    actual suspend fun send(): Result<ChannelReceiver<T>, E>
}
