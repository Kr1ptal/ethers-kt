package io.ethers.solana.signers

import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.crypto.Hashing
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaDecodingError
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.decodeFixedSize
import kotlin.jvm.JvmStatic

class KeypairSigner private constructor(seed: ByteArray) : SolanaSigner {
    private val seed = seed.copyOf()
    override val publicKey = SolanaAddress(Ed25519.publicKey(this.seed))
    override fun signMessage(message: ByteArray): SolanaSignature = SolanaSignature(Ed25519.sign(seed, message))

    /** Export the Solana 64-byte seed + public key format. The returned array is independent. */
    fun toSecretKey(): ByteArray = seed + publicKey.toByteArray()
    override fun toString(): String = "KeypairSigner($publicKey)"

    // the public key identifies the keypair, so equality never has to read the seed
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as KeypairSigner

        return publicKey == other.publicKey
    }

    override fun hashCode(): Int = publicKey.hashCode()

    companion object {
        /** Create a new [KeypairSigner] from random entropy, using [Hashing.generateRandomBytes]. */
        @JvmStatic
        fun random(): KeypairSigner = KeypairSigner(Hashing.generateRandomBytes(32))

        /** Import a 32-byte Ed25519 seed, failing if it is any other length. The seed is copied. */
        @JvmStatic
        fun fromSeed(seed: ByteArray): Result<KeypairSigner, SolanaDecodingError> = decodeFixedSize(seed, 32, "Seed", ::KeypairSigner)

        /**
         * Import Solana's 64-byte secret key format: the seed followed by its public key, as exported by
         * [toSecretKey] and the Solana CLI. Fails if it is any other length or its public half does not match the seed.
         */
        @JvmStatic
        fun fromSecretKey(secretKey: ByteArray): Result<KeypairSigner, SolanaDecodingError> {
            if (secretKey.size != 64) return failure(SolanaDecodingError("Secret key must contain a 32-byte seed and 32-byte public key, got ${secretKey.size} bytes"))
            val signer = KeypairSigner(secretKey.copyOfRange(0, 32))
            if (!signer.publicKey.asByteArray().contentEquals(secretKey.copyOfRange(32, 64))) {
                return failure(SolanaDecodingError("Secret key public half does not match its seed"))
            }
            return success(signer)
        }
    }
}
