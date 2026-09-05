package io.ethers.solana.types

import io.ethers.crypto.Base58
import kotlinx.serialization.Serializable

@Serializable(with = SolanaBlockhashSerializer::class)
class SolanaBlockhash(bytes: ByteArray) {
    private val value = bytes.copyOf().also { require(it.size == 32) { "Blockhash must contain 32 bytes" } }
    constructor(base58: String) : this(Base58.decode(base58))
    fun toByteArray(): ByteArray = value.copyOf()
    fun toBase58(): String = Base58.encode(value)
    override fun toString(): String = toBase58()
    override fun equals(other: Any?): Boolean = other is SolanaBlockhash && value.contentEquals(other.value)
    override fun hashCode(): Int = value.contentHashCode()
}
