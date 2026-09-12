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

    /**
     * Create [account] with [lamports] and [space] bytes, owned by [owner].
     *
     * [lamports] must cover rent exemption for [space] or the account is purged; ask the node with
     * `getMinimumBalanceForRentExemption`.
     */
    @JvmStatic
    fun createAccount(from: SolanaAddress, account: SolanaAddress, lamports: BigInteger, space: Long, owner: SolanaAddress): Instruction = Instruction(
        ID,
        listOf(AccountMeta.signerAndWritable(from), AccountMeta.signerAndWritable(account)),
        byteArrayOf(0, 0, 0, 0) + littleEndian(requireU64(lamports), 8) + littleEndian(requireSpace(space), 8) + owner.asByteArray(),
    )

    @JvmStatic
    fun createAccount(from: SolanaAddress, account: SolanaAddress, lamports: Long, space: Long, owner: SolanaAddress): Instruction = createAccount(from, account, bigIntegerOf(lamports), space, owner)

    /** Hand ownership of [account], which must be empty of data, to [owner]. */
    @JvmStatic
    fun assign(account: SolanaAddress, owner: SolanaAddress): Instruction = Instruction(
        ID,
        listOf(AccountMeta.signerAndWritable(account)),
        byteArrayOf(1, 0, 0, 0) + owner.asByteArray(),
    )

    /** Give [account] [space] bytes of data, which it must not already have. */
    @JvmStatic
    fun allocate(account: SolanaAddress, space: Long): Instruction = Instruction(
        ID,
        listOf(AccountMeta.signerAndWritable(account)),
        byteArrayOf(8, 0, 0, 0) + littleEndian(requireSpace(space), 8),
    )

    /**
     * Prepare a nonce account, which lets a transaction be signed now and submitted much later: it is
     * authorized by a stored nonce rather than a blockhash, so it does not expire after a minute.
     * The account must already exist with [NONCE_ACCOUNT_SIZE] bytes and rent-exempt lamports.
     */
    @JvmStatic
    fun initializeNonceAccount(nonceAccount: SolanaAddress, authority: SolanaAddress): Instruction = Instruction(
        ID,
        listOf(AccountMeta.writable(nonceAccount), AccountMeta(Programs.SYSVAR_RECENT_BLOCKHASHES), AccountMeta(Programs.SYSVAR_RENT)),
        byteArrayOf(6, 0, 0, 0) + authority.asByteArray(),
    )

    /**
     * Consume the stored nonce, which every transaction authorized by one must do first, as its very
     * first instruction. The nonce advances so the transaction cannot be replayed.
     */
    @JvmStatic
    fun advanceNonceAccount(nonceAccount: SolanaAddress, authority: SolanaAddress): Instruction = Instruction(
        ID,
        listOf(AccountMeta.writable(nonceAccount), AccountMeta(Programs.SYSVAR_RECENT_BLOCKHASHES), AccountMeta.signer(authority)),
        byteArrayOf(4, 0, 0, 0),
    )

    /** Move [lamports] out of a nonce account, which must keep enough to stay rent exempt. */
    @JvmStatic
    fun withdrawNonceAccount(nonceAccount: SolanaAddress, authority: SolanaAddress, to: SolanaAddress, lamports: BigInteger): Instruction = Instruction(
        ID,
        listOf(
            AccountMeta.writable(nonceAccount),
            AccountMeta.writable(to),
            AccountMeta(Programs.SYSVAR_RECENT_BLOCKHASHES),
            AccountMeta(Programs.SYSVAR_RENT),
            AccountMeta.signer(authority),
        ),
        byteArrayOf(5, 0, 0, 0) + littleEndian(requireU64(lamports), 8),
    )

    /** Bytes a nonce account occupies, which its rent exemption is calculated from. */
    const val NONCE_ACCOUNT_SIZE: Long = 80

    private fun requireSpace(space: Long): Long {
        require(space in 0..MAX_PERMITTED_DATA_LENGTH) { "Account space must be in 0..$MAX_PERMITTED_DATA_LENGTH, got $space" }
        return space
    }

    /** The runtime's ceiling on a single account's data length, 10 MiB. */
    const val MAX_PERMITTED_DATA_LENGTH: Long = 10 * 1024 * 1024
}
