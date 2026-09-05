package io.ethers.solana.types.transaction

import io.ethers.solana.serialization.SolanaMessageDecoder
import io.ethers.solana.serialization.SolanaMessageEncoder
import io.ethers.solana.signers.SolanaSigner
import io.ethers.solana.types.Blockhash
import io.ethers.solana.types.Programs
import io.ethers.solana.types.Signature
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.utils.requireU64
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlin.io.encoding.Base64
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/** Immutable signed or partially signed transaction. Message changes always discard signatures. */
class SolanaTransaction private constructor(val message: TransactionMessage, signatures: List<Signature?>) {
    private val signatureSlots = signatures.toList()
    val signatures: List<Signature?> get() = signatureSlots.toList()
    val isFullySigned: Boolean get() = signatureSlots.all { it != null }
    constructor(message: TransactionMessage) : this(message, List(message.header.requiredSignatures) { null })

    fun sign(signer: SolanaSigner): SolanaTransaction = addSignature(signer.publicKey, signer.signMessage(message.serialize()))

    fun addSignature(signer: SolanaAddress, signature: Signature): SolanaTransaction {
        val index = message.signers.indexOf(signer)
        require(index >= 0) { "Public key is not a required signer" }
        require(signer.verify(signature, message.serialize())) { "Invalid transaction signature" }
        return SolanaTransaction(message, signatureSlots.toMutableList().also { it[index] = signature })
    }

    fun withMessage(message: TransactionMessage): SolanaTransaction = SolanaTransaction(message)
    fun withNewBlockhash(blockhash: Blockhash): SolanaTransaction = withMessage(message.withNewBlockhash(blockhash))

    /** Serialize a submit-ready transaction. Use [serializePartial] for offline signing or simulation. */
    fun serialize(): ByteArray {
        require(isFullySigned) { "Missing required signatures" }
        return serializePartial()
    }

    fun serializePartial(): ByteArray {
        val encoder = SolanaMessageEncoder().writeShortVecLength(signatureSlots.size)
        signatureSlots.forEach { encoder.writeBytes(it?.toByteArray() ?: ByteArray(64)) }
        return encoder.writeBytes(message.serialize()).toByteArray()
    }

    fun toBase64(): String = Base64.encode(serialize())

    /**
     * Estimate base + priority fee in lamports. A nonzero price requires an explicit compute-unit limit;
     * runtime defaults depend on the invoked programs. The node's getFeeForMessage is authoritative.
     */
    fun estimateFee(lamportsPerSignature: BigInteger): BigInteger {
        requireU64(lamportsPerSignature)
        var units: BigInteger? = null
        var price = bigIntegerOf(0)
        message.instructions.filter { message.accounts[it.programIdIndex] == Programs.COMPUTE_BUDGET }.forEach {
            val decoder = SolanaMessageDecoder(it.data)
            when (decoder.readByte()) {
                2 -> {
                    units = decoder.readUnsignedLittleEndian(4)
                    decoder.requireDone()
                }
                3 -> {
                    price = decoder.readUnsignedLittleEndian(8)
                    decoder.requireDone()
                }
            }
        }
        require(price.signum() == 0 || units != null) { "Specify a compute-unit limit or query getFeeForMessage" }
        val priority = (units ?: bigIntegerOf(0)).multiply(price).add(bigIntegerOf(999999)).divide(bigIntegerOf(1000000))
        return lamportsPerSignature.multiply(bigIntegerOf(signatureSlots.size)).add(priority)
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun deserialize(bytes: ByteArray, allowPartial: Boolean = false): SolanaTransaction {
            val decoder = SolanaMessageDecoder(bytes)
            val count = decoder.readShortVecLength()
            require(count in 1..127) { "Invalid signature count" }
            val signatures = List(count) {
                val signature = decoder.readBytes(64)
                if (signature.all { it == 0.toByte() }) null else Signature(signature)
            }
            val message = TransactionMessage.deserialize(decoder.readBytes(decoder.remaining))
            require(message.header.requiredSignatures == count) { "Signature count does not match message" }
            require(allowPartial || signatures.all { it != null }) { "Missing required signatures" }
            val serializedMessage = message.serialize()
            signatures.forEachIndexed { index, signature ->
                require(signature == null || message.signers[index].verify(signature, serializedMessage)) { "Invalid transaction signature" }
            }
            return SolanaTransaction(message, signatures)
        }
        @JvmStatic
        @JvmOverloads
        fun fromBase64(encoded: String, allowPartial: Boolean = false): SolanaTransaction = deserialize(Base64.decode(encoded), allowPartial)
    }
}
