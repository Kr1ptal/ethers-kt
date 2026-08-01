package io.ethers.providers.types

import io.channels.core.ChannelReceiver
import io.ethers.core.Result
import kotlin.time.Duration

/**
 * Seams through which each platform can add its own conveniences to the provider API.
 *
 * They exist so platform-specific helpers can be inherited *members* rather than extension functions - Java callers
 * get `request.sendAwait()` instead of `BlockingRequests.sendAwait(request)`. Everything else, including all
 * implementation, stays in ordinary common code in the types that extend these.
 *
 * On JVM and Android the actuals add blocking and `CompletableFuture` variants. A platform without those primitives
 * simply actualizes them with no extra members.
 */
expect abstract class PlatformRpcRequest<T, E : Result.Error>() {
    abstract suspend fun send(): Result<T, E>
}

expect interface PlatformRpcSubscribe<T : Any, E : Result.Error> {
    suspend fun send(): Result<ChannelReceiver<T>, E>
}

expect interface PlatformPendingInclusion<T> {
    suspend fun inclusion(
        retries: Int = DEFAULT_RETRIES,
        interval: Duration = DEFAULT_INCLUSION_INTERVAL,
        confirmations: Int = DEFAULT_CONFIRMATIONS,
    ): Result<T, PendingInclusion.Error>
}
