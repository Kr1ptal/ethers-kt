package io.ethers.solana.types.transaction

import io.ethers.solana.instruction.Instruction
import io.ethers.solana.serialization.SolanaMessageEncoder
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
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
        validateLegacyEnvelopeSize(this, addressLookupTables)
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

    companion object {
        const val MAX_TRANSACTION_SIZE: Int = SolanaTxLegacy.MAX_TRANSACTION_SIZE

        @JvmStatic
        @JvmOverloads
        fun compile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instruction: Instruction, lookupTables: List<AddressLookupTableAccount> = emptyList()): SolanaTxV0 = compile(feePayer, blockhash, listOf(instruction), lookupTables)

        @JvmStatic
        @JvmOverloads
        fun compile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instructions: List<Instruction>, lookupTables: List<AddressLookupTableAccount> = emptyList()): SolanaTxV0 {
            val fields = compileMessage(feePayer, blockhash, instructions, lookupTables)
            return SolanaTxV0(fields.header, fields.accounts, fields.recentBlockhash, fields.instructions, fields.lookups)
        }
    }
}
