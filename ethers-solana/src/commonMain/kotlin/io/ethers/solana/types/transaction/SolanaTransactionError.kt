package io.ethers.solana.types.transaction

import io.ethers.core.ThrowableError
import io.ethers.solana.types.SolanaAddress

/**
 * A reason a Solana transaction could not be constructed or decoded.
 *
 * The cases a caller can act on are distinct types: [EnvelopeTooLarge] is answered by splitting the
 * instructions, moving accounts into a lookup table, or compiling as v1; [PartiallySigned] by
 * importing through `SolanaTransactionSigned.Builder.deserializePartial`; [InvalidSignature] by
 * collecting that signature again; [UnsupportedVersion] by reading the transaction as a
 * `SolanaRPCTransaction`. The remaining failures mean the message is malformed, so they are grouped
 * into [CountOutOfRange] and [InvalidMessage], each tagged with which check failed.
 *
 * The validating constructors and factories throw [SolanaTransactionException], which keeps the
 * error reachable from the catch block; the `try` factories return it as a value instead, without
 * building an exception at all.
 */
sealed class SolanaTransactionError : ThrowableError {
    /** Thrown form of this error. Remains an [IllegalArgumentException], as these are argument failures. */
    override fun toException(): SolanaTransactionException = SolanaTransactionException(this)

    /**
     * A count fell outside the range the wire format or the message version allows.
     * [instructionIndex] is set only for the per-instruction limits.
     */
    data class CountOutOfRange(
        val limit: Limit,
        val count: Int,
        val allowed: IntRange,
        val instructionIndex: Int? = null,
    ) : SolanaTransactionError()

    /** Which count [CountOutOfRange] refers to. */
    enum class Limit {
        /** Inline accounts, or inline plus every address loaded from a lookup table. */
        ACCOUNTS,
        INSTRUCTIONS,

        /** Required signatures, from the message header or the envelope's signature vector. */
        SIGNERS,
        READONLY_ACCOUNTS,

        /** Addresses held by a single lookup table. */
        LOOKUP_TABLE,

        /** Accounts referenced by one instruction. */
        INSTRUCTION_ACCOUNTS,

        /** Data bytes carried by one instruction. */
        INSTRUCTION_DATA,
    }

    /** The message's own parts do not agree with each other. */
    data class InvalidMessage(val reason: Reason, override val message: String) : SolanaTransactionError()

    /** Which consistency check [InvalidMessage] failed. */
    enum class Reason {
        /** The same address appears twice in the inline account list. */
        DUPLICATE_ACCOUNT,

        /** The fee payer is the first account and pays, so it can never be readonly. */
        FEE_PAYER_READONLY,

        /** An instruction referenced a program or account slot outside the resolved account list. */
        ACCOUNT_INDEX,

        /** A lookup table referenced an address slot outside the addressable 0..255 range. */
        LOOKUP_INDEX,

        /** A v1 inline config value the wire format cannot represent, or that this library cannot compile. */
        CONFIG,

        /** The signature vector does not match the count the message header requires. */
        SIGNATURE_COUNT,
    }

    /** The serialized envelope, including a slot for every required signature, exceeds the version's limit. */
    data class EnvelopeTooLarge(val type: SolanaTxType, val size: Long, val max: Int) : SolanaTransactionError()

    /** Truncated, overlong or otherwise unreadable bytes, as reported by the wire decoder. */
    data class MalformedBytes(override val message: String, override val cause: Throwable? = null) : SolanaTransactionError()

    /** A message version this library cannot construct or sign. */
    data class UnsupportedVersion(val version: Int) : SolanaTransactionError()

    /** A populated signature slot does not verify against the message and its signer. */
    data class InvalidSignature(val index: Int, val signer: SolanaAddress) : SolanaTransactionError()

    /** Some, but not all, signature slots are filled. Import these with `Builder.deserializePartial`. */
    data class PartiallySigned(val missing: Int, val required: Int) : SolanaTransactionError()
}

/**
 * Thrown by the validating transaction constructors and factories. Stays an [IllegalArgumentException],
 * while keeping the typed [error] reachable:
 *
 * ```kotlin
 * try {
 *     SolanaTxV1.compile(feePayer, blockhash, instructions, config)
 * } catch (e: SolanaTransactionException) {
 *     val tooLarge = e.error as? SolanaTransactionError.EnvelopeTooLarge
 * }
 * ```
 */
class SolanaTransactionException(val error: SolanaTransactionError) :
    IllegalArgumentException(error.message ?: error.toString(), error.cause)
