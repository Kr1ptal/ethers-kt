package io.ethers.solana.types.rpc

import io.github.artificialpb.bignum.BigInteger
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * Options for submitting a transaction.
 *
 * A node runs the transaction against [preflightCommitment] before forwarding it, rejecting one that
 * would fail. [skipPreflight] trades that check for latency, which is the usual choice once the same
 * transaction has already been simulated, and the only choice when the failure is expected - an
 * arbitrage attempt that loses its race, say.
 */
data class SolanaSendConfig @JvmOverloads constructor(
    /** Submit without the node simulating the transaction first. */
    val skipPreflight: Boolean = false,
    /** Bank state the preflight simulation runs against; ignored when [skipPreflight] is set. */
    val preflightCommitment: Commitment? = null,
    /**
     * How many times the node rebroadcasts to the leader before giving up. Null leaves the node's own
     * default, which retries until the blockhash expires.
     */
    val maxRetries: Int? = null,
    /** Fail rather than submit against a slot older than this one. */
    val minContextSlot: BigInteger? = null,
) {
    init {
        require(maxRetries == null || maxRetries >= 0) { "maxRetries cannot be negative, got $maxRetries" }
    }

    companion object {
        /** Skip the node's simulation, for a transaction already simulated or expected to fail. */
        @JvmField
        val SKIP_PREFLIGHT = SolanaSendConfig(skipPreflight = true)
    }
}
