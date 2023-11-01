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
                _hash = Hash(Hashing.keccak256(toRlp()))
            }
            return _hash!!
        }

    /**
     * Get the sender's address, or throw an exception if the signature is invalid.
     * */
    override val from: Address
        get() {
            if (_from == null) {
                if (!hasValidSignature) {
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
                if (!hasValidSignature) {
                    return null
                }
            }
            return _from
        }

    /**
     * Check if transaction has a valid signature by trying to recover signer address from signature hash.
     */
    @get:JvmName("hasValidSignature")
    val hasValidSignature: Boolean
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

    override fun rlpEncode(rlp: RlpEncoder) {
        // non-legacy txs are enveloped based on eip2718
        if (tx.type != TxType.LEGACY) {
            rlp.appendRaw(tx.type.value.toByte())
        }

        rlp.encodeList {
            tx.rlpEncodeFields(this)
            signature.rlpEncode(this)
        }
    }

    companion object : RlpDecodable<TransactionSigned> {
        @JvmStatic
        override fun rlpDecode(rlp: RlpDecoder): TransactionSigned? {
            val type = rlp.peekByte().toUByte().toInt()
            return when {
                type == TxType.ACCESS_LIST.value -> {
                    rlp.readByte()

                    rlp.decodeList {
                        val tx = TxAccessList.rlpDecode(rlp)
                        val signature = rlp.decode(Signature) ?: return null
                        TransactionSigned(tx, signature)
                    }
                }

                type == TxType.DYNAMIC_FEE.value -> {
                    rlp.readByte()

                    rlp.decodeList {
                        val tx = TxDynamicFee.rlpDecode(rlp)
                        val signature = rlp.decode(Signature) ?: return null
                        TransactionSigned(tx, signature)
                    }
                }

                type >= 0xc0 -> rlp.decodeList {
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

                else -> null
            }
        }
    }
}
