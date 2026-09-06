package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.ethers.solana.serialization.SolanaMessageDecoder
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.utils.requireU64
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlin.io.encoding.Base64
import kotlin.jvm.JvmStatic

/** Common immutable properties of unsigned and fully signed Solana transactions. */
sealed interface SolanaTransaction {
    val type: SolanaTxType
    val header: MessageHeader
    val accounts: List<SolanaAddress>
    val recentBlockhash: SolanaBlockhash
    val instructions: List<CompiledInstruction>
    val feePayer: SolanaAddress get() = accounts.first()
    val signers: List<SolanaAddress> get() = accounts.take(header.requiredSignatures)

    /** The exact message bytes signed by Ed25519, without the signature envelope. */
    fun serializeMessage(): ByteArray

    /** Full transaction envelope, using zero-filled slots for missing signatures. Not necessarily submit-ready. */
    fun serializeForSimulation(): ByteArray

    /**
     * Estimate base + priority fee in lamports. A nonzero price requires an explicit compute-unit limit;
     * runtime defaults depend on the invoked programs. The node's getFeeForMessage is authoritative.
     */
    fun estimateFee(lamportsPerSignature: BigInteger): BigInteger {
        requireU64(lamportsPerSignature)
        var units: BigInteger? = null
        var price = bigIntegerOf(0)
        instructions.filter { accounts[it.programIdIndex] == Programs.COMPUTE_BUDGET }.forEach {
            val decoder = SolanaMessageDecoder(it.data.backing)
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
        return lamportsPerSignature.multiply(bigIntegerOf(header.requiredSignatures)).add(priority)
    }

    companion object {
        /**
         * Decode an unsigned or fully signed envelope. Partial signatures must be imported explicitly using
         * [SolanaTransactionSigned.Builder.deserializePartial] so they are not silently discarded.
         */
        @JvmStatic
        fun deserialize(bytes: ByteArray): SolanaTransaction {
            val (tx, signatures) = decodeTransactionEnvelope(bytes)
            return when {
                signatures.all { it == null } -> tx
                signatures.all { it != null } -> SolanaTransactionSigned(tx, signatures.map { requireNotNull(it) })
                else -> throw SolanaTransactionError.PartiallySigned(signatures.count { it == null }, signatures.size).toException()
            }
        }

        /** As [deserialize], returning the reason the bytes could not be decoded instead of throwing. */
        @JvmStatic
        fun tryDeserialize(bytes: ByteArray): Result<SolanaTransaction, SolanaTransactionError> = catchTransactionError { deserialize(bytes) }

        @JvmStatic
        fun fromBase64(encoded: String): SolanaTransaction = deserialize(Base64.decode(encoded))

        /** As [fromBase64], returning the reason the input could not be decoded instead of throwing. */
        @JvmStatic
        fun tryFromBase64(encoded: String): Result<SolanaTransaction, SolanaTransactionError> = catchTransactionError { fromBase64(encoded) }
    }
}
