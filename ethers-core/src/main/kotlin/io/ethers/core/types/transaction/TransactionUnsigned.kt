package io.ethers.core.types.transaction

import io.ethers.crypto.Hashing
import io.ethers.rlp.RlpDecodable
import io.ethers.rlp.RlpDecoder
import io.ethers.rlp.RlpEncoder

/**
 * An unsigned [Transaction] with functions for signing.
 * */
sealed interface TransactionUnsigned : Transaction {
    /**
     * RLP encode transaction fields.
     */
    fun rlpEncodeFields(rlp: RlpEncoder)

    /**
     * Get hash used for signing the transaction.
     * */
    fun signatureHash(): ByteArray {
        val encoder = RlpEncoder()
        rlpEncode(encoder, true)
        return Hashing.keccak256(encoder.toByteArray())
    }

    /**
     * Encode [TransactionUnsigned] into provided [RlpEncoder].
     */
    fun rlpEncode(encoder: RlpEncoder, forSigHash: Boolean = false) {
        // non-legacy txs are enveloped based on eip2718
        if (type != TxType.LEGACY) {
            encoder.appendRaw(type.value.toByte())
        }

        encoder.encodeList {
            rlpEncodeFields(encoder)

            if (!forSigHash) {
                // Encode empty r, s, v signature fields
                encoder.encode(0)
                encoder.encode(0)
                encoder.encode(0)
                return@encodeList
            }

            if (type == TxType.LEGACY && ChainId.isValid(chainId)) {
                // EIP-155 support for LegacyTx, applies only if we have a valid chainId
                // see: https://github.com/ethereum/EIPs/blob/master/EIPS/eip-155.md
                encoder.encode(chainId)
                encoder.encode(0)
                encoder.encode(0)
            }
        }
    }

    companion object : RlpDecodable<TransactionUnsigned> {

        override fun rlpDecode(rlp: RlpDecoder) = rlpDecode(rlp, ChainId.NONE)

        /**
         * Decode RLP data array. Compared to base [rlpDecode], this function provides additional [chainId]
         * parameter which is needed for correct replay-protected [TxType.LEGACY] transaction decoding.
         */
        fun rlpDecode(data: ByteArray, chainId: Long) = rlpDecode(RlpDecoder(data), chainId)

        /**
         * Decode [rlp]. Compared to base [rlpDecode], this function provides additional [chainId]
         * parameter which is needed for correct replay-protected [TxType.LEGACY] transaction decoding.
         */
        fun rlpDecode(rlp: RlpDecoder, chainId: Long): TransactionUnsigned? {
            val type = rlp.peekByte().toUByte().toInt()
            val ret = when {
                type == TxType.ACCESS_LIST.value -> {
                    rlp.readByte()
                    rlp.decodeList { TxAccessList.rlpDecode(rlp).also { dropEmptyRSV() } }
                }

                type == TxType.DYNAMIC_FEE.value -> {
                    rlp.readByte()
                    rlp.decodeList { TxDynamicFee.rlpDecode(rlp).also { dropEmptyRSV() } }
                }

                type >= 0xc0 -> rlp.decodeList { TxLegacy.rlpDecode(rlp, chainId).also { dropEmptyRSV() } }
                else -> null
            }

            return ret
        }

        // Decode and ignore empty r, s, v signature fields
        private fun RlpDecoder.dropEmptyRSV() {
            decodeLong()
            decodeLong()
            decodeLong()
        }
    }
}
