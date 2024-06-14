package io.ethers.core.types.transaction

import io.ethers.core.types.Signature
import io.ethers.crypto.Hashing
import io.ethers.rlp.RlpDecodable
import io.ethers.rlp.RlpDecoder
import io.ethers.rlp.RlpEncodable
import io.ethers.rlp.RlpEncoder
import java.math.BigInteger

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
     * Get hash used for signing the transaction.
     * */
    fun signatureHash(): ByteArray {
        val encoder = RlpEncoder()
        rlpEncodeEnveloped(encoder, null, true)
        return Hashing.keccak256(encoder.toByteArray())
    }

    /**
     * Encode [TransactionUnsigned] via provided [RlpEncoder]. This RLP format includes all-zero signature fields and
     * is the inverse of [rlpDecode].
     */
    override fun rlpEncode(rlp: RlpEncoder) {
        rlpEncodeEnveloped(rlp, EMPTY_SIGNATURE, true)
    }

    companion object : RlpDecodable<TransactionUnsigned> {
        private val EMPTY_SIGNATURE = Signature(BigInteger.ZERO, BigInteger.ZERO, 0L)

        override fun rlpDecode(rlp: RlpDecoder) = rlpDecode(rlp, ChainId.NONE)

        /**
         * Decode RLP data array. Compared to base [rlpDecode], this function provides additional [chainId]
         * parameter which is needed for correct replay-protected [TxType.Legacy] transaction decoding.
         */
        fun rlpDecode(data: ByteArray, chainId: Long) = rlpDecode(RlpDecoder(data), chainId)

        /**
         * Decode [rlp]. Compared to base [rlpDecode], this function provides additional [chainId]
         * parameter which is needed for correct replay-protected [TxType.Legacy] transaction decoding.
         *
         * This function is inverse of [rlpEncode].
         */
        fun rlpDecode(rlp: RlpDecoder, chainId: Long): TransactionUnsigned? {
            val type = rlp.peekByte().toUByte().toInt()

            // legacy tx
            if (type >= 0xc0) {
                return rlp.decodeList { TxLegacy.rlpDecode(rlp, chainId).also { dropEmptyRSV() } }
            }

            return when (TxType.fromType(type)) {
                TxType.Legacy -> throw IllegalStateException("Should not happen")

                TxType.AccessList -> {
                    rlp.readByte()
                    rlp.decodeList { TxAccessList.rlpDecode(rlp).also { dropEmptyRSV() } }
                }

                TxType.DynamicFee -> {
                    rlp.readByte()
                    rlp.decodeList { TxDynamicFee.rlpDecode(rlp).also { dropEmptyRSV() } }
                }

                TxType.Blob -> {
                    rlp.readByte()
                    rlp.decodeList { TxBlob.rlpDecode(rlp).also { dropEmptyRSV() } }
                }

                is TxType.Unsupported -> null
            }
        }

        // Decode and ignore empty r, s, v signature fields
        private fun RlpDecoder.dropEmptyRSV() {
            decodeLong()
            decodeLong()
            decodeLong()
        }
    }
}
