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
    /** Takes ownership of [bytes] rather than copying, so do not mutate the array afterwards. */
    private val value = bytes.also { require(it.size == 32) { "Solana address must contain 32 bytes" } }

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
    fun isOnCurve(): Boolean = isEd25519Point(value)
    fun verify(signature: SolanaSignature, message: ByteArray): Boolean {
        val bytes = signature.asByteArray()
        // TweetNaCl's verifier accepts some non-canonical scalars that OpenSSL rejects. Enforce the
        // RFC8032 S < L condition before dispatching, so both platforms reject malleable signatures.
        return isCanonicalScalar(bytes) && Ed25519.verify(value, message, bytes)
    }
    override fun toString(): String = toBase58()
    override fun equals(other: Any?): Boolean = other is SolanaAddress && value.contentEquals(other.value)
    override fun hashCode(): Int = value.contentHashCode()

    companion object {
        /** The Ed25519 group order L, little-endian, as the S half of a signature is encoded. */
        private val SCALAR_ORDER = byteArrayOf(
            0xed.toByte(), 0xd3.toByte(), 0xf5.toByte(), 0x5c, 0x1a, 0x63, 0x12, 0x58,
            0xd6.toByte(), 0x9c.toByte(), 0xf7.toByte(), 0xa2.toByte(), 0xde.toByte(), 0xf9.toByte(), 0xde.toByte(), 0x14,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0x10,
        )

        /** Whether the S half of [signature] is below the group order, compared from its high byte down. */
        private fun isCanonicalScalar(signature: ByteArray): Boolean {
            for (i in 31 downTo 0) {
                val s = signature[32 + i].toInt() and 0xff
                val order = SCALAR_ORDER[i].toInt() and 0xff
                if (s != order) return s < order
            }
            // S equal to the order is itself non-canonical
            return false
        }

        /**
         * Derive an address from at most 16 seeds of at most 32 bytes each, or null if the seeds
         * produce an on-curve address, which around half of all seed sets do.
         */
        @JvmStatic
        fun createProgramAddress(seeds: List<ByteArray>, programId: SolanaAddress): SolanaAddress? {
            validateSeeds(seeds, 16)
            return derive(seeds, programId)
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
            return findProgramAddress(listOf(owner.asByteArray(), tokenProgram.asByteArray(), mint.asByteArray()), Programs.ASSOCIATED_TOKEN)
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

object SolanaAddressSerializer : Base58Serializer<SolanaAddress>("io.ethers.solana.SolanaAddress", ::SolanaAddress)
