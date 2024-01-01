package io.ethers.core.types.transaction

import io.ethers.core.types.AccessList
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
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
class TxBlob(
    override val to: Address,
    override val value: BigInteger,
    override val nonce: Long,
    override val gas: Long,
    override val gasFeeCap: BigInteger,
    override val gasTipCap: BigInteger,
    override val data: Bytes?,
    override val chainId: Long,
    override var accessList: List<AccessList.Item>?,
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
        accessList: List<AccessList.Item>?,
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
        if (gasFeeCap < gasTipCap) {
            throw IllegalArgumentException("gasFeeCap must be greater than or equal to gasTipCap")
        }
    }

    override val gasPrice: BigInteger
        get() = gasFeeCap

    override val type: TxType
        get() = TxType.BLOB

    override val blobGas: Long
        get() = GAS_PER_BLOB * blobVersionedHashes.size.toLong()

    override fun rlpEncodeFields(rlp: RlpEncoder) {
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

    /**
     * Copy or override `this` parameters into new [TxBlob] object.
     */
    fun copy(
        to: Address = this.to,
        value: BigInteger = this.value,
        nonce: Long = this.nonce,
        gas: Long = this.gas,
        gasFeeCap: BigInteger = this.gasFeeCap,
        gasTipCap: BigInteger = this.gasTipCap,
        data: Bytes? = this.data,
        chainId: Long = this.chainId,
        accessList: List<AccessList.Item>? = this.accessList,
        blobFeeCap: BigInteger = this.blobFeeCap,
        blobHashes: List<Hash> = this.blobVersionedHashes,
        sidecar: Sidecar? = this.sidecar,
    ): TxBlob {
        return TxBlob(
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
            blobVersionedHashes = blobHashes,
            sidecar = sidecar,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TxBlob

        if (to != other.to) return false
        if (value != other.value) return false
        if (nonce != other.nonce) return false
        if (gas != other.gas) return false
        if (gasFeeCap != other.gasFeeCap) return false
        if (gasTipCap != other.gasTipCap) return false
        if (data != other.data) return false
        if (chainId != other.chainId) return false
        if (accessList != other.accessList) return false
        if (blobFeeCap != other.blobFeeCap) return false
        if (blobVersionedHashes != other.blobVersionedHashes) return false
        if (sidecar != other.sidecar) return false

        return true
    }

    override fun hashCode(): Int {
        var result = to.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + nonce.hashCode()
        result = 31 * result + gas.hashCode()
        result = 31 * result + gasFeeCap.hashCode()
        result = 31 * result + gasTipCap.hashCode()
        result = 31 * result + (data?.hashCode() ?: 0)
        result = 31 * result + chainId.hashCode()
        result = 31 * result + (accessList?.hashCode() ?: 0)
        result = 31 * result + blobFeeCap.hashCode()
        result = 31 * result + blobVersionedHashes.hashCode()
        result = 31 * result + (sidecar?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "TxBlob(to=$to, value=$value, nonce=$nonce, gas=$gas, gasFeeCap=$gasFeeCap, gasTipCap=$gasTipCap, data=$data, chainId=$chainId, accessList=$accessList, blobFeeCap=$blobFeeCap, blobVersionedHashes=$blobVersionedHashes, sidecar=$sidecar)"
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
            commitments.map { Hash(Hashing.blobVersionedHash(it.value)) }
        }

        override fun rlpEncode(rlp: RlpEncoder) {
            rlp.encodeList(blobs)
            rlp.encodeList(commitments)
            rlp.encodeList(proofs)
        }

        companion object : RlpDecodable<Sidecar> {
            const val BLOB_LENGTH = 131072
            const val COMMITMENT_LENGTH = 48
            const val PROOF_LENGTH = 48

            override fun rlpDecode(rlp: RlpDecoder): Sidecar? {
                return Sidecar(
                    blobs = rlp.decodeAsList(Bytes) ?: return null,
                    commitments = rlp.decodeAsList(Bytes) ?: return null,
                    proofs = rlp.decodeAsList(Bytes) ?: return null,
                )
            }
        }
    }

    companion object : RlpDecodable<TxBlob> {
        const val GAS_PER_BLOB = 1L shl 17

        @JvmStatic
        override fun rlpDecode(rlp: RlpDecoder): TxBlob? {
            return TxBlob(
                chainId = rlp.decodeLong(),
                nonce = rlp.decodeLong(),
                gasTipCap = rlp.decodeBigIntegerElse(BigInteger.ZERO),
                gasFeeCap = rlp.decodeBigIntegerElse(BigInteger.ZERO),
                gas = rlp.decodeLong(),
                to = rlp.decode(Address) ?: return null,
                value = rlp.decodeBigIntegerElse(BigInteger.ZERO),
                data = rlp.decode(Bytes),
                accessList = rlp.decodeAsList(AccessList.Item),
                blobFeeCap = rlp.decodeBigIntegerElse(BigInteger.ZERO),
                blobVersionedHashes = rlp.decodeAsList(Hash) ?: emptyList(),
            )
        }
    }
}
