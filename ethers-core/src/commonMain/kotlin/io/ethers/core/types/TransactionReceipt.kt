package io.ethers.core.types

import io.ethers.core.asAddress
import io.ethers.core.asBloom
import io.ethers.core.asBytes
import io.ethers.core.asHash
import io.ethers.core.asHexBigInteger
import io.ethers.core.asHexInt
import io.ethers.core.asHexLong
import io.ethers.core.json.JsonElement
import io.ethers.core.types.transaction.TxType
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
 * Result of transaction execution.
 */
@Serializable(with = TxReceiptSerializer::class)
data class TransactionReceipt(
    val blockHash: Hash,
    val blockNumber: Long,
    val transactionHash: Hash,
    val transactionIndex: Int,
    val from: Address,
    val to: Address?,
    val gasUsed: Long,
    val cumulativeGasUsed: Long,
    val contractAddress: Address?,
    val logs: List<Log>,
    val logsBloom: Bloom,
    val type: TxType,
    val effectiveGasPrice: BigInteger,
    val status: Long,
    val root: Bytes?,
    val otherFields: Map<String, JsonElement> = emptyMap(),
) {
    val isSuccessful: Boolean
        get() = status == 1L

    /**
     * Calculate the effective gas tip (gas price minus base fee) paid by this transaction, relative to the [baseFee].
     * */
    fun getEffectiveGasTip(baseFee: BigInteger): BigInteger {
        return effectiveGasPrice - baseFee
    }
}

object TxReceiptSerializer : KSerializer<TransactionReceipt> {
    override val descriptor = buildClassSerialDescriptor("TransactionReceipt")

    override fun serialize(encoder: Encoder, value: TransactionReceipt) = throw UnsupportedOperationException()

    override fun deserialize(decoder: Decoder): TransactionReceipt {
        val jsonDecoder = decoder as JsonDecoder
        val obj = jsonDecoder.decodeJsonElement().jsonObject

        lateinit var blockHash: Hash
        var blockNumber = -1L
        lateinit var transactionHash: Hash
        var transactionIndex = -1
        lateinit var from: Address
        var to: Address? = null
        var gasUsed = -1L
        var cumulativeGasUsed = -1L
        var contractAddress: Address? = null
        var logs = emptyList<Log>()
        lateinit var logsBloom: Bloom
        var type = -1
        lateinit var effectiveGasPrice: BigInteger
        var status = -1L
        var root: Bytes? = null
        var otherFields: MutableMap<String, JsonElement>? = null

        for ((key, element) in obj.entries) {
            when (key) {
                "blockHash" -> blockHash = element.jsonPrimitive.asHash()
                "blockNumber" -> blockNumber = element.jsonPrimitive.asHexLong()
                "transactionHash" -> transactionHash = element.jsonPrimitive.asHash()
                "transactionIndex" -> transactionIndex = element.jsonPrimitive.asHexInt()
                "from" -> from = element.jsonPrimitive.asAddress()
                "to" -> to = if (element is JsonNull) null else element.jsonPrimitive.asAddress()
                "gasUsed" -> gasUsed = element.jsonPrimitive.asHexLong()
                "cumulativeGasUsed" -> cumulativeGasUsed = element.jsonPrimitive.asHexLong()
                "contractAddress" -> contractAddress = if (element is JsonNull) null else element.jsonPrimitive.asAddress()
                "logs" -> logs = element.jsonArray.map { jsonDecoder.json.decodeFromJsonElement(Log.serializer(), it) }
                "logsBloom" -> logsBloom = element.jsonPrimitive.asBloom()
                "type" -> type = element.jsonPrimitive.asHexInt()
                "effectiveGasPrice" -> effectiveGasPrice = element.jsonPrimitive.asHexBigInteger()
                "status" -> status = element.jsonPrimitive.asHexLong()
                "root" -> root = element.jsonPrimitive.asBytes()
                else -> {
                    if (otherFields == null) otherFields = HashMap()
                    otherFields[key] = JsonElement(element.toString())
                }
            }
        }

        return TransactionReceipt(
            blockHash,
            blockNumber,
            transactionHash,
            transactionIndex,
            from,
            to,
            gasUsed,
            cumulativeGasUsed,
            contractAddress,
            logs,
            logsBloom,
            TxType.fromType(type),
            effectiveGasPrice,
            status,
            root,
            otherFields ?: emptyMap(),
        )
    }
}
