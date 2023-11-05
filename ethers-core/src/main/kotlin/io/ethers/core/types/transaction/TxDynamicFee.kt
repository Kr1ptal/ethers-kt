package io.ethers.core.types.transaction

import io.ethers.core.types.AccessList
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.rlp.RlpDecodable
import io.ethers.rlp.RlpDecoder
import io.ethers.rlp.RlpEncoder
import java.math.BigInteger

/**
 * EIP-1559 transaction with optional access list and [gasFeeCap]/[gasTipCap] instead of [gasPrice].
 *
 * - [EIP-1559](https://eips.ethereum.org/EIPS/eip-1559)
 * */
class TxDynamicFee(
    override val to: Address?,
    override val value: BigInteger,
    override val nonce: Long,
    override val gas: Long,
    override val gasFeeCap: BigInteger,
    override val gasTipCap: BigInteger,
    override val data: Bytes?,
    override val chainId: Long,
    override var accessList: List<AccessList.Item>?,
) : TransactionUnsigned {
    init {
        if (!ChainId.isValid(chainId)) {
            throw IllegalArgumentException("DynamicFee transactions must have a chainId")
        }
        if (gasFeeCap < gasTipCap) {
            throw IllegalArgumentException("gasFeeCap must be greater than or equal to gasTipCap")
        }
    }

    override val gasPrice: BigInteger
        get() = gasFeeCap

    override val type: TxType
        get() = TxType.DYNAMIC_FEE

    override val blobFeeCap: BigInteger?
        get() = null

    override val blobVersionedHashes: List<Hash>?
        get() = null

    override val blobGas: Long
        get() = 0

    override fun rlpEncodeFields(rlp: RlpEncoder) {
        rlp.encode(chainId)
        rlp.encode(nonce)
        rlp.encode(gasTipCap)
        rlp.encode(gasFeeCap)
        rlp.encode(gas)
        rlp.encode(to)
        rlp.encode(value)
        rlp.encode(data)
        rlp.encodeList(accessList)
    }

    /**
     * Copy or override `this` parameters into new [TxDynamicFee] object.
     */
    fun copy(
        to: Address? = this.to,
        value: BigInteger = this.value,
        nonce: Long = this.nonce,
        gas: Long = this.gas,
        gasFeeCap: BigInteger = this.gasFeeCap,
        gasTipCap: BigInteger = this.gasTipCap,
        data: Bytes? = this.data,
        chainId: Long = this.chainId,
        accessList: List<AccessList.Item>? = this.accessList,
    ): TxDynamicFee {
        return TxDynamicFee(
            to = to,
            value = value,
            nonce = nonce,
            gas = gas,
            gasFeeCap = gasFeeCap,
            gasTipCap = gasTipCap,
            data = data,
            chainId = chainId,
            accessList = accessList,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TxDynamicFee

        if (to != other.to) return false
        if (value != other.value) return false
        if (nonce != other.nonce) return false
        if (gas != other.gas) return false
        if (gasFeeCap != other.gasFeeCap) return false
        if (gasTipCap != other.gasTipCap) return false
        if (data != other.data) return false
        if (chainId != other.chainId) return false
        if (accessList != other.accessList) return false

        return true
    }

    override fun hashCode(): Int {
        var result = to?.hashCode() ?: 0
        result = 31 * result + value.hashCode()
        result = 31 * result + nonce.hashCode()
        result = 31 * result + gas.hashCode()
        result = 31 * result + gasFeeCap.hashCode()
        result = 31 * result + gasTipCap.hashCode()
        result = 31 * result + (data?.hashCode() ?: 0)
        result = 31 * result + chainId.hashCode()
        result = 31 * result + (accessList?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "TxDynamicFee(to=$to, value=$value, nonce=$nonce, gas=$gas, gasFeeCap=$gasFeeCap, gasTipCap=$gasTipCap, data=$data, chainId=$chainId, accessList=$accessList)"
    }

    companion object : RlpDecodable<TxDynamicFee> {
        @JvmStatic
        override fun rlpDecode(rlp: RlpDecoder): TxDynamicFee {
            return TxDynamicFee(
                chainId = rlp.decodeLong(),
                nonce = rlp.decodeLong(),
                gasTipCap = rlp.decodeBigIntegerElse(BigInteger.ZERO),
                gasFeeCap = rlp.decodeBigIntegerElse(BigInteger.ZERO),
                gas = rlp.decodeLong(),
                to = rlp.decode(Address),
                value = rlp.decodeBigIntegerElse(BigInteger.ZERO),
                data = rlp.decode(Bytes),
                accessList = rlp.decodeAsList(AccessList.Item),
            )
        }
    }
}
