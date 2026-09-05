package io.ethers.solana.instruction

import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.Programs
import io.ethers.solana.types.PublicKey
import io.ethers.solana.utils.littleEndian
import io.ethers.solana.utils.requireU64
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlin.jvm.JvmOverloads

/** SPL TransferChecked, including multisig owners. Token-2022 extensions can require extra account metas. */
open class TokenTransferInstruction @JvmOverloads constructor(
    from: PublicKey,
    to: PublicKey,
    mint: PublicKey,
    owner: PublicKey,
    amount: BigInteger,
    decimals: Int,
    signers: List<PublicKey> = emptyList(),
    tokenProgram: PublicKey = Programs.TOKEN,
) : BaseInstruction(
    tokenProgram,
    listOf(AccountMeta.writable(from), AccountMeta(mint), AccountMeta.writable(to), AccountMeta(owner, signer = signers.isEmpty())) + signers.map(AccountMeta::signer),
    byteArrayOf(12) + littleEndian(requireU64(amount), 8) + byteArrayOf(decimals.also { require(it in 0..255) }.toByte()),
)

class SplTransferInstruction @JvmOverloads constructor(from: PublicKey, to: PublicKey, mint: PublicKey, owner: PublicKey, amount: BigInteger, decimals: Int, signers: List<PublicKey> = emptyList()) :
    TokenTransferInstruction(from, to, mint, owner, amount, decimals, signers, Programs.TOKEN) {
    @JvmOverloads constructor(from: PublicKey, to: PublicKey, mint: PublicKey, owner: PublicKey, amount: Long, decimals: Int, signers: List<PublicKey> = emptyList()) :
        this(from, to, mint, owner, bigIntegerOf(amount), decimals, signers)
}

class Token2022TransferInstruction @JvmOverloads constructor(from: PublicKey, to: PublicKey, mint: PublicKey, owner: PublicKey, amount: BigInteger, decimals: Int, signers: List<PublicKey> = emptyList()) :
    TokenTransferInstruction(from, to, mint, owner, amount, decimals, signers, Programs.TOKEN_2022) {
    @JvmOverloads constructor(from: PublicKey, to: PublicKey, mint: PublicKey, owner: PublicKey, amount: Long, decimals: Int, signers: List<PublicKey> = emptyList()) :
        this(from, to, mint, owner, bigIntegerOf(amount), decimals, signers)
}
