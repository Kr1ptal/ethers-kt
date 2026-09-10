package io.ethers.solana.types

import io.ethers.crypto.Base58
import kotlinx.serialization.Serializable

/** A detached Ed25519 signature, also used as a Solana transaction identifier. */
@Serializable(with = SolanaSignatureSerializer::class)
class SolanaSignature(bytes: ByteArray) {
    /** Takes ownership of [bytes] rather than copying, so do not mutate the array afterwards. */
    private val value = bytes.also { require(it.size == 64) { "Signature must contain 64 bytes" } }
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
    override fun equals(other: Any?): Boolean = other is SolanaSignature && value.contentEquals(other.value)
    override fun hashCode(): Int = value.contentHashCode()
}

object SolanaSignatureSerializer : Base58Serializer<SolanaSignature>("io.ethers.solana.SolanaSignature", ::SolanaSignature)
