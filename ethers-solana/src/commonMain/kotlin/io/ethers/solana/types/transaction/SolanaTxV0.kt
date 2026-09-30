package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.ethers.core.unwrapOrReturn
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.serialization.SolanaMessageDecoder
import io.ethers.solana.serialization.SolanaMessageEncoder
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaSignature
import io.github.artificialpb.bignum.BigInteger
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * V0 transaction payload. Lookup table contents are supplied by the caller during compilation.
 *
 * The constructor keeps the lists it is given rather than copying them, so pass immutable lists;
 * mutating them afterwards changes the transaction and invalidates its validated state.
 */
class SolanaTxV0 private constructor(
    override val header: MessageHeader,
    override val accounts: List<SolanaAddress>,
    override val recentBlockhash: SolanaBlockhash,
    override val instructions: List<MessageInstruction>,
    override val addressLookupTables: List<CompiledAddressLookupTable>,
    validated: Boolean,
    message: ByteArray? = null,
) : SolanaTransactionUnsigned {
    override val type: SolanaTxType get() = SolanaTxType.V0

    // decoded once: reading the settings back means scanning and parsing the instructions
    @Volatile
    private var cachedComputeBudget: ComputeBudgetValues? = null
    private val computeBudget: ComputeBudgetValues get() = cachedComputeBudget ?: decodeComputeBudget(accounts, instructions).also { cachedComputeBudget = it }
    override val computeUnitLimit: Long? get() = computeBudget.computeUnitLimit
    override val computeUnitPrice: BigInteger? get() = computeBudget.computeUnitPrice
    override val priorityFee: BigInteger? get() = computeBudget.priorityFee
    override val loadedAccountsDataSizeLimit: Long? get() = computeBudget.loadedAccountsDataSizeLimit
    override val heapSize: Long? get() = computeBudget.heapSize

    /**
     * Validate the fields, throwing [SolanaTransactionException] if they do not describe a legal
     * message. [create] reports the same failure as a value, without building an exception.
     */
    @JvmOverloads
    constructor(
        header: MessageHeader,
        accounts: List<SolanaAddress>,
        recentBlockhash: SolanaBlockhash,
        instructions: List<MessageInstruction>,
        addressLookupTables: List<CompiledAddressLookupTable> = emptyList(),
    ) : this(header, accounts, recentBlockhash, instructions, addressLookupTables, false)

    init {
        if (!validated) validate(header, accounts, instructions, addressLookupTables)?.let { throw it.toException() }
    }

    // the blockhash plays no part in validity, so the fields stay validated
    override fun withNewBlockhash(blockhash: SolanaBlockhash): SolanaTxV0 = SolanaTxV0(header, accounts, blockhash, instructions, addressLookupTables, validated = true)

    // seeded with the wire bytes when decoded, which are byte for byte what encoding would produce
    @Volatile
    private var encodedMessage: ByteArray? = message

    internal fun messageBytes(): ByteArray = encodedMessage ?: encodeMessage().also { encodedMessage = it }

    override fun serializeMessage(): ByteArray = messageBytes().copyOf()

    private fun encodeMessage(): ByteArray {
        val size = envelopeSize() - shortVecSize(header.requiredSignatures) - 64L * header.requiredSignatures
        val encoder = SolanaMessageEncoder(size.toInt()).writeByte(128)
        encoder.writeMessageBody(this)
        encoder.writeShortVecLength(addressLookupTables.size)

        for ((key, writableIndexes, readonlyIndexes) in addressLookupTables) {
            encoder.writeBytes(key.asByteArray()).writeShortVecLength(writableIndexes.size).writeU8List(writableIndexes)
            encoder.writeShortVecLength(readonlyIndexes.size).writeU8List(readonlyIndexes)
        }
        return encoder.finish()
    }

    override fun envelopeSize(): Long = legacyEnvelopeSize(header, accounts, instructions, addressLookupTables)

    override fun encodeEnvelope(signatures: List<SolanaSignature?>): ByteArray = encodeSignaturesFirstEnvelope(this, signatures)

    // the encoded message is the canonical form of every field above, and is already cached
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SolanaTxV0

        return messageBytes().contentEquals(other.messageBytes())
    }

    override fun hashCode(): Int = messageBytes().contentHashCode()

    override fun toString(): String {
        return "SolanaTxV0(header=$header, accounts=$accounts, recentBlockhash=$recentBlockhash, instructions=$instructions, addressLookupTables=$addressLookupTables)"
    }

    companion object {
        const val MAX_TRANSACTION_SIZE: Int = SolanaTxLegacy.MAX_TRANSACTION_SIZE

        /** Every reason these fields cannot form a v0 message, or null if they can. */
        internal fun validate(
            header: MessageHeader,
            accounts: List<SolanaAddress>,
            instructions: List<MessageInstruction>,
            lookups: List<CompiledAddressLookupTable>,
        ): SolanaTransactionError? = messageError(header, accounts, instructions, lookups)
            ?: envelopeSizeError(SolanaTxType.V0, legacyEnvelopeSize(header, accounts, instructions, lookups), MAX_TRANSACTION_SIZE)

        /** As the constructor, reporting the reason the fields are invalid as a value rather than throwing. */
        @JvmStatic
        @JvmOverloads
        fun create(
            header: MessageHeader,
            accounts: List<SolanaAddress>,
            recentBlockhash: SolanaBlockhash,
            instructions: List<MessageInstruction>,
            addressLookupTables: List<CompiledAddressLookupTable> = emptyList(),
        ): Result<SolanaTxV0, SolanaTransactionError> = create(header, accounts, recentBlockhash, instructions, addressLookupTables, null)

        private fun create(
            header: MessageHeader,
            accounts: List<SolanaAddress>,
            recentBlockhash: SolanaBlockhash,
            instructions: List<MessageInstruction>,
            addressLookupTables: List<CompiledAddressLookupTable>,
            message: ByteArray?,
        ): Result<SolanaTxV0, SolanaTransactionError> {
            validate(header, accounts, instructions, addressLookupTables)?.let { return Result.failure(it) }
            return Result.success(SolanaTxV0(header, accounts, recentBlockhash, instructions, addressLookupTables, validated = true, message))
        }

        /** The version prefix at [start] has already been read. The message bytes are kept rather than encoded again. */
        internal fun decodeBody(decoder: SolanaMessageDecoder, start: Int): Result<SolanaTxV0, SolanaTransactionError> {
            val body = decoder.readMessageBody(decoder.readByte()).unwrapOrReturn { return Result.failure(it) }
            val lookups = decoder.readList(decoder.readShortVecLength()) {
                val key = SolanaAddress(decoder.readBytes(32))
                val writable = decoder.readU8List(decoder.readShortVecLength())
                val readonly = decoder.readU8List(decoder.readShortVecLength())
                CompiledAddressLookupTable(key, writable, readonly)
            }
            if (decoder.failed) return Result.failure(decoder.malformed())
            return create(body.header, body.accounts, body.recentBlockhash, body.instructions, lookups, decoder.copyFrom(start))
        }

        @JvmStatic
        @JvmOverloads
        fun compile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instruction: Instruction, lookupTables: List<AddressLookupTableAccount> = emptyList()): Result<SolanaTxV0, SolanaTransactionError> = compile(feePayer, blockhash, listOf(instruction), lookupTables)

        @JvmStatic
        @JvmOverloads
        fun compile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instructions: List<Instruction>, lookupTables: List<AddressLookupTableAccount> = emptyList()): Result<SolanaTxV0, SolanaTransactionError> = compileMessage(feePayer, blockhash, instructions, lookupTables)
            .andThen { create(it.header, it.accounts, it.recentBlockhash, it.instructions, it.lookups) }
    }
}
