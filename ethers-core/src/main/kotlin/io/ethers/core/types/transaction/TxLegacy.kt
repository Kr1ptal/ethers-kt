package io.ethers.core.types.transaction

import io.ethers.core.types.AccessList
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.rlp.RlpDecodable
import io.ethers.rlp.RlpDecoder
import io.ethers.rlp.RlpEncoder
import java.math.BigInteger

data class TxLegacy(
    override val to: Address?,
    override val value: BigInteger,
    override val nonce: Long,
    override val gas: Long,
    override val gasPrice: BigInteger,
    override val data: Bytes?,
    override val chainId: Long,
) : TransactionUnsigned {
    override val gasTipCap: BigInteger
        get() = gasPrice

    override val gasFeeCap: BigInteger
        get() = gasPrice

    override val accessList: List<AccessList.Item>?
        get() = null

    override val type: TxType
        get() = TxType.LEGACY

    override fun rlpEncodeFields(rlp: RlpEncoder) {
        rlp.encode(nonce)
        rlp.encode(gasPrice)
        rlp.encode(gas)
        rlp.encode(to)
        rlp.encode(value)
        rlp.encode(data)
    }

    companion object : RlpDecodable<TxLegacy> {
        /**
         * Decode a non-replay protected legacy transaction from RLP. ChainId is encoded only in signature.
         * */
        @JvmStatic
        override fun rlpDecode(rlp: RlpDecoder): TxLegacy {
            return rlpDecode(rlp, ChainId.NONE)
        }

        /**
         * Decode optionally replay-protected legacy transaction from RLP. Replay protected transactions
         * must provide a valid [chainId] parameter.
         */
        @JvmStatic
        fun rlpDecode(rlp: RlpDecoder, chainId: Long): TxLegacy {
            return TxLegacy(
                nonce = rlp.decodeLong(),
                gasPrice = rlp.decodeBigIntegerElse(BigInteger.ZERO),
                gas = rlp.decodeLong(),
                to = rlp.decode(Address),
                value = rlp.decodeBigIntegerElse(BigInteger.ZERO),
                data = rlp.decode(Bytes),
                chainId = chainId,
            )
        }
    }
}
