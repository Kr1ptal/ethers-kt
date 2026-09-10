package io.ethers.solana.types.transaction

import io.ethers.solana.instruction.ComputeBudgetProgram
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf

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
        /** The ComputeBudget discriminant this instruction carries, or -1 if it is not a decodable one. */
        fun discriminant(instruction: Instruction): Int = discriminant(instruction.programId, instruction.data)

        /** As above, for an instruction whose program has already been resolved from an account list. */
        fun discriminant(programId: SolanaAddress, data: SolanaBytes): Int {
            if (programId != Programs.COMPUTE_BUDGET || data.isEmpty) return -1
            val width = ComputeBudgetProgram.payloadWidth(data[0].toInt())
            if (width < 0) return -1
            return if (data.size == 1 + width) data[0].toInt() else -1
        }

        fun decode(instructions: List<Instruction>): ComputeBudgetSettings {
            var limit: Long? = null
            var price: BigInteger? = null
            var dataSize: Long? = null
            var heap: Long? = null
            val remaining = instructions.filter { instruction ->
                val discriminant = discriminant(instruction)
                if (discriminant < 0) return@filter true
                val value = ComputeBudgetProgram.decodeValue(discriminant, instruction.data)
                when (discriminant) {
                    ComputeBudgetProgram.REQUEST_HEAP_FRAME -> heap = value.toLong()
                    ComputeBudgetProgram.SET_COMPUTE_UNIT_LIMIT -> limit = value.toLong()
                    ComputeBudgetProgram.SET_COMPUTE_UNIT_PRICE -> price = value
                    ComputeBudgetProgram.SET_LOADED_ACCOUNTS_DATA_SIZE_LIMIT -> dataSize = value.toLong()
                }
                false
            }
            return ComputeBudgetSettings(remaining, limit, price, dataSize, heap)
        }
    }
}

/**
 * The ComputeBudget settings a message carries, however its version states them.
 *
 * Every one is optional: a message that sets none of them runs on the runtime's defaults, which is
 * most of them.
 */
internal class ComputeBudgetValues(
    val computeUnitLimit: Long?,
    val computeUnitPrice: BigInteger?,
    val loadedAccountsDataSizeLimit: Long?,
    val heapSize: Long?,
) {
    /**
     * The total the runtime charges for priority, which legacy and v0 only imply.
     *
     * Null when no price is stated at all, and also when a nonzero price has no limit to multiply,
     * since the runtime would then apply a default limit this library cannot predict.
     */
    val priorityFee: BigInteger?
        get() {
            val price = computeUnitPrice ?: return null
            if (price.signum() == 0) return bigIntegerOf(0)
            val limit = computeUnitLimit ?: return null
            return bigIntegerOf(limit).multiply(price).add(bigIntegerOf(999999)).divide(bigIntegerOf(1000000))
        }

    companion object {
        val NONE = ComputeBudgetValues(null, null, null, null)
    }
}

/** Read the ComputeBudget settings out of a compiled legacy or v0 message. */
internal fun decodeComputeBudget(accounts: List<SolanaAddress>, instructions: List<CompiledInstruction>): ComputeBudgetValues {
    var limit: Long? = null
    var price: BigInteger? = null
    var dataSize: Long? = null
    var heap: Long? = null
    instructions.forEach { instruction ->
        val programId = accounts.getOrNull(instruction.programIdIndex) ?: return@forEach
        val discriminant = ComputeBudgetSettings.discriminant(programId, instruction.data)
        if (discriminant < 0) return@forEach
        val value = ComputeBudgetProgram.decodeValue(discriminant, instruction.data)
        when (discriminant) {
            ComputeBudgetProgram.REQUEST_HEAP_FRAME -> heap = value.toLong()
            ComputeBudgetProgram.SET_COMPUTE_UNIT_LIMIT -> limit = value.toLong()
            ComputeBudgetProgram.SET_COMPUTE_UNIT_PRICE -> price = value
            ComputeBudgetProgram.SET_LOADED_ACCOUNTS_DATA_SIZE_LIMIT -> dataSize = value.toLong()
        }
    }
    return ComputeBudgetValues(limit, price, dataSize, heap)
}
