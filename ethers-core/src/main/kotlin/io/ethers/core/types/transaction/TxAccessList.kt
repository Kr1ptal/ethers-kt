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
 * EIP-2930 transaction with optional access list.
 *
 * - [EIP-2930](https://eips.ethereum.org/EIPS/eip-2930)
 * */
class TxAccessList(
    override val to: Address?,
    override val value: BigInteger,
    override val nonce: Long,
    override val gas: Long,
    override val gasPrice: BigInteger,
    override val data: Bytes?,
    override val chainId: Long,
    override var accessList: List<AccessList.Item>?,
) : TransactionUnsigned {
    init {
        if (!ChainId.isValid(chainId)) {
            throw IllegalArgumentException("AccessList transactions must have a chainId")
        }
    }

    override val gasTipCap: BigInteger
        get() = gasPrice

    override val gasFeeCap: BigInteger
        get() = gasPrice

    override val type: TxType
        get() = TxType.ACCESS_LIST

    override val blobFeeCap: BigInteger?
        get() = null

    override val blobVersionedHashes: List<Hash>?
        get() = null

    override val blobGas: Long
        get() = 0

    override fun rlpEncodeFields(rlp: RlpEncoder) {
        rlp.encode(chainId)
        rlp.encode(nonce)
        rlp.encode(gasPrice)
        rlp.encode(gas)
        rlp.encode(to)
        rlp.encode(value)
        rlp.encode(data)
        rlp.encodeList(accessList)
    }

    /**
     * Copy or override `this` parameters into new [TxAccessList] object.
     */
    fun copy(
        to: Address? = this.to,
        value: BigInteger = this.value,
        nonce: Long = this.nonce,
        gas: Long = this.gas,
        gasPrice: BigInteger = this.gasPrice,
        data: Bytes? = this.data,
        chainId: Long = this.chainId,
        accessList: List<AccessList.Item>? = this.accessList,
    ): TxAccessList {
        return TxAccessList(
            to = to,
            value = value,
            nonce = nonce,
            gas = gas,
            gasPrice = gasPrice,
            data = data,
            chainId = chainId,
            accessList = accessList,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TxAccessList

        if (to != other.to) return false
        if (value != other.value) return false
        if (nonce != other.nonce) return false
        if (gas != other.gas) return false
        if (gasPrice != other.gasPrice) return false
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
        result = 31 * result + gasPrice.hashCode()
        result = 31 * result + (data?.hashCode() ?: 0)
        result = 31 * result + chainId.hashCode()
        result = 31 * result + (accessList?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "TxAccessList(to=$to, value=$value, nonce=$nonce, gas=$gas, gasPrice=$gasPrice, data=$data, chainId=$chainId, accessList=$accessList)"
    }

    companion object : RlpDecodable<TxAccessList> {
        @JvmStatic
        override fun rlpDecode(rlp: RlpDecoder): TxAccessList {
            return TxAccessList(
                chainId = rlp.decodeLong(),
                nonce = rlp.decodeLong(),
                gasPrice = rlp.decodeBigIntegerElse(BigInteger.ZERO),
                gas = rlp.decodeLong(),
                to = rlp.decode(Address),
                value = rlp.decodeBigIntegerElse(BigInteger.ZERO),
                data = rlp.decode(Bytes),
                accessList = rlp.decodeAsList(AccessList.Item),
            )
        }
    }
}
