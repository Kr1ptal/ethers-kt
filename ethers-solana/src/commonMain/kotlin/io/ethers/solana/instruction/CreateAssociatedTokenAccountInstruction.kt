package io.ethers.solana.instruction

import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress

class CreateAssociatedTokenAccountInstruction(payer: SolanaAddress, associatedToken: SolanaAddress, owner: SolanaAddress, mint: SolanaAddress) : BaseInstruction(
    Programs.ASSOCIATED_TOKEN,
    listOf(AccountMeta.signerAndWritable(payer), AccountMeta.writable(associatedToken), AccountMeta(owner), AccountMeta(mint), AccountMeta(Programs.SYSTEM), AccountMeta(Programs.TOKEN), AccountMeta(Programs.SYSVAR_RENT)),
    byteArrayOf(0),
)

class CreateAssociatedToken2022AccountInstruction(payer: SolanaAddress, associatedToken: SolanaAddress, owner: SolanaAddress, mint: SolanaAddress) : BaseInstruction(
    Programs.ASSOCIATED_TOKEN,
    listOf(AccountMeta.signerAndWritable(payer), AccountMeta.writable(associatedToken), AccountMeta(owner), AccountMeta(mint), AccountMeta(Programs.SYSTEM), AccountMeta(Programs.TOKEN_2022)),
    byteArrayOf(0),
)
