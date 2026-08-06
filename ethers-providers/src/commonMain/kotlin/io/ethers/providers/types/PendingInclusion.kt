package io.ethers.providers.types

import io.ethers.core.Result
import io.ethers.core.ThrowableError
import io.ethers.core.types.Hash
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal const val DEFAULT_RETRIES = 3
internal val DEFAULT_INCLUSION_INTERVAL = 6.seconds
internal const val DEFAULT_CONFIRMATIONS = 1

/**
 * Seam through which each platform adds its own conveniences to [PendingInclusion].
 *
 * It exists so platform helpers can be inherited *members* rather than extension functions - Java callers get
 * `pendingTx.awaitInclusion()` instead of a static call. All implementation stays in ordinary common code in [PendingInclusion].
 *
 * JVM and Android actualize this with blocking and `CompletableFuture` variants. A platform without those
 * primitives actualizes it with no extra members.
 */
expect interface PlatformPendingInclusion<T> {
    suspend fun inclusion(
        retries: Int = DEFAULT_RETRIES,
        interval: Duration = DEFAULT_INCLUSION_INTERVAL,
        confirmations: Int = DEFAULT_CONFIRMATIONS,
    ): Result<T, PendingInclusion.Error>
}

/**
 * Result that is pending block inclusion (e.i. getting mined).
 * */
interface PendingInclusion<T> : PlatformPendingInclusion<T> {
    sealed class Error : ThrowableError {
        data class NoInclusion(val txHash: Hash, val retries: Int) : Error()
        data class RpcError(val txHash: Hash, val error: io.ethers.providers.RpcError) : Error()
    }
}
