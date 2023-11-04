package io.ethers.crypto

import org.bouncycastle.crypto.digests.RIPEMD160Digest
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.jcajce.provider.digest.Keccak
import org.bouncycastle.jcajce.provider.digest.SHA256

object Hashing {
    private val MESSAGE_PREFIX = "\u0019Ethereum Signed Message:\n".toByteArray()

    /**
     * Construct and hash [message] based on [EIP-191](https://eips.ethereum.org/EIPS/eip-191) standard (version 0x01).
     *
     * The hashed message is constructed as follows: `"\x19Ethereum Signed Message:\n" + message.size + message`.
     * */
    @JvmStatic
    fun hashMessage(message: ByteArray): ByteArray {
        val messageSizeString = message.size.toString().toByteArray()

        val input = ByteArray(MESSAGE_PREFIX.size + messageSizeString.size + message.size)
        System.arraycopy(MESSAGE_PREFIX, 0, input, 0, MESSAGE_PREFIX.size)
        System.arraycopy(messageSizeString, 0, input, MESSAGE_PREFIX.size, messageSizeString.size)
        System.arraycopy(message, 0, input, MESSAGE_PREFIX.size + messageSizeString.size, message.size)

        return Keccak.Digest256().digest(input)
    }

    /**
     * Compute Keccak-256 hash of the given [data].
     * */
    @JvmStatic
    fun keccak256(data: ByteArray): ByteArray {
        return Keccak.Digest256().digest(data)
    }

    /**
     * Compute the HMAC SHA-512 of the given [input] using the given [key].
     * */
    @JvmStatic
    fun hmacSha512(key: ByteArray, input: ByteArray): ByteArray {
        val out = ByteArray(64)
        return HMac(SHA512Digest()).run {
            init(KeyParameter(key))
            update(input, 0, input.size)
            doFinal(out, 0)

            out
        }
    }

    /**
     * Compute the RIPEMD-160 of the SHA-256 hash of given [input].
     * */
    fun sha256ripe160(input: ByteArray): ByteArray {
        val out = ByteArray(20)
        return RIPEMD160Digest().run {
            val sha256 = SHA256.Digest().digest(input)

            update(sha256, 0, sha256.size)
            doFinal(out, 0)

            out
        }
    }
}
