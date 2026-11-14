package io.ethers.solana.codec

import io.ethers.core.FastHex
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.solana.types.SolanaBytes
import kotlin.jvm.JvmStatic

/**
 * Leading bytes that identify an account, instruction or event type.
 *
 * The bytes are taken as declared: Anchor's default is eight bytes, but IDLs may declare custom
 * discriminators of other lengths, so none is assumed.
 */
class SolanaDiscriminator(bytes: ByteArray) {
    /** Takes ownership of [bytes] rather than copying, so do not mutate the array afterwards. */
    private val value = bytes.also { require(it.isNotEmpty()) { "Discriminator must not be empty" } }

    val size: Int get() = value.size

    /** Whether [data] starts with this discriminator. */
    fun matches(data: ByteArray): Boolean {
        if (data.size < value.size) return false
        for (i in value.indices) if (data[i] != value[i]) return false
        return true
    }

    fun matches(data: SolanaBytes): Boolean = matches(data.asByteArray())

    /**
     * Return the internal byte array.
     *
     * IMPORTANT: Do not modify the returned array, it will lead to undefined behavior.
     */
    fun asByteArray(): ByteArray = value

    fun toByteArray(): ByteArray = value.copyOf()

    override fun equals(other: Any?): Boolean = other is SolanaDiscriminator && value.contentEquals(other.value)
    override fun hashCode(): Int = value.contentHashCode()
    override fun toString(): String = "SolanaDiscriminator(${FastHex.encodeWithoutPrefix(value)})"

    companion object {
        /** Build a discriminator from unsigned byte values, as IDLs list them. */
        @JvmStatic
        fun of(vararg bytes: Int): SolanaDiscriminator = SolanaDiscriminator(
            ByteArray(bytes.size) {
                require(bytes[it] in 0..255) { "Discriminator byte ${bytes[it]} is outside 0..255" }
                bytes[it].toByte()
            },
        )
    }
}

/** Decode the payload after [discriminator], or report the data as [SolanaDecodeError.Unrecognized]. */
internal inline fun <T> decodeDiscriminated(
    discriminator: SolanaDiscriminator,
    data: ByteArray,
    name: String,
    requireDone: Boolean,
    read: (BorshReader) -> T,
): Result<T, SolanaDecodeError> {
    if (!discriminator.matches(data)) {
        return failure(SolanaDecodeError.Unrecognized(SolanaBytes.fromBytes(data), "Data is not $name"))
    }
    return BorshReader.decode(data, discriminator.size, requireDone, read)
}
