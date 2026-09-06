package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.ethers.solana.instruction.ComputeBudgetProgram
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.serialization.SolanaMessageDecoder
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.utils.U32_MAX
import io.ethers.solana.utils.requireU64
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmSynthetic

/**
 * An incomplete transaction, to be compiled into a [SolanaTransactionUnsigned] of a chosen version.
 *
 * This is the Solana counterpart of `CallRequest`: every field is optional, so the same request can
 * be filled in by a provider, simulated, and finally compiled. Compilation derives the account list,
 * the message header and every account index, so a request cannot describe an inconsistent message.
 *
 * [computeUnitLimit] and [computeUnitPrice] are stated once and encoded per version, since legacy and
 * v0 carry them as ComputeBudget instructions while v1 carries them as inline config:
 *
 * ```kotlin
 * val request = SolanaTransactionRequest {
 *     feePayer(alice)
 *     blockhash(hash)
 *     instruction(SystemProgram.transfer(alice, bob, 1_000))
 *     computeUnitLimit(200_000)
 *     computeUnitPrice(5_000)
 * }
 *
 * val v0 = request.compileV0(lookupTables)
 * val v1 = request.compileV1()
 * ```
 */
class SolanaTransactionRequest() {
    constructor(other: SolanaTransactionRequest) : this() {
        this.feePayer = other.feePayer
        this.blockhash = other.blockhash
        this.instructions = other.instructions
        this.computeUnitLimit = other.computeUnitLimit
        this.computeUnitPrice = other.computeUnitPrice
        this.loadedAccountsDataSizeLimit = other.loadedAccountsDataSizeLimit
        this.heapSize = other.heapSize
    }

    // make property setters unavailable from Java since we provide custom chained functions
    var feePayer: SolanaAddress? = null
        @JvmSynthetic set

    /** Absent until set, so a provider can fill it in just before signing. */
    var blockhash: SolanaBlockhash? = null
        @JvmSynthetic set

    var instructions: List<Instruction> = emptyList()
        @JvmSynthetic set

    /** Compute units the transaction may consume, encoded per version. */
    var computeUnitLimit: Long? = null
        @JvmSynthetic set(value) {
            require(value == null || value in 0..U32_MAX) { "Compute unit limit must fit an unsigned 32-bit integer" }
            field = value
        }

    /** Micro-lamports per compute unit, as the ComputeBudget program states it. */
    var computeUnitPrice: BigInteger? = null
        @JvmSynthetic set(value) {
            value?.let(::requireU64)
            field = value
        }

    /** Combined size of the accounts the transaction may load, encoded per version. */
    var loadedAccountsDataSizeLimit: Long? = null
        @JvmSynthetic set(value) {
            require(value == null || value in 0..U32_MAX) { "Loaded accounts data size limit must fit an unsigned 32-bit integer" }
            field = value
        }

    /** Heap space the transaction may use, encoded per version. */
    var heapSize: Long? = null
        @JvmSynthetic set(value) {
            require(value == null || value in 0..U32_MAX) { "Heap size must fit an unsigned 32-bit integer" }
            field = value
        }

    fun feePayer(feePayer: SolanaAddress?) = apply { this.feePayer = feePayer }
    fun blockhash(blockhash: SolanaBlockhash?) = apply { this.blockhash = blockhash }
    fun instructions(instructions: List<Instruction>) = apply { this.instructions = instructions }
    fun instruction(instruction: Instruction) = apply { this.instructions += instruction }
    fun computeUnitLimit(computeUnitLimit: Long?) = apply { this.computeUnitLimit = computeUnitLimit }
    fun computeUnitPrice(computeUnitPrice: BigInteger?) = apply { this.computeUnitPrice = computeUnitPrice }
    fun computeUnitPrice(computeUnitPrice: Long) = apply { this.computeUnitPrice = bigIntegerOf(computeUnitPrice) }
    fun loadedAccountsDataSizeLimit(loadedAccountsDataSizeLimit: Long?) = apply { this.loadedAccountsDataSizeLimit = loadedAccountsDataSizeLimit }
    fun heapSize(heapSize: Long?) = apply { this.heapSize = heapSize }

    /**
     * Compile a legacy message. [computeUnitLimit] and [computeUnitPrice] replace any ComputeBudget
     * instruction that sets the same value, and are prepended otherwise.
     */
    fun tryCompileLegacy(): Result<SolanaTxLegacy, SolanaTransactionError> {
        val payer = feePayer ?: return Result.failure(SolanaTransactionError.MissingFeePayer)
        val hash = blockhash ?: return Result.failure(SolanaTransactionError.MissingBlockhash)
        return SolanaTxLegacy.tryCompile(payer, hash, withComputeBudgetInstructions())
    }

    fun compileLegacy(): SolanaTxLegacy = tryCompileLegacy().unwrap()

    /**
     * Compile a v0 message, moving accounts covered by [lookupTables] out of the inline account list.
     * Compute budget is encoded as for [tryCompileLegacy].
     */
    @JvmOverloads
    fun tryCompileV0(lookupTables: List<AddressLookupTableAccount> = emptyList()): Result<SolanaTxV0, SolanaTransactionError> {
        val payer = feePayer ?: return Result.failure(SolanaTransactionError.MissingFeePayer)
        val hash = blockhash ?: return Result.failure(SolanaTransactionError.MissingBlockhash)
        return SolanaTxV0.tryCompile(payer, hash, withComputeBudgetInstructions(), lookupTables)
    }

    @JvmOverloads
    fun compileV0(lookupTables: List<AddressLookupTableAccount> = emptyList()): SolanaTxV0 = tryCompileV0(lookupTables).unwrap()

    /**
     * Compile a v1 message. ComputeBudget instructions do not configure v1, so recognised ones are
     * translated into the inline config and dropped; whatever this library cannot decode is left in
     * place rather than silently discarded.
     */
    fun tryCompileV1(): Result<SolanaTxV1, SolanaTransactionError> {
        val payer = feePayer ?: return Result.failure(SolanaTransactionError.MissingFeePayer)
        val hash = blockhash ?: return Result.failure(SolanaTransactionError.MissingBlockhash)

        val translated = ComputeBudgetSettings.decode(instructions)
        val limit = computeUnitLimit ?: translated.computeUnitLimit
        val price = computeUnitPrice ?: translated.computeUnitPrice
        val priorityFee = when {
            price == null || price.signum() == 0 -> null
            limit == null -> return Result.failure(
                SolanaTransactionError.InvalidMessage(
                    SolanaTransactionError.Reason.CONFIG,
                    "A compute unit price needs a compute unit limit to become a v1 priority fee",
                ),
            )
            // total lamports, rounding up, as the runtime charges it
            else -> bigIntegerOf(limit).multiply(price).add(bigIntegerOf(999999)).divide(bigIntegerOf(1000000))
        }

        val config = SolanaTransactionConfig(
            priorityFee = priorityFee,
            computeUnitLimit = limit,
            loadedAccountsDataSizeLimit = loadedAccountsDataSizeLimit ?: translated.loadedAccountsDataSizeLimit,
            heapSize = heapSize ?: translated.heapSize,
        )
        return SolanaTxV1.tryCompile(payer, hash, translated.remaining, config)
    }

    fun compileV1(): SolanaTxV1 = tryCompileV1().unwrap()

    /** Legacy and v0 encoding: the fields win over an instruction setting the same value. */
    private fun withComputeBudgetInstructions(): List<Instruction> {
        val limit = computeUnitLimit
        val price = computeUnitPrice
        val dataSize = loadedAccountsDataSizeLimit
        val heap = heapSize
        if (limit == null && price == null && dataSize == null && heap == null) return instructions

        val kept = instructions.filterNot {
            when (ComputeBudgetSettings.discriminant(it)) {
                ComputeBudgetSettings.REQUEST_HEAP_FRAME -> heap != null
                ComputeBudgetSettings.SET_UNIT_LIMIT -> limit != null
                ComputeBudgetSettings.SET_UNIT_PRICE -> price != null
                ComputeBudgetSettings.SET_LOADED_ACCOUNTS_DATA_SIZE_LIMIT -> dataSize != null
                else -> false
            }
        }
        // emitted in discriminant order, so a request always produces the same instruction sequence
        val prefix = ArrayList<Instruction>(4)
        if (heap != null) prefix.add(ComputeBudgetProgram.requestHeapFrame(heap))
        if (limit != null) prefix.add(ComputeBudgetProgram.setComputeUnitLimit(limit))
        if (price != null) prefix.add(ComputeBudgetProgram.setComputeUnitPrice(price))
        if (dataSize != null) prefix.add(ComputeBudgetProgram.setLoadedAccountsDataSizeLimit(dataSize))
        return prefix + kept
    }

    companion object {
        @JvmSynthetic
        inline operator fun invoke(builder: SolanaTransactionRequest.() -> Unit): SolanaTransactionRequest {
            return SolanaTransactionRequest().apply(builder)
        }
    }
}

/**
 * ComputeBudget settings recovered from a request's instructions, with those instructions removed.
 *
 * Only the four documented ComputeBudget instructions are recognised. Anything else the program
 * accepts stays in [remaining], since translating what we cannot decode would change the message.
 */
internal class ComputeBudgetSettings(
    val remaining: List<Instruction>,
    val computeUnitLimit: Long?,
    val computeUnitPrice: BigInteger?,
    val loadedAccountsDataSizeLimit: Long?,
    val heapSize: Long?,
) {
    companion object {
        const val REQUEST_HEAP_FRAME = 1
        const val SET_UNIT_LIMIT = 2
        const val SET_UNIT_PRICE = 3
        const val SET_LOADED_ACCOUNTS_DATA_SIZE_LIMIT = 4

        /** The ComputeBudget discriminant this instruction carries, or -1 if it is not a decodable one. */
        fun discriminant(instruction: Instruction): Int {
            if (instruction.programId != Programs.COMPUTE_BUDGET || instruction.data.isEmpty) return -1
            val expected = when (instruction.data[0].toInt()) {
                REQUEST_HEAP_FRAME, SET_UNIT_LIMIT, SET_LOADED_ACCOUNTS_DATA_SIZE_LIMIT -> 5
                SET_UNIT_PRICE -> 9
                else -> return -1
            }
            return if (instruction.data.size == expected) instruction.data[0].toInt() else -1
        }

        fun decode(instructions: List<Instruction>): ComputeBudgetSettings {
            var limit: Long? = null
            var price: BigInteger? = null
            var dataSize: Long? = null
            var heap: Long? = null
            val remaining = instructions.filter { instruction ->
                val discriminant = discriminant(instruction)
                if (discriminant < 0) return@filter true
                val decoder = SolanaMessageDecoder(instruction.data.asByteArray())
                decoder.readByte()
                when (discriminant) {
                    REQUEST_HEAP_FRAME -> heap = decoder.readUnsignedLittleEndian(4).toLong()
                    SET_UNIT_LIMIT -> limit = decoder.readUnsignedLittleEndian(4).toLong()
                    SET_UNIT_PRICE -> price = decoder.readUnsignedLittleEndian(8)
                    SET_LOADED_ACCOUNTS_DATA_SIZE_LIMIT -> dataSize = decoder.readUnsignedLittleEndian(4).toLong()
                }
                false
            }
            return ComputeBudgetSettings(remaining, limit, price, dataSize, heap)
        }
    }
}
