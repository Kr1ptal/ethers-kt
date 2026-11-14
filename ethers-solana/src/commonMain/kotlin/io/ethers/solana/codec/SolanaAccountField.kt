package io.ethers.solana.codec

import kotlin.jvm.JvmField

/**
 * A field of account type [T], [size] bytes at [offset] into the payload after the discriminator, read as [V].
 *
 * Generated account types declare one for each field whose offset and size are the same in every account, for
 * selecting a slice of the account data with `SolanaAccountSelection`.
 */
class SolanaAccountField<T, out V>(
    @JvmField val name: String,
    @JvmField val offset: Int,
    @JvmField val size: Int,
    private val reader: (BorshReader) -> V,
) {
    init {
        require(offset >= 0) { "Offset must not be negative" }
        require(size > 0) { "Size must be positive" }
    }

    /** Read this field from [reader], positioned at its first byte. */
    fun read(reader: BorshReader): V = this.reader(reader)

    override fun toString(): String = "SolanaAccountField($name, offset=$offset, size=$size)"
}
