package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.ethers.core.unwrapOrReturn
import kotlin.jvm.JvmStatic

/**
 * A transaction this library compiled itself, so it owns the message down to the byte: the accounts,
 * the compiled instructions and the version are all its own. Writing it back to the wire follows from
 * that, which is why serialization lives here and not on [SolanaTransaction].
 *
 * A node's response is a [SolanaTransaction] but never one of these, since it can carry a version this
 * library cannot compile.
 */
sealed interface SolanaTransactionCompiled : SolanaTransaction {
    override val instructions: List<MessageInstruction>

    /** The exact message bytes signed by Ed25519, without the signature envelope. */
    fun serializeMessage(): ByteArray

    /** Full transaction envelope, using zero-filled slots for missing signatures. Not necessarily submit-ready. */
    fun serializeForSimulation(): ByteArray

    companion object {
        /**
         * Decode an unsigned or fully signed envelope. Partial signatures must be imported explicitly using
         * [SolanaTransactionSigned.Builder.deserializePartial] so they are not silently discarded.
         */
        @JvmStatic
        fun deserialize(bytes: ByteArray): Result<SolanaTransactionCompiled, SolanaTransactionError> {
            val (tx, signatures) = decodeTransactionEnvelope(bytes).unwrapOrReturn { return Result.failure(it) }
            return when {
                signatures.all { it == null } -> Result.success(tx)
                signatures.all { it != null } -> Result.success(SolanaTransactionSigned(tx, signatures.filled()))
                else -> Result.failure(SolanaTransactionError.PartiallySigned(signatures.count { it == null }, signatures.size))
            }
        }

        /** As [deserialize], from a base64 envelope. */
        @JvmStatic
        fun fromBase64(encoded: String): Result<SolanaTransactionCompiled, SolanaTransactionError> = decodeBase64(encoded).andThen { deserialize(it) }
    }
}
