package io.ethers.solana.types

import io.ethers.crypto.Base58
import io.ethers.crypto.Hashing
import io.ethers.solana.signers.Ed25519
import io.ethers.solana.utils.isEd25519Point
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/** A 32-byte Solana address, including off-curve program derived addresses. */
@Serializable(with = SolanaAddressSerializer::class)
class SolanaAddress(bytes: ByteArray) {
    private val value = bytes.copyOf().also { require(it.size == 32) { "Solana address must contain 32 bytes" } }

    constructor(base58: String) : this(Base58.decode(base58))

    fun toByteArray(): ByteArray = value.copyOf()
    fun toBase58(): String = Base58.encode(value)
    fun isOnCurve(): Boolean = isEd25519Point(value)
    fun verify(signature: Signature, message: ByteArray): Boolean {
        val bytes = signature.toByteArray()
        // TweetNaCl's verifier accepts some non-canonical scalars that OpenSSL rejects. Enforce the
        // RFC8032 S < L condition before dispatching, so both platforms reject malleable signatures.
        val scalar = io.github.artificialpb.bignum.BigInteger(1, bytes.copyOfRange(32, 64).reversedArray())
        return scalar < SCALAR_ORDER && Ed25519.verify(value, message, bytes)
    }
    override fun toString(): String = toBase58()
    override fun equals(other: Any?): Boolean = other is SolanaAddress && value.contentEquals(other.value)
    override fun hashCode(): Int = value.contentHashCode()

    companion object {
        private val SCALAR_ORDER = io.github.artificialpb.bignum.BigInteger("1000000000000000000000000000000014def9dea2f79cd65812631a5cf5d3ed", 16)

        /** Derive an address from at most 16 seeds of at most 32 bytes each. */
        @JvmStatic
        fun createProgramAddress(seeds: List<ByteArray>, programId: SolanaAddress): SolanaAddress {
            validateSeeds(seeds, 16)
            return derive(seeds, programId) ?: throw IllegalArgumentException("Seeds produce an on-curve address")
        }

        /** Find the highest valid bump, including zero. The bump occupies the sixteenth seed slot. */
        @JvmStatic
        fun findProgramAddress(seeds: List<ByteArray>, programId: SolanaAddress): ProgramDerivedAddress {
            validateSeeds(seeds, 15)
            for (bump in 255 downTo 0) {
                val key = derive(seeds + listOf(byteArrayOf(bump.toByte())), programId)
                if (key != null) return ProgramDerivedAddress(key, bump)
            }
            throw IllegalArgumentException("Unable to find an off-curve program address")
        }

        @JvmStatic
        @JvmOverloads
        fun findAssociatedTokenAddress(owner: SolanaAddress, mint: SolanaAddress, tokenProgram: SolanaAddress = Programs.TOKEN): ProgramDerivedAddress {
            return findProgramAddress(listOf(owner.toByteArray(), tokenProgram.toByteArray(), mint.toByteArray()), Programs.ASSOCIATED_TOKEN)
        }

        private fun validateSeeds(seeds: List<ByteArray>, maximum: Int) {
            require(seeds.size <= maximum) { "Too many PDA seeds (maximum $maximum)" }
            require(seeds.all { it.size <= 32 }) { "PDA seed exceeds 32 bytes" }
        }

        private fun derive(seeds: List<ByteArray>, programId: SolanaAddress): SolanaAddress? {
            val suffix = "ProgramDerivedAddress".encodeToByteArray()
            val bytes = ByteArray(seeds.sumOf { it.size } + 32 + suffix.size)
            var offset = 0
            for (seed in seeds) {
                seed.copyInto(bytes, offset)
                offset += seed.size
            }
            programId.value.copyInto(bytes, offset)
            suffix.copyInto(bytes, offset + 32)
            val hash = Hashing.sha256(bytes)
            return if (isEd25519Point(hash)) null else SolanaAddress(hash)
        }
    }
}

data class ProgramDerivedAddress(val address: SolanaAddress, val bump: Int)
