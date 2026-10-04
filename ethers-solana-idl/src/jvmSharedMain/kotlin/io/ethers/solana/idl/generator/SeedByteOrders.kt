package io.ethers.solana.idl.generator

import java.io.Serializable

/** The byte order of an integer PDA seed wider than one byte. */
enum class SeedByteOrder { LITTLE_ENDIAN, BIG_ENDIAN }

/**
 * The byte order of a program's integer PDA seeds, which IDLs do not record: [default] for every seed, except the
 * ones in [seeds].
 */
data class SeedByteOrders @JvmOverloads constructor(
    val default: SeedByteOrder = SeedByteOrder.LITTLE_ENDIAN,
    val seeds: Map<Seed, SeedByteOrder> = emptyMap(),
) : Serializable {
    /** The seed at [path] of the PDA account named [account], in every instruction that has the account. */
    data class Seed(val account: String, val path: String) : Serializable {
        private companion object {
            private const val serialVersionUID = 1L
        }
    }

    /** The byte order of the seed at [path] of the PDA account named [account]. */
    fun of(account: String, path: String): SeedByteOrder = seeds[Seed(account, path)] ?: default

    private companion object {
        private const val serialVersionUID = 1L
    }
}
