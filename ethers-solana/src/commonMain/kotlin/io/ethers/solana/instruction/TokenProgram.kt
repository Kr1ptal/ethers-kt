package io.ethers.solana.instruction

import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.utils.littleEndianInto
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
        checkedAmountPayload(12, amount, decimals),
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

    /** Let [delegate] move up to [amount] from [account], checked against the mint's [decimals]. */
    @JvmStatic
    @JvmOverloads
    fun approveChecked(
        account: SolanaAddress,
        mint: SolanaAddress,
        delegate: SolanaAddress,
        owner: SolanaAddress,
        amount: BigInteger,
        decimals: Int,
        signers: List<SolanaAddress> = emptyList(),
        tokenProgram: SolanaAddress = ID,
    ): Instruction = Instruction(
        tokenProgram,
        listOf(AccountMeta.writable(account), AccountMeta(mint), AccountMeta(delegate), AccountMeta(owner, signer = signers.isEmpty())) + signers.map(AccountMeta::signer),
        checkedAmountPayload(13, amount, decimals),
    )

    /** Withdraw any delegation on [account]. */
    @JvmStatic
    @JvmOverloads
    fun revoke(
        account: SolanaAddress,
        owner: SolanaAddress,
        signers: List<SolanaAddress> = emptyList(),
        tokenProgram: SolanaAddress = ID,
    ): Instruction = Instruction(
        tokenProgram,
        listOf(AccountMeta.writable(account), AccountMeta(owner, signer = signers.isEmpty())) + signers.map(AccountMeta::signer),
        byteArrayOf(5),
    )

    /** Destroy [amount] tokens held by [account], reducing the mint's supply. */
    @JvmStatic
    @JvmOverloads
    fun burnChecked(
        account: SolanaAddress,
        mint: SolanaAddress,
        owner: SolanaAddress,
        amount: BigInteger,
        decimals: Int,
        signers: List<SolanaAddress> = emptyList(),
        tokenProgram: SolanaAddress = ID,
    ): Instruction = Instruction(
        tokenProgram,
        listOf(AccountMeta.writable(account), AccountMeta.writable(mint), AccountMeta(owner, signer = signers.isEmpty())) + signers.map(AccountMeta::signer),
        checkedAmountPayload(15, amount, decimals),
    )

    /** Create [amount] new tokens in [account], which only the mint authority may do. */
    @JvmStatic
    @JvmOverloads
    fun mintToChecked(
        mint: SolanaAddress,
        account: SolanaAddress,
        authority: SolanaAddress,
        amount: BigInteger,
        decimals: Int,
        signers: List<SolanaAddress> = emptyList(),
        tokenProgram: SolanaAddress = ID,
    ): Instruction = Instruction(
        tokenProgram,
        listOf(AccountMeta.writable(mint), AccountMeta.writable(account), AccountMeta(authority, signer = signers.isEmpty())) + signers.map(AccountMeta::signer),
        checkedAmountPayload(14, amount, decimals),
    )

    /** Close an empty [account], returning its rent lamports to [destination]. */
    @JvmStatic
    @JvmOverloads
    fun closeAccount(
        account: SolanaAddress,
        destination: SolanaAddress,
        owner: SolanaAddress,
        signers: List<SolanaAddress> = emptyList(),
        tokenProgram: SolanaAddress = ID,
    ): Instruction = Instruction(
        tokenProgram,
        listOf(AccountMeta.writable(account), AccountMeta.writable(destination), AccountMeta(owner, signer = signers.isEmpty())) + signers.map(AccountMeta::signer),
        byteArrayOf(9),
    )

    /**
     * The layout every checked instruction shares: a one-byte discriminant, the amount, and the
     * decimals it is checked against. Built in one array rather than concatenated in three.
     */
    private fun checkedAmountPayload(discriminant: Int, amount: BigInteger, decimals: Int): ByteArray {
        val data = ByteArray(10)
        data[0] = discriminant.toByte()
        littleEndianInto(data, 1, requireU64(amount), 8)
        data[9] = requireDecimals(decimals).toByte()
        return data
    }

    private fun requireDecimals(decimals: Int): Int {
        require(decimals in 0..255) { "Decimals must fit a byte, got $decimals" }
        return decimals
    }
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
