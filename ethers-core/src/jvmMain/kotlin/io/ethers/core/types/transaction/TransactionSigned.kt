package io.ethers.core.types.transaction

import io.ethers.core.types.Address
import io.ethers.core.types.CallRequest
import io.ethers.core.types.Hash
import io.ethers.core.types.Signature
import io.ethers.crypto.Hashing
import io.ethers.rlp.RlpDecodable
import io.ethers.rlp.RlpDecoder
import io.ethers.rlp.RlpEncodable
import io.ethers.rlp.RlpEncoder

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

    override fun toCallRequest(): CallRequest {
        return super<TransactionRecovered>.toCallRequest()
    }

    /**
     * Get the transaction hash.
     * */
    override val hash: Hash
        get() {
            if (_hash == null) {
                val rlp = RlpEncoder(tx.rlpEnvelopedSize(signature, true), isExactSize = true)
                    .also { tx.rlpEncodeEnveloped(it, signature, true) }
                    .toByteArray()

                _hash = Hash(Hashing.keccak256(rlp))
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
        if (other == null || this::class != other::class) return false

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

    override fun rlpEncode(rlp: RlpEncoder) {
        tx.rlpEncodeEnveloped(rlp, signature, false)
    }

    override fun rlpSize(): Int {
        return tx.rlpEnvelopedSize(signature, false)
    }

    companion object : RlpDecodable<TransactionSigned> {
        @JvmStatic
        override fun rlpDecode(data: ByteArray): TransactionSigned? {
            return super.rlpDecode(data)
        }

        @JvmStatic
        override fun rlpDecode(rlp: RlpDecoder): TransactionSigned? {
            if (rlp.isDone) return null
            val type = rlp.peekByte().toUByte().toInt()

            val txType = when {
                type >= 0xc0 -> TxType.Legacy
                else -> TxType.fromType(type)
            }

            return when (txType) {
                TxType.Legacy -> rlp.decodeListOrNull {
                    var tx = TxLegacy.rlpDecode(rlp, true) ?: return null
                    val signature = Signature.rlpDecode(rlp) ?: return null

                    // since we're decoding a signed tx, we have a signature from which we can recover the chainId
                    val chainId = ChainId.fromSignature(signature)
                    if (ChainId.isValid(chainId)) {
                        tx = tx.copy(chainId = chainId)
                    }

                    TransactionSigned(tx, signature)
                }

                TxType.AccessList -> {
                    rlp.readByte()

                    rlp.decodeListOrNull {
                        val tx = TxAccessList.rlpDecode(rlp) ?: return null
                        val signature = Signature.rlpDecode(rlp) ?: return null
                        TransactionSigned(tx, signature)
                    }
                }

                TxType.DynamicFee -> {
                    rlp.readByte()

                    rlp.decodeListOrNull {
                        val tx = TxDynamicFee.rlpDecode(rlp) ?: return null
                        val signature = Signature.rlpDecode(rlp) ?: return null
                        TransactionSigned(tx, signature)
                    }
                }

                TxType.Blob -> {
                    rlp.readByte()

                    rlp.decodeListOrNull {
                        // see: https://eips.ethereum.org/EIPS/eip-4844#networking
                        val isNetworkEncoding = rlp.isNextElementList()

                        if (isNetworkEncoding) {
                            lateinit var tx: TxBlob
                            lateinit var signature: Signature
                            rlp.decodeListOrNull {
                                tx = TxBlob.rlpDecode(rlp) ?: return null
                                signature = Signature.rlpDecode(rlp) ?: return null
                            }

                            val sidecar = TxBlob.Sidecar.rlpDecode(rlp) ?: return null
                            TransactionSigned(tx.copy(sidecar = sidecar), signature)
                        } else {
                            val tx = TxBlob.rlpDecode(rlp) ?: return null
                            val signature = Signature.rlpDecode(rlp) ?: return null

                            TransactionSigned(tx, signature)
                        }
                    }
                }

                TxType.SetCode -> {
                    rlp.readByte()

                    rlp.decodeListOrNull {
                        val tx = TxSetCode.rlpDecode(rlp) ?: return null
                        val signature = Signature.rlpDecode(rlp) ?: return null
                        TransactionSigned(tx, signature)
                    }
                }

                is TxType.Unsupported -> null
            }
        }
    }
}
