package io.ethers.solana.instruction

import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

object AssociatedTokenProgram {
    @JvmField val ID = Programs.ASSOCIATED_TOKEN

    /** Create [owner]'s associated account for an SPL Token [mint], funded by [payer]. */
    @JvmStatic
    fun createAccount(payer: SolanaAddress, associatedToken: SolanaAddress, owner: SolanaAddress, mint: SolanaAddress): Instruction = Instruction(
        ID,
        listOf(
            AccountMeta.signerAndWritable(payer),
            AccountMeta.writable(associatedToken),
            AccountMeta(owner),
            AccountMeta(mint),
            AccountMeta(Programs.SYSTEM),
            AccountMeta(Programs.TOKEN),
            AccountMeta(Programs.SYSVAR_RENT),
        ),
        byteArrayOf(0),
    )

    /** Create [owner]'s associated account for a Token-2022 [mint], funded by [payer]. */
    @JvmStatic
    fun createToken2022Account(payer: SolanaAddress, associatedToken: SolanaAddress, owner: SolanaAddress, mint: SolanaAddress): Instruction = Instruction(
        ID,
        listOf(
            AccountMeta.signerAndWritable(payer),
            AccountMeta.writable(associatedToken),
            AccountMeta(owner),
            AccountMeta(mint),
            AccountMeta(Programs.SYSTEM),
            AccountMeta(Programs.TOKEN_2022),
        ),
        byteArrayOf(0),
    )

    /** Create the SPL Token ATA if absent; an existing matching account succeeds unchanged. */
    @JvmStatic
    fun createAccountIdempotent(payer: SolanaAddress, associatedToken: SolanaAddress, owner: SolanaAddress, mint: SolanaAddress): Instruction = createIdempotent(payer, associatedToken, owner, mint, Programs.TOKEN)

    /** Derive the SPL Token ATA and create it if absent. */
    @JvmStatic
    fun createAccountIdempotent(payer: SolanaAddress, owner: SolanaAddress, mint: SolanaAddress): Instruction = createAccountIdempotent(payer, SolanaAddress.findAssociatedTokenAddress(owner, mint).address, owner, mint)

    /** Create the Token-2022 ATA if absent; an existing matching account succeeds unchanged. */
    @JvmStatic
    fun createToken2022AccountIdempotent(payer: SolanaAddress, associatedToken: SolanaAddress, owner: SolanaAddress, mint: SolanaAddress): Instruction = createIdempotent(payer, associatedToken, owner, mint, Programs.TOKEN_2022)

    /** Derive the Token-2022 ATA and create it if absent. */
    @JvmStatic
    fun createToken2022AccountIdempotent(payer: SolanaAddress, owner: SolanaAddress, mint: SolanaAddress): Instruction = createToken2022AccountIdempotent(payer, SolanaAddress.findAssociatedTokenAddress(owner, mint, Programs.TOKEN_2022).address, owner, mint)

    private fun createIdempotent(payer: SolanaAddress, associatedToken: SolanaAddress, owner: SolanaAddress, mint: SolanaAddress, tokenProgram: SolanaAddress): Instruction = Instruction(
        ID,
        listOf(
            AccountMeta.signerAndWritable(payer),
            AccountMeta.writable(associatedToken),
            AccountMeta(owner),
            AccountMeta(mint),
            AccountMeta(Programs.SYSTEM),
            AccountMeta(tokenProgram),
        ),
        byteArrayOf(1),
    )
}
