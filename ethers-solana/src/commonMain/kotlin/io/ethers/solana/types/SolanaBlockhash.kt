package io.ethers.solana.types

import io.ethers.crypto.Base58
import kotlinx.serialization.Serializable

@Serializable(with = SolanaBlockhashSerializer::class)
class SolanaBlockhash(bytes: ByteArray) {
    /** Takes ownership of [bytes] rather than copying, so do not mutate the array afterwards. */
    private val value = bytes.also { require(it.size == 32) { "Blockhash must contain 32 bytes" } }
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
}

object SolanaBlockhashSerializer : Base58Serializer<SolanaBlockhash>("io.ethers.solana.SolanaBlockhash", ::SolanaBlockhash)
