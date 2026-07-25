package io.ethers.providers.types

import io.ethers.core.Result
import io.ethers.core.types.Hash
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal const val DEFAULT_RETRIES = 3
internal val DEFAULT_INCLUSION_INTERVAL = 6.seconds
internal const val DEFAULT_CONFIRMATIONS = 1

/**
 * Result that is pending block inclusion (e.i. getting mined).
 * */
interface PendingInclusion<T> {
    /**
     * Wait for pending transaction to be included in a block (= mined), without blocking the calling thread.
     *
     * @param retries number of attempts to receive a transaction inclusion response
     * @param interval time to wait between retries
     * @param confirmations number of mined blocks required to announce inclusion of the pending transaction
     */
    suspend fun inclusion(
        retries: Int = DEFAULT_RETRIES,
        interval: Duration = DEFAULT_INCLUSION_INTERVAL,
        confirmations: Int = DEFAULT_CONFIRMATIONS,
    ): Result<T, Error>

    sealed class Error : Result.Error {
        data class NoInclusion(val txHash: Hash, val retries: Int) : Error()
        data class RpcError(val txHash: Hash, val error: io.ethers.providers.RpcError) : Error()
    }
}
