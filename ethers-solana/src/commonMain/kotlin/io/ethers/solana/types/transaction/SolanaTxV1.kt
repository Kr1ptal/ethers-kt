package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.serialization.SolanaMessageDecoder
import io.ethers.solana.serialization.SolanaMessageEncoder
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaBytes
import io.ethers.solana.types.SolanaSignature
import io.github.artificialpb.bignum.BigInteger
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmStatic

/**
 * Immutable SIMD-0385 v1 payload: inline accounts/config, followed by signatures in the envelope.
 * Requires v1 activation on the target cluster. Set compute and loaded-account limits explicitly;
 * omitted limits are zero. Address lookup tables are not supported.
 *
 * The constructor keeps the lists it is given rather than copying them, so pass immutable lists;
 * mutating them afterwards changes the transaction and invalidates its validated state.
 */
class SolanaTxV1 private constructor(
    override val header: MessageHeader,
    override val accounts: List<SolanaAddress>,
    override val recentBlockhash: SolanaBlockhash,
    override val instructions: List<MessageInstruction>,
    val config: SolanaTransactionConfig,
    validated: Boolean,
    message: ByteArray? = null,
) : SolanaTransactionUnsigned {
    override val type: SolanaTxType get() = SolanaTxType.V1

    // ComputeBudget instructions do not configure v1, so only the inline config is reported
    override val computeUnitLimit: Long? get() = config.computeUnitLimit
    override val computeUnitPrice: BigInteger? get() = null
    override val priorityFee: BigInteger? get() = config.priorityFee
    override val loadedAccountsDataSizeLimit: Long? get() = config.loadedAccountsDataSizeLimit
    override val heapSize: Long? get() = config.heapSize

    /**
     * Validate the fields, throwing [SolanaTransactionException] if they do not describe a legal
     * message. [create] reports the same failure as a value, without building an exception.
     */
    constructor(
        header: MessageHeader,
        accounts: List<SolanaAddress>,
        recentBlockhash: SolanaBlockhash,
        instructions: List<MessageInstruction>,
        config: SolanaTransactionConfig,
    ) : this(header, accounts, recentBlockhash, instructions, config, false)

    init {
        if (!validated) validate(header, accounts, instructions, config)?.let { throw it.toException() }
    }

    // the blockhash plays no part in validity, so the fields stay validated
    override fun withNewBlockhash(blockhash: SolanaBlockhash): SolanaTxV1 = SolanaTxV1(header, accounts, blockhash, instructions, config, validated = true)

    /** Replace inline requests; signatures must be collected again for the new payload. */
    fun withConfig(config: SolanaTransactionConfig): SolanaTxV1 = SolanaTxV1(header, accounts, recentBlockhash, instructions, config)

    override fun envelopeSize(): Long = envelopeSize(header, accounts, instructions, config)

    /** V1 puts the signature slots after the message, unlike the legacy and v0 envelopes. */
    override fun encodeEnvelope(signatures: List<SolanaSignature?>): ByteArray {
        val message = messageBytes()
        val encoder = SolanaMessageEncoder(message.size + 64 * signatures.size).writeBytes(message)
        signatures.forEach { if (it == null) encoder.writeZeros(64) else encoder.writeBytes(it.asByteArray()) }
        return encoder.finish()
    }

    // the encoded message is the canonical form of every field above, and is already cached
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SolanaTxV1

        return messageBytes().contentEquals(other.messageBytes())
    }

    override fun hashCode(): Int = messageBytes().contentHashCode()

    override fun toString(): String {
        return "SolanaTxV1(header=$header, accounts=$accounts, recentBlockhash=$recentBlockhash, instructions=$instructions, config=$config)"
    }

    // seeded with the wire bytes when decoded, which are byte for byte what encoding would produce
    @Volatile
    private var encodedMessage: ByteArray? = message

    internal fun messageBytes(): ByteArray = encodedMessage ?: encodeMessage().also { encodedMessage = it }

    override fun serializeMessage(): ByteArray = messageBytes().copyOf()

    private fun encodeMessage(): ByteArray {
        val size = envelopeSize() - 64L * header.requiredSignatures
        val encoder = SolanaMessageEncoder(size.toInt()).writeByte(129)
            .writeByte(header.requiredSignatures).writeByte(header.readonlySignedAccounts).writeByte(header.readonlyUnsignedAccounts)
            .writeLittleEndian(config.mask.toLong(), 4).writeBytes(recentBlockhash.asByteArray())
            .writeByte(instructions.size).writeByte(accounts.size)
        accounts.forEach { encoder.writeBytes(it.asByteArray()) }
        config.priorityFee?.let { encoder.writeLittleEndian(it, 8) }
        if (config.computeUnitLimit != null) {
            encoder.writeLittleEndian(config.computeUnitLimit, 4)
        }
        if (config.loadedAccountsDataSizeLimit != null) {
            encoder.writeLittleEndian(config.loadedAccountsDataSizeLimit, 4)
        }
        if (config.heapSize != null) {
            encoder.writeLittleEndian(config.heapSize, 4)
        }
        instructions.forEach {
            encoder.writeByte(it.programIdIndex).writeByte(it.accounts.size).writeLittleEndian(it.data.size.toLong(), 2)
        }
        instructions.forEach {
            encoder.writeU8List(it.accounts).writeBytes(it.data.asByteArray())
        }
        return encoder.finish()
    }

    companion object {
        const val MAX_TRANSACTION_SIZE: Int = 4096

        /** Envelope size from the raw fields, so it can run before a transaction is constructed. */
        internal fun envelopeSize(
            header: MessageHeader,
            accounts: List<SolanaAddress>,
            instructions: List<MessageInstruction>,
            config: SolanaTransactionConfig,
        ): Long = 42L + accounts.size * 32L + config.wireSize +
            instructions.sumOf { 4L + it.accounts.size + it.data.size } + header.requiredSignatures * 64L

        /** Every reason these fields cannot form a v1 message, or null if they can. */
        internal fun validate(
            header: MessageHeader,
            accounts: List<SolanaAddress>,
            instructions: List<MessageInstruction>,
            config: SolanaTransactionConfig,
        ): SolanaTransactionError? {
            messageError(header, accounts, instructions, emptyList())?.let { return it }
            if (config.otherFields.isNotEmpty()) {
                return SolanaTransactionError.InvalidMessage(SolanaTransactionError.Reason.CONFIG, "Cannot compile unknown transaction config fields")
            }
            if (header.requiredSignatures > 12) {
                return SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.SIGNERS, header.requiredSignatures, 1..12)
            }
            if (accounts.size > 64) return SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.ACCOUNTS, accounts.size, 1..64)
            if (instructions.size > 64) {
                return SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.INSTRUCTIONS, instructions.size, 0..64)
            }
            instructions.forEachIndexed { index, instruction ->
                if (instruction.accounts.size > 255) {
                    return SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.INSTRUCTION_ACCOUNTS, instruction.accounts.size, 0..255, index)
                }
                if (instruction.data.size > 65535) {
                    return SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.INSTRUCTION_DATA, instruction.data.size, 0..65535, index)
                }
            }
            val heapSize = config.heapSize
            if (heapSize != null && (heapSize !in 32768L..262144L || heapSize % 1024L != 0L)) {
                return SolanaTransactionError.InvalidMessage(
                    SolanaTransactionError.Reason.CONFIG,
                    "V1 heap size must be a multiple of 1 KiB in 32..256 KiB, got $heapSize",
                )
            }
            return envelopeSizeError(SolanaTxType.V1, envelopeSize(header, accounts, instructions, config), MAX_TRANSACTION_SIZE)
        }

        /** As the constructor, reporting the reason the fields are invalid as a value rather than throwing. */
        @JvmStatic
        fun create(
            header: MessageHeader,
            accounts: List<SolanaAddress>,
            recentBlockhash: SolanaBlockhash,
            instructions: List<MessageInstruction>,
            config: SolanaTransactionConfig,
        ): Result<SolanaTxV1, SolanaTransactionError> = create(header, accounts, recentBlockhash, instructions, config, null)

        private fun create(
            header: MessageHeader,
            accounts: List<SolanaAddress>,
            recentBlockhash: SolanaBlockhash,
            instructions: List<MessageInstruction>,
            config: SolanaTransactionConfig,
            message: ByteArray?,
        ): Result<SolanaTxV1, SolanaTransactionError> {
            validate(header, accounts, instructions, config)?.let { return Result.failure(it) }
            return Result.success(SolanaTxV1(header, accounts, recentBlockhash, instructions, config, validated = true, message))
        }

        /**
         * The version prefix at [start] has already been read. Leaves any trailing signature bytes for
         * the envelope reader, and keeps the message bytes rather than encoding them again.
         */
        internal fun decodeBody(decoder: SolanaMessageDecoder, start: Int): Result<SolanaTxV1, SolanaTransactionError> = with(decoder) {
            val header = MessageHeader(readByte(), readByte(), readByte())
            val mask = readLittleEndianLong(4)
            if (failed) return Result.failure(malformed())
            if (mask and 31L != mask) {
                return Result.failure(SolanaTransactionError.InvalidMessage(SolanaTransactionError.Reason.CONFIG, "Unsupported v1 config mask $mask"))
            }
            if (mask and 3L != 0L && mask and 3L != 3L) {
                return Result.failure(SolanaTransactionError.InvalidMessage(SolanaTransactionError.Reason.CONFIG, "Both priority-fee config bits must be set"))
            }
            val blockhash = SolanaBlockhash(readBytes(32))
            val instructionCount = readByte()
            val accountCount = readByte()
            if (failed) return Result.failure(malformed())
            if (instructionCount > 64) {
                return Result.failure(SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.INSTRUCTIONS, instructionCount, 0..64))
            }
            if (accountCount !in 1..64) {
                return Result.failure(SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.ACCOUNTS, accountCount, 1..64))
            }
            if (header.requiredSignatures !in 1..12) {
                return Result.failure(SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.SIGNERS, header.requiredSignatures, 1..12))
            }
            val accounts = readList(accountCount) { SolanaAddress(readBytes(32)) }
            val config = SolanaTransactionConfig(
                priorityFee = if (mask and 3L != 0L) readUnsignedLittleEndian(8) else null,
                computeUnitLimit = if (mask and 4L != 0L) readLittleEndianLong(4) else null,
                loadedAccountsDataSizeLimit = if (mask and 8L != 0L) readLittleEndianLong(4) else null,
                heapSize = if (mask and 16L != 0L) readLittleEndianLong(4) else null,
            )
            // program index, account count and data size of each instruction, which precede all their bodies
            val headers = IntArray(instructionCount * 3)
            for (i in 0 until instructionCount) {
                headers[3 * i] = readByte()
                headers[3 * i + 1] = readByte()
                headers[3 * i + 2] = readLittleEndianLong(2).toInt()
            }
            val instructions = readList(instructionCount) { i ->
                MessageInstruction(headers[3 * i], readU8List(headers[3 * i + 1]), SolanaBytes.fromBytes(readBytes(headers[3 * i + 2])))
            }
            if (failed) return Result.failure(malformed())
            return create(header, accounts, blockhash, instructions, config, copyFrom(start))
        }

        @JvmStatic
        fun compile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instruction: Instruction, config: SolanaTransactionConfig): Result<SolanaTxV1, SolanaTransactionError> = compile(feePayer, blockhash, listOf(instruction), config)

        @JvmStatic
        fun compile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instructions: List<Instruction>, config: SolanaTransactionConfig): Result<SolanaTxV1, SolanaTransactionError> = compileMessage(feePayer, blockhash, instructions, emptyList())
            .andThen { create(it.header, it.accounts, it.recentBlockhash, it.instructions, config) }
    }
}
