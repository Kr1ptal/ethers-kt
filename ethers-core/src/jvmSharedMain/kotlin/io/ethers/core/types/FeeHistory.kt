package io.ethers.core.types

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.ethers.core.forEachObjectField
import io.ethers.core.readAnyLong
import io.ethers.core.readHexBigInteger
import io.ethers.core.readListOf
import io.ethers.core.readOrNull
import java.math.BigInteger

/**
 * This class represents a FeeHistory.
 *
 * @property oldestBlock The oldest block in the fee history.
 * @property baseFeePerGas The base fee per gas for each block in the fee history. This includes the base fee for the
 * next block as the last element. For pre-EIP-1559 blocks, the base fee is ZERO.
 * @property gasUsedRatio The gas used ratio for each block in the fee history.
 * @property rewards The gas tip rewards at requested percentiles for each block in the fee history. This can be null.
 * @property baseFeePerBlobGas The base fee per blob gas for each block in the fee history. All values for a block that
 * is empty are returned as ZERO. This can be null.
 * @property blobGasUsedRatio The blob gas used ratio for each block in the fee history. For pre-EIP-4844 blocks, the
 * base fee is ZERO. This can be null.
 */
@JsonDeserialize(using = FeeHistoryDeserializer::class)
data class FeeHistory(
    val oldestBlock: Long,
    val baseFeePerGas: List<BigInteger>,
    val gasUsedRatio: List<Double>,
    val rewards: List<List<BigInteger>>?,
    val baseFeePerBlobGas: List<BigInteger>?,
    val blobGasUsedRatio: List<Double>?,
) {
    /**
     * Get the next base fee per gas, or ZERO for pre-EIP-1559 blocks.
     * */
    val nextBaseFeePerGas: BigInteger
        get() = baseFeePerGas.lastOrNull() ?: BigInteger.ZERO

    /**
     * Get the next base fee per blob gas, or ZERO for pre-EIP-4844 blocks.
     * */
    val nextBaseFeePerBlobGas: BigInteger
        get() = baseFeePerBlobGas?.lastOrNull() ?: BigInteger.ZERO
}

class FeeHistoryDeserializer : JsonDeserializer<FeeHistory>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): FeeHistory {
        var oldestBlock = 0L
        var baseFeePerGas: List<BigInteger> = emptyList()
        var gasUsedRatio: List<Double> = emptyList()
        var rewards: List<List<BigInteger>>? = null
        var baseFeePerBlobGas: List<BigInteger>? = null
        var blobGasUsedRatio: List<Double>? = null

        p.forEachObjectField { field ->
            when (field) {
                "oldestBlock" -> oldestBlock = p.readAnyLong()
                "baseFeePerGas" -> baseFeePerGas = p.readListOf { readHexBigInteger() }
                "gasUsedRatio" -> gasUsedRatio = p.readListOf { p.doubleValue }
                "reward" -> rewards = p.readOrNull { p.readListOf { p.readListOf { readHexBigInteger() } } }
                "baseFeePerBlobGas" -> baseFeePerBlobGas = p.readOrNull { p.readListOf { readHexBigInteger() } }
                "blobGasUsedRatio" -> blobGasUsedRatio = p.readOrNull { p.readListOf { p.doubleValue } }
                else -> p.skipChildren()
            }
        }

        return FeeHistory(oldestBlock, baseFeePerGas, gasUsedRatio, rewards, baseFeePerBlobGas, blobGasUsedRatio)
    }
}
