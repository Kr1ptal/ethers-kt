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
import kotlin.jvm.optionals.getOrNull

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

    override val authorizationList: List<Authorization>?
        get() = null

    override fun rlpEncodeEnveloped(rlp: RlpEncoder, signature: Signature?, hashEncoding: Boolean) {
        rlp.appendRaw(type.type.toByte())

        rlp.encodeList(rlpFieldsWithSignatureSize(signature)) {
            rlp.encode(chainId)
            rlp.encode(nonce)
            rlp.encode(gasTipCap)
            rlp.encode(gasFeeCap)
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
            sizeOf(gasTipCap) +
            sizeOf(gasFeeCap) +
            sizeOf(gas) +
            sizeOf(to) +
            sizeOf(value) +
            sizeOf(data) +
            sizeOfList(accessList) +
            (signature?.rlpSize() ?: 0)
    }

    companion object : RlpDecodable<TxDynamicFee> {
        @JvmStatic
        override fun rlpDecode(rlp: RlpDecoder): TxDynamicFee? {
            return TxDynamicFee(
                chainId = rlp.decodeLongOrElse { return null },
                nonce = rlp.decodeLongOrElse { return null },
                gasTipCap = rlp.decodeBigIntegerOrNull() ?: return null,
                gasFeeCap = rlp.decodeBigIntegerOrNull() ?: return null,
                gas = rlp.decodeLongOrElse { return null },
                to = (rlp.decodeOptionalOrNull(Address) ?: return null).getOrNull(),
                value = rlp.decodeBigIntegerOrNull() ?: return null,
                data = (rlp.decodeOrNull(Bytes) ?: return null).takeIf { it.size > 0 },
                accessList = rlp.decodeAsListOrNull(AccessList.Item) ?: return null,
            )
        }
    }
}
