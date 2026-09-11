package io.ethers.solana.providers

import io.ethers.core.Result
import io.ethers.core.ThrowableError
import io.ethers.core.unwrapOrReturn
import io.ethers.solana.providers.middleware.SolanaApi
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.rpc.Commitment
import io.ethers.solana.types.rpc.SignatureStatus
import kotlinx.coroutines.delay
import kotlin.jvm.JvmOverloads
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** How often a submitted transaction's status is polled while waiting for it to land. */
val DEFAULT_CONFIRMATION_INTERVAL: Duration = 1.seconds

/** How long to wait before giving up on a transaction whose expiry cannot be checked. */
val DEFAULT_CONFIRMATION_TIMEOUT: Duration = 90.seconds

/**
 * A submitted transaction, tracked until the cluster confirms it.
 *
 * The Solana counterpart of the EVM `PendingInclusion`, with one difference the EVM has no equivalent
 * of: a transaction is only valid while its blockhash is, roughly a minute, after which the cluster
 * will never accept it. Given that blockhash this waits for a definite answer rather than a timeout -
 * the transaction landed, or it expired and never can. Without one it can only wait.
 */
class PendingSolanaTransaction @JvmOverloads constructor(
    val signature: SolanaSignature,
    private val api: SolanaApi,
    /**
     * The blockhash the transaction was signed against. Supplying it is what lets expiry be detected:
     * without it a transaction that can never land is indistinguishable from one that has not landed yet.
     */
    private val blockhash: SolanaBlockhash? = null,
) : PlatformPendingSolanaTransaction {
    /**
     * Wait until the transaction reaches [commitment]. JVM and Android also get blocking
     * `awaitConfirmation` and `CompletableFuture`-returning `confirmationAsync` variants.
     *
     * A transaction that lands and then fails on chain is returned, not raised: it was included, and
     * [SignatureStatus.err] says why it failed. Only never landing is an error.
     */
    override suspend fun confirmation(
        commitment: Commitment,
        interval: Duration,
        timeout: Duration,
    ): Result<SignatureStatus, Error> {
        val started = TimeSource.Monotonic.markNow()
        while (true) {
            val statuses = api.getSignatureStatuses(signature).send()
                .unwrapOrReturn { return Result.failure(Error.Rpc(signature, it)) }
            val status = statuses.value.firstOrNull()

            if (status != null && status.isAtLeast(commitment)) return Result.success(status)

            // a blockhash the cluster no longer accepts means an unseen transaction can never land
            if (status == null && blockhash != null) {
                val valid = api.isBlockhashValid(blockhash, Commitment.FINALIZED).send()
                    .unwrapOrReturn { return Result.failure(Error.Rpc(signature, it)) }
                if (!valid.value) return Result.failure(Error.Expired(signature, blockhash))
            }

            val waited = started.elapsedNow()
            if (waited >= timeout) return Result.failure(Error.TimedOut(signature, waited))
            delay(minOf(interval, timeout - waited).coerceAtLeast(1.milliseconds))
        }
    }

    override fun toString(): String = "PendingSolanaTransaction($signature)"

    sealed class Error : ThrowableError {
        /** The blockhash the transaction was signed against is no longer accepted, so it never landed. */
        data class Expired(val signature: SolanaSignature, val blockhash: SolanaBlockhash) : Error() {
            override val message: String get() = "Transaction $signature expired: blockhash $blockhash is no longer valid"
        }

        /** Still unconfirmed when the wait ran out, with no blockhash to prove it can no longer land. */
        data class TimedOut(val signature: SolanaSignature, val waited: Duration) : Error() {
            override val message: String get() = "Transaction $signature was still unconfirmed after $waited"
        }

        /** The node could not be asked, so nothing is known about the transaction either way. */
        data class Rpc(val signature: SolanaSignature, val error: io.ethers.providers.RpcError) : Error() {
            override val message: String get() = "Could not read the status of $signature: ${error.message}"
            override val cause: Throwable get() = error.toException()
        }
    }
}
