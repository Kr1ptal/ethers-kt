package io.ethers.solana.types.rpc

import io.github.artificialpb.bignum.BigInteger
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * The window of an account's data to read, which the node applies before encoding.
 *
 * A program's accounts are often far larger than the part a caller needs, and slicing moves that
 * saving onto the node rather than the wire.
 *
 * [AccountInfo.space] still reports the whole account, since the node sends it alongside the slice.
 * A node old enough to omit that field is the one exception: the size then falls back to the bytes
 * returned, which under a slice is the slice.
 *
 * Not a data class: a generated `copy` would re-run the validation without declaring it, which a Swift caller
 * cannot catch. The hand-written members below match what a data class generates.
 */
class DataSlice
@JvmOverloads
@Throws(IllegalArgumentException::class)
constructor(val offset: Int, val length: Int = 0) {
    init {
        require(offset >= 0) { "A data slice cannot start before the account, got $offset" }
        require(length >= 0) { "A data slice cannot have a negative length, got $length" }
    }

    @Throws(IllegalArgumentException::class)
    fun copy(offset: Int = this.offset, length: Int = this.length): DataSlice = DataSlice(offset, length)

    operator fun component1(): Int = offset
    operator fun component2(): Int = length

    override fun equals(other: Any?): Boolean = this === other || (other is DataSlice && offset == other.offset && length == other.length)
    override fun hashCode(): Int = 31 * offset + length
    override fun toString(): String = "DataSlice(offset=$offset, length=$length)"

    companion object {
        /** Read no data at all, for a query that wants only the addresses and lamports. */
        @JvmField
        val NONE = DataSlice(0, 0)
    }
}

/**
 * Options for reading accounts, including a minimum context slot and optional data slicing.
 */
data class SolanaAccountConfig @JvmOverloads constructor(
    val commitment: Commitment? = null,
    /** Read only this window of each account's data, instead of all of it. */
    val dataSlice: DataSlice? = null,
    /** Fail rather than answer from a bank older than this slot, at the requested commitment. */
    val minContextSlot: BigInteger? = null,
) {
    companion object {
        /** Read every account in full, at the provider's default commitment. */
        @JvmField
        val DEFAULT = SolanaAccountConfig()

        /** Read only addresses and lamports, for a query that does not need account data. */
        @JvmField
        val WITHOUT_DATA = SolanaAccountConfig(dataSlice = DataSlice.NONE)
    }
}
