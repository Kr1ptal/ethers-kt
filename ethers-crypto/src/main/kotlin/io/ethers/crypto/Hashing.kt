package io.ethers.crypto

import org.bouncycastle.jcajce.provider.digest.Keccak

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
}
