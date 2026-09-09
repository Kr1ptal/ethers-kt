package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.github.artificialpb.bignum.BigInteger
import kotlin.io.encoding.Base64
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
        fun deserialize(bytes: ByteArray): SolanaTransactionCompiled {
            val (tx, signatures) = decodeTransactionEnvelope(bytes)
            return when {
                signatures.all { it == null } -> tx
                signatures.all { it != null } -> SolanaTransactionSigned(tx, signatures.map { requireNotNull(it) })
                else -> throw SolanaTransactionError.PartiallySigned(signatures.count { it == null }, signatures.size).toException()
            }
        }

        /** As [deserialize], returning the reason the bytes could not be decoded instead of throwing. */
        @JvmStatic
        fun tryDeserialize(bytes: ByteArray): Result<SolanaTransactionCompiled, SolanaTransactionError> = catchTransactionError { deserialize(bytes) }

        @JvmStatic
        fun fromBase64(encoded: String): SolanaTransactionCompiled = deserialize(Base64.decode(encoded))

        /** As [fromBase64], returning the reason the input could not be decoded instead of throwing. */
        @JvmStatic
        fun tryFromBase64(encoded: String): Result<SolanaTransactionCompiled, SolanaTransactionError> = catchTransactionError { fromBase64(encoded) }
    }
}
