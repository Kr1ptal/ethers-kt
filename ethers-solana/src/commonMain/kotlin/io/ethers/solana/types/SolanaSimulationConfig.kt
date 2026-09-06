package io.ethers.solana.types

import io.github.artificialpb.bignum.BigInteger
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * Options for simulating a transaction.
 *
 * The node rejects [sigVerify] together with [replaceRecentBlockhash], since a substituted blockhash
 * invalidates the signatures that would be checked. [READ_ONLY] pairs the settings that let an
 * unsigned transaction with no live blockhash be simulated at all.
 */
data class SolanaSimulationConfig @JvmOverloads constructor(
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

    companion object {
        /**
         * Simulate without signatures and against the node's own blockhash, the closest equivalent of
         * an EVM `eth_call`.
         */
        @JvmField
        val READ_ONLY = SolanaSimulationConfig(sigVerify = false, replaceRecentBlockhash = true)
    }
}
