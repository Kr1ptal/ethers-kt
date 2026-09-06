package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.serialization.SolanaMessageDecoder
import io.ethers.solana.serialization.SolanaMessageEncoder
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaSignature
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
    override val instructions: List<CompiledInstruction>,
    validated: Boolean,
) : SolanaTransactionUnsigned {
    override val type: SolanaTxType get() = SolanaTxType.Legacy

    /**
     * Validate the fields, throwing [SolanaTransactionException] if they do not describe a legal
     * message. [tryCreate] reports the same failure as a value, without building an exception.
     */
    constructor(
        header: MessageHeader,
        accounts: List<SolanaAddress>,
        recentBlockhash: SolanaBlockhash,
        instructions: List<CompiledInstruction>,
    ) : this(header, accounts, recentBlockhash, instructions, false)

    init {
        if (!validated) validate(header, accounts, instructions)?.let { throw it.toException() }
    }

    override fun withNewBlockhash(blockhash: SolanaBlockhash): SolanaTxLegacy = SolanaTxLegacy(header, accounts, blockhash, instructions)

    override fun serializeMessage(): ByteArray = SolanaMessageEncoder().also { it.writeMessageBody(this) }.toByteArray()

    override fun envelopeSize(): Long = legacyEnvelopeSize(header, accounts, instructions, null)

    override fun encodeEnvelope(signatures: List<SolanaSignature?>): ByteArray = encodeSignaturesFirstEnvelope(this, signatures)

    companion object {
        const val MAX_TRANSACTION_SIZE: Int = 1232

        /** Every reason these fields cannot form a legacy message, or null if they can. */
        internal fun validate(header: MessageHeader, accounts: List<SolanaAddress>, instructions: List<CompiledInstruction>): SolanaTransactionError? = messageError(header, accounts, instructions, emptyList())
            ?: envelopeSizeError(SolanaTxType.Legacy, legacyEnvelopeSize(header, accounts, instructions, null), MAX_TRANSACTION_SIZE)

        /** As the constructor, reporting the reason the fields are invalid instead of throwing. */
        @JvmStatic
        fun tryCreate(
            header: MessageHeader,
            accounts: List<SolanaAddress>,
            recentBlockhash: SolanaBlockhash,
            instructions: List<CompiledInstruction>,
        ): Result<SolanaTxLegacy, SolanaTransactionError> {
            validate(header, accounts, instructions)?.let { return Result.failure(it) }
            return Result.success(SolanaTxLegacy(header, accounts, recentBlockhash, instructions, validated = true))
        }

        /** The required-signature count stands in for a version byte and has already been read. */
        internal fun decodeBody(decoder: SolanaMessageDecoder, requiredSignatures: Int): SolanaTxLegacy {
            val body = decoder.readMessageBody(requiredSignatures)
            return SolanaTxLegacy(body.header, body.accounts, body.recentBlockhash, body.instructions)
        }

        @JvmStatic
        fun compile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instruction: Instruction): SolanaTxLegacy = compile(feePayer, blockhash, listOf(instruction))

        /** As [compile], returning the reason it could not be compiled instead of throwing. */
        @JvmStatic
        fun tryCompile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instruction: Instruction): Result<SolanaTxLegacy, SolanaTransactionError> = tryCompile(feePayer, blockhash, listOf(instruction))

        /** As [compile], returning the reason it could not be compiled instead of throwing. */
        @JvmStatic
        fun tryCompile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instructions: List<Instruction>): Result<SolanaTxLegacy, SolanaTransactionError> = compileMessage(feePayer, blockhash, instructions, emptyList())
            .andThen { tryCreate(it.header, it.accounts, it.recentBlockhash, it.instructions) }

        @JvmStatic
        fun compile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instructions: List<Instruction>): SolanaTxLegacy = tryCompile(feePayer, blockhash, instructions).unwrap()
    }
}
