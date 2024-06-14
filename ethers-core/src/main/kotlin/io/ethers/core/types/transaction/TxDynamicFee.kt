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
data class TxDynamicFee(
    override val to: Address?,
    override val value: BigInteger,
    override val nonce: Long,
    override val gas: Long,
    override val gasFeeCap: BigInteger,
    override val gasTipCap: BigInteger,
    override val data: Bytes?,
    override val chainId: Long,
    override val accessList: List<AccessList.Item>,
) : TransactionUnsigned {
    init {
        if (!ChainId.isValid(chainId)) {
            throw IllegalArgumentException("DynamicFee transactions must have a chainId")
        }
    }

    override val gasPrice: BigInteger
        get() = gasFeeCap

    override val type: TxType
        get() = TxType.DynamicFee

    override val blobFeeCap: BigInteger?
        get() = null

    override val blobVersionedHashes: List<Hash>?
        get() = null

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
