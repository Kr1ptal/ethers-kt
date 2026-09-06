package io.ethers.solana.types

import io.ethers.core.FastHex
import io.ethers.crypto.Base58
import kotlin.io.encoding.Base64
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Immutable binary data with explicit text encodings.
 *
 * No default JSON serializer is provided: the containing field determines its wire encoding.
 * This type carries no address, instruction, or transaction semantics.
 */
class SolanaBytes private constructor(private val value: ByteArray) {
    val size: Int get() = value.size
    val isEmpty: Boolean get() = value.isEmpty()

    operator fun get(index: Int): Byte = value[index]

    /** Copy from [fromIndex] (inclusive) to [toIndex] (exclusive), rejecting invalid ranges. */
    fun slice(fromIndex: Int, toIndex: Int): SolanaBytes {
        if (fromIndex !in 0..size || toIndex !in 0..size) {
            throw IndexOutOfBoundsException("Range [$fromIndex, $toIndex) is outside 0..$size")
        }
        require(fromIndex <= toIndex) { "fromIndex must not exceed toIndex" }
        if (fromIndex == toIndex) return EMPTY
        if (fromIndex == 0 && toIndex == size) return this
        return SolanaBytes(value.copyOfRange(fromIndex, toIndex))
    }

    /** Return an independent copy. Mutable backing storage is never exposed. */
    fun toByteArray(): ByteArray = value.copyOf()

    /** Backing storage, for in-module readers that write it straight out. Never mutate it. */
    internal val backing: ByteArray get() = value

    /** Copy all bytes, validating the destination range before writing anything. */
    @JvmOverloads
    fun copyInto(destination: ByteArray, destinationOffset: Int = 0) {
        if (destinationOffset < 0 || destinationOffset > destination.size || size > destination.size - destinationOffset) {
            throw IndexOutOfBoundsException("Insufficient destination space at offset $destinationOffset for $size bytes")
        }
        value.copyInto(destination, destinationOffset)
    }

    fun toBase58(): String = Base58.encode(value)
    fun toBase64(): String = Base64.encode(value)

    /** Lowercase hexadecimal without a prefix. */
    fun toHex(): String = FastHex.encodeWithoutPrefix(value)

    override fun equals(other: Any?): Boolean = this === other || (other is SolanaBytes && value.contentEquals(other.value))
    override fun hashCode(): Int = value.contentHashCode()

    /** Diagnostic summary, not a wire encoding or a dump of the payload. */
    override fun toString(): String = "SolanaBytes(size=$size)"

    companion object {
        @JvmField
        val EMPTY = SolanaBytes(ByteArray(0))

        /** Copy caller-owned bytes so subsequent mutations cannot change this value. */
        @JvmStatic
        fun fromBytes(bytes: ByteArray): SolanaBytes = if (bytes.isEmpty()) EMPTY else SolanaBytes(bytes.copyOf())

        /**
         * Take ownership of [bytes] without copying. Only for arrays this module has just produced
         * and does not retain, such as a freshly decoded instruction payload.
         */
        internal fun wrap(bytes: ByteArray): SolanaBytes = if (bytes.isEmpty()) EMPTY else SolanaBytes(bytes)

        @JvmStatic
        fun fromBase58(value: String): SolanaBytes = if (value.isEmpty()) EMPTY else SolanaBytes(Base58.decode(value))

        @JvmStatic
        fun fromBase64(value: String): SolanaBytes = if (value.isEmpty()) EMPTY else SolanaBytes(Base64.decode(value))

        /** Decode hexadecimal with an optional 0x/0X prefix. Invalid or odd-length hex is rejected. */
        @JvmStatic
        fun fromHex(value: String): SolanaBytes {
            require(value.length % 2 == 0) { "Hex data must contain an even number of digits" }
            val decoded = FastHex.decode(value)
            return if (decoded.isEmpty()) EMPTY else SolanaBytes(decoded)
        }
    }
}
