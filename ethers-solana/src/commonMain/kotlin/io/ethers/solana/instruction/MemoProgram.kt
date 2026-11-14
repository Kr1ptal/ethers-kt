package io.ethers.solana.instruction

import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.utils.encodeUtf8Strict
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

object MemoProgram {
    @JvmField val ID = Programs.MEMO

    /** Attach UTF-8 text, optionally requiring the listed accounts to sign the transaction. */
    @JvmStatic
    @JvmOverloads
    @Throws(IllegalArgumentException::class)
    fun memo(message: String, signers: List<SolanaAddress> = emptyList()): Instruction = Instruction(ID, signers.map(AccountMeta::signer), message.encodeUtf8Strict())
}
