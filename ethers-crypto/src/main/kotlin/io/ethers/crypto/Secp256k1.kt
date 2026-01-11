package io.ethers.crypto

import java.math.BigInteger
import fr.acinq.secp256k1.Secp256k1 as AcinqSecp256k1

/**
 * Secp256k1 elliptic curve operations using ACINQ's secp256k1-kmp library.
 * */
object Secp256k1 {
    private const val UNCOMPRESSED_KEY_FLAG = (0x04).toByte()

    /**
     * Add [add] to the provided [privateKey] and return the result.
     * */
    fun privateKeyAdd(privateKey: ByteArray, add: ByteArray): ByteArray {
        require(privateKey.size == 32) { "Private key must be 32 bytes" }
        require(add.size == 32) { "Tweak must be 32 bytes" }

        return try {
            AcinqSecp256k1.privKeyTweakAdd(privateKey, add)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to add to private key: ${e.message}", e)
        }
    }

    /**
     * Hash provided [publicKey] using Keccak-256 algorithm, and return last 20 bytes, which is the address.
     * */
    fun publicKeyToAddress(publicKey: ByteArray): ByteArray {
        if (publicKey.size != 65 || publicKey[0] != UNCOMPRESSED_KEY_FLAG) {
            throw IllegalArgumentException("Public key must be 65 bytes with 0x04 prefix")
        }

        // skip first byte (0x04) which indicates uncompressed public key
        val hash = Hashing.keccak256(publicKey.copyOfRange(1, publicKey.size))
        return hash.copyOfRange(hash.size - 20, hash.size)
    }

    /**
     * Recover public key from hash original unsigned message and provided signature.
     *
     * This can be convenient when you have a message and a signature and want to find out who signed it,
     * rather than requiring the user to provide the expected identity.
     *
     * @param hash of the original unsigned message
     * @param r x-coordinate of random point R
     * @param s signature proof
     * @param recId index from 0 to 3 which indicates which of the 4 possible keys is the correct one
     */
    fun recoverPublicKey(hash: ByteArray, r: BigInteger, s: BigInteger, recId: Long): ByteArray? {
        if (recId < 0) {
            throw IllegalArgumentException("Parameter 'recId' must be positive.")
        }
        if (r < BigInteger.ZERO) {
            throw IllegalArgumentException("Parameter 'r' must be positive.")
        }
        if (s < BigInteger.ZERO) {
            throw IllegalArgumentException("Parameter 's' must be positive.")
        }

        val sig = ByteArray(64)
        r.toByteArray32().copyInto(sig, 0)
        s.toByteArray32().copyInto(sig, 32)

        return try {
            AcinqSecp256k1.ecdsaRecover(sig, hash, recId.toInt())
        } catch (e: Exception) {
            null
        }
    }

    private fun BigInteger.toByteArray32(): ByteArray {
        return bigIntegerToByteArray32(this)
    }

    private fun bigIntegerToByteArray32(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        if (bytes.size > 33 || bytes.size == 33 && bytes[0] != 0.toByte()) {
            throw IllegalArgumentException("Input is too large to put in byte array of size 32")
        }

        if (bytes.size == 32) {
            return bytes
        }

        if (bytes.size == 33 && bytes[0] == 0.toByte()) {
            return bytes.copyOfRange(1, 33)
        }

        val ret = ByteArray(32)
        bytes.copyInto(ret, destinationOffset = 32 - bytes.size)
        return ret
    }

    /**
     * An ECDSA signature with [r], [s], and recovery id [v].
     * */
    data class ECDSASignature(
        val r: BigInteger,
        val s: BigInteger,
        val v: Long,
    )

    /**
     * Elliptic curve public/private key pair.
     */
    class SigningKey(val privateKey: ByteArray) {
        /**
         * Public key encoded in uncompressed format. Contains the 0x04 prefix.
         * */
        val publicKey: ByteArray

        /**
         * Public key encoded in compressed format (33 bytes).
         * */
        val publicKeyCompressed: ByteArray by lazy(LazyThreadSafetyMode.NONE) {
            AcinqSecp256k1.pubKeyCompress(publicKey)
        }

        init {
            require(privateKey.size == 32) { "Private key must be 32 bytes" }

            try {
                publicKey = AcinqSecp256k1.pubkeyCreate(privateKey)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid private key: ${e.message}", e)
            }
        }

        constructor(privateKey: BigInteger) : this(bigIntegerToByteArray32(privateKey))

        /**
         * Sign [hash] message and return its signature as [r, s, v].
         */
        fun signHash(hash: ByteArray): ECDSASignature {
            require(hash.size == 32) { "Hash must be 32 bytes" }

            val sig = AcinqSecp256k1.sign(hash, privateKey)

            // Extract r and s from the compact signature (64 bytes)
            val r = BigInteger(1, sig.copyOfRange(0, 32))
            val s = BigInteger(1, sig.copyOfRange(32, 64))

            // Determine recovery ID by trying each value and checking which recovers our public key
            val recId = findRecoveryId(hash, sig, publicKey)
            return ECDSASignature(r, s, recId.toLong())
        }

        private fun findRecoveryId(hash: ByteArray, sig: ByteArray, expectedPubKey: ByteArray): Int {
            for (recId in 0..3) {
                try {
                    val recoveredPubKey = AcinqSecp256k1.ecdsaRecover(sig, hash, recId)
                    if (recoveredPubKey.contentEquals(expectedPubKey)) {
                        return recId
                    }
                } catch (e: Exception) {
                    // This recovery ID doesn't work, try next
                }
            }
            throw IllegalStateException("Could not determine recovery ID for signature")
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as SigningKey

            return privateKey.contentEquals(other.privateKey)
        }

        override fun hashCode(): Int {
            return privateKey.contentHashCode()
        }
    }
}
