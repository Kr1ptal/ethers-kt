package io.ethers.core.types

import io.ethers.core.asHexLong
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = SyncStatusSerializer::class)
sealed interface SyncStatus {
    /**
     * Returns `true` if the sync is finished.
     * */
    val isFinished: Boolean
        get() = this is Finished

    /**
     * Returns `true` if the sync is in progress.
     * */
    val isInProgress: Boolean
        get() = this is InProgress

    data object Finished : SyncStatus

    // TODO each node implementation has its own sync fields, along with these ones. Add support for them
    data class InProgress(
        val startingBlock: Long,
        val currentBlock: Long,
        val highestBlock: Long,
    ) : SyncStatus {
        /**
         * Get the progress of the sync, as a number between 0 and 1.
         * */
        val progress: Double
            get() = (currentBlock - startingBlock).toDouble() / (highestBlock - startingBlock)
    }
}

object SyncStatusSerializer : KSerializer<SyncStatus> {
    override val descriptor = buildClassSerialDescriptor("SyncStatus")

    override fun serialize(encoder: Encoder, value: SyncStatus) = throw UnsupportedOperationException()

    override fun deserialize(decoder: Decoder): SyncStatus {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        if (element !is JsonObject) {
            return SyncStatus.Finished
        }

        var startingBlock = -1L
        var currentBlock = -1L
        var highestBlock = -1L

        for ((key, value) in element.entries) {
            when (key) {
                "startingBlock" -> startingBlock = value.jsonPrimitive.asHexLong()
                "currentBlock" -> currentBlock = value.jsonPrimitive.asHexLong()
                "highestBlock" -> highestBlock = value.jsonPrimitive.asHexLong()
            }
        }

        if (startingBlock == -1L || currentBlock == -1L || highestBlock == -1L) {
            throw IllegalArgumentException("Invalid SyncStatus: $element")
        }

        return SyncStatus.InProgress(startingBlock, currentBlock, highestBlock)
    }
}
