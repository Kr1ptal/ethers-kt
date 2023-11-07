package io.ethers.crypto.bip32

import io.ethers.crypto.Hashing
import io.ethers.crypto.Secp256k1
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Implementation of [BIP-0032](https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki) extended keys with
 * CKD (Child Key Derivation) functions.
 * */
class ExtendedSigningKey(privateKey: ByteArray, val chainCode: ByteArray) {
    val signingKey = Secp256k1.SigningKey(privateKey)

    init {
        if (privateKey.size != 32) {
            throw IllegalArgumentException("Invalid private key length. Should be exactly 32 bytes, got ${privateKey.size}")
        }
        if (chainCode.size != 32) {
            throw IllegalArgumentException("Invalid chain code length. Should be exactly 32 bytes, got ${chainCode.size}")
        }
    }

    private val pubCompressed by lazy(LazyThreadSafetyMode.NONE) { signingKey.publicPoint.getEncoded(true) }

    /**
     * Return fingerprint, which is the first 4 bytes of the identifier of the public key.
     * */
    val fingerprint by lazy(LazyThreadSafetyMode.NONE) {
        val id = Hashing.sha256ripe160(pubCompressed)
        id[3].toInt() and 0xFF or (id[2].toInt() and 0xFF shl 8) or (id[1].toInt() and 0xFF shl 16) or (id[0].toInt() and 0xFF shl 24)
    }

    /**
     * Derive a child key from this key, based on the given path.
     * */
    fun derivePath(path: HDPath): ExtendedSigningKey {
        var key = this
        for (depth in 0 until path.depth) {
            key = key.deriveChild(path.indexAtDepth(depth))
        }

        return key
    }

    /**
     * Derive a child key from this key, based on the given index.
     * */
    fun deriveChild(index: Int): ExtendedSigningKey {
        val buff = ByteBuffer.allocate(37)
        if (HDPath.isHardened(index)) {
            buff.position(1)
            buff.put(signingKey.privateKey)
        } else {
            buff.put(pubCompressed)
        }

        buff.putInt(index)

        val hmac = Hashing.hmacSha512(chainCode, buff.array())
        val privateKeyNew = Secp256k1.privateKeyAdd(signingKey.privateKey, hmac.copyOfRange(0, 32))
        val chainCodeNew = hmac.copyOfRange(32, 64)
        return ExtendedSigningKey(privateKeyNew, chainCodeNew)
    }

    companion object {
        private val PREFIX_KEY_BYTES = "Bitcoin seed".toByteArray(StandardCharsets.UTF_8)

        @JvmStatic
        fun fromSeed(seed: ByteArray): ExtendedSigningKey {
            val hmac = Hashing.hmacSha512(PREFIX_KEY_BYTES, seed)
            return ExtendedSigningKey(hmac.copyOfRange(0, 32), hmac.copyOfRange(32, 64))
        }
    }
}
