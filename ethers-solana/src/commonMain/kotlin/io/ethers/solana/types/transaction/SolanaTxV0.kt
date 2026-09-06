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
class SolanaTxV0 @JvmOverloads constructor(
    override val header: MessageHeader,
    override val accounts: List<SolanaAddress>,
    override val recentBlockhash: SolanaBlockhash,
    override val instructions: List<CompiledInstruction>,
    val addressLookupTables: List<CompiledAddressLookupTable> = emptyList(),
) : SolanaTransactionUnsigned {
    override val type: SolanaTxType get() = SolanaTxType.V0

    init {
        validateMessage(header, accounts, instructions, addressLookupTables)
        envelopeSizeError(this, MAX_TRANSACTION_SIZE)?.let { throw it.toException() }
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

    override fun envelopeSize(): Long = legacyEnvelopeSize(this, addressLookupTables)

    override fun serializeEnvelope(signatures: List<SolanaSignature?>): ByteArray = encodeSignaturesFirstEnvelope(this, signatures)

    companion object {
        const val MAX_TRANSACTION_SIZE: Int = SolanaTxLegacy.MAX_TRANSACTION_SIZE

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
        fun tryCompile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instructions: List<Instruction>, lookupTables: List<AddressLookupTableAccount> = emptyList()): Result<SolanaTxV0, SolanaTransactionError> = catchTransactionError { compile(feePayer, blockhash, instructions, lookupTables) }

        @JvmStatic
        @JvmOverloads
        fun compile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instructions: List<Instruction>, lookupTables: List<AddressLookupTableAccount> = emptyList()): SolanaTxV0 {
            val fields = compileMessage(feePayer, blockhash, instructions, lookupTables)
            return SolanaTxV0(fields.header, fields.accounts, fields.recentBlockhash, fields.instructions, fields.lookups)
        }
    }
}
