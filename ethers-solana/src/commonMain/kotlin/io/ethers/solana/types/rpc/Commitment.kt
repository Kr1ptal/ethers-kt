
package io.ethers.solana.types.rpc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How finalized a node's answer must be. Rendered lowercase, as the RPC spells it. */
@Serializable
enum class Commitment {
    @SerialName("finalized")
    FINALIZED,
    @SerialName("confirmed")
    CONFIRMED,
    @SerialName("processed")
    PROCESSED,
    ;

    override fun toString(): String = name.lowercase()
}
