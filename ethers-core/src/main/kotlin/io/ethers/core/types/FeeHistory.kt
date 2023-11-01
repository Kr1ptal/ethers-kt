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

@JsonDeserialize(using = FeeHistoryDeserializer::class)
data class FeeHistory(
    val oldestBlock: Long,
    val baseFeePerGas: List<BigInteger>,
    val gasUsedRatio: List<Double>,
    val rewards: List<List<BigInteger>>?,
)

class FeeHistoryDeserializer : JsonDeserializer<FeeHistory>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): FeeHistory {
        var oldestBlock = 0L
        var baseFeePerGas: List<BigInteger> = emptyList()
        var gasUsedRatio: List<Double> = emptyList()
        var rewards: List<List<BigInteger>>? = null

        p.forEachObjectField { field ->
            when (field) {
                "oldestBlock" -> oldestBlock = p.readAnyLong()
                "baseFeePerGas" -> baseFeePerGas = p.readListOf { readHexBigInteger() }
                "gasUsedRatio" -> gasUsedRatio = p.readListOf { p.doubleValue }
                "reward" -> rewards = p.readOrNull { p.readListOf { p.readListOf { readHexBigInteger() } } }
                else -> throw IllegalArgumentException("Unknown field $field")
            }
        }

        return FeeHistory(oldestBlock, baseFeePerGas, gasUsedRatio, rewards)
    }
}
