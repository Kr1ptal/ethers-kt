package io.ethers.solana.types.transaction

import io.ethers.solana.instruction.Instruction
import io.ethers.solana.serialization.SolanaMessageEncoder
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import kotlin.jvm.JvmStatic

/** Immutable legacy transaction payload. Every account is inline; lookup tables are not supported. */
class SolanaTxLegacy(
    override val header: MessageHeader,
    override val accounts: List<SolanaAddress>,
    override val recentBlockhash: SolanaBlockhash,
    override val instructions: List<CompiledInstruction>,
) : SolanaTransactionUnsigned {
    override val type: SolanaTxType get() = SolanaTxType.Legacy

    init {
        validateMessage(header, accounts, instructions, emptyList())
        validateLegacyEnvelopeSize(this)
    }

    override fun withNewBlockhash(blockhash: SolanaBlockhash): SolanaTxLegacy = SolanaTxLegacy(header, accounts, blockhash, instructions)

    override fun serializeMessage(): ByteArray = SolanaMessageEncoder().also { it.writeMessageBody(this) }.toByteArray()

    companion object {
        const val MAX_TRANSACTION_SIZE: Int = 1232

        @JvmStatic
        fun compile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instruction: Instruction): SolanaTxLegacy = compile(feePayer, blockhash, listOf(instruction))

        @JvmStatic
        fun compile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instructions: List<Instruction>): SolanaTxLegacy {
            val fields = compileMessage(feePayer, blockhash, instructions, emptyList())
            return SolanaTxLegacy(fields.header, fields.accounts, fields.recentBlockhash, fields.instructions)
        }
    }
}
