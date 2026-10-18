@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(io.ethers.solana.types.U64Serializer::class)

package io.ethers.solana.types.transaction

import io.ethers.solana.types.ExtensibleJsonSerializer
import io.ethers.solana.types.OtherFieldsSerializer
import io.ethers.solana.types.U32Serializer
import io.ethers.solana.utils.U32_MAX
import io.ethers.solana.utils.requireU64
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.jvm.JvmOverloads
import kotlin.native.ObjCName

/**
 * V1 inline resource requests, also used by the RPC message's transactionConfig.
 * Null means the request is absent, distinct from an explicitly encoded zero.
 * Absent compute/data limits and priority fee are zero; absent heap size is 32 KiB.
 * ComputeBudget instructions do not configure v1 transactions.
 *
 * Not a data class: a generated `copy` would re-run the validation without declaring it, which a Swift caller
 * cannot catch. The hand-written members below match what a data class generates.
 */
@KeepGeneratedSerializer
@Serializable(with = SolanaTransactionConfigSerializer::class)
@ObjCName("SolanaTransactionConfig", exact = true)
class SolanaTransactionConfig
@JvmOverloads
@Throws(IllegalArgumentException::class)
constructor(
    /** Total priority fee in lamports, NOT micro-lamports per compute unit. */
    val priorityFee: BigInteger? = null,
    @Serializable(with = U32Serializer::class) val computeUnitLimit: Long? = null,
    @Serializable(with = U32Serializer::class) val loadedAccountsDataSizeLimit: Long? = null,
    @Serializable(with = U32Serializer::class) val heapSize: Long? = null,
    @Serializable(with = OtherFieldsSerializer::class) val otherFields: Map<String, JsonElement> = emptyMap(),
) {
    init {
        priorityFee?.let(::requireU64)
        listOf(computeUnitLimit, loadedAccountsDataSizeLimit, heapSize).forEach {
            require(it == null || it in 0..U32_MAX) { "Config value must fit an unsigned 32-bit integer" }
        }
    }

    @Throws(IllegalArgumentException::class)
    fun copy(
        priorityFee: BigInteger? = this.priorityFee,
        computeUnitLimit: Long? = this.computeUnitLimit,
        loadedAccountsDataSizeLimit: Long? = this.loadedAccountsDataSizeLimit,
        heapSize: Long? = this.heapSize,
        otherFields: Map<String, JsonElement> = this.otherFields,
    ): SolanaTransactionConfig = SolanaTransactionConfig(priorityFee, computeUnitLimit, loadedAccountsDataSizeLimit, heapSize, otherFields)

    operator fun component1(): BigInteger? = priorityFee
    operator fun component2(): Long? = computeUnitLimit
    operator fun component3(): Long? = loadedAccountsDataSizeLimit
    operator fun component4(): Long? = heapSize
    operator fun component5(): Map<String, JsonElement> = otherFields

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SolanaTransactionConfig) return false
        return priorityFee == other.priorityFee &&
            computeUnitLimit == other.computeUnitLimit &&
            loadedAccountsDataSizeLimit == other.loadedAccountsDataSizeLimit &&
            heapSize == other.heapSize &&
            otherFields == other.otherFields
    }

    override fun hashCode(): Int {
        var result = priorityFee?.hashCode() ?: 0
        result = 31 * result + (computeUnitLimit?.hashCode() ?: 0)
        result = 31 * result + (loadedAccountsDataSizeLimit?.hashCode() ?: 0)
        result = 31 * result + (heapSize?.hashCode() ?: 0)
        result = 31 * result + otherFields.hashCode()
        return result
    }

    override fun toString(): String = "SolanaTransactionConfig(priorityFee=$priorityFee, computeUnitLimit=$computeUnitLimit, " +
        "loadedAccountsDataSizeLimit=$loadedAccountsDataSizeLimit, heapSize=$heapSize, otherFields=$otherFields)"

    internal val mask: Int get() = (if (priorityFee != null) 3 else 0) or
        (if (computeUnitLimit != null) 4 else 0) or
        (if (loadedAccountsDataSizeLimit != null) 8 else 0) or
        (if (heapSize != null) 16 else 0)

    internal val wireSize: Int get() = mask.countOneBits() * 4
}

object SolanaTransactionConfigSerializer : ExtensibleJsonSerializer<SolanaTransactionConfig>(SolanaTransactionConfig.generatedSerializer(), { it.otherFields })
