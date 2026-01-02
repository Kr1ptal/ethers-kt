package io.ethers.signers

import io.ethers.core.FastHex
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Signature
import io.ethers.crypto.Hashing
import io.ethers.crypto.Secp256k1

/**
 * A [Signer] that uses a private key to sign messages.
 * */
class PrivateKeySigner(val signingKey: Secp256k1.SigningKey) : Signer {
    override val address = Address(Secp256k1.publicKeyToAddress(signingKey.publicKey))

    constructor(privateKey: String) : this(validHexToKey(privateKey))
    constructor(privateKey: Bytes) : this(validByteArrayToKey(privateKey.asByteArray()))
    constructor(privateKey: ByteArray) : this(validByteArrayToKey(privateKey))

    override fun signHash(hash: ByteArray): Signature {
        val sig = signingKey.signHash(hash)
        return Signature(
            r = sig.r,
            s = sig.s,
            v = sig.v,
        )
    }

    override fun toString(): String {
        return "PrivateKeySigner(address=$address)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PrivateKeySigner

        return signingKey == other.signingKey
    }

    override fun hashCode(): Int {
        return signingKey.hashCode()
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

        /**
         * Create a new [PrivateKeySigner] from random entropy, using [Hashing.generateRandomBytes].
         * */
        @JvmStatic
        fun random(): PrivateKeySigner {
            val privateKey = Hashing.generateRandomBytes(32)
            return PrivateKeySigner(privateKey)
        }
    }
}
