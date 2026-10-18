package io.ethers.solana.types.rpc

import io.ethers.solana.types.SolanaAddress
import io.github.artificialpb.bignum.BigInteger
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * Options for simulating a transaction.
 *
 * The node rejects [sigVerify] together with [replaceRecentBlockhash], since a substituted blockhash
 * invalidates the signatures that would be checked. [READ_ONLY] pairs the settings that let an
 * unsigned transaction with no live blockhash be simulated at all.
 *
 * Not a data class: a generated `copy` would re-run the validation without declaring it, which a Swift caller
 * cannot catch. The hand-written members below match what a data class generates.
 */
class SolanaSimulationConfig
@JvmOverloads
@Throws(IllegalArgumentException::class)
constructor(
    val commitment: Commitment? = null,
    /** Verify the transaction's signatures, rather than ignoring them. */
    val sigVerify: Boolean = false,
    /** Let the node substitute its most recent blockhash for the one in the message. */
    val replaceRecentBlockhash: Boolean = false,
    /** Return the instructions each top-level instruction invoked. */
    val innerInstructions: Boolean = false,
    /** Accounts whose post-simulation state to return, in the order given. */
    val accounts: List<SolanaAddress> = emptyList(),
    /** Fail rather than answer from a slot older than this one. */
    val minContextSlot: BigInteger? = null,
) {
    init {
        require(!sigVerify || !replaceRecentBlockhash) { "sigVerify cannot be combined with replaceRecentBlockhash" }
        require(accounts.size <= 255) { "At most 255 accounts can be returned, got ${accounts.size}" }
    }

    @Throws(IllegalArgumentException::class)
    fun copy(
        commitment: Commitment? = this.commitment,
        sigVerify: Boolean = this.sigVerify,
        replaceRecentBlockhash: Boolean = this.replaceRecentBlockhash,
        innerInstructions: Boolean = this.innerInstructions,
        accounts: List<SolanaAddress> = this.accounts,
        minContextSlot: BigInteger? = this.minContextSlot,
    ): SolanaSimulationConfig = SolanaSimulationConfig(commitment, sigVerify, replaceRecentBlockhash, innerInstructions, accounts, minContextSlot)

    operator fun component1(): Commitment? = commitment
    operator fun component2(): Boolean = sigVerify
    operator fun component3(): Boolean = replaceRecentBlockhash
    operator fun component4(): Boolean = innerInstructions
    operator fun component5(): List<SolanaAddress> = accounts
    operator fun component6(): BigInteger? = minContextSlot

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SolanaSimulationConfig) return false
        return commitment == other.commitment &&
            sigVerify == other.sigVerify &&
            replaceRecentBlockhash == other.replaceRecentBlockhash &&
            innerInstructions == other.innerInstructions &&
            accounts == other.accounts &&
            minContextSlot == other.minContextSlot
    }

    override fun hashCode(): Int {
        var result = commitment?.hashCode() ?: 0
        result = 31 * result + sigVerify.hashCode()
        result = 31 * result + replaceRecentBlockhash.hashCode()
        result = 31 * result + innerInstructions.hashCode()
        result = 31 * result + accounts.hashCode()
        result = 31 * result + (minContextSlot?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "SolanaSimulationConfig(commitment=$commitment, sigVerify=$sigVerify, replaceRecentBlockhash=$replaceRecentBlockhash, " +
        "innerInstructions=$innerInstructions, accounts=$accounts, minContextSlot=$minContextSlot)"

    companion object {
        /**
         * Simulate without signatures and against the node's own blockhash, the closest equivalent of
         * an EVM `eth_call`.
         */
        @JvmField
        val READ_ONLY = SolanaSimulationConfig(sigVerify = false, replaceRecentBlockhash = true)
    }
}
