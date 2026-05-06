package io.ethers.core.types

import io.ethers.core.asAddress
import io.ethers.core.asBytes
import io.ethers.core.asHash
import io.ethers.core.asHexInt
import io.ethers.core.asHexLong
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Contract log event.
 */
@Serializable(with = LogSerializer::class)
data class Log(
    val address: Address,
    val topics: List<Hash>,
    val data: Bytes,
    val blockHash: Hash,
    val blockNumber: Long,
    val blockTimestamp: Long,
    val transactionHash: Hash,
    val transactionIndex: Int,
    val logIndex: Int,
    val removed: Boolean,
)

object LogSerializer : KSerializer<Log> {
    override val descriptor = buildClassSerialDescriptor("Log")

    override fun serialize(encoder: Encoder, value: Log) = throw UnsupportedOperationException()

    override fun deserialize(decoder: Decoder): Log {
        val obj = (decoder as JsonDecoder).decodeJsonElement().jsonObject

        lateinit var address: Address
        var topics: List<Hash> = emptyList()
        var data: Bytes? = null
        var blockHash: Hash? = null
        var blockNumber = -1L
        var blockTimestamp = -1L
        var transactionHash: Hash? = null
        var transactionIndex: Int? = null
        var logIndex = -1
        var removed = false

        for ((key, element) in obj.entries) {
            when (key) {
                "address" -> address = element.jsonPrimitive.asAddress()
                "topics" -> topics = if (element is JsonNull) emptyList()
                else element.jsonArray.map { it.jsonPrimitive.asHash() }
                "data" -> data = element.jsonPrimitive.asBytes()
                "blockHash" -> blockHash = element.jsonPrimitive.asHash()
                "blockNumber" -> blockNumber = element.jsonPrimitive.asHexLong()
                "blockTimestamp" -> blockTimestamp = element.jsonPrimitive.asHexLong()
                "transactionHash" -> transactionHash = element.jsonPrimitive.asHash()
                "transactionIndex" -> transactionIndex = element.jsonPrimitive.asHexInt()
                "logIndex" -> logIndex = element.jsonPrimitive.asHexInt()
                "removed" -> removed = element.jsonPrimitive.boolean
            }
        }

        return Log(
            address = address,
            topics = topics,
            data = data!!,
            blockHash = blockHash!!,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            transactionHash = transactionHash!!,
            transactionIndex = transactionIndex!!,
            logIndex = logIndex,
            removed = removed,
        )
    }
}
