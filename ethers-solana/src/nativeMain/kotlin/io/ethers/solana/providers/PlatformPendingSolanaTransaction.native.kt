package io.ethers.solana.providers

import io.ethers.core.Result
import io.ethers.solana.types.rpc.Commitment
import io.ethers.solana.types.rpc.SignatureStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlin.native.HiddenFromObjC
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Swift sees neither Kotlin default arguments nor [Duration], a value class exported as its raw encoded `Int64`.
 * So the defaults are spelled out as overloads, and the timing overload takes seconds, as Foundation's
 * `TimeInterval` does.
 */
actual interface PlatformPendingSolanaTransaction {
    @HiddenFromObjC
    actual suspend fun confirmation(
        commitment: Commitment,
        interval: Duration,
        timeout: Duration,
    ): Result<SignatureStatus, PendingSolanaTransaction.Error>

    /** Wait for the cluster to confirm the transaction. */
    suspend fun confirmation(): Result<SignatureStatus, PendingSolanaTransaction.Error> = confirmation(Commitment.CONFIRMED, DEFAULT_CONFIRMATION_INTERVAL, DEFAULT_CONFIRMATION_TIMEOUT)

    /** Wait until the transaction reaches [commitment]. */
    suspend fun confirmation(commitment: Commitment): Result<SignatureStatus, PendingSolanaTransaction.Error> = confirmation(commitment, DEFAULT_CONFIRMATION_INTERVAL, DEFAULT_CONFIRMATION_TIMEOUT)

    /**
     * Wait until the transaction reaches [commitment].
     *
     * @param intervalSeconds how often the transaction's status is polled
     * @param timeoutSeconds how long to wait when expiry cannot be checked, because no blockhash was supplied
     * @throws IllegalArgumentException if either duration is NaN
     */
    @Throws(IllegalArgumentException::class, CancellationException::class)
    suspend fun confirmation(
        commitment: Commitment,
        intervalSeconds: Double,
        timeoutSeconds: Double,
    ): Result<SignatureStatus, PendingSolanaTransaction.Error> = confirmation(commitment, intervalSeconds.seconds, timeoutSeconds.seconds)
}
