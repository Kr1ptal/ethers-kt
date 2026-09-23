package io.ethers.solana.instruction

import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

object MemoProgram {
    @JvmField val ID = Programs.MEMO

    /** Attach UTF-8 text, optionally requiring the listed accounts to sign the transaction. */
    @JvmStatic
    @JvmOverloads
    fun memo(message: String, signers: List<SolanaAddress> = emptyList()): Instruction = Instruction(ID, signers.map(AccountMeta::signer), message.encodeToByteArray(throwOnInvalidSequence = true))
}
