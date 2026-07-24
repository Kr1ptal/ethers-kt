package io.ethers.core.types

import io.ethers.core.asAnyLong
import io.ethers.core.asDouble
import io.ethers.core.asHexBigInteger
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
@Serializable(with = FeeHistorySerializer::class)
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

object FeeHistorySerializer : KSerializer<FeeHistory> {
    override val descriptor = buildClassSerialDescriptor("FeeHistory")

    override fun serialize(encoder: Encoder, value: FeeHistory) = throw UnsupportedOperationException()

    override fun deserialize(decoder: Decoder): FeeHistory {
        val obj = (decoder as JsonDecoder).decodeJsonElement().jsonObject

        var oldestBlock = 0L
        var baseFeePerGas: List<BigInteger> = emptyList()
        var gasUsedRatio: List<Double> = emptyList()
        var rewards: List<List<BigInteger>>? = null
        var baseFeePerBlobGas: List<BigInteger>? = null
        var blobGasUsedRatio: List<Double>? = null

        for ((key, element) in obj.entries) {
            when (key) {
                "oldestBlock" -> oldestBlock = element.jsonPrimitive.asAnyLong()
                "baseFeePerGas" -> baseFeePerGas = element.jsonArray.map { it.jsonPrimitive.asHexBigInteger() }
                "gasUsedRatio" -> gasUsedRatio = element.jsonArray.map { it.jsonPrimitive.asDouble }
                "reward" -> rewards = if (element is JsonNull) null
                else element.jsonArray.map { inner ->
                    inner.jsonArray.map { it.jsonPrimitive.asHexBigInteger() }
                }
                "baseFeePerBlobGas" -> baseFeePerBlobGas = if (element is JsonNull) null
                else element.jsonArray.map { it.jsonPrimitive.asHexBigInteger() }
                "blobGasUsedRatio" -> blobGasUsedRatio = if (element is JsonNull) null
                else element.jsonArray.map { it.jsonPrimitive.asDouble }
            }
        }

        return FeeHistory(oldestBlock, baseFeePerGas, gasUsedRatio, rewards, baseFeePerBlobGas, blobGasUsedRatio)
    }
}
