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

    override fun signHash(hash: ByteArray): Signature {
        val sig = signingKey.signHash(hash)

        return Signature(
            r = sig[0],
            s = sig[1],
            v = sig[2].toLong(),
        )
    }

    companion object {
        @JvmName("from")
        operator fun invoke(privateKey: String): PrivateKeySigner {
            if (!FastHex.isValidHex(privateKey)) {
                throw IllegalArgumentException("Invalid private key format. Should be hex string.")
            }

            return invoke(FastHex.decode(privateKey))
        }

        @JvmName("from")
        operator fun invoke(privateKey: Bytes): PrivateKeySigner {
            return invoke(privateKey.value)
        }

        @JvmName("from")
        operator fun invoke(privateKey: ByteArray): PrivateKeySigner {
            if (privateKey.size != 32) {
                throw IllegalArgumentException("Invalid private key length. Should be exactly 32 bytes, got ${privateKey.size}")
            }

            return PrivateKeySigner(Secp256k1.SigningKey(privateKey))
        }
    }
}
