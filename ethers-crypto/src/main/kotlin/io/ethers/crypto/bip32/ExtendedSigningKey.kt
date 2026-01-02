package io.ethers.crypto.bip32

import com.ditchoom.buffer.PlatformBuffer
import com.ditchoom.buffer.wrap
import io.ethers.crypto.Hashing
import io.ethers.crypto.Secp256k1

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

    /**
     * Return fingerprint, which is the first 4 bytes of the identifier of the public key.
     * */
    val fingerprint by lazy(LazyThreadSafetyMode.NONE) {
        val id = Hashing.sha256ripe160(signingKey.publicKeyCompressed)
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
        val arr = ByteArray(37)
        val buff = PlatformBuffer.wrap(arr)
        if (HDPath.isHardened(index)) {
            buff.writeByte(0) // Skip 1 byte (padding)
            buff.writeBytes(signingKey.privateKey)
        } else {
            buff.writeBytes(signingKey.publicKeyCompressed)
        }

        buff.writeInt(index)

        val hmac = Hashing.hmacSha512(chainCode, arr)
        val privateKeyNew = Secp256k1.privateKeyAdd(signingKey.privateKey, hmac.copyOfRange(0, 32))
        val chainCodeNew = hmac.copyOfRange(32, 64)
        return ExtendedSigningKey(privateKeyNew, chainCodeNew)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ExtendedSigningKey

        if (!chainCode.contentEquals(other.chainCode)) return false
        if (signingKey != other.signingKey) return false

        return true
    }

    override fun hashCode(): Int {
        var result = chainCode.contentHashCode()
        result = 31 * result + signingKey.hashCode()
        return result
    }

    companion object {
        private val PREFIX_KEY_BYTES = "Bitcoin seed".toByteArray(Charsets.UTF_8)

        @JvmStatic
        fun fromSeed(seed: ByteArray): ExtendedSigningKey {
            val hmac = Hashing.hmacSha512(PREFIX_KEY_BYTES, seed)
            return ExtendedSigningKey(hmac.copyOfRange(0, 32), hmac.copyOfRange(32, 64))
        }
    }
}
