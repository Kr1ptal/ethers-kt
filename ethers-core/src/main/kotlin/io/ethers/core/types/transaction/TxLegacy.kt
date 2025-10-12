package io.ethers.core.types.transaction

import io.ethers.core.types.AccessList
import io.ethers.core.types.Address
import io.ethers.core.types.Authorization
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.Signature
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

    override val accessList: List<AccessList.Item>
        get() = emptyList()

    override val type: TxType
        get() = TxType.Legacy

    override val blobFeeCap: BigInteger?
        get() = null

    override val blobVersionedHashes: List<Hash>?
        get() = null

    override val authorizationList: List<Authorization>?
        get() = null

    override fun rlpEncodeEnveloped(rlp: RlpEncoder, signature: Signature?, hashEncoding: Boolean) {
        rlp.encodeList(rlpFieldsWithSignatureSize(signature, hashEncoding)) {
            rlp.encode(nonce)
            rlp.encode(gasPrice)
            rlp.encode(gas)
            rlp.encode(to)
            rlp.encode(value)
            rlp.encode(data)

            when {
                hashEncoding && signature == null -> {
                    if (ChainId.isValid(chainId)) {
                        rlp.encode(chainId)
                        rlp.encode(0)
                        rlp.encode(0)
                    }
                }

                signature != null -> rlp.encode(signature)
            }
        }
    }

    override fun rlpEnvelopedSize(signature: Signature?, hashEncoding: Boolean): Int = with(RlpEncoder) {
        return sizeOfList(rlpFieldsWithSignatureSize(signature, hashEncoding))
    }

    private fun rlpFieldsWithSignatureSize(signature: Signature?, hashEncoding: Boolean): Int = with(RlpEncoder) {
        var size = sizeOf(nonce) +
            sizeOf(gasPrice) +
            sizeOf(gas) +
            sizeOf(to) +
            sizeOf(value) +
            sizeOf(data)

        when {
            hashEncoding && signature == null -> {
                if (ChainId.isValid(chainId)) {
                    // chainId + 2x zero
                    size += sizeOf(chainId) + 2
                }
            }

            signature != null -> size += signature.rlpSize()
        }

        return size
    }

    companion object : RlpDecodable<TxLegacy> {
        @JvmStatic
        override fun rlpDecode(rlp: RlpDecoder): TxLegacy? {
            return rlpDecode(rlp, false)
        }

        fun rlpDecode(rlp: RlpDecoder, signedTx: Boolean): TxLegacy? {
            val nonce = rlp.decodeLongOrElse { return null }
            val gasPrice = rlp.decodeBigIntegerOrElse { return null }
            val gas = rlp.decodeLongOrElse { return null }
            val to = rlp.decode(Address)
            val value = rlp.decodeBigIntegerOrElse { return null }
            val data = rlp.decode(Bytes)
            val chainId = when {
                signedTx -> ChainId.NONE
                rlp.isDone -> ChainId.NONE
                else -> {
                    // read chain id and drop remaining EIP-155 fields
                    val chainId = rlp.decodeLongOrElse { return null }
                    val emptyR = rlp.decodeLongOrElse { return null }
                    val emptyS = rlp.decodeLongOrElse { return null }

                    // validate this is indeed the expected EIP-155 signature placeholder
                    if (emptyR != 0L || emptyS != 0L) {
                        return null
                    }

                    chainId
                }
            }

            return TxLegacy(
                to = to,
                value = value,
                nonce = nonce,
                gas = gas,
                gasPrice = gasPrice,
                data = data?.takeIf { it.size > 0 },
                chainId = chainId,
            )
        }
    }
}
