package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.serialization.SolanaMessageDecoder
import io.ethers.solana.serialization.SolanaMessageEncoder
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaSignature
import io.github.artificialpb.bignum.BigInteger
import kotlin.jvm.JvmStatic

/**
 * Legacy transaction payload. Every account is inline; lookup tables are not supported.
 *
 * The constructor keeps the lists it is given rather than copying them, so pass immutable lists;
 * mutating them afterwards changes the transaction and invalidates its validated state.
 */
class SolanaTxLegacy private constructor(
    override val header: MessageHeader,
    override val accounts: List<SolanaAddress>,
    override val recentBlockhash: SolanaBlockhash,
    override val instructions: List<MessageInstruction>,
    validated: Boolean,
) : SolanaTransactionUnsigned {
    override val type: SolanaTxType get() = SolanaTxType.Legacy

    // decoded once: reading the settings back means scanning and parsing the instructions
    private val computeBudget by lazy(LazyThreadSafetyMode.PUBLICATION) { decodeComputeBudget(accounts, instructions) }
    override val computeUnitLimit: Long? get() = computeBudget.computeUnitLimit
    override val computeUnitPrice: BigInteger? get() = computeBudget.computeUnitPrice
    override val priorityFee: BigInteger? get() = computeBudget.priorityFee
    override val loadedAccountsDataSizeLimit: Long? get() = computeBudget.loadedAccountsDataSizeLimit
    override val heapSize: Long? get() = computeBudget.heapSize

    /**
     * Validate the fields, throwing [SolanaTransactionException] if they do not describe a legal
     * message. [create] reports the same failure as a value, without building an exception.
     */
    constructor(
        header: MessageHeader,
        accounts: List<SolanaAddress>,
        recentBlockhash: SolanaBlockhash,
        instructions: List<MessageInstruction>,
    ) : this(header, accounts, recentBlockhash, instructions, false)

    init {
        if (!validated) validate(header, accounts, instructions)?.let { throw it.toException() }
    }

    override fun withNewBlockhash(blockhash: SolanaBlockhash): SolanaTxLegacy = SolanaTxLegacy(header, accounts, blockhash, instructions)

    private val encodedMessage: ByteArray by lazy(LazyThreadSafetyMode.PUBLICATION) { SolanaMessageEncoder().also { it.writeMessageBody(this) }.toByteArray() }

    override fun serializeMessage(): ByteArray = encodedMessage.copyOf()

    override fun envelopeSize(): Long = legacyEnvelopeSize(header, accounts, instructions, null)

    override fun encodeEnvelope(signatures: List<SolanaSignature?>): ByteArray = encodeSignaturesFirstEnvelope(this, signatures)

    // the encoded message is the canonical form of every field above, and is already cached
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SolanaTxLegacy

        return encodedMessage.contentEquals(other.encodedMessage)
    }

    override fun hashCode(): Int = encodedMessage.contentHashCode()

    override fun toString(): String {
        return "SolanaTxLegacy(header=$header, accounts=$accounts, recentBlockhash=$recentBlockhash, instructions=$instructions)"
    }

    companion object {
        const val MAX_TRANSACTION_SIZE: Int = 1232

        /** Every reason these fields cannot form a legacy message, or null if they can. */
        internal fun validate(header: MessageHeader, accounts: List<SolanaAddress>, instructions: List<MessageInstruction>): SolanaTransactionError? = messageError(header, accounts, instructions, emptyList())
            ?: envelopeSizeError(SolanaTxType.Legacy, legacyEnvelopeSize(header, accounts, instructions, null), MAX_TRANSACTION_SIZE)

        /** As the constructor, reporting the reason the fields are invalid as a value rather than throwing. */
        @JvmStatic
        fun create(
            header: MessageHeader,
            accounts: List<SolanaAddress>,
            recentBlockhash: SolanaBlockhash,
            instructions: List<MessageInstruction>,
        ): Result<SolanaTxLegacy, SolanaTransactionError> {
            validate(header, accounts, instructions)?.let { return Result.failure(it) }
            return Result.success(SolanaTxLegacy(header, accounts, recentBlockhash, instructions, validated = true))
        }

        /** The required-signature count stands in for a version byte and has already been read. */
        internal fun decodeBody(decoder: SolanaMessageDecoder, requiredSignatures: Int): Result<SolanaTxLegacy, SolanaTransactionError> = decoder.readMessageBody(requiredSignatures)
            .andThen { create(it.header, it.accounts, it.recentBlockhash, it.instructions) }

        @JvmStatic
        fun compile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instruction: Instruction): Result<SolanaTxLegacy, SolanaTransactionError> = compile(feePayer, blockhash, listOf(instruction))

        @JvmStatic
        fun compile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instructions: List<Instruction>): Result<SolanaTxLegacy, SolanaTransactionError> = compileMessage(feePayer, blockhash, instructions, emptyList())
            .andThen { create(it.header, it.accounts, it.recentBlockhash, it.instructions) }
    }
}
