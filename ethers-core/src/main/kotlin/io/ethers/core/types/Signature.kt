package io.ethers.core.types

import io.ethers.crypto.Hashing
import io.ethers.crypto.Secp256k1
import io.ethers.rlp.RlpDecodable
import io.ethers.rlp.RlpDecoder
import io.ethers.rlp.RlpEncodable
import io.ethers.rlp.RlpEncoder
import java.math.BigInteger

class Signature(
    val r: BigInteger,
    val s: BigInteger,
    v: Long,
) : RlpEncodable {
    var v = v
        private set

    // used mainly when adjusting for EIP-155, use custom name for setter to make it less likely to be used by mistake
    fun updateV(updatedV: Long) {
        v = updatedV
    }

    /**
     * Verify that signature for provided [message] was created by [address].
     *
     * @return `true` if signature is valid, `false` otherwise.
     * */
    fun verifyFromMessage(message: ByteArray, address: Address): Boolean {
        return verifyFromHash(Hashing.hashMessage(message), address)
    }

    /**
     * Verify that signature for provided [hash] was created by [address].
     *
     * @return `true` if signature is valid, `false` otherwise.
     * */
    fun verifyFromHash(hash: ByteArray, address: Address): Boolean {
        return recoverFromHash(hash) == address
    }

    /**
     * Recover signer address from signature for provided [message].
     *
     * @return [Address] if signature is valid, `null` otherwise.
     * */
    fun recoverFromMessage(message: ByteArray): Address? {
        return recoverFromHash(Hashing.hashMessage(message))
    }

    /**
     * Recover signer address from signature for provided [hash].
     *
     * @return [Address] if signature is valid, `null` otherwise.
     * */
    fun recoverFromHash(hash: ByteArray): Address? {
        val recoveryId = getRecoveryId()
        if (recoveryId == -1L) {
            return null
        }

        val publicKey = Secp256k1.recoverPublicKey(hash, r, s, recoveryId) ?: return null
        return Address(Secp256k1.publicKeyToAddress(publicKey))
    }

    /**
     * Recover recoveryId from [v] value, or throws if signature is invalid.
     * */
    fun recoveryId(): Long {
        val recoveryId = getRecoveryId()
        if (recoveryId == -1L) {
            throw IllegalStateException("Unable to recover 'recId' from 'v': $v")
        }
        return recoveryId
    }

    private fun getRecoveryId(): Long {
        return when (v) {
            0L -> 0
            1L -> 1
            27L -> 0
            28L -> 1
            else -> {
                if (v < V_EIP155_OFFSET) {
                    return -1L
                }

                (v - 1) % 2
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Signature

        if (r != other.r) return false
        if (s != other.s) return false
        if (v != other.v) return false

        return true
    }

    override fun hashCode(): Int {
        var result = r.hashCode()
        result = 31 * result + s.hashCode()
        result = 31 * result + v.hashCode()
        return result
    }

    override fun toString(): String {
        return "Signature(r=$r, s=$s, v=$v)"
    }

    override fun rlpEncode(rlp: RlpEncoder) {
        rlp.encode(v)
        rlp.encode(r)
        rlp.encode(s)
    }

    companion object : RlpDecodable<Signature> {
        const val V_ELECTRUM_OFFSET = 27L
        const val V_EIP155_OFFSET = 35L

        @JvmStatic
        override fun rlpDecode(rlp: RlpDecoder): Signature? {
            val v = rlp.decodeLong()
            val r = rlp.decodeBigInteger() ?: return null
            val s = rlp.decodeBigInteger() ?: return null
            return Signature(r, s, v)
        }
    }
}
