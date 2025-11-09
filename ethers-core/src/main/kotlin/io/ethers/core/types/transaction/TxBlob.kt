package io.ethers.core.types.transaction

import io.ethers.core.types.AccessList
import io.ethers.core.types.Address
import io.ethers.core.types.Authorization
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
 * An [EIP-4844](https://eips.ethereum.org/EIPS/eip-4844) blob-carrying transaction with additional
 * [blobFeeCap], [blobVersionedHashes], and [sidecar] fields. The network encoding contains the [sidecar], while
 * the canonical encoding does not.
 *
 * The field "to" deviates slightly from the semantics with the exception that it MUST NOT be null and therefore must
 * always represent a 20-byte address. This means that blob transactions cannot have the form of a "create" transaction.
 * */
data class TxBlob(
    override val to: Address,
    override val value: BigInteger,
    override val nonce: Long,
    override val gas: Long,
    override val gasFeeCap: BigInteger,
    override val gasTipCap: BigInteger,
    override val data: Bytes?,
    override val chainId: Long,
    override val accessList: List<AccessList.Item>,
    override val blobFeeCap: BigInteger,
    override val blobVersionedHashes: List<Hash>,
    val sidecar: Sidecar? = null,
) : TransactionUnsigned {
    constructor(
        to: Address,
        value: BigInteger,
        nonce: Long,
        gas: Long,
        gasFeeCap: BigInteger,
        gasTipCap: BigInteger,
        data: Bytes?,
        chainId: Long,
        accessList: List<AccessList.Item>,
        blobFeeCap: BigInteger,
        sidecar: Sidecar,
    ) : this(
        to = to,
        value = value,
        nonce = nonce,
        gas = gas,
        gasFeeCap = gasFeeCap,
        gasTipCap = gasTipCap,
        data = data,
        chainId = chainId,
        accessList = accessList,
        blobFeeCap = blobFeeCap,
        blobVersionedHashes = sidecar.versionedHashes,
        sidecar = sidecar,
    )

    init {
        if (!ChainId.isValid(chainId)) {
            throw IllegalArgumentException("TxBlob transactions must have a chainId")
        }
    }

    override val gasPrice: BigInteger
        get() = gasFeeCap

    override val type: TxType
        get() = TxType.Blob

    override val authorizationList: List<Authorization>?
        get() = null

    override fun rlpEncodeEnveloped(rlp: RlpEncoder, signature: Signature?, hashEncoding: Boolean) = with(RlpEncoder) {
        rlp.appendRaw(type.type.toByte())

        // If blob tx has sidecar, encode as network encoding - but only if not encoding for hash. For hash, we use
        // canonical encoding.
        //
        // Network encoding: 'type || rlp([tx_payload_body, blobs, commitments, proofs])'
        // Canonical encoding: 'type || rlp(tx_payload_body)', where 'tx_payload_body' is a list of tx fields with
        // signature values.
        //
        // See: https://eips.ethereum.org/EIPS/eip-4844#networking
        val fieldsWithSignatureSize = rlpFieldsWithSignatureSize(signature)
        when {
            hashEncoding || sidecar == null -> {
                rlp.encodeList(fieldsWithSignatureSize) {
                    rlpEncodeFields(this)
                    signature?.rlpEncode(this)
                }
            }

            else -> {
                rlp.encodeList(sizeOfList(fieldsWithSignatureSize) + sidecar.rlpSize()) {
                    rlp.encodeList(fieldsWithSignatureSize) {
                        rlpEncodeFields(this)
                        signature?.rlpEncode(this)
                    }

                    sidecar.rlpEncode(rlp)
                }
            }
        }

        return@with
    }

    private fun rlpEncodeFields(rlp: RlpEncoder) {
        rlp.encode(chainId)
        rlp.encode(nonce)
        rlp.encode(gasTipCap)
        rlp.encode(gasFeeCap)
        rlp.encode(gas)
        rlp.encode(to)
        rlp.encode(value)
        rlp.encode(data)
        rlp.encodeList(accessList)
        rlp.encode(blobFeeCap)
        rlp.encodeList(blobVersionedHashes)
    }

    override fun rlpEnvelopedSize(signature: Signature?, hashEncoding: Boolean): Int = with(RlpEncoder) {
        var size = 1
        when {
            hashEncoding || sidecar == null -> {
                size += sizeOfList(rlpFieldsWithSignatureSize(signature))
            }
            else -> {
                val fieldsListSize = sizeOfList(rlpFieldsWithSignatureSize(signature))
                size += sizeOfList(fieldsListSize + sidecar.rlpSize())
            }
        }

        return size
    }

    private fun rlpFieldsWithSignatureSize(signature: Signature?): Int = with(RlpEncoder) {
        val size = sizeOf(chainId) +
            sizeOf(nonce) +
            sizeOf(gasTipCap) +
            sizeOf(gasFeeCap) +
            sizeOf(gas) +
            sizeOf(to) +
            sizeOf(value) +
            sizeOf(data) +
            sizeOfList(accessList) +
            sizeOf(blobFeeCap) +
            sizeOfList(blobVersionedHashes)

        if (signature != null) {
            return size + signature.rlpSize()
        }

        return size
    }

    data class Sidecar(
        val blobs: List<Bytes>,
        val commitments: List<Bytes>,
        val proofs: List<Bytes>,
    ) : RlpEncodable {
        init {
            if (blobs.size != commitments.size || blobs.size != proofs.size) {
                throw IllegalArgumentException("blobs, commitments, and proofs must be the same size")
            }
            if (blobs.any { it.size != BLOB_LENGTH }) {
                throw IllegalArgumentException("blobs must be $BLOB_LENGTH bytes long")
            }
            if (commitments.any { it.size != COMMITMENT_LENGTH }) {
                throw IllegalArgumentException("commitments must be $COMMITMENT_LENGTH bytes long")
            }
            if (proofs.any { it.size != PROOF_LENGTH }) {
                throw IllegalArgumentException("proofs must be $PROOF_LENGTH bytes long")
            }
        }

        /**
         * The versioned hashes of the blobs in this sidecar.
         * */
        val versionedHashes by lazy(LazyThreadSafetyMode.NONE) {
            commitments.map { Hash(Hashing.blobVersionedHash(it.asByteArray())) }
        }

        override fun rlpEncode(rlp: RlpEncoder) {
            rlp.encodeList(blobs)
            rlp.encodeList(commitments)
            rlp.encodeList(proofs)
        }

        override fun rlpSize(): Int {
            return with(RlpEncoder) { sizeOfList(blobs) + sizeOfList(commitments) + sizeOfList(proofs) }
        }

        companion object : RlpDecodable<Sidecar> {
            const val BLOB_LENGTH = 131072
            const val COMMITMENT_LENGTH = 48
            const val PROOF_LENGTH = 48

            override fun rlpDecode(rlp: RlpDecoder): Sidecar? {
                return Sidecar(
                    blobs = rlp.decodeAsListOrNull(Bytes) ?: return null,
                    commitments = rlp.decodeAsListOrNull(Bytes) ?: return null,
                    proofs = rlp.decodeAsListOrNull(Bytes) ?: return null,
                )
            }
        }
    }

    companion object : RlpDecodable<TxBlob> {
        const val GAS_PER_BLOB = 1L shl 17

        @JvmStatic
        override fun rlpDecode(rlp: RlpDecoder): TxBlob? {
            return TxBlob(
                chainId = rlp.decodeLongOrElse { return null },
                nonce = rlp.decodeLongOrElse { return null },
                gasTipCap = rlp.decodeBigIntegerOrNull() ?: return null,
                gasFeeCap = rlp.decodeBigIntegerOrNull() ?: return null,
                gas = rlp.decodeLongOrElse { return null },
                to = rlp.decodeOrNull(Address) ?: return null,
                value = rlp.decodeBigIntegerOrNull() ?: return null,
                data = rlp.decodeOrNull(Bytes)?.takeIf { it.size > 0 },
                accessList = rlp.decodeAsListOrNull(AccessList.Item) ?: return null,
                blobFeeCap = rlp.decodeBigIntegerOrNull() ?: return null,
                blobVersionedHashes = rlp.decodeAsListOrNull(Hash) ?: return null,
            )
        }
    }
}
