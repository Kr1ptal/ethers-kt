package io.ethers.solana.types.transaction

import io.ethers.solana.instruction.Instruction
import io.ethers.solana.serialization.SolanaMessageEncoder
import io.ethers.solana.types.Blockhash
import io.ethers.solana.types.SolanaAddress
import kotlin.jvm.JvmStatic

/** Immutable legacy transaction payload. Every account is inline; lookup tables are not supported. */
class SolanaTxLegacy(
    override val header: MessageHeader,
    accounts: List<SolanaAddress>,
    override val recentBlockhash: Blockhash,
    instructions: List<CompiledInstruction>,
) : SolanaTransactionUnsigned {
    private val staticAccounts = accounts.toList()
    private val compiledInstructions = instructions.toList()
    override val accounts: List<SolanaAddress> get() = staticAccounts.toList()
    override val instructions: List<CompiledInstruction> get() = compiledInstructions.toList()
    override val type: SolanaTxType get() = SolanaTxType.LEGACY

    init {
        validateMessage(header, staticAccounts, compiledInstructions, emptyList())
    }

    override fun withNewBlockhash(blockhash: Blockhash): SolanaTxLegacy = SolanaTxLegacy(header, staticAccounts, blockhash, compiledInstructions)

    override fun serializeMessage(): ByteArray = SolanaMessageEncoder().also { it.writeMessageBody(this) }.toByteArray()

    companion object {
        @JvmStatic
        fun compile(feePayer: SolanaAddress, blockhash: Blockhash, instruction: Instruction): SolanaTxLegacy = compile(feePayer, blockhash, listOf(instruction))

        @JvmStatic
        fun compile(feePayer: SolanaAddress, blockhash: Blockhash, instructions: List<Instruction>): SolanaTxLegacy {
            val fields = compileMessage(feePayer, blockhash, instructions, emptyList())
            return SolanaTxLegacy(fields.header, fields.accounts, fields.recentBlockhash, fields.instructions)
        }
    }
}
