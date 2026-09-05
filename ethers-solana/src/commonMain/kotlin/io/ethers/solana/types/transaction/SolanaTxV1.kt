package io.ethers.solana.types.transaction

import io.ethers.solana.instruction.Instruction
import io.ethers.solana.serialization.SolanaMessageEncoder
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
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
    accounts: List<SolanaAddress>,
    override val recentBlockhash: SolanaBlockhash,
    instructions: List<CompiledInstruction>,
    val config: SolanaTransactionConfig,
) : SolanaTransactionUnsigned {
    private val staticAccounts = accounts.toList()
    private val compiledInstructions = instructions.toList()
    override val accounts: List<SolanaAddress> get() = staticAccounts.toList()
    override val instructions: List<CompiledInstruction> get() = compiledInstructions.toList()
    override val type: SolanaTxType get() = SolanaTxType.V1

    init {
        validateMessage(header, staticAccounts, compiledInstructions, emptyList())
        require(config.otherFields.isEmpty()) { "Cannot compile unknown transaction config fields" }
        require(header.requiredSignatures <= 12) { "V1 supports at most 12 signatures" }
        require(staticAccounts.size <= 64) { "V1 supports at most 64 accounts" }
        require(compiledInstructions.size <= 64) { "V1 supports at most 64 instructions" }
        require(compiledInstructions.all { it.accounts.size <= 255 && it.data.size <= 65535 }) { "V1 instruction exceeds wire limits" }
        require(config.heapSize == null || (config.heapSize in 32768L..262144L && config.heapSize % 1024L == 0L)) { "V1 heap size must be a multiple of 1 KiB in 32..256 KiB" }
        val envelopeSize = 42L + staticAccounts.size * 32L + config.wireSize +
            compiledInstructions.sumOf { 4L + it.accounts.size + it.data.size } + header.requiredSignatures * 64L
        require(envelopeSize <= MAX_TRANSACTION_SIZE) { "V1 transaction exceeds $MAX_TRANSACTION_SIZE bytes" }
    }

    override fun withNewBlockhash(blockhash: SolanaBlockhash): SolanaTxV1 = SolanaTxV1(header, staticAccounts, blockhash, compiledInstructions, config)

    /** Replace inline requests; signatures must be collected again for the new payload. */
    fun withConfig(config: SolanaTransactionConfig): SolanaTxV1 = SolanaTxV1(header, staticAccounts, recentBlockhash, compiledInstructions, config)

    override fun serializeMessage(): ByteArray {
        val encoder = SolanaMessageEncoder().writeByte(129)
            .writeByte(header.requiredSignatures).writeByte(header.readonlySignedAccounts).writeByte(header.readonlyUnsignedAccounts)
            .writeBytes(littleEndian(bigIntegerOf(config.mask), 4)).writeBytes(recentBlockhash.toByteArray())
            .writeByte(compiledInstructions.size).writeByte(staticAccounts.size)
        staticAccounts.forEach { encoder.writeBytes(it.toByteArray()) }
        config.priorityFee?.let { encoder.writeBytes(littleEndian(it, 8)) }
        listOf(config.computeUnitLimit, config.loadedAccountsDataSizeLimit, config.heapSize).forEach {
            if (it != null) encoder.writeBytes(littleEndian(bigIntegerOf(it), 4))
        }
        compiledInstructions.forEach {
            encoder.writeByte(it.programIdIndex).writeByte(it.accounts.size).writeBytes(littleEndian(bigIntegerOf(it.data.size), 2))
        }
        compiledInstructions.forEach {
            it.accounts.forEach(encoder::writeByte)
            encoder.writeBytes(it.data)
        }
        return encoder.toByteArray()
    }

    override fun estimateFee(lamportsPerSignature: BigInteger): BigInteger = requireU64(lamportsPerSignature).multiply(bigIntegerOf(header.requiredSignatures)).add(config.priorityFee ?: bigIntegerOf(0))

    companion object {
        const val MAX_TRANSACTION_SIZE: Int = 4096

        @JvmStatic
        fun compile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instruction: Instruction, config: SolanaTransactionConfig): SolanaTxV1 = compile(feePayer, blockhash, listOf(instruction), config)

        @JvmStatic
        fun compile(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instructions: List<Instruction>, config: SolanaTransactionConfig): SolanaTxV1 {
            val fields = compileMessage(feePayer, blockhash, instructions, emptyList())
            return SolanaTxV1(fields.header, fields.accounts, fields.recentBlockhash, fields.instructions, config)
        }
    }
}
