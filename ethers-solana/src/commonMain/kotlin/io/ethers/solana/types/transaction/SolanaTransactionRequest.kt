package io.ethers.solana.types.transaction

import io.ethers.core.Result
import io.ethers.core.unwrapOrReturn
import io.ethers.solana.instruction.ComputeBudgetProgram
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.instruction.SystemProgram
import io.ethers.solana.instruction.isAdvanceNonceData
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
        this.priorityFee = other.priorityFee
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

    /**
     * Total priority fee in lamports, taking strict precedence over [computeUnitPrice].
     *
     * V1 carries this exact total. Legacy and v0 can only price per compute unit, so the total is
     * turned into `ceil(priorityFee * 1_000_000 / computeUnitLimit)` micro-lamports, which the runtime
     * charges back at no less than the requested total; stating it there needs a compute unit limit.
     */
    var priorityFee: BigInteger? = null
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

    /**
     * Use [nonce] from [nonceAccount] as this transaction's blockhash and put its advance instruction
     * first. [authority] must sign the transaction. Replaces an existing leading nonce advance,
     * preserving every other instruction in order, so repeated calls do not add duplicate advances.
     *
     * This only updates the request; it does not fetch the nonce or submit a transaction. Subsequent
     * calls to [blockhash] or [instructions] replace those fields normally. Append instructions with
     * [instruction] to retain the nonce setup. Compilation keeps generated compute settings after it.
     */
    fun durableNonce(nonceAccount: SolanaAddress, authority: SolanaAddress, nonce: SolanaBlockhash) = apply {
        val first = instructions.firstOrNull()
        val remaining = if (first?.programId == Programs.SYSTEM && first.data.asByteArray().isAdvanceNonceData()) {
            instructions.drop(1)
        } else {
            instructions
        }
        blockhash = nonce
        instructions = listOf(SystemProgram.advanceNonceAccount(nonceAccount, authority)) + remaining
    }

    fun instructions(instructions: List<Instruction>) = apply { this.instructions = instructions }
    fun instruction(instruction: Instruction) = apply { this.instructions += instruction }
    fun computeUnitLimit(computeUnitLimit: Long?) = apply { this.computeUnitLimit = computeUnitLimit }
    fun computeUnitPrice(computeUnitPrice: BigInteger?) = apply { this.computeUnitPrice = computeUnitPrice }
    fun computeUnitPrice(computeUnitPrice: Long) = apply { this.computeUnitPrice = bigIntegerOf(computeUnitPrice) }
    fun priorityFee(priorityFee: BigInteger?) = apply { this.priorityFee = priorityFee }
    fun priorityFee(priorityFee: Long) = apply { this.priorityFee = bigIntegerOf(priorityFee) }
    fun loadedAccountsDataSizeLimit(loadedAccountsDataSizeLimit: Long?) = apply { this.loadedAccountsDataSizeLimit = loadedAccountsDataSizeLimit }
    fun heapSize(heapSize: Long?) = apply { this.heapSize = heapSize }

    /**
     * Compile to whichever of legacy and v0 encodes this request in fewer bytes, the counterpart of
     * `CallRequest.toUnsignedTransactionOrNull` choosing a transaction type from the fields that are set.
     *
     * V0 is chosen exactly when a lookup table earns its place, since an empty table list costs two
     * bytes more than legacy and a table is only ever selected when it pays for itself. Supply no
     * lookup tables and this always compiles legacy, so it is safe against a wallet or an RPC that
     * accepts nothing else: v0 can only appear once you have asked for it by passing tables. V1 is
     * never chosen for you: its envelope is larger, but it needs SIMD-0385 activation on the target
     * cluster, which is the caller's to know - ask for it with [compileV1].
     *
     * Other Solana clients make the version an explicit choice with no automatic path at all, and the
     * per-version [compileLegacy], [compileV0] and [compileV1] are that choice here, each
     * returning its own type.
     */
    @JvmOverloads
    fun compile(lookupTables: List<AddressLookupTableAccount> = emptyList()): Result<SolanaTransactionUnsigned, SolanaTransactionError> {
        val payer = feePayer ?: return Result.failure(SolanaTransactionError.MissingFeePayer)
        val hash = blockhash ?: return Result.failure(SolanaTransactionError.MissingBlockhash)
        val instructions = withComputeBudgetInstructions().unwrapOrReturn { return Result.failure(it) }
        // decide from the tables the compiler actually used, so a message that fits legacy but not an
        // empty-table v0 is not rejected by compiling v0 first
        return compileMessage(payer, hash, instructions, lookupTables).andThen { fields ->
            when {
                fields.lookups.isEmpty() -> SolanaTxLegacy.create(fields.header, fields.accounts, fields.recentBlockhash, fields.instructions)
                else -> SolanaTxV0.create(fields.header, fields.accounts, fields.recentBlockhash, fields.instructions, fields.lookups)
            }
        }
    }

    /**
     * Compile a legacy message. [computeUnitLimit] and [computeUnitPrice] replace any ComputeBudget
     * instruction that sets the same value, and are inserted before ordinary instructions, after a
     * leading nonce advance.
     */
    fun compileLegacy(): Result<SolanaTxLegacy, SolanaTransactionError> {
        val payer = feePayer ?: return Result.failure(SolanaTransactionError.MissingFeePayer)
        val hash = blockhash ?: return Result.failure(SolanaTransactionError.MissingBlockhash)
        val instructions = withComputeBudgetInstructions().unwrapOrReturn { return Result.failure(it) }
        return SolanaTxLegacy.compile(payer, hash, instructions)
    }

    /**
     * Compile a v0 message, moving accounts covered by [lookupTables] out of the inline account list.
     * Compute budget is encoded as for [compileLegacy].
     */
    @JvmOverloads
    fun compileV0(lookupTables: List<AddressLookupTableAccount> = emptyList()): Result<SolanaTxV0, SolanaTransactionError> {
        val payer = feePayer ?: return Result.failure(SolanaTransactionError.MissingFeePayer)
        val hash = blockhash ?: return Result.failure(SolanaTransactionError.MissingBlockhash)
        val instructions = withComputeBudgetInstructions().unwrapOrReturn { return Result.failure(it) }
        return SolanaTxV0.compile(payer, hash, instructions, lookupTables)
    }

    /**
     * Compile a v1 message. The inline config states the compute budget, so it is taken from this
     * request's own settings; ComputeBudget instructions are ordinary instructions to v1 and are
     * compiled as given.
     */
    fun compileV1(): Result<SolanaTxV1, SolanaTransactionError> {
        val payer = feePayer ?: return Result.failure(SolanaTransactionError.MissingFeePayer)
        val hash = blockhash ?: return Result.failure(SolanaTransactionError.MissingBlockhash)

        val limit = computeUnitLimit
        val price = computeUnitPrice
        val explicit = priorityFee
        val priorityFee = when {
            explicit != null -> explicit
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
            loadedAccountsDataSizeLimit = loadedAccountsDataSizeLimit,
            heapSize = heapSize,
        )
        return SolanaTxV1.compile(payer, hash, instructions, config)
    }

    /** Legacy and v0 encoding: the fields win over an instruction setting the same value. */
    private fun withComputeBudgetInstructions(): Result<List<Instruction>, SolanaTransactionError> {
        val limit = computeUnitLimit
        val price = perUnitPrice(limit).unwrapOrReturn { return Result.failure(it) }
        val dataSize = loadedAccountsDataSizeLimit
        val heap = heapSize
        if (limit == null && price == null && dataSize == null && heap == null) return Result.success(instructions)

        val kept = instructions.filterNot {
            when (ComputeBudgetSettings.discriminant(it)) {
                ComputeBudgetProgram.REQUEST_HEAP_FRAME -> heap != null
                ComputeBudgetProgram.SET_COMPUTE_UNIT_LIMIT -> limit != null
                ComputeBudgetProgram.SET_COMPUTE_UNIT_PRICE -> price != null
                ComputeBudgetProgram.SET_LOADED_ACCOUNTS_DATA_SIZE_LIMIT -> dataSize != null
                else -> false
            }
        }
        // emitted in discriminant order, so a request always produces the same instruction sequence
        val prefix = ArrayList<Instruction>(4)
        if (heap != null) prefix.add(ComputeBudgetProgram.requestHeapFrame(heap))
        if (limit != null) prefix.add(ComputeBudgetProgram.setComputeUnitLimit(limit))
        if (price != null) prefix.add(ComputeBudgetProgram.setComputeUnitPrice(price))
        if (dataSize != null) prefix.add(ComputeBudgetProgram.setLoadedAccountsDataSizeLimit(dataSize))
        // Durable nonce recognition requires the advance instruction to remain at index zero.
        val first = instructions.firstOrNull()
        if (first?.programId == Programs.SYSTEM && first.data.asByteArray().isAdvanceNonceData()) {
            return Result.success(listOf(first) + prefix + kept.drop(1))
        }
        return Result.success(prefix + kept)
    }

    /**
     * The micro-lamports-per-unit price legacy and v0 encode, honouring [priorityFee] over
     * [computeUnitPrice]. Rounds up, so the runtime never charges less than the requested total.
     */
    private fun perUnitPrice(limit: Long?): Result<BigInteger?, SolanaTransactionError> {
        val total = priorityFee ?: return Result.success(computeUnitPrice)
        val units = limit ?: ComputeBudgetSettings.decode(instructions).computeUnitLimit
            ?: return Result.failure(
                SolanaTransactionError.InvalidMessage(
                    SolanaTransactionError.Reason.CONFIG,
                    "A priority fee needs a compute unit limit to become a per-unit price",
                ),
            )
        if (units == 0L) return Result.success(bigIntegerOf(0))
        val scaled = total.multiply(bigIntegerOf(1000000)).add(bigIntegerOf(units - 1))
        return Result.success(scaled.divide(bigIntegerOf(units)))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SolanaTransactionRequest

        if (feePayer != other.feePayer) return false
        if (blockhash != other.blockhash) return false
        if (instructions != other.instructions) return false
        if (computeUnitLimit != other.computeUnitLimit) return false
        if (computeUnitPrice != other.computeUnitPrice) return false
        if (priorityFee != other.priorityFee) return false
        if (loadedAccountsDataSizeLimit != other.loadedAccountsDataSizeLimit) return false
        if (heapSize != other.heapSize) return false

        return true
    }

    override fun hashCode(): Int {
        var result = feePayer?.hashCode() ?: 0
        result = 31 * result + (blockhash?.hashCode() ?: 0)
        result = 31 * result + instructions.hashCode()
        result = 31 * result + (computeUnitLimit?.hashCode() ?: 0)
        result = 31 * result + (computeUnitPrice?.hashCode() ?: 0)
        result = 31 * result + (priorityFee?.hashCode() ?: 0)
        result = 31 * result + (loadedAccountsDataSizeLimit?.hashCode() ?: 0)
        result = 31 * result + (heapSize?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "SolanaTransactionRequest(feePayer=$feePayer, blockhash=$blockhash, instructions=$instructions, " +
            "computeUnitLimit=$computeUnitLimit, computeUnitPrice=$computeUnitPrice, priorityFee=$priorityFee, " +
            "loadedAccountsDataSizeLimit=$loadedAccountsDataSizeLimit, heapSize=$heapSize)"
    }

    companion object {
        @JvmSynthetic
        inline operator fun invoke(builder: SolanaTransactionRequest.() -> Unit): SolanaTransactionRequest {
            return SolanaTransactionRequest().apply(builder)
        }
    }
}
