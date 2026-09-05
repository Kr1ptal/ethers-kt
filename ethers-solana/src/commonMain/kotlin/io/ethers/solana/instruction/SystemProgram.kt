package io.ethers.solana.instruction

import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.utils.littleEndian
import io.ethers.solana.utils.requireU64
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

object SystemProgram {
    @JvmField val ID = Programs.SYSTEM

    /** Move [lamports] of SOL from [from] to [to]. */
    @JvmStatic
    fun transfer(from: SolanaAddress, to: SolanaAddress, lamports: BigInteger): Instruction = Instruction(
        ID,
        listOf(AccountMeta.signerAndWritable(from), AccountMeta.writable(to)),
        byteArrayOf(2, 0, 0, 0) + littleEndian(requireU64(lamports), 8),
    )

    @JvmStatic
    fun transfer(from: SolanaAddress, to: SolanaAddress, lamports: Long): Instruction = transfer(from, to, bigIntegerOf(lamports))
}
