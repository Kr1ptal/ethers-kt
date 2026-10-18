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
 *
 * Not a data class: a generated `copy` would re-run the validation without declaring it, which a Swift caller
 * cannot catch. The hand-written members below match what a data class generates.
 */
class SolanaSendConfig
@JvmOverloads
@Throws(IllegalArgumentException::class)
constructor(
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

    @Throws(IllegalArgumentException::class)
    fun copy(
        skipPreflight: Boolean = this.skipPreflight,
        preflightCommitment: Commitment? = this.preflightCommitment,
        maxRetries: Int? = this.maxRetries,
        minContextSlot: BigInteger? = this.minContextSlot,
    ): SolanaSendConfig = SolanaSendConfig(skipPreflight, preflightCommitment, maxRetries, minContextSlot)

    operator fun component1(): Boolean = skipPreflight
    operator fun component2(): Commitment? = preflightCommitment
    operator fun component3(): Int? = maxRetries
    operator fun component4(): BigInteger? = minContextSlot

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SolanaSendConfig) return false
        return skipPreflight == other.skipPreflight &&
            preflightCommitment == other.preflightCommitment &&
            maxRetries == other.maxRetries &&
            minContextSlot == other.minContextSlot
    }

    override fun hashCode(): Int {
        var result = skipPreflight.hashCode()
        result = 31 * result + (preflightCommitment?.hashCode() ?: 0)
        result = 31 * result + (maxRetries ?: 0)
        result = 31 * result + (minContextSlot?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "SolanaSendConfig(skipPreflight=$skipPreflight, preflightCommitment=$preflightCommitment, maxRetries=$maxRetries, minContextSlot=$minContextSlot)"

    companion object {
        /** Skip the node's simulation, for a transaction already simulated or expected to fail. */
        @JvmField
        val SKIP_PREFLIGHT = SolanaSendConfig(skipPreflight = true)
    }
}
