package io.ethers.solana.instruction

import io.ethers.core.Result
import io.ethers.solana.serialization.SolanaMessageDecoder
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.transaction.SolanaTransactionError
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

/**
 * A `COption`: a four-byte tag, then the value, whose bytes are present and zeroed when the tag says
 * the option is empty, so the value is read either way.
 */
private inline fun <T> SolanaMessageDecoder.readCOption(read: SolanaMessageDecoder.() -> T): T? {
    val present = readUnsignedLittleEndian(4).toLong() == 1L
    val value = read()
    return if (present) value else null
}

/**
 * A token account, as the Token and Token-2022 programs store it on chain.
 *
 * [amount] is in the mint's base units, so a caller that wants a decimal figure needs the mint's
 * decimals. [isNative] is the rent-exempt reserve of an account that wraps SOL, and null for any
 * other mint. [delegate] may spend up to [delegatedAmount].
 */
data class TokenAccount(
    val mint: SolanaAddress,
    val owner: SolanaAddress,
    val amount: BigInteger,
    val delegate: SolanaAddress?,
    val state: State,
    val isNative: BigInteger?,
    val delegatedAmount: BigInteger,
    val closeAuthority: SolanaAddress?,
) {
    /** Whether the account may be used, as the program records it. */
    enum class State {
        UNINITIALIZED,
        INITIALIZED,
        FROZEN,
    }

    companion object {
        /** Bytes the base layout occupies, which Token-2022 pads every account to before its extensions. */
        const val SIZE: Int = 165

        /** Token-2022 writes this at [SIZE] to tell an account apart from a mint padded to the same length. */
        private const val ACCOUNT_TYPE: Int = 2

        /**
         * Decode the base layout, which Token and Token-2022 share. Token-2022 extensions follow it and
         * are not decoded, but an account carrying them still reports its base fields.
         */
        @JvmStatic
        fun decode(data: ByteArray): Result<TokenAccount, SolanaTransactionError> {
            if (data.size < SIZE) {
                return Result.failure(SolanaTransactionError.MalformedBytes("A token account holds at least $SIZE bytes, got ${data.size}"))
            }
            // a Token-2022 mint is padded to the same length, so the discriminant is what separates them
            if (data.size > SIZE && data[SIZE].toInt() != ACCOUNT_TYPE) {
                return Result.failure(SolanaTransactionError.MalformedBytes("Account is not a token account, got type ${data[SIZE].toInt()}"))
            }

            val decoder = SolanaMessageDecoder(data)
            val mint = SolanaAddress(decoder.readBytes(32))
            val owner = SolanaAddress(decoder.readBytes(32))
            val amount = decoder.readUnsignedLittleEndian(8)
            val delegate = decoder.readCOption { SolanaAddress(readBytes(32)) }
            val state = when (val raw = decoder.readByte()) {
                0 -> State.UNINITIALIZED
                1 -> State.INITIALIZED
                2 -> State.FROZEN
                else -> return Result.failure(SolanaTransactionError.MalformedBytes("Token account state $raw is not one the program defines"))
            }
            val isNative = decoder.readCOption { readUnsignedLittleEndian(8) }
            val delegatedAmount = decoder.readUnsignedLittleEndian(8)
            val closeAuthority = decoder.readCOption { SolanaAddress(readBytes(32)) }
            if (decoder.failed) {
                return Result.failure(SolanaTransactionError.MalformedBytes(decoder.error ?: "Malformed token account"))
            }
            return Result.success(TokenAccount(mint, owner, amount, delegate, state, isNative, delegatedAmount, closeAuthority))
        }
    }
}

/**
 * A token mint, as the Token and Token-2022 programs store it on chain.
 *
 * [decimals] is what turns a [TokenAccount.amount] into a display figure. A null [mintAuthority] means
 * the supply is fixed, and a null [freezeAuthority] means no account of this mint can be frozen.
 */
data class TokenMint(
    val mintAuthority: SolanaAddress?,
    val supply: BigInteger,
    val decimals: Int,
    val isInitialized: Boolean,
    val freezeAuthority: SolanaAddress?,
) {
    companion object {
        /** Bytes the base layout occupies. */
        const val SIZE: Int = 82

        /** Token-2022 writes this at [TokenAccount.SIZE] to tell a mint apart from an account. */
        private const val MINT_TYPE: Int = 1

        /**
         * Decode the base layout, which Token and Token-2022 share. A Token-2022 mint carrying
         * extensions is padded to [TokenAccount.SIZE] and marked at that offset, which is what
         * separates it from an account; the extensions themselves are not decoded.
         */
        @JvmStatic
        fun decode(data: ByteArray): Result<TokenMint, SolanaTransactionError> {
            if (data.size != SIZE && (data.size <= TokenAccount.SIZE || data[TokenAccount.SIZE].toInt() != MINT_TYPE)) {
                return Result.failure(
                    SolanaTransactionError.MalformedBytes("A mint holds $SIZE bytes, or is padded past ${TokenAccount.SIZE} and marked as one, got ${data.size}"),
                )
            }

            val decoder = SolanaMessageDecoder(data)
            val mintAuthority = decoder.readCOption { SolanaAddress(readBytes(32)) }
            val supply = decoder.readUnsignedLittleEndian(8)
            val decimals = decoder.readByte()
            val isInitialized = decoder.readByte() != 0
            val freezeAuthority = decoder.readCOption { SolanaAddress(readBytes(32)) }
            if (decoder.failed) {
                return Result.failure(SolanaTransactionError.MalformedBytes(decoder.error ?: "Malformed mint"))
            }
            return Result.success(TokenMint(mintAuthority, supply, decimals, isInitialized, freezeAuthority))
        }
    }
}
