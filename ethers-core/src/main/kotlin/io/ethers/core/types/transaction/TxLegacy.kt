package io.ethers.core.types.transaction

import io.ethers.core.types.AccessList
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.Signature
import io.ethers.rlp.RlpDecodable
import io.ethers.rlp.RlpDecoder
import io.ethers.rlp.RlpEncoder
import io.ethers.rlp.RlpSizer
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

    override val accessList: List<AccessList.Item>
        get() = emptyList()

    override val type: TxType
        get() = TxType.Legacy

    override val blobFeeCap: BigInteger?
        get() = null

    override val blobVersionedHashes: List<Hash>?
        get() = null

    override fun rlpFieldsSize(): Int {
        return with(RlpSizer) {
            sizeOf(nonce) +
                sizeOf(gasPrice) +
                sizeOf(gas) +
                sizeOf(to) +
                sizeOf(value) +
                sizeOf(data)
        }
    }

    override fun rlpEncodeEnveloped(rlp: RlpEncoder, signature: Signature?, hashEncoding: Boolean) {
        rlp.encodeList {
            rlp.encode(nonce)
            rlp.encode(gasPrice)
            rlp.encode(gas)
            rlp.encode(to)
            rlp.encode(value)
            rlp.encode(data)

            if (hashEncoding) {
                if (signature == null) {
                    if (ChainId.isValid(chainId)) {
                        rlp.encode(chainId)
                        rlp.encode(0)
                        rlp.encode(0)
                    }
                    return@encodeList
                }
            }

            signature?.rlpEncode(this)
        }
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
