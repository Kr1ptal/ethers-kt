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
        get() = blobVersionedHashes?.size?.toLong()?.times(TxBlob.GAS_PER_BLOB) ?: 0
}

/**
 * [EIP-2718](https://eips.ethereum.org/EIPS/eip-2718) Transaction Type.
 *
 * If type is not officially supported by this library - meaning it cannot construct, sign, and send it -, it will be
 * represented as [TxType.Unsupported]. Unsupported tx types can still be received from the network.
 * */
sealed class TxType(val type: Int) {
    /**
     * @return true if this transaction type is supported by this library, false otherwise.
     * */
    val isSupported: Boolean
        get() = this !is Unsupported

    data object Legacy : TxType(0x0)
    data object AccessList : TxType(0x1)
    data object DynamicFee : TxType(0x2)
    data object Blob : TxType(0x3)

    /**
     * A transaction type that is not supported by this library, but can still be received from the network.
     * */
    class Unsupported(type: Int) : TxType(type) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            return type == (other as Unsupported).type
        }

        override fun hashCode(): Int {
            return type
        }

        override fun toString(): String {
            return "Unsupported(type=$type)"
        }
    }

    companion object {
        fun fromType(type: Int): TxType {
            return when (type) {
                Legacy.type -> Legacy
                AccessList.type -> AccessList
                DynamicFee.type -> DynamicFee
                Blob.type -> Blob
                else -> Unsupported(type)
            }
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
