package io.ethers.solana.types.transaction

import io.ethers.solana.signers.SolanaSigner
import io.ethers.solana.types.Blockhash
import io.ethers.solana.types.Programs
import io.ethers.solana.types.PublicKey
import io.ethers.solana.types.Signature
import io.ethers.solana.utils.BinaryReader
import io.ethers.solana.utils.BinaryWriter
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

    fun addSignature(signer: PublicKey, signature: Signature): SolanaTransaction {
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
        val writer = BinaryWriter().length(signatureSlots.size)
        signatureSlots.forEach { writer.bytes(it?.toByteArray() ?: ByteArray(64)) }
        return writer.bytes(message.serialize()).toByteArray()
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
            val reader = BinaryReader(it.data)
            when (reader.byte()) {
                2 -> {
                    units = reader.unsigned(4)
                    reader.requireDone()
                }
                3 -> {
                    price = reader.unsigned(8)
                    reader.requireDone()
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
            val reader = BinaryReader(bytes)
            val count = reader.length()
            require(count in 1..127) { "Invalid signature count" }
            val signatures = List(count) {
                val signature = reader.bytes(64)
                if (signature.all { it == 0.toByte() }) null else Signature(signature)
            }
            val message = TransactionMessage.deserialize(reader.bytes(reader.remaining))
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
