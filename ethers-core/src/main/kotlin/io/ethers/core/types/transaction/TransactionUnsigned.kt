package io.ethers.core.types.transaction

import io.ethers.core.types.Signature
import io.ethers.core.types.transaction.TransactionUnsigned.Companion.rlpDecode
import io.ethers.crypto.Hashing
import io.ethers.rlp.RlpDecodable
import io.ethers.rlp.RlpDecoder
import io.ethers.rlp.RlpEncodable
import io.ethers.rlp.RlpEncoder

/**
 * An unsigned [Transaction] with functions for signing.
 * */
sealed interface TransactionUnsigned : Transaction, RlpEncodable {
    /**
     * RLP encode transaction with optional signature according to [EIP-2718](https://eips.ethereum.org/EIPS/eip-2718)
     * enveloped format. If [hashEncoding] is true, the transaction is encoded for hash calculation, either signature
     * hash (if [signature] is null) or transaction hash (if [signature] is not null).
     * */
    fun rlpEncodeEnveloped(rlp: RlpEncoder, signature: Signature?, hashEncoding: Boolean)

    /**
     * Return the size of the RLP encoded transaction with optional signature according to
     * [EIP-2718](https://eips.ethereum.org/EIPS/eip-2718) enveloped format. If [hashEncoding] is true, the size
     * is either for signature hash (if [signature] is null) or transaction hash (if [signature] is not null).
     *
     * This must return the exact size required for the RLP encoding via [rlpEncodeEnveloped] when provided with the
     * same parameters.
     * */
    fun rlpEnvelopedSize(signature: Signature?, hashEncoding: Boolean): Int

    /**
     * Get hash used for signing the transaction.
     * */
    fun signatureHash(): ByteArray {
        val rlp = RlpEncoder(rlpEnvelopedSize(null, true), isExactSize = true)
            .also { rlpEncodeEnveloped(it, null, true) }
            .toByteArray()

        return Hashing.keccak256(rlp)
    }

    /**
     * Encode [TransactionUnsigned] via provided [RlpEncoder].
     */
    override fun rlpEncode(rlp: RlpEncoder) {
        rlpEncodeEnveloped(rlp, null, true)
    }

    override fun rlpSize(): Int {
        return rlpEnvelopedSize(null, true)
    }

    companion object : RlpDecodable<TransactionUnsigned> {
        override fun rlpDecode(rlp: RlpDecoder): TransactionUnsigned? {
            if (rlp.isDone) return null
            val type = rlp.peekByte().toUByte().toInt()

            // legacy tx
            if (type >= 0xc0) {
                return rlp.decodeList { TxLegacy.rlpDecode(rlp) }
            }

            return when (TxType.fromType(type)) {
                TxType.Legacy -> throw IllegalStateException("Should not happen")

                TxType.AccessList -> {
                    rlp.readByte()
                    rlp.decodeList { TxAccessList.rlpDecode(rlp) }
                }

                TxType.DynamicFee -> {
                    rlp.readByte()
                    rlp.decodeList { TxDynamicFee.rlpDecode(rlp) }
                }

                TxType.Blob -> {
                    rlp.readByte()
                    rlp.decodeList { TxBlob.rlpDecode(rlp) }
                }

                TxType.SetCode -> {
                    rlp.readByte()
                    rlp.decodeList { TxSetCode.rlpDecode(rlp) }
                }

                is TxType.Unsupported -> null
            }
        }
    }
}
