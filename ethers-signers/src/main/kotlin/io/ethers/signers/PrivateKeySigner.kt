package io.ethers.signers

import io.ethers.core.FastHex
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Signature
import io.ethers.crypto.Secp256k1

class PrivateKeySigner(privateKey: ByteArray) : Signer {
    private val signingKey: Secp256k1.SigningKey

    override val address: Address

    constructor(privateKey: Bytes) : this(privateKey.value)

    constructor(privateKey: String) : this(FastHex.decode(privateKey)) {
        if (!FastHex.isValidHex(privateKey)) {
            throw IllegalArgumentException("Invalid private key format. Should be hex string.")
        }
    }

    init {
        if (privateKey.size != 32) {
            throw IllegalArgumentException("Invalid private key length. Should be exactly 32 bytes, got ${privateKey.size}")
        }

        this.signingKey = Secp256k1.SigningKey(privateKey)
        this.address = Address(Secp256k1.publicKeyToAddress(signingKey.publicKey))
    }

    override fun signHash(hash: ByteArray): Signature {
        val sig = signingKey.signHash(hash)

        return Signature(
            r = sig[0],
            s = sig[1],
            v = sig[2].toLong(),
        )
    }
}
