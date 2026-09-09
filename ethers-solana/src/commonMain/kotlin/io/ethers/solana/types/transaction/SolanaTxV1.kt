package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.serialization.SolanaMessageDecoder
import io.ethers.solana.serialization.SolanaMessageEncoder
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaBytes
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.utils.littleEndian
import io.ethers.solana.utils.requireU64
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
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
     * message. [tryCreate] reports the same failure as a value, without building an exception.
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

    override fun withNewBlockhash(blockhash: SolanaBlockhash): SolanaTxV1 = SolanaTxV1(header, accounts, blockhash, instructions, config)

    /** Replace inline requests; signatures must be collected again for the new payload. */
    fun withConfig(config: SolanaTransactionConfig): SolanaTxV1 = SolanaTxV1(header, accounts, recentBlockhash, instructions, config)

    override fun envelopeSize(): Long = envelopeSize(header, accounts, instructions, config)

    /** V1 puts the signature slots after the message, unlike the legacy and v0 envelopes. */
    override fun encodeEnvelope(signatures: List<SolanaSignature?>): ByteArray {
        val encoder = SolanaMessageEncoder().writeBytes(serializeMessage())
        signatures.forEach { encoder.writeBytes(it?.asByteArray() ?: ByteArray(64)) }
        return encoder.toByteArray()
    }

    override fun serializeMessage(): ByteArray {
        val encoder = SolanaMessageEncoder().writeByte(129)
            .writeByte(header.requiredSignatures).writeByte(header.readonlySignedAccounts).writeByte(header.readonlyUnsignedAccounts)
            .writeBytes(littleEndian(bigIntegerOf(config.mask), 4)).writeBytes(recentBlockhash.asByteArray())
            .writeByte(instructions.size).writeByte(accounts.size)
        accounts.forEach { encoder.writeBytes(it.asByteArray()) }
        config.priorityFee?.let { encoder.writeBytes(littleEndian(it, 8)) }
        if (config.computeUnitLimit != null) {
            encoder.writeBytes(littleEndian(bigIntegerOf(config.computeUnitLimit), 4))
        }
        if (config.loadedAccountsDataSizeLimit != null) {
            encoder.writeBytes(littleEndian(bigIntegerOf(config.loadedAccountsDataSizeLimit), 4))
        }
        if (config.heapSize != null) {
            encoder.writeBytes(littleEndian(bigIntegerOf(config.heapSize), 4))
        }
        instructions.forEach {
            encoder.writeByte(it.programIdIndex).writeByte(it.accounts.size).writeBytes(littleEndian(bigIntegerOf(it.data.size), 2))
        }
        instructions.forEach {
            it.accounts.forEach(encoder::writeByte)
            encoder.writeBytes(it.data.asByteArray())
        }
        return encoder.toByteArray()
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

        /** As the constructor, reporting the reason the fields are invalid instead of throwing. */
        @JvmStatic
        fun tryCreate(
            header: MessageHeader,
            accounts: List<SolanaAddress>,
            recentBlockhash: SolanaBlockhash,
            instructions: List<MessageInstruction>,
            config: SolanaTransactionConfig,
        ): Result<SolanaTxV1, SolanaTransactionError> {
            validate(header, accounts, instructions, config)?.let { return Result.failure(it) }
            return Result.success(SolanaTxV1(header, accounts, recentBlockhash, instructions, config, validated = true))
        }

        /** The version prefix has already been read. Leaves any trailing signature bytes for the envelope reader. */
        internal fun decodeBody(decoder: SolanaMessageDecoder): SolanaTxV1 = with(decoder) {
            val header = MessageHeader(readByte(), readByte(), readByte())
            val mask = readUnsignedLittleEndian(4).toLong()
            if (mask and 31L != mask) {
                throw SolanaTransactionError.InvalidMessage(SolanaTransactionError.Reason.CONFIG, "Unsupported v1 config mask $mask").toException()
            }
            if (mask and 3L != 0L && mask and 3L != 3L) {
                throw SolanaTransactionError.InvalidMessage(SolanaTransactionError.Reason.CONFIG, "Both priority-fee config bits must be set").toException()
            }
            val blockhash = SolanaBlockhash(readBytes(32))
            val instructionCount = readByte()
            val accountCount = readByte()
            if (instructionCount > 64) {
                throw SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.INSTRUCTIONS, instructionCount, 0..64).toException()
            }
            if (accountCount !in 1..64) {
                throw SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.ACCOUNTS, accountCount, 1..64).toException()
            }
            if (header.requiredSignatures !in 1..12) {
                throw SolanaTransactionError.CountOutOfRange(SolanaTransactionError.Limit.SIGNERS, header.requiredSignatures, 1..12).toException()
            }
            val accounts = List(accountCount) { SolanaAddress(readBytes(32)) }
            val config = SolanaTransactionConfig(
                priorityFee = if (mask and 3L != 0L) readUnsignedLittleEndian(8) else null,
                computeUnitLimit = if (mask and 4L != 0L) readUnsignedLittleEndian(4).toLong() else null,
                loadedAccountsDataSizeLimit = if (mask and 8L != 0L) readUnsignedLittleEndian(4).toLong() else null,
                heapSize = if (mask and 16L != 0L) readUnsignedLittleEndian(4).toLong() else null,
            )
            val headers = List(instructionCount) { Triple(readByte(), readByte(), readUnsignedLittleEndian(2).toInt()) }
            val instructions = headers.map { (program, count, size) ->
                MessageInstruction(program, List(count) { readByte() }, SolanaBytes.fromBytes(readBytes(size)))
            }
            SolanaTxV1(header, accounts, blockhash, instructions, config)
        }

        @JvmStatic
        fun compile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instruction: Instruction, config: SolanaTransactionConfig): SolanaTxV1 = compile(feePayer, blockhash, listOf(instruction), config)

        /** As [compile], returning the reason it could not be compiled instead of throwing. */
        @JvmStatic
        fun tryCompile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instruction: Instruction, config: SolanaTransactionConfig): Result<SolanaTxV1, SolanaTransactionError> = tryCompile(feePayer, blockhash, listOf(instruction), config)

        /** As [compile], returning the reason it could not be compiled instead of throwing. */
        @JvmStatic
        fun tryCompile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instructions: List<Instruction>, config: SolanaTransactionConfig): Result<SolanaTxV1, SolanaTransactionError> = compileMessage(feePayer, blockhash, instructions, emptyList())
            .andThen { tryCreate(it.header, it.accounts, it.recentBlockhash, it.instructions, config) }

        @JvmStatic
        fun compile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instructions: List<Instruction>, config: SolanaTransactionConfig): SolanaTxV1 = tryCompile(feePayer, blockhash, instructions, config).unwrap()
    }
}
