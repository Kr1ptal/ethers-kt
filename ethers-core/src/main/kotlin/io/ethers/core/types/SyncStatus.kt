package io.ethers.core.types

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import io.ethers.core.forEachObjectField
import io.ethers.core.readHexLong

@JsonDeserialize(using = SyncStatusDeserializer::class)
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

private class SyncStatusDeserializer : StdDeserializer<SyncStatus>(SyncStatus::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): SyncStatus {
        if (p.currentToken.isBoolean) {
            return SyncStatus.Finished
        }
        var startingBlock: Long = -1L
        var currentBlock: Long = -1L
        var highestBlock: Long = -1L
        p.forEachObjectField { name ->
            when (name) {
                "startingBlock" -> startingBlock = p.readHexLong()
                "currentBlock" -> currentBlock = p.readHexLong()
                "highestBlock" -> highestBlock = p.readHexLong()
            }
        }
        if (startingBlock == -1L || currentBlock == -1L || highestBlock == -1L) {
            throw IllegalArgumentException("Invalid SyncStatus: $p")
        }

        return SyncStatus.InProgress(startingBlock, currentBlock, highestBlock)
    }
}
