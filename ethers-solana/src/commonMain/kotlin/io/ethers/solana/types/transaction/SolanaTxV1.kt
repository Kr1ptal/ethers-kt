package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.serialization.SolanaMessageDecoder
import io.ethers.solana.serialization.SolanaMessageEncoder
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
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
 */
class SolanaTxV1(
    override val header: MessageHeader,
    override val accounts: List<SolanaAddress>,
    override val recentBlockhash: SolanaBlockhash,
    override val instructions: List<CompiledInstruction>,
    val config: SolanaTransactionConfig,
) : SolanaTransactionUnsigned {
    override val type: SolanaTxType get() = SolanaTxType.V1

    init {
        validateMessage(header, accounts, instructions, emptyList())
        validate()?.let { throw it.toException() }
    }

    private fun validate(): SolanaTransactionError? {
        if (config.otherFields.isNotEmpty()) {
            return SolanaTransactionError.InvalidConfig("otherFields", "Cannot compile unknown transaction config fields")
        }
        if (header.requiredSignatures > 12) return SolanaTransactionError.InvalidSignerCount(header.requiredSignatures, 12)
        if (accounts.size > 64) return SolanaTransactionError.TooManyAccounts(accounts.size, 64)
        if (instructions.size > 64) return SolanaTransactionError.TooManyInstructions(instructions.size, 64)
        instructions.forEachIndexed { index, instruction ->
            if (instruction.accounts.size > 255 || instruction.data.size > 65535) {
                return SolanaTransactionError.InstructionTooLarge(index, instruction.accounts.size, instruction.data.size)
            }
        }
        val heapSize = config.heapSize
        if (heapSize != null && (heapSize !in 32768L..262144L || heapSize % 1024L != 0L)) {
            return SolanaTransactionError.InvalidConfig("heapSize", "V1 heap size must be a multiple of 1 KiB in 32..256 KiB")
        }
        return envelopeSizeError(this, MAX_TRANSACTION_SIZE)
    }

    override fun withNewBlockhash(blockhash: SolanaBlockhash): SolanaTxV1 = SolanaTxV1(header, accounts, blockhash, instructions, config)

    /** Replace inline requests; signatures must be collected again for the new payload. */
    fun withConfig(config: SolanaTransactionConfig): SolanaTxV1 = SolanaTxV1(header, accounts, recentBlockhash, instructions, config)

    override fun envelopeSize(): Long = 42L + accounts.size * 32L + config.wireSize +
        instructions.sumOf { 4L + it.accounts.size + it.data.size } + header.requiredSignatures * 64L

    /** V1 puts the signature slots after the message, unlike the legacy and v0 envelopes. */
    override fun serializeEnvelope(signatures: List<SolanaSignature?>): ByteArray {
        val encoder = SolanaMessageEncoder().writeBytes(serializeMessage())
        signatures.forEach { encoder.writeBytes(it?.toByteArray() ?: ByteArray(64)) }
        return encoder.toByteArray()
    }

    override fun serializeMessage(): ByteArray {
        val encoder = SolanaMessageEncoder().writeByte(129)
            .writeByte(header.requiredSignatures).writeByte(header.readonlySignedAccounts).writeByte(header.readonlyUnsignedAccounts)
            .writeBytes(littleEndian(bigIntegerOf(config.mask), 4)).writeBytes(recentBlockhash.toByteArray())
            .writeByte(instructions.size).writeByte(accounts.size)
        accounts.forEach { encoder.writeBytes(it.toByteArray()) }
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
            encoder.writeBytes(it.data)
        }
        return encoder.toByteArray()
    }

    override fun estimateFee(lamportsPerSignature: BigInteger): BigInteger = requireU64(lamportsPerSignature).multiply(bigIntegerOf(header.requiredSignatures)).add(config.priorityFee ?: bigIntegerOf(0))

    companion object {
        const val MAX_TRANSACTION_SIZE: Int = 4096

        /** The version prefix has already been read. Leaves any trailing signature bytes for the envelope reader. */
        internal fun decodeBody(decoder: SolanaMessageDecoder): SolanaTxV1 = with(decoder) {
            val header = MessageHeader(readByte(), readByte(), readByte())
            val mask = readUnsignedLittleEndian(4).toLong()
            if (mask and 31L != mask) throw SolanaTransactionError.InvalidConfig("mask", "Unsupported v1 config mask").toException()
            if (mask and 3L != 0L && mask and 3L != 3L) {
                throw SolanaTransactionError.InvalidConfig("mask", "Both priority-fee config bits must be set").toException()
            }
            val blockhash = SolanaBlockhash(readBytes(32))
            val instructionCount = readByte()
            val accountCount = readByte()
            if (instructionCount > 64) throw SolanaTransactionError.TooManyInstructions(instructionCount, 64).toException()
            if (accountCount !in 1..64) throw SolanaTransactionError.TooManyAccounts(accountCount, 64).toException()
            if (header.requiredSignatures !in 1..12) throw SolanaTransactionError.InvalidSignerCount(header.requiredSignatures, 12).toException()
            val accounts = List(accountCount) { SolanaAddress(readBytes(32)) }
            val config = SolanaTransactionConfig(
                priorityFee = if (mask and 3L != 0L) readUnsignedLittleEndian(8) else null,
                computeUnitLimit = if (mask and 4L != 0L) readUnsignedLittleEndian(4).toLong() else null,
                loadedAccountsDataSizeLimit = if (mask and 8L != 0L) readUnsignedLittleEndian(4).toLong() else null,
                heapSize = if (mask and 16L != 0L) readUnsignedLittleEndian(4).toLong() else null,
            )
            val headers = List(instructionCount) { Triple(readByte(), readByte(), readUnsignedLittleEndian(2).toInt()) }
            val instructions = headers.map { (program, count, size) ->
                CompiledInstruction(program, List(count) { readByte() }, readBytes(size))
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
        fun tryCompile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instructions: List<Instruction>, config: SolanaTransactionConfig): Result<SolanaTxV1, SolanaTransactionError> = catchTransactionError { compile(feePayer, blockhash, instructions, config) }

        @JvmStatic
        fun compile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instructions: List<Instruction>, config: SolanaTransactionConfig): SolanaTxV1 {
            val fields = compileMessage(feePayer, blockhash, instructions, emptyList())
            return SolanaTxV1(fields.header, fields.accounts, fields.recentBlockhash, fields.instructions, config)
        }
    }
}
