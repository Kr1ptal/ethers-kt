package io.ethers.signers

import io.ethers.core.FastHex
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Signature
import io.ethers.crypto.Secp256k1

/**
 * A [Signer] that uses a private key to sign messages.
 * */
class PrivateKeySigner(val signingKey: Secp256k1.SigningKey) : Signer {
    override val address = Address(Secp256k1.publicKeyToAddress(signingKey.publicKey))

    constructor(privateKey: String) : this(validHexToKey(privateKey))
    constructor(privateKey: Bytes) : this(validByteArrayToKey(privateKey.value))
    constructor(privateKey: ByteArray) : this(validByteArrayToKey(privateKey))

    override fun signHash(hash: ByteArray): Signature {
        val sig = signingKey.signHash(hash)

        return Signature(
            r = sig[0],
            s = sig[1],
            v = sig[2].toLong(),
        )
    }

    companion object {
        private fun validHexToKey(hex: String): Secp256k1.SigningKey {
            if (!FastHex.isValidHex(hex)) {
                throw IllegalArgumentException("Invalid private key format. Should be hex string.")
            }

            return validByteArrayToKey(FastHex.decode(hex))
        }

        private fun validByteArrayToKey(bytes: ByteArray): Secp256k1.SigningKey {
            if (bytes.size != 32) {
                throw IllegalArgumentException("Invalid private key length. Should be exactly 32 bytes, got ${bytes.size}")
            }

            return Secp256k1.SigningKey(bytes)
        }
    }
}
