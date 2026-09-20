package io.ethers.solana.providers

import io.ethers.core.Result
import io.ethers.core.ThrowableError
import io.ethers.core.unwrapOrReturn
import io.ethers.solana.providers.middleware.SolanaApi
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.rpc.AccountInfo
import io.ethers.solana.types.rpc.Commitment
import io.ethers.solana.types.rpc.SignatureStatus
import io.ethers.solana.types.rpc.SolanaAccountConfig
import io.ethers.solana.types.rpc.SolanaReadConfig
import io.github.artificialpb.bignum.BigInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.jvm.JvmOverloads
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** How often a submitted transaction's status is polled while waiting for it to land. */
val DEFAULT_CONFIRMATION_INTERVAL: Duration = 1.seconds

/** How long to wait for a transaction to reach the requested commitment. */
val DEFAULT_CONFIRMATION_TIMEOUT: Duration = 90.seconds

/**
 * A submitted transaction tracked until the requested commitment, expiry evidence, or timeout.
 * Tracking is inferred automatically when sending. Unknown transactions use status-only tracking.
 * Timing out never cancels a transaction.
 */
class PendingSolanaTransaction internal constructor(
    val signature: SolanaSignature,
    private val api: SolanaApi,
    private val tracking: ConfirmationTracking,
) : PlatformPendingSolanaTransaction {
    /** A bare hash must be an ordinary recent blockhash, never a durable nonce. */
    @JvmOverloads
    constructor(signature: SolanaSignature, api: SolanaApi, blockhash: SolanaBlockhash? = null) : this(
        signature,
        api,
        blockhash?.let { ConfirmationTracking.Blockhash(it) } ?: ConfirmationTracking.StatusOnly,
    )

    /**
     * Wait for inclusion at [commitment]. On-chain failures are returned as [SignatureStatus] values.
     * Expiry means the validity window closed and a sufficiently current history lookup found no
     * inclusion; it cannot establish what an RPC node has pruned or omitted from its history.
     * If the hash or nonce is never observed valid, an absent signature remains inconclusive until timeout.
     */
    override suspend fun confirmation(
        commitment: Commitment,
        interval: Duration,
        timeout: Duration,
    ): Result<SignatureStatus, Error> {
        val started = TimeSource.Monotonic.markNow()
        return withTimeoutOrNull(timeout) { pollConfirmation(commitment, interval) }
            ?: Result.failure(Error.TimedOut(signature, started.elapsedNow()))
    }

    private suspend fun pollConfirmation(commitment: Commitment, interval: Duration): Result<SignatureStatus, Error> {
        val pollInterval = interval.coerceAtLeast(1.milliseconds)
        var validityAnchor: BigInteger? = null
        var invalidation: Invalidation? = null
        while (true) {
            // Once invalidation is observed, search history without moving its slot watermark.
            val statuses = api.getSignatureStatuses(listOf(signature), invalidation != null).send()
                .unwrapOrReturn { return Result.failure(Error.Rpc(signature, it)) }
            val status = statuses.value.firstOrNull()
            if (status != null) {
                if (status.isAtLeast(commitment)) return Result.success(status)
                delay(pollInterval)
                continue
            }

            val observedInvalidation = invalidation
            if (observedInvalidation != null) {
                // An absent signature is conclusive only after the status node catches up.
                if (statuses.context.slot >= observedInvalidation.slot) return Result.failure(observedInvalidation.error)
                delay(pollInterval)
                continue
            }

            when (val tracking = tracking) {
                is ConfirmationTracking.Blockhash -> {
                    val anchor = validityAnchor
                    val config = SolanaReadConfig(
                        commitment = if (anchor == null) Commitment.CONFIRMED else Commitment.FINALIZED,
                        minContextSlot = anchor,
                    )
                    val result = api.isBlockhashValid(tracking.blockhash, config).send()
                    if (result is Result.Failure) {
                        // A finalized bank normally trails confirmed state; this is not expiry.
                        if (result.error.code != MIN_CONTEXT_SLOT_NOT_REACHED) return Result.failure(Error.Rpc(signature, result.error))
                        delay(pollInterval)
                        continue
                    }
                    val validity = result.unwrap()
                    if (anchor == null) {
                        // An initially unknown hash might simply be newer than this node.
                        if (validity.value) validityAnchor = validity.context.slot
                    } else if (!validity.value) {
                        invalidation = Invalidation(validity.context.slot, Error.Expired(signature, tracking.blockhash))
                    }
                }

                is ConfirmationTracking.DurableNonce -> {
                    val anchor = validityAnchor
                    val config = SolanaAccountConfig(
                        commitment = if (anchor == null) Commitment.CONFIRMED else Commitment.FINALIZED,
                        minContextSlot = anchor,
                    )
                    val result = api.getAccountInfo(tracking.nonceAccount, config).send()
                    if (result is Result.Failure) {
                        if (result.error.code != MIN_CONTEXT_SLOT_NOT_REACHED) return Result.failure(Error.Rpc(signature, result.error))
                        delay(pollInterval)
                        continue
                    }
                    val account = result.unwrap()
                    val matches = account.value.matchesNonce(tracking.nonce)
                    if (anchor == null) {
                        if (matches == true) validityAnchor = account.context.slot
                    } else if (account.context.slot >= anchor && matches == false) {
                        invalidation = Invalidation(account.context.slot, Error.NonceInvalidated(signature, tracking.nonceAccount, tracking.nonce))
                    }
                }

                ConfirmationTracking.StatusOnly -> Unit
            }
            delay(pollInterval)
        }
    }

    /** Evidence to check against signature history before returning the associated error. */
    private data class Invalidation(val slot: BigInteger, val error: Error)

    override fun toString(): String = "PendingSolanaTransaction($signature)"

    sealed class Error : ThrowableError {
        /** Its blockhash is no longer valid and the RPC history lookup found no inclusion. */
        data class Expired(val signature: SolanaSignature, val blockhash: SolanaBlockhash) : Error() {
            override val message: String get() = "Transaction $signature expired: blockhash $blockhash is no longer valid"
        }

        /** The anchored nonce is no longer usable and a current history lookup found no inclusion. */
        data class NonceInvalidated(val signature: SolanaSignature, val nonceAccount: SolanaAddress, val nonce: SolanaBlockhash) : Error() {
            override val message: String get() = "Transaction $signature cannot use nonce $nonce from $nonceAccount anymore"
        }

        /** The wait ended without a conclusive result; the transaction may still execute. */
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

private const val MIN_CONTEXT_SLOT_NOT_REACHED = -32016

/** null means an unrecognized account encoding: do not infer invalidation from it. */
private fun AccountInfo?.matchesNonce(nonce: SolanaBlockhash): Boolean? {
    if (this == null || owner != Programs.SYSTEM || executable) return false
    val bytes = data.asByteArray()
    if (bytes.size != 80) return null
    // System nonce accounts encode Versions and State as little-endian u32 discriminants.
    if (bytes[1] != 0.toByte() || bytes[2] != 0.toByte() || bytes[3] != 0.toByte() ||
        bytes[5] != 0.toByte() || bytes[6] != 0.toByte() || bytes[7] != 0.toByte()
    ) return null
    val version = bytes[0].toInt()
    val state = bytes[4].toInt()
    if (version !in 0..1 || state !in 0..1) return null
    if (version == 0 || state == 0) return false
    return SolanaBlockhash(bytes.copyOfRange(40, 72)) == nonce
}
