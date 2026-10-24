package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.ethers.core.ThrowableError
import io.ethers.solana.types.SolanaAddress
import kotlin.native.ObjCName

/**
 * A reason a Solana transaction could not be constructed or decoded.
 *
 * The cases a caller can act on are distinct types: [EnvelopeTooLarge] is answered by splitting the
 * instructions, moving accounts into a lookup table, or compiling as v1; [PartiallySigned] by
 * importing through `SolanaTransactionSigned.Builder.deserializePartial`; [UnsignedSlots] and
 * [InvalidSignature] by collecting those signatures again; [UnsupportedVersion] by reading the
 * transaction as a
 * `SolanaRPCTransaction`. The remaining failures mean the message is malformed, so they are grouped
 * into [CountOutOfRange] and [InvalidMessage], each tagged with which check failed.
 *
 * Every factory that can fail returns one of these as a value: compiling, decoding and resolving a
 * message never throw, and the wire decoders record malformed input rather than raising it, so
 * rejecting untrusted bytes costs no stack trace. [toException] builds one on demand, which is what
 * `unwrap` does, and the validating constructors still throw [SolanaTransactionException] for callers
 * who prefer that.
 */
@ObjCName("SolanaTransactionError", exact = true)
sealed class SolanaTransactionError : ThrowableError {
    /** Thrown form of this error. Remains an [IllegalArgumentException], as these are argument failures. */
    override fun toException(): SolanaTransactionException = SolanaTransactionException(this)

    /**
     * A count fell outside the range the wire format or the message version allows.
     * [instructionIndex] is set only for the per-instruction limits.
     */
    @ObjCName("SolanaTransactionErrorCountOutOfRange", exact = true)
    data class CountOutOfRange(
        val limit: Limit,
        val count: Int,
        val allowed: IntRange,
        val instructionIndex: Int? = null,
    ) : SolanaTransactionError()

    /** Which count [CountOutOfRange] refers to. */
    @ObjCName("SolanaTransactionErrorLimit", exact = true)
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
    @ObjCName("SolanaTransactionErrorInvalidMessage", exact = true)
    data class InvalidMessage(val reason: Reason, override val message: String) : SolanaTransactionError()

    /** Which consistency check [InvalidMessage] failed. */
    @ObjCName("SolanaTransactionErrorReason", exact = true)
    enum class Reason {
        /** The same address appears twice in the inline account list. */
        DUPLICATE_ACCOUNT,

        /** The fee payer is the first account and pays, so it can never be readonly. */
        FEE_PAYER_READONLY,

        /** An instruction referenced a program or account slot outside the resolved account list. */
        ACCOUNT_INDEX,

        /**
         * A lookup table referenced an address slot outside the addressable 0..255 range, or, when
         * resolving a compiled message back to addresses, one the table does not hold.
         */
        LOOKUP_INDEX,

        /** A message loads addresses from a lookup table whose contents were not supplied. */
        UNKNOWN_LOOKUP_TABLE,

        /** A v1 inline config value the wire format cannot represent, or that this library cannot compile. */
        CONFIG,

        /** The signature vector does not match the count the message header requires. */
        SIGNATURE_COUNT,
    }

    /** The serialized envelope, including a slot for every required signature, exceeds the version's limit. */
    @ObjCName("SolanaTransactionErrorEnvelopeTooLarge", exact = true)
    data class EnvelopeTooLarge(val type: SolanaTxType, val size: Long, val max: Int) : SolanaTransactionError()

    /** Truncated, overlong or otherwise unreadable bytes, as reported by the wire decoder. */
    @ObjCName("SolanaTransactionErrorMalformedBytes", exact = true)
    data class MalformedBytes(override val message: String, override val cause: Throwable? = null) : SolanaTransactionError()

    /** A message version this library cannot construct or sign. */
    @ObjCName("SolanaTransactionErrorUnsupportedVersion", exact = true)
    data class UnsupportedVersion(val version: Int) : SolanaTransactionError()

    /** A populated signature slot does not verify against the message and its signer. */
    @ObjCName("SolanaTransactionErrorInvalidSignature", exact = true)
    data class InvalidSignature(val index: Int, val signer: SolanaAddress) : SolanaTransactionError()

    /** A request cannot be compiled without a fee payer, which is always the first account. */
    @ObjCName("SolanaTransactionErrorMissingFeePayer", exact = true)
    data object MissingFeePayer : SolanaTransactionError()

    /** A request cannot be compiled without a recent blockhash. */
    @ObjCName("SolanaTransactionErrorMissingBlockhash", exact = true)
    data object MissingBlockhash : SolanaTransactionError()

    /** Some, but not all, signature slots are filled. Import these with `Builder.deserializePartial`. */
    @ObjCName("SolanaTransactionErrorPartiallySigned", exact = true)
    data class PartiallySigned(val missing: Int, val required: Int) : SolanaTransactionError()

    /**
     * Signature slots that are empty ([missing]) or hold a signature that does not verify ([invalid]).
     * Both are indexes into the message's required signers, so `tx.signers[index]` names each signer.
     */
    @ObjCName("SolanaTransactionErrorUnsignedSlots", exact = true)
    data class UnsignedSlots(val missing: List<Int>, val invalid: List<Int>) : SolanaTransactionError() {
        override val message: String get() = buildString {
            append("Cannot build a signed transaction: ")
            if (missing.isNotEmpty()) append("slots ${missing.joinToString()} have no signature")
            if (missing.isNotEmpty() && invalid.isNotEmpty()) append(", and ")
            if (invalid.isNotEmpty()) append("slots ${invalid.joinToString()} do not verify")
        }
    }
}

/**
 * Carries a [SolanaTransactionError] that was raised rather than returned. Stays an
 * [IllegalArgumentException], while keeping the typed [error] reachable.
 *
 * Two things raise one: the validating constructors, and [Result.unwrap] on a failure. The factories
 * do not - they answer with the error as a value, so wrapping one in a try/catch catches nothing:
 *
 * ```kotlin
 * // compile returns a Result, so this catch never runs
 * try { SolanaTxV1.compile(feePayer, blockhash, instructions, config) } catch (e: SolanaTransactionException) { }
 *
 * // either read the error as a value
 * val tooLarge = SolanaTxV1.compile(feePayer, blockhash, instructions, config)
 *     .unwrapErrorOrNull() as? SolanaTransactionError.EnvelopeTooLarge
 *
 * // or opt into the exception with unwrap()
 * try {
 *     SolanaTxV1.compile(feePayer, blockhash, instructions, config).unwrap()
 * } catch (e: SolanaTransactionException) {
 *     val tooLarge = e.error as? SolanaTransactionError.EnvelopeTooLarge
 * }
 * ```
 */
@ObjCName("SolanaTransactionException", exact = true)
class SolanaTransactionException(val error: SolanaTransactionError) :
    IllegalArgumentException(error.message ?: error.toString(), error.cause)
