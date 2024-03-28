package io.ethers.core.types

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.ethers.core.forEachObjectField
import io.ethers.core.readAddress
import io.ethers.core.readBytes
import io.ethers.core.readHash
import io.ethers.core.readHexInt
import io.ethers.core.readHexLong
import io.ethers.core.readListOfHashes

/**
 * Contract log event.
 */
@JsonDeserialize(using = LogDeserializer::class)
data class Log(
    val address: Address,
    val topics: List<Hash>,
    val data: Bytes,
    val blockHash: Hash,
    val blockNumber: Long,
    val transactionHash: Hash,
    val transactionIndex: Int,
    val logIndex: Int,
    val removed: Boolean,
)

private class LogDeserializer : JsonDeserializer<Log>() {
    override fun deserialize(p: JsonParser, context: DeserializationContext): Log {
        if (p.currentToken != JsonToken.START_OBJECT) {
            throw IllegalStateException("Expected start object, got: ${p.currentToken}")
        }

        lateinit var address: Address
        var topics: List<Hash>? = null
        var data: Bytes? = null
        var blockHash: Hash? = null
        var blockNumber: Long = -1L
        var transactionHash: Hash? = null
        var transactionIndex: Int? = null
        var logIndex: Int = -1
        var removed: Boolean? = null

        p.forEachObjectField { field ->
            when (field) {
                "address" -> address = p.readAddress()
                "topics" -> topics = p.readListOfHashes()
                "data" -> data = p.readBytes()
                "blockHash" -> blockHash = p.readHash()
                "blockNumber" -> blockNumber = p.readHexLong()
                "transactionHash" -> transactionHash = p.readHash()
                "transactionIndex" -> transactionIndex = p.readHexInt()
                "logIndex" -> logIndex = p.readHexInt()
                "removed" -> removed = p.currentToken() == JsonToken.VALUE_TRUE

                else -> throw IllegalStateException("Unexpected field name: ${p.currentName()}")
            }
        }

        return Log(
            address = address,
            topics = topics!!,
            data = data!!,
            blockHash = blockHash!!,
            blockNumber = blockNumber,
            transactionHash = transactionHash!!,
            transactionIndex = transactionIndex!!,
            logIndex = logIndex,
            removed = removed!!,
        )
    }
}
