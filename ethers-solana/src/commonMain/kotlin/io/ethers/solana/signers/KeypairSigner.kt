package io.ethers.solana.signers

import io.ethers.crypto.Hashing
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaSignature
import kotlin.jvm.JvmStatic

class KeypairSigner private constructor(seed: ByteArray) : SolanaSigner {
    private val seed = seed.copyOf()
    override val publicKey = SolanaAddress(Ed25519.publicKey(this.seed))
    override fun signMessage(message: ByteArray): SolanaSignature = SolanaSignature(Ed25519.sign(seed, message))

    /** Export the Solana 64-byte seed + public key format. The returned array is independent. */
    fun toSecretKey(): ByteArray = seed + publicKey.toByteArray()
    override fun toString(): String = "KeypairSigner($publicKey)"

    companion object {
        @JvmStatic fun generate(): KeypairSigner = fromSeed(Hashing.generateRandomBytes(32))
        @JvmStatic fun fromSeed(seed: ByteArray): KeypairSigner {
            require(seed.size == 32) { "Seed must contain exactly 32 bytes" }
            return KeypairSigner(seed)
        }
        @JvmStatic fun fromSecretKey(secretKey: ByteArray): KeypairSigner {
            require(secretKey.size == 64) { "Secret key must contain a 32-byte seed and 32-byte public key" }
            val signer = fromSeed(secretKey.copyOfRange(0, 32))
            require(signer.publicKey.asByteArray().contentEquals(secretKey.copyOfRange(32, 64))) { "Secret key public half does not match its seed" }
            return signer
        }
    }
}
