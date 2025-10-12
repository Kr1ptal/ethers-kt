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
 * EIP-7702 SetCode transaction with authorization list for delegating contract code execution.
 *
 * - [EIP-7702](https://eips.ethereum.org/EIPS/eip-7702)
 */
data class TxSetCode(
    override val to: Address,
    override val value: BigInteger,
    override val nonce: Long,
    override val gas: Long,
    override val gasFeeCap: BigInteger,
    override val gasTipCap: BigInteger,
    override val data: Bytes?,
    override val chainId: Long,
    override val accessList: List<AccessList.Item>,
    override val authorizationList: List<Authorization>,
) : TransactionUnsigned {

    init {
        if (!ChainId.isValid(chainId)) {
            throw IllegalArgumentException("SetCode transactions must have a chainId")
        }
        if (authorizationList.isEmpty()) {
            throw IllegalArgumentException("SetCode transactions must have a non-empty authorization list")
        }
    }

    override val gasPrice: BigInteger
        get() = gasFeeCap

    override val type: TxType
        get() = TxType.SetCode

    override val blobFeeCap: BigInteger?
        get() = null

    override val blobVersionedHashes: List<Hash>?
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
            rlp.encodeList(authorizationList)

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
            sizeOfList(authorizationList) +
            (signature?.rlpSize() ?: 0)
    }

    companion object : RlpDecodable<TxSetCode> {
        @JvmStatic
        override fun rlpDecode(rlp: RlpDecoder): TxSetCode? {
            return TxSetCode(
                chainId = rlp.decodeLongOrElse { return null },
                nonce = rlp.decodeLongOrElse { return null },
                gasTipCap = rlp.decodeBigIntegerOrElse { return null },
                gasFeeCap = rlp.decodeBigIntegerOrElse { return null },
                gas = rlp.decodeLongOrElse { return null },
                to = rlp.decode(Address) ?: return null,
                value = rlp.decodeBigIntegerOrElse { return null },
                data = rlp.decode(Bytes)?.takeIf { it.size > 0 },
                accessList = rlp.decodeAsList(AccessList.Item) ?: return null,
                authorizationList = rlp.decodeAsList(Authorization) ?: return null,
            )
        }
    }
}
