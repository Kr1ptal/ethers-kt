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

    /** Change the authority that may advance or withdraw from a nonce account. */
    @JvmStatic
    fun authorizeNonceAccount(nonceAccount: SolanaAddress, authority: SolanaAddress, newAuthority: SolanaAddress): Instruction = Instruction(
        ID,
        listOf(AccountMeta.writable(nonceAccount), AccountMeta.signer(authority)),
        payload(7, 32) { newAuthority.asByteArray().copyInto(it, 4) },
    )

    /** Upgrade a legacy nonce account to the current nonce domain. No authority signature is required. */
    @JvmStatic
    fun upgradeNonceAccount(nonceAccount: SolanaAddress): Instruction = Instruction(ID, listOf(AccountMeta.writable(nonceAccount)), payload(12, 0) {})

    /** Create a seeded account. Its address must match [SolanaAddress.createWithSeed]. */
    @JvmStatic
    fun createAccountWithSeed(from: SolanaAddress, account: SolanaAddress, base: SolanaAddress, seed: String, lamports: BigInteger, space: Long, owner: SolanaAddress): Instruction {
        val encoded = seededPrefix(account, base, seed, owner)
        return Instruction(
            ID,
            listOf(AccountMeta.signerAndWritable(from), AccountMeta.writable(account)) + if (base == from) emptyList() else listOf(AccountMeta.signer(base)),
            payload(3, encoded.size + 48) {
                encoded.copyInto(it, 4)
                littleEndianInto(it, 4 + encoded.size, requireU64(lamports), 8)
                littleEndianInto(it, 12 + encoded.size, requireSpace(space), 8)
                owner.asByteArray().copyInto(it, 20 + encoded.size)
            },
        )
    }

    @JvmStatic
    fun createAccountWithSeed(from: SolanaAddress, account: SolanaAddress, base: SolanaAddress, seed: String, lamports: Long, space: Long, owner: SolanaAddress): Instruction = createAccountWithSeed(from, account, base, seed, bigIntegerOf(lamports), space, owner)

    /** Allocate and assign a seeded account, authorized by its base. */
    @JvmStatic
    fun allocateWithSeed(account: SolanaAddress, base: SolanaAddress, seed: String, space: Long, owner: SolanaAddress): Instruction {
        val encoded = seededPrefix(account, base, seed, owner)
        return Instruction(
            ID,
            listOf(AccountMeta.writable(account), AccountMeta.signer(base)),
            payload(9, encoded.size + 40) {
                encoded.copyInto(it, 4)
                littleEndianInto(it, 4 + encoded.size, requireSpace(space), 8)
                owner.asByteArray().copyInto(it, 12 + encoded.size)
            },
        )
    }

    /** Assign a seeded account to the owner used in its derivation. */
    @JvmStatic
    fun assignWithSeed(account: SolanaAddress, base: SolanaAddress, seed: String, owner: SolanaAddress): Instruction {
        val encoded = seededPrefix(account, base, seed, owner)
        return Instruction(
            ID,
            listOf(AccountMeta.writable(account), AccountMeta.signer(base)),
            payload(10, encoded.size + 32) {
                encoded.copyInto(it, 4)
                owner.asByteArray().copyInto(it, 4 + encoded.size)
            },
        )
    }

    /** Transfer from a seeded account, using its base signature instead of an account signature. */
    @JvmStatic
    fun transferWithSeed(from: SolanaAddress, base: SolanaAddress, seed: String, owner: SolanaAddress, to: SolanaAddress, lamports: BigInteger): Instruction {
        val prefix = seededPrefix(from, base, seed, owner)
        val encoded = prefix.copyOfRange(32, prefix.size)
        return Instruction(
            ID,
            listOf(AccountMeta.writable(from), AccountMeta.signer(base), AccountMeta.writable(to)),
            payload(11, 40 + encoded.size) {
                littleEndianInto(it, 4, requireU64(lamports), 8)
                encoded.copyInto(it, 12)
                owner.asByteArray().copyInto(it, 12 + encoded.size)
            },
        )
    }

    @JvmStatic
    fun transferWithSeed(from: SolanaAddress, base: SolanaAddress, seed: String, owner: SolanaAddress, to: SolanaAddress, lamports: Long): Instruction = transferWithSeed(from, base, seed, owner, to, bigIntegerOf(lamports))

    private fun seededPrefix(account: SolanaAddress, base: SolanaAddress, seed: String, owner: SolanaAddress): ByteArray {
        require(account == SolanaAddress.createWithSeed(base, seed, owner)) { "Account does not match base, seed and owner" }
        val bytes = seed.encodeToByteArray()
        return ByteArray(40 + bytes.size).also {
            base.asByteArray().copyInto(it)
            littleEndianInto(it, 32, bytes.size.toLong(), 8)
            bytes.copyInto(it, 40)
        }
    }

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

internal fun ByteArray.isAdvanceNonceData(): Boolean = size == 4 && this[0] == 4.toByte() && this[1] == 0.toByte() && this[2] == 0.toByte() && this[3] == 0.toByte()
