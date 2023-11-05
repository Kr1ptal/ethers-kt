package io.ethers.core.types.transaction

import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.Signature
import io.ethers.crypto.Hashing
import io.ethers.rlp.RlpDecodable
import io.ethers.rlp.RlpDecoder
import io.ethers.rlp.RlpEncodable
import io.ethers.rlp.RlpEncoder
import java.math.BigInteger

/**
 * A [Transaction] with a valid signature from which the sender's address and hash can be recovered.
 *
 * Transactions are RLP-encoded/decoded based on `Typed Transaction Envelope` format.
 *
 * - [EIP-2718](https://eips.ethereum.org/EIPS/eip-2718)
 * */
class TransactionSigned @JvmOverloads constructor(
    val tx: TransactionUnsigned,
    val signature: Signature,
    hash: Hash? = null,
    from: Address? = null,
) : TransactionRecovered, Transaction by tx, RlpEncodable {
    private var _hash: Hash? = hash
    private var _from: Address? = from
    private var _isValidSignature: Int = -1

    /**
     * Get the transaction hash.
     * */
    override val hash: Hash
        get() {
            if (_hash == null) {
                val hashRlp = RlpEncoder().also { rlpEncode(it, true) }.toByteArray()
                _hash = Hash(Hashing.keccak256(hashRlp))
            }
            return _hash!!
        }

    /**
     * Get the sender's address, or throw an exception if the signature is invalid.
     * */
    override val from: Address
        get() {
            if (_from == null) {
                if (!isSignatureValid) {
                    throw IllegalStateException("Unable to recover sender, invalid signature")
                }
            }
            return _from!!
        }

    /**
     * Get the sender's address if the signature is valid, otherwise null.
     * */
    val fromOrNull: Address?
        get() {
            if (_from == null) {
                if (!isSignatureValid) {
                    return null
                }
            }
            return _from
        }

    /**
     * Check if transaction has a valid signature by trying to recover signer address from signature hash.
     */
    val isSignatureValid: Boolean
        get() {
            if (_isValidSignature == -1) {
                val from = signature.recoverFromHash(tx.signatureHash())

                _isValidSignature = if (from != null) 1 else 0
                _from = from
            }
            return _isValidSignature == 1
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransactionSigned

        if (tx != other.tx) return false
        if (signature != other.signature) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tx.hashCode()
        result = 31 * result + signature.hashCode()
        return result
    }

    override fun toString(): String {
        return "TransactionSigned(tx=$tx, signature=$signature)"
    }

    override fun rlpEncode(rlp: RlpEncoder) = rlpEncode(rlp, false)

    private fun rlpEncode(rlp: RlpEncoder, hashEncoding: Boolean) {
        // non-legacy txs are enveloped based on eip2718
        if (tx.type != TxType.LEGACY) {
            rlp.appendRaw(tx.type.value.toByte())
        }

        rlp.encodeList {
            // If blob tx has sidecar, encode as network encoding - but only if not encoding for hash. For hash, we use
            // canonical encoding.
            //
            // Network encoding: 'type || rlp([tx_payload_body, blobs, commitments, proofs])'
            // Canonical encoding: 'type || rlp(tx_payload_body)', where 'tx_payload_body' is a list of tx fields with
            // signature values.
            //
            // See: https://eips.ethereum.org/EIPS/eip-4844#networking
            if (!hashEncoding && tx.type == TxType.BLOB && (tx as TxBlob).sidecar != null) {
                rlp.encodeList {
                    tx.rlpEncodeFields(this)
                    signature.rlpEncode(this)
                }

                tx.sidecar!!.rlpEncode(this)
            } else {
                tx.rlpEncodeFields(this)
                signature.rlpEncode(this)
            }
        }
    }

    companion object : RlpDecodable<TransactionSigned> {
        @JvmStatic
        override fun rlpDecode(rlp: RlpDecoder): TransactionSigned? {
            val type = rlp.peekByte().toUByte().toInt()

            // legacy tx
            if (type >= 0xc0) {
                return rlp.decodeList {
                    val nonce = rlp.decodeLong()
                    val gasPrice = rlp.decodeBigInteger() ?: BigInteger.ZERO
                    val gas = rlp.decodeLong()
                    val to = rlp.decode(Address)
                    val value = rlp.decodeBigInteger() ?: BigInteger.ZERO
                    val data = rlp.decode(Bytes)

                    val signature = rlp.decode(Signature) ?: return null

                    // since we're decoding a signed tx, we have a signature from which we can recover the chainId
                    val chainId = ChainId.fromSignature(signature)

                    val tx = TxLegacy(
                        nonce = nonce,
                        gasPrice = gasPrice,
                        gas = gas,
                        to = to,
                        value = value,
                        data = data,
                        chainId = chainId,
                    )

                    TransactionSigned(tx, signature)
                }
            }

            return when (TxType.findOrNull(type)) {
                TxType.LEGACY -> throw IllegalStateException("Should not happen")
                TxType.ACCESS_LIST -> {
                    rlp.readByte()

                    rlp.decodeList {
                        val tx = TxAccessList.rlpDecode(rlp)
                        val signature = rlp.decode(Signature) ?: return null
                        TransactionSigned(tx, signature)
                    }
                }

                TxType.DYNAMIC_FEE -> {
                    rlp.readByte()

                    rlp.decodeList {
                        val tx = TxDynamicFee.rlpDecode(rlp)
                        val signature = rlp.decode(Signature) ?: return null
                        TransactionSigned(tx, signature)
                    }
                }

                TxType.BLOB -> {
                    rlp.readByte()

                    rlp.decodeList {
                        val isNetworkEncoding = rlp.isNextElementList()
                        if (!isNetworkEncoding) {
                            val tx = TxBlob.rlpDecode(rlp) ?: return null
                            val signature = rlp.decode(Signature) ?: return null

                            TransactionSigned(tx, signature)
                        } else {
                            // see: https://eips.ethereum.org/EIPS/eip-4844#networking
                            lateinit var tx: TxBlob
                            lateinit var signature: Signature
                            rlp.decodeList {
                                tx = TxBlob.rlpDecode(rlp) ?: return null
                                signature = rlp.decode(Signature) ?: return null
                            }

                            val sidecar = rlp.decode(TxBlob.Sidecar) ?: return null

                            // TODO avoid creating a copy just with sidecar
                            TransactionSigned(tx.copy(sidecar = sidecar), signature)
                        }
                    }
                }

                null -> null
            }
        }
    }
}
