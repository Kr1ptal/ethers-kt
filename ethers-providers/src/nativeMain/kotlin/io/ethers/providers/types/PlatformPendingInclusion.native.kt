package io.ethers.providers.types

import io.ethers.core.Result
import kotlin.time.Duration

/**
 * Native targets have no blocking or [java.util.concurrent.CompletableFuture] primitives to expose, so this adds
 * no members beyond the suspending [inclusion] that [PendingInclusion] implements.
 *
 * Default argument values live on the `expect` declaration and must not be repeated here.
 */
actual interface PlatformPendingInclusion<T> {
    actual suspend fun inclusion(
        retries: Int,
        interval: Duration,
        confirmations: Int,
    ): Result<T, PendingInclusion.Error>
}
