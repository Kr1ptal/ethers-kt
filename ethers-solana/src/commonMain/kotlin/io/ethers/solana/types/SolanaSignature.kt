package io.ethers.solana.types

import io.ethers.crypto.Base58
import kotlinx.serialization.Serializable

/** A detached Ed25519 signature, also used as a Solana transaction identifier. */
@Serializable(with = SolanaSignatureSerializer::class)
class SolanaSignature(bytes: ByteArray) {
    private val value = bytes.copyOf().also { require(it.size == 64) { "Signature must contain 64 bytes" } }
    constructor(base58: String) : this(Base58.decode(base58))
    fun toByteArray(): ByteArray = value.copyOf()
    fun toBase58(): String = Base58.encode(value)
    override fun toString(): String = toBase58()
    override fun equals(other: Any?): Boolean = other is SolanaSignature && value.contentEquals(other.value)
    override fun hashCode(): Int = value.contentHashCode()
}
