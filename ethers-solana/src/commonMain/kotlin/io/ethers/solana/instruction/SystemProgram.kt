package io.ethers.solana.instruction

import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.utils.littleEndianInto
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
        payload(TRANSFER, 8) { littleEndianInto(it, 4, requireU64(lamports), 8) },
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
        payload(CREATE_ACCOUNT, 48) {
            littleEndianInto(it, 4, requireU64(lamports), 8)
            littleEndianInto(it, 12, requireSpace(space), 8)
            owner.asByteArray().copyInto(it, 20)
        },
    )

    @JvmStatic
    fun createAccount(from: SolanaAddress, account: SolanaAddress, lamports: Long, space: Long, owner: SolanaAddress): Instruction = createAccount(from, account, bigIntegerOf(lamports), space, owner)

    /** Hand ownership of [account], which must be empty of data, to [owner]. */
    @JvmStatic
    fun assign(account: SolanaAddress, owner: SolanaAddress): Instruction = Instruction(
        ID,
        listOf(AccountMeta.signerAndWritable(account)),
        payload(ASSIGN, 32) { owner.asByteArray().copyInto(it, 4) },
    )

    /** Give [account] [space] bytes of data, which it must not already have. */
    @JvmStatic
    fun allocate(account: SolanaAddress, space: Long): Instruction = Instruction(
        ID,
        listOf(AccountMeta.signerAndWritable(account)),
        payload(ALLOCATE, 8) { littleEndianInto(it, 4, requireSpace(space), 8) },
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
        payload(INITIALIZE_NONCE_ACCOUNT, 32) { authority.asByteArray().copyInto(it, 4) },
    )

    /**
     * Consume the stored nonce, which every transaction authorized by one must do first, as its very
     * first instruction. The nonce advances so the transaction cannot be replayed.
     */
    @JvmStatic
    fun advanceNonceAccount(nonceAccount: SolanaAddress, authority: SolanaAddress): Instruction = Instruction(
        ID,
        listOf(AccountMeta.writable(nonceAccount), AccountMeta(Programs.SYSVAR_RECENT_BLOCKHASHES), AccountMeta.signer(authority)),
        payload(ADVANCE_NONCE_ACCOUNT, 0) {},
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
        payload(WITHDRAW_NONCE_ACCOUNT, 8) { littleEndianInto(it, 4, requireU64(lamports), 8) },
    )

    /** Bytes a nonce account occupies, which its rent exemption is calculated from. */
    const val NONCE_ACCOUNT_SIZE: Long = 80

    private fun requireSpace(space: Long): Long {
        require(space in 0..MAX_PERMITTED_DATA_LENGTH) { "Account space must be in 0..$MAX_PERMITTED_DATA_LENGTH, got $space" }
        return space
    }

    /** The runtime's ceiling on a single account's data length, 10 MiB. */
    const val MAX_PERMITTED_DATA_LENGTH: Long = 10 * 1024 * 1024

    // discriminants, which the program reads as a little-endian u32
    private const val CREATE_ACCOUNT = 0
    private const val ASSIGN = 1
    private const val TRANSFER = 2
    private const val ADVANCE_NONCE_ACCOUNT = 4
    private const val WITHDRAW_NONCE_ACCOUNT = 5
    private const val INITIALIZE_NONCE_ACCOUNT = 6
    private const val ALLOCATE = 8

    /**
     * One array for the whole payload: the discriminant, then [size] bytes the caller fills in place.
     * The array starts zeroed, so the discriminant's three high bytes need no writing.
     */
    private inline fun payload(discriminant: Int, size: Int, fill: (ByteArray) -> Unit): ByteArray = ByteArray(4 + size).also { it[0] = discriminant.toByte() }.also(fill)
}
