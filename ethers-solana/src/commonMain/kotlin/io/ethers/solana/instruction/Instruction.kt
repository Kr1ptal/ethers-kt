package io.ethers.solana.instruction

import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes

/**
 * A single program invocation.
 *
 * This is the only instruction type the transaction layer knows about: built-in instructions are
 * factory functions on the program objects in this package (e.g. [SystemProgram.transfer]), and
 * instructions for any other program are constructed directly.
 *
 * The type is final and its fields are read-only, so message compilation can read [data] as many
 * times as it needs - to validate the encoded size and then to serialize - and see the same bytes.
 * [keys] is not copied; pass an immutable list.
 */
data class Instruction(
    val programId: SolanaAddress,
    val keys: List<AccountMeta>,
    val data: SolanaBytes,
) {
    /** Takes ownership of [data] rather than copying it, so do not mutate the array afterwards. */
    constructor(programId: SolanaAddress, keys: List<AccountMeta>, data: ByteArray) :
        this(programId, keys, SolanaBytes.fromBytes(data))
}
