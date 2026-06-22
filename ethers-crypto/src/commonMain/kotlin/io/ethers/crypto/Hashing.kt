package io.ethers.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.RIPEMD160
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.SHA512
import dev.whyoleg.cryptography.random.CryptographyRandom
import org.kotlincrypto.hash.sha3.Keccak256

object Hashing {
    private val MESSAGE_PREFIX = "\u0019Ethereum Signed Message:\n".toByteArray()
    private const val VERSIONED_HASH_VERSION_KZG = 0x01.toByte()

    private val provider = CryptographyProvider.Default

    // Cache algorithm lookups (stateless, thread-safe)
    private val sha256Algorithm = provider.get(SHA256)

    @OptIn(DelicateCryptographyApi::class)
    private val ripemd160Algorithm = provider.get(RIPEMD160)
    private val hmacKeyDecoder = provider.get(HMAC).keyDecoder(SHA512)

    /**
     * Generate cryptographically secure random bytes of given [size].
     * */
    @JvmStatic
    fun generateRandomBytes(size: Int): ByteArray {
        return CryptographyRandom.nextBytes(size)
    }

    /**
     * Construct and hash [message] based on [EIP-191](https://eips.ethereum.org/EIPS/eip-191) standard (version 0x01).
     *
     * The hashed message is constructed as follows: `"\x19Ethereum Signed Message:\n" + message.size + message`.
     * */
    @JvmStatic
    fun hashMessage(message: ByteArray): ByteArray {
        val messageSizeString = message.size.toString().toByteArray()

        val input = ByteArray(MESSAGE_PREFIX.size + messageSizeString.size + message.size)
        MESSAGE_PREFIX.copyInto(input)
        messageSizeString.copyInto(input, destinationOffset = MESSAGE_PREFIX.size)
        message.copyInto(input, destinationOffset = MESSAGE_PREFIX.size + messageSizeString.size)

        return keccak256(input)
    }

    /**
     * Compute Keccak-256 hash of the given [data].
     *
     * Note: Keccak-256 is NOT the same as SHA3-256. Ethereum uses the pre-standardized
     * Keccak algorithm, which differs from the final NIST SHA-3 standard.
     * */
    @JvmStatic
    fun keccak256(data: ByteArray): ByteArray {
        // Using KotlinCrypto for Keccak-256 as whyoleg only supports SHA3-256
        return Keccak256().digest(data)
    }

    /**
     * Compute the HMAC SHA-512 of the given [input] using the given [key].
     * */
    @JvmStatic
    fun hmacSha512(key: ByteArray, input: ByteArray): ByteArray {
        val hmac = hmacKeyDecoder.decodeFromByteArrayBlocking(HMAC.Key.Format.RAW, key)
        return hmac.signatureGenerator().generateSignatureBlocking(input)
    }

    /**
     * Compute the SHA-256 hash of given [input].
     * */
    fun sha256(input: ByteArray): ByteArray {
        return sha256Algorithm.hasher().hashBlocking(input)
    }

    /**
     * Compute RIPEMD-160 of the SHA-256 hash of given [input].
     * Used for BIP-32 fingerprint calculation.
     * */
    fun sha256ripe160(input: ByteArray): ByteArray {
        val sha256Hash = sha256(input)
        return ripemd160Algorithm.hasher().hashBlocking(sha256Hash)
    }

    /**
     * Compute blob versioned hash of the given KZG [commitment].
     *
     * The blob versioned hash is constructed as follows: `VERSIONED_HASH_VERSION_KZG + SHA-256(commitment)[1:]`.
     * See: [EIP-4844](https://eips.ethereum.org/EIPS/eip-4844#helpers)
     * */
    @JvmStatic
    fun blobVersionedHash(commitment: ByteArray): ByteArray {
        val hash = sha256(commitment)
        hash[0] = VERSIONED_HASH_VERSION_KZG
        return hash
    }
}
