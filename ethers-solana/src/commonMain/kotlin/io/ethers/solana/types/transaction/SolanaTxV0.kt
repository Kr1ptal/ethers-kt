package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.serialization.SolanaMessageDecoder
import io.ethers.solana.serialization.SolanaMessageEncoder
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaSignature
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/** Immutable v0 transaction payload. Lookup table contents are supplied by the caller during compilation. */
class SolanaTxV0 private constructor(
    override val header: MessageHeader,
    override val accounts: List<SolanaAddress>,
    override val recentBlockhash: SolanaBlockhash,
    override val instructions: List<CompiledInstruction>,
    val addressLookupTables: List<CompiledAddressLookupTable>,
    validated: Boolean,
) : SolanaTransactionUnsigned {
    override val type: SolanaTxType get() = SolanaTxType.V0

    /**
     * Validate the fields, throwing [SolanaTransactionException] if they do not describe a legal
     * message. [tryCreate] reports the same failure as a value, without building an exception.
     */
    @JvmOverloads
    constructor(
        header: MessageHeader,
        accounts: List<SolanaAddress>,
        recentBlockhash: SolanaBlockhash,
        instructions: List<CompiledInstruction>,
        addressLookupTables: List<CompiledAddressLookupTable> = emptyList(),
    ) : this(header, accounts, recentBlockhash, instructions, addressLookupTables, false)

    init {
        if (!validated) validate(header, accounts, instructions, addressLookupTables)?.let { throw it.toException() }
    }

    override fun withNewBlockhash(blockhash: SolanaBlockhash): SolanaTxV0 = SolanaTxV0(header, accounts, blockhash, instructions, addressLookupTables)

    override fun serializeMessage(): ByteArray {
        val encoder = SolanaMessageEncoder().writeByte(128)
        encoder.writeMessageBody(this)
        encoder.writeShortVecLength(addressLookupTables.size)

        for ((key, writableIndexes, readonlyIndexes) in addressLookupTables) {
            encoder.writeBytes(key.toByteArray()).writeShortVecLength(writableIndexes.size)
            writableIndexes.forEach { encoder.writeByte(it) }
            encoder.writeShortVecLength(readonlyIndexes.size)
            readonlyIndexes.forEach { encoder.writeByte(it) }
        }
        return encoder.toByteArray()
    }

    override fun envelopeSize(): Long = legacyEnvelopeSize(header, accounts, instructions, addressLookupTables)

    override fun serializeEnvelope(signatures: List<SolanaSignature?>): ByteArray = encodeSignaturesFirstEnvelope(this, signatures)

    companion object {
        const val MAX_TRANSACTION_SIZE: Int = SolanaTxLegacy.MAX_TRANSACTION_SIZE

        /** Every reason these fields cannot form a v0 message, or null if they can. */
        internal fun validate(
            header: MessageHeader,
            accounts: List<SolanaAddress>,
            instructions: List<CompiledInstruction>,
            lookups: List<CompiledAddressLookupTable>,
        ): SolanaTransactionError? = messageError(header, accounts, instructions, lookups)
            ?: envelopeSizeError(SolanaTxType.V0, legacyEnvelopeSize(header, accounts, instructions, lookups), MAX_TRANSACTION_SIZE)

        /** As the constructor, reporting the reason the fields are invalid instead of throwing. */
        @JvmStatic
        @JvmOverloads
        fun tryCreate(
            header: MessageHeader,
            accounts: List<SolanaAddress>,
            recentBlockhash: SolanaBlockhash,
            instructions: List<CompiledInstruction>,
            addressLookupTables: List<CompiledAddressLookupTable> = emptyList(),
        ): Result<SolanaTxV0, SolanaTransactionError> {
            validate(header, accounts, instructions, addressLookupTables)?.let { return Result.failure(it) }
            return Result.success(SolanaTxV0(header, accounts, recentBlockhash, instructions, addressLookupTables, validated = true))
        }

        /** The version prefix has already been read. */
        internal fun decodeBody(decoder: SolanaMessageDecoder): SolanaTxV0 {
            val body = decoder.readMessageBody(decoder.readByte())
            val lookups = List(decoder.readShortVecLength()) {
                val key = SolanaAddress(decoder.readBytes(32))
                val writable = List(decoder.readShortVecLength()) { decoder.readByte() }
                val readonly = List(decoder.readShortVecLength()) { decoder.readByte() }
                CompiledAddressLookupTable(key, writable, readonly)
            }
            return SolanaTxV0(body.header, body.accounts, body.recentBlockhash, body.instructions, lookups)
        }

        @JvmStatic
        @JvmOverloads
        fun compile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instruction: Instruction, lookupTables: List<AddressLookupTableAccount> = emptyList()): SolanaTxV0 = compile(feePayer, blockhash, listOf(instruction), lookupTables)

        /** As [compile], returning the reason it could not be compiled instead of throwing. */
        @JvmStatic
        @JvmOverloads
        fun tryCompile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instruction: Instruction, lookupTables: List<AddressLookupTableAccount> = emptyList()): Result<SolanaTxV0, SolanaTransactionError> = tryCompile(feePayer, blockhash, listOf(instruction), lookupTables)

        /** As [compile], returning the reason it could not be compiled instead of throwing. */
        @JvmStatic
        @JvmOverloads
        fun tryCompile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instructions: List<Instruction>, lookupTables: List<AddressLookupTableAccount> = emptyList()): Result<SolanaTxV0, SolanaTransactionError> = compileMessage(feePayer, blockhash, instructions, lookupTables)
            .andThen { tryCreate(it.header, it.accounts, it.recentBlockhash, it.instructions, it.lookups) }

        @JvmStatic
        @JvmOverloads
        fun compile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instructions: List<Instruction>, lookupTables: List<AddressLookupTableAccount> = emptyList()): SolanaTxV0 = tryCompile(feePayer, blockhash, instructions, lookupTables).unwrap()
    }
}
