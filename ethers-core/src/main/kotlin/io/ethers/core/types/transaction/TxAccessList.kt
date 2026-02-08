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

/**
 * EIP-2930 transaction with optional access list.
 *
 * - [EIP-2930](https://eips.ethereum.org/EIPS/eip-2930)
 * */
data class TxAccessList(
    override val to: Address?,
    override val value: BigInteger,
    override val nonce: Long,
    override val gas: Long,
    override val gasPrice: BigInteger,
    override val data: Bytes?,
    override val chainId: Long,
    override val accessList: List<AccessList.Item>,
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
        get() = TxType.AccessList

    override val blobFeeCap: BigInteger?
        get() = null

    override val blobVersionedHashes: List<Hash>?
        get() = null

    override val authorizationList: List<Authorization>?
        get() = null

    override fun rlpEncodeEnveloped(rlp: RlpEncoder, signature: Signature?, hashEncoding: Boolean) {
        rlp.appendRaw(type.type.toByte())

        rlp.encodeList(rlpFieldsWithSignatureSize(signature)) {
            rlp.encode(chainId)
            rlp.encode(nonce)
            rlp.encode(gasPrice)
            rlp.encode(gas)
            rlp.encode(to)
            rlp.encode(value)
            rlp.encode(data)
            rlp.encodeList(accessList)

            signature?.rlpEncode(this)
        }
    }

    override fun rlpEnvelopedSize(signature: Signature?, hashEncoding: Boolean): Int = with(RlpEncoder) {
        return 1 + sizeOfList(rlpFieldsWithSignatureSize(signature))
    }

    private fun rlpFieldsWithSignatureSize(signature: Signature?): Int = with(RlpEncoder) {
        return sizeOf(chainId) +
            sizeOf(nonce) +
            sizeOf(gasPrice) +
            sizeOf(gas) +
            sizeOf(to) +
            sizeOf(value) +
            sizeOf(data) +
            sizeOfList(accessList) +
            (signature?.rlpSize() ?: 0)
    }

    companion object : RlpDecodable<TxAccessList> {
        @JvmStatic
        override fun rlpDecode(rlp: RlpDecoder): TxAccessList? {
            return TxAccessList(
                chainId = rlp.decodeLongOrElse { return null },
                nonce = rlp.decodeLongOrElse { return null },
                gasPrice = rlp.decodeBigIntegerOrNull() ?: return null,
                gas = rlp.decodeLongOrElse { return null },
                to = rlp.decodeOptionalOrElse(Address) { return null },
                value = rlp.decodeBigIntegerOrNull() ?: return null,
                data = rlp.decodeOrNull(Bytes)?.takeIf { it.size > 0 },
                accessList = rlp.decodeAsListOrNull(AccessList.Item) ?: return null,
            )
        }
    }
}
