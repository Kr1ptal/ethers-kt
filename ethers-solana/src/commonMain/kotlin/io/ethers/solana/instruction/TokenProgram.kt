package io.ethers.solana.instruction

import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.utils.littleEndian
import io.ethers.solana.utils.requireU64
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

object TokenProgram {
    @JvmField val ID = Programs.TOKEN

    /**
     * SPL TransferChecked, including multisig owners. [tokenProgram] selects the program that owns
     * the accounts; Token-2022 extensions can require extra account metas.
     */
    @JvmStatic
    @JvmOverloads
    fun transferChecked(
        from: SolanaAddress,
        to: SolanaAddress,
        mint: SolanaAddress,
        owner: SolanaAddress,
        amount: BigInteger,
        decimals: Int,
        signers: List<SolanaAddress> = emptyList(),
        tokenProgram: SolanaAddress = ID,
    ): Instruction = Instruction(
        tokenProgram,
        listOf(AccountMeta.writable(from), AccountMeta(mint), AccountMeta.writable(to), AccountMeta(owner, signer = signers.isEmpty())) + signers.map(AccountMeta::signer),
        byteArrayOf(12) + littleEndian(requireU64(amount), 8) + byteArrayOf(decimals.also { require(it in 0..255) }.toByte()),
    )

    @JvmStatic
    @JvmOverloads
    fun transferChecked(
        from: SolanaAddress,
        to: SolanaAddress,
        mint: SolanaAddress,
        owner: SolanaAddress,
        amount: Long,
        decimals: Int,
        signers: List<SolanaAddress> = emptyList(),
    ): Instruction = transferChecked(from, to, mint, owner, bigIntegerOf(amount), decimals, signers, ID)
}

object Token2022Program {
    @JvmField val ID = Programs.TOKEN_2022

    /** Token-2022 TransferChecked, including multisig owners. */
    @JvmStatic
    @JvmOverloads
    fun transferChecked(
        from: SolanaAddress,
        to: SolanaAddress,
        mint: SolanaAddress,
        owner: SolanaAddress,
        amount: BigInteger,
        decimals: Int,
        signers: List<SolanaAddress> = emptyList(),
    ): Instruction = TokenProgram.transferChecked(from, to, mint, owner, amount, decimals, signers, ID)

    @JvmStatic
    @JvmOverloads
    fun transferChecked(
        from: SolanaAddress,
        to: SolanaAddress,
        mint: SolanaAddress,
        owner: SolanaAddress,
        amount: Long,
        decimals: Int,
        signers: List<SolanaAddress> = emptyList(),
    ): Instruction = transferChecked(from, to, mint, owner, bigIntegerOf(amount), decimals, signers)
}
