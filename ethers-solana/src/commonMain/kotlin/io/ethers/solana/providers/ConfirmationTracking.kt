package io.ethers.solana.providers

import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.transaction.SolanaTransactionCompiled
import io.ethers.solana.types.transaction.decodeTransactionEnvelope

/** Confirmation inputs inferred from the transaction, never selected by callers or sent to RPC. */
internal sealed interface ConfirmationTracking {
    /** The envelope could not be resolved safely; only signature status can be checked. */
    data object StatusOnly : ConfirmationTracking

    data class Blockhash(val blockhash: SolanaBlockhash) : ConfirmationTracking

    data class DurableNonce(val nonceAccount: SolanaAddress, val nonce: SolanaBlockhash) : ConfirmationTracking
}

internal fun SolanaTransactionCompiled.confirmationTracking(): ConfirmationTracking {
    val first = instructions.firstOrNull()
    if (first != null) {
        val program = accounts.getOrNull(first.programIdIndex) ?: return ConfirmationTracking.StatusOnly
        if (program == Programs.SYSTEM && first.data.asByteArray().isAdvanceNonceData()) {
            val nonceAccount = first.accounts.firstOrNull()?.let { accounts.getOrNull(it) }
                ?: return ConfirmationTracking.StatusOnly
            return ConfirmationTracking.DurableNonce(nonceAccount, recentBlockhash)
        }
    }
    return ConfirmationTracking.Blockhash(recentBlockhash)
}

internal fun ByteArray.isAdvanceNonceData(): Boolean = size == 4 && this[0] == 4.toByte() && this[1] == 0.toByte() && this[2] == 0.toByte() && this[3] == 0.toByte()

/** Reuse the wire decoder for raw submissions; malformed envelopes still reach RPC unchanged. */
internal fun decodeConfirmationTracking(bytes: ByteArray): ConfirmationTracking = decodeTransactionEnvelope(bytes).unwrapOrNull()?.first?.confirmationTracking() ?: ConfirmationTracking.StatusOnly
