package io.ethers.solana.types.transaction

import io.ethers.core.ThrowableError
import io.ethers.solana.types.SolanaAddress

/**
 * A reason a Solana transaction could not be constructed or decoded.
 *
 * Every case is a plain value carrying the numbers that caused it, so a caller can react to the
 * specific failure instead of parsing a message. [EnvelopeTooLarge] is the one most worth branching
 * on: the usual responses are to split the instructions across transactions, move accounts into a
 * lookup table, or compile as v1.
 *
 * The validating constructors and factories throw [SolanaTransactionException], which keeps the
 * error reachable from the catch block; the `try` factories return it as a value instead.
 */
sealed class SolanaTransactionError : ThrowableError {
    /** Thrown form of this error. Remains an [IllegalArgumentException], as these are argument failures. */
    override fun toException(): SolanaTransactionException = SolanaTransactionException(this)

    // --- message structure ---

    /** A message must carry at least one and at most 256 inline accounts. */
    data class InvalidAccountCount(val count: Int) : SolanaTransactionError()

    /** Inline accounts are a set; the same address must not appear twice. */
    data class DuplicateAccount(val address: SolanaAddress) : SolanaTransactionError()

    /** Inline accounts plus every address loaded from a lookup table, against the version's ceiling. */
    data class TooManyAccounts(val count: Int, val max: Int) : SolanaTransactionError()

    data class TooManyInstructions(val count: Int, val max: Int) : SolanaTransactionError()

    /** Required signatures must be at least one and fit both the account list and the version's ceiling. */
    data class InvalidSignerCount(val count: Int, val max: Int) : SolanaTransactionError()

    /** The fee payer is the first account and pays, so it can never be readonly. */
    data object FeePayerNotWritable : SolanaTransactionError()

    data class InvalidReadonlyAccountCount(val count: Int, val max: Int) : SolanaTransactionError()

    /** An instruction referenced a program or account slot outside the resolved account list. */
    data class AccountIndexOutOfRange(val instructionIndex: Int, val accountIndex: Int, val accountCount: Int) : SolanaTransactionError()

    /** A lookup table entry referenced an address slot outside the addressable 0..255 range. */
    data class LookupIndexOutOfRange(val tableIndex: Int, val accountIndex: Int) : SolanaTransactionError()

    /** A single lookup table can hold at most 256 addresses. */
    data class LookupTableTooLarge(val size: Int) : SolanaTransactionError()

    // --- wire limits ---

    /** The serialized envelope, including a slot for every required signature, exceeds the version's limit. */
    data class EnvelopeTooLarge(val type: SolanaTxType, val size: Long, val max: Int) : SolanaTransactionError()

    /** A v1 instruction exceeds the 255-account or 65535-byte fields that encode it. */
    data class InstructionTooLarge(val instructionIndex: Int, val accountCount: Int, val dataSize: Int) : SolanaTransactionError()

    /** A v1 inline config value the wire format cannot represent, or that this library cannot compile. */
    data class InvalidConfig(val field: String, override val message: String) : SolanaTransactionError()

    // --- decoding ---

    /** A message version this library cannot construct or sign. */
    data class UnsupportedMessageVersion(val version: Int) : SolanaTransactionError()

    /** V1 places its signatures after the message, so it cannot appear in a signatures-first envelope. */
    data object V1SignaturesMustFollowMessage : SolanaTransactionError()

    /** Truncated, overlong or otherwise unreadable bytes, as reported by the wire decoder. */
    data class MalformedMessage(override val message: String, override val cause: Throwable?) : SolanaTransactionError()

    /** The envelope's signature vector does not match the count the message header requires. */
    data class SignatureCountMismatch(val actual: Int, val expected: Int) : SolanaTransactionError()

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
