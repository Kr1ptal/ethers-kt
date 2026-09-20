package io.ethers.solana.types.rpc

import io.github.artificialpb.bignum.BigInteger
import kotlin.jvm.JvmOverloads

/** Options for RPC methods that support both commitment and a minimum context slot. */
data class SolanaReadConfig @JvmOverloads constructor(
    /** Bank state to read, or null to use the provider's default. */
    val commitment: Commitment? = null,
    /** Fail rather than answer from a bank older than this slot, at the requested commitment. */
    val minContextSlot: BigInteger? = null,
)
