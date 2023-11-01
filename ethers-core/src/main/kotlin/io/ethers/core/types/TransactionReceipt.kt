package io.ethers.core.types

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.ethers.core.forEachObjectField
import io.ethers.core.readAddress
import io.ethers.core.readBloom
import io.ethers.core.readBytes
import io.ethers.core.readHash
import io.ethers.core.readHexBigInteger
import io.ethers.core.readHexLong
import io.ethers.core.readListOf
import io.ethers.core.readOrNull
import java.math.BigInteger

/**
 * Result of transaction execution.
 */
@JsonDeserialize(using = TxReceiptDeserializer::class)
data class TransactionReceipt(
    val blockHash: Hash,
    val blockNumber: Long,
    val transactionHash: Hash,
    val transactionIndex: Long,
    val from: Address,
    val to: Address?,
    val gasUsed: Long,
    val cumulativeGasUsed: Long,
    val contractAddress: Address?,
    val logs: List<Log>,
    val logsBloom: Bloom,
    val type: Long,
    val effectiveGasPrice: BigInteger,
    val status: Long,
    val root: Bytes?,
    val otherFields: Map<String, JsonNode> = emptyMap(),
) {
    val isSuccessful: Boolean
        get() = status == 1L
}

private class TxReceiptDeserializer : JsonDeserializer<TransactionReceipt>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): TransactionReceipt {
        if (p.currentToken != JsonToken.START_OBJECT) {
            throw IllegalStateException("Expected start object, got: ${p.currentToken}")
        }

        lateinit var blockHash: Hash
        var blockNumber: Long = -1L
        lateinit var transactionHash: Hash
        var transactionIndex: Long = -1L
        lateinit var from: Address
        var to: Address? = null
        var gasUsed: Long = -1L
        var cumulativeGasUsed: Long = -1L
        var contractAddress: Address? = null
        var logs = emptyList<Log>()
        lateinit var logsBloom: Bloom
        var type: Long = -1L
        lateinit var effectiveGasPrice: BigInteger
        var status: Long = -1L
        var root: Bytes? = null
        var otherFields: MutableMap<String, JsonNode>? = null

        p.forEachObjectField { field ->
            when (field) {
                "blockHash" -> blockHash = p.readHash()
                "blockNumber" -> blockNumber = p.readHexLong()
                "transactionHash" -> transactionHash = p.readHash()
                "transactionIndex" -> transactionIndex = p.readHexLong()
                "from" -> from = p.readAddress()
                "to" -> to = p.readOrNull { readAddress() }
                "gasUsed" -> gasUsed = p.readHexLong()
                "cumulativeGasUsed" -> cumulativeGasUsed = p.readHexLong()
                "contractAddress" -> contractAddress = p.readOrNull { readAddress() }
                "logs" -> logs = p.readListOf(Log::class.java)
                "logsBloom" -> logsBloom = p.readBloom()
                "type" -> type = p.readHexLong()
                "effectiveGasPrice" -> effectiveGasPrice = p.readHexBigInteger()
                "status" -> status = p.readHexLong()
                "root" -> root = p.readBytes()
                else -> {
                    if (otherFields == null) {
                        otherFields = HashMap()
                    }
                    otherFields!![p.currentName] = p.readValueAsTree()
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
            type,
            effectiveGasPrice,
            status,
            root,
            otherFields ?: emptyMap(),
        )
    }
}
