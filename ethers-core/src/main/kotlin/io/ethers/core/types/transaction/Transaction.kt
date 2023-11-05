package io.ethers.core.types.transaction

import io.ethers.core.types.AccessList
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.Signature
import java.math.BigInteger

/**
 * A [Transaction] with recovered sender address ([from]) and [hash].
 * */
interface TransactionRecovered : Transaction {
    val hash: Hash
    val from: Address
}

/**
 * Base interface with properties common to all transactions.
 * */
interface Transaction {
    val to: Address?
    val value: BigInteger
    val nonce: Long
    val gas: Long
    val gasPrice: BigInteger
    val gasTipCap: BigInteger
    val gasFeeCap: BigInteger
    val data: Bytes?
    val chainId: Long
    val accessList: List<AccessList.Item>?
    val type: TxType
    val blobFeeCap: BigInteger?
    val blobVersionedHashes: List<Hash>?
    val blobGas: Long
}

/**
 * Supported transaction types.
 */
enum class TxType(val value: Int) {
    LEGACY(0x0),
    ACCESS_LIST(0x1),
    DYNAMIC_FEE(0x2),
    BLOB(0x3),
    ;

    companion object {
        // optimization to avoid allocating an iterator
        fun findOrNull(value: Int): TxType? {
            for (i in entries.indices) {
                val entry = entries[i]
                if (entry.value == value) {
                    return entry
                }
            }
            return null
        }
    }
}

object ChainId {
    const val NONE = -1L

    /**
     * Check if [chainId] is greater than 0.
     */
    @JvmStatic
    fun isValid(chainId: Long) = chainId > 0

    /**
     * Recover chainId from [Signature].
     */
    @JvmStatic
    fun fromSignature(sig: Signature): Long {
        if (sig.v < Signature.V_EIP155_OFFSET) {
            if (sig.v != Signature.V_ELECTRUM_OFFSET && sig.v != Signature.V_ELECTRUM_OFFSET + 1) {
                throw IllegalArgumentException("Invalid signature 'v' value. Should be 27 or 28, got $sig.v")
            }

            return NONE
        }

        // rounds down so the y-parity has no impact
        return (sig.v - Signature.V_EIP155_OFFSET) / 2
    }
}
