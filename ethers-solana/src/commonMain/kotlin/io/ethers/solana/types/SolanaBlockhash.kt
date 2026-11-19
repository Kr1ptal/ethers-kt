package io.ethers.solana.types

import io.ethers.core.Result
import io.ethers.crypto.Base58
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

@Serializable(with = SolanaBlockhashSerializer::class)
@ObjCName("SolanaBlockhash", exact = true)
class SolanaBlockhash
@Throws(IllegalArgumentException::class)
constructor(bytes: ByteArray) {
    /** Takes ownership of [bytes] rather than copying, so do not mutate the array afterwards. */
    private val value = bytes.also { require(it.size == 32) { "Blockhash must contain 32 bytes" } }
    @Throws(IllegalArgumentException::class)
    constructor(base58: String) : this(Base58.decode(base58))

    /**
     * Return the internal byte array.
     *
     * If you need to modify the array, use [toByteArray] instead, which returns a new copy.
     *
     * IMPORTANT: Do not modify the returned array, it will lead to undefined behavior.
     */
    fun asByteArray(): ByteArray = value

    /**
     * Return a copy of the internal byte array.
     *
     * If you do not need to modify the array, use [asByteArray] instead, which returns the internal
     * array without copying.
     */
    fun toByteArray(): ByteArray = value.copyOf()
    fun toBase58(): String = Base58.encode(value)
    override fun toString(): String = toBase58()
    override fun equals(other: Any?): Boolean = other is SolanaBlockhash && value.contentEquals(other.value)
    override fun hashCode(): Int = value.contentHashCode()

    /** Safe factories returning a [Result]; platform ones, such as the Apple `NSData` factory, extend it. */
    companion object {
        /**
         * Decode a blockhash from Base58 [value], failing if it is not valid Base58 or not 32 bytes. Prefer this
         * over the throwing constructor for text from outside the program, such as user input or a QR code.
         */
        @JvmStatic
        fun fromBase58(value: String): Result<SolanaBlockhash, SolanaDecodingError> = decodeFixedSizeBase58(value, 32, "Blockhash", ::SolanaBlockhash)

        /**
         * Wrap [bytes] as a blockhash, failing unless they are exactly 32 bytes. Takes ownership of [bytes] rather than
         * copying, so do not mutate the array afterwards.
         */
        @JvmStatic
        fun fromBytes(bytes: ByteArray): Result<SolanaBlockhash, SolanaDecodingError> = decodeFixedSize(bytes, 32, "Blockhash", ::SolanaBlockhash)
    }
}

object SolanaBlockhashSerializer : Base58Serializer<SolanaBlockhash>("io.ethers.solana.SolanaBlockhash", ::SolanaBlockhash)
