package io.ethers.solana.types.transaction

import io.ethers.solana.instruction.Instruction
import io.ethers.solana.serialization.SolanaMessageEncoder
import io.ethers.solana.types.Blockhash
import io.ethers.solana.types.SolanaAddress
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/** Immutable v0 transaction payload. Lookup table contents are supplied by the caller during compilation. */
class SolanaTxV0 @JvmOverloads constructor(
    override val header: MessageHeader,
    accounts: List<SolanaAddress>,
    override val recentBlockhash: Blockhash,
    instructions: List<CompiledInstruction>,
    addressLookupTables: List<CompiledAddressLookupTable> = emptyList(),
) : SolanaTransactionUnsigned {
    private val staticAccounts = accounts.toList()
    private val compiledInstructions = instructions.toList()
    private val lookups = addressLookupTables.toList()
    override val accounts: List<SolanaAddress> get() = staticAccounts.toList()
    override val instructions: List<CompiledInstruction> get() = compiledInstructions.toList()
    val addressLookupTables: List<CompiledAddressLookupTable> get() = lookups.toList()
    override val type: SolanaTxType get() = SolanaTxType.V0

    init {
        validateMessage(header, staticAccounts, compiledInstructions, lookups)
    }

    override fun withNewBlockhash(blockhash: Blockhash): SolanaTxV0 = SolanaTxV0(header, staticAccounts, blockhash, compiledInstructions, lookups)

    override fun serializeMessage(): ByteArray {
        val encoder = SolanaMessageEncoder().writeByte(128)
        encoder.writeMessageBody(this)
        encoder.writeShortVecLength(lookups.size)
        for (table in lookups) {
            encoder.writeBytes(table.key.toByteArray()).writeShortVecLength(table.writableIndexes.size)
            table.writableIndexes.forEach { encoder.writeByte(it) }
            encoder.writeShortVecLength(table.readonlyIndexes.size)
            table.readonlyIndexes.forEach { encoder.writeByte(it) }
        }
        return encoder.toByteArray()
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun compile(feePayer: SolanaAddress, blockhash: Blockhash, instruction: Instruction, lookupTables: List<AddressLookupTableAccount> = emptyList()): SolanaTxV0 = compile(feePayer, blockhash, listOf(instruction), lookupTables)

        @JvmStatic
        @JvmOverloads
        fun compile(feePayer: SolanaAddress, blockhash: Blockhash, instructions: List<Instruction>, lookupTables: List<AddressLookupTableAccount> = emptyList()): SolanaTxV0 {
            val fields = compileMessage(feePayer, blockhash, instructions, lookupTables)
            return SolanaTxV0(fields.header, fields.accounts, fields.recentBlockhash, fields.instructions, fields.lookups)
        }
    }
}
