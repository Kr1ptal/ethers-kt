package io.ethers.solana.providers

import io.ethers.core.Result
import io.ethers.solana.types.rpc.Commitment
import io.ethers.solana.types.rpc.SignatureStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration

actual interface PlatformPendingSolanaTransaction {
    actual suspend fun confirmation(
        commitment: Commitment,
        interval: Duration,
        timeout: Duration,
    ): Result<SignatureStatus, PendingSolanaTransaction.Error>

    /** Wait for the cluster to confirm the transaction, blocking the calling thread. */
    fun awaitConfirmation(): Result<SignatureStatus, PendingSolanaTransaction.Error> = awaitConfirmation(Commitment.CONFIRMED, DEFAULT_CONFIRMATION_INTERVAL, DEFAULT_CONFIRMATION_TIMEOUT)

    /** Wait until the transaction reaches [commitment], blocking the calling thread. */
    fun awaitConfirmation(commitment: Commitment): Result<SignatureStatus, PendingSolanaTransaction.Error> = awaitConfirmation(commitment, DEFAULT_CONFIRMATION_INTERVAL, DEFAULT_CONFIRMATION_TIMEOUT)

    /**
     * Wait until the transaction reaches [commitment], blocking the calling thread.
     *
     * @param interval how often the transaction's status is polled
     * @param timeout how long to wait when expiry cannot be checked, because no blockhash was supplied
     */
    fun awaitConfirmation(
        commitment: Commitment,
        interval: Duration,
        timeout: Duration,
    ): Result<SignatureStatus, PendingSolanaTransaction.Error> = runBlocking { confirmation(commitment, interval, timeout) }

    /** Wait for the cluster to confirm the transaction, as a [CompletableFuture]. */
    fun confirmationAsync(): CompletableFuture<Result<SignatureStatus, PendingSolanaTransaction.Error>> = confirmationAsync(Commitment.CONFIRMED, DEFAULT_CONFIRMATION_INTERVAL, DEFAULT_CONFIRMATION_TIMEOUT)

    /** Wait until the transaction reaches [commitment], as a [CompletableFuture]. */
    fun confirmationAsync(commitment: Commitment): CompletableFuture<Result<SignatureStatus, PendingSolanaTransaction.Error>> = confirmationAsync(commitment, DEFAULT_CONFIRMATION_INTERVAL, DEFAULT_CONFIRMATION_TIMEOUT)

    /**
     * Wait until the transaction reaches [commitment], as a [CompletableFuture].
     *
     * @param interval how often the transaction's status is polled
     * @param timeout how long to wait when expiry cannot be checked, because no blockhash was supplied
     */
    fun confirmationAsync(
        commitment: Commitment,
        interval: Duration,
        timeout: Duration,
    ): CompletableFuture<Result<SignatureStatus, PendingSolanaTransaction.Error>> {
        return CoroutineScope(Dispatchers.Default)
            .async { confirmation(commitment, interval, timeout) }
            .asCompletableFuture()
    }
}
