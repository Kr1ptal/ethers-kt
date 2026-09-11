package io.ethers.solana.types.transaction

import io.ethers.solana.types.SolanaAddress
import kotlinx.serialization.Serializable

/**
 * The addresses a message loads from lookup tables, in the order the runtime resolves them: every
 * writable address across all tables, then every readonly one.
 *
 * Unknown JSON fields are not retained, unlike the other RPC response types. This is the resolved
 * image of `CompiledAddressLookupTable`, which names writable and readonly slots and nothing else, so
 * a third category cannot appear without a new message format. A node reporting something new about
 * loaded addresses would add it to the surrounding metadata, which does retain unknown fields.
 */
@Serializable
data class LoadedAddresses(
    val writable: List<SolanaAddress>,
    val readonly: List<SolanaAddress>,
) {
    companion object {
        /** A message that loads nothing, which is every legacy and v1 message. */
        val NONE = LoadedAddresses(emptyList(), emptyList())
    }
}
