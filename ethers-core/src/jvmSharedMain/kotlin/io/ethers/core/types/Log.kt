package io.ethers.core.types

import io.ethers.core.HexIntSerializer
import io.ethers.core.HexLongSerializer
import kotlinx.serialization.Serializable

/**
 * Contract log event.
 */
@Serializable
data class Log(
    val address: Address,
    val topics: List<Hash> = emptyList(),
    val data: Bytes,
    val blockHash: Hash,
    @Serializable(with = HexLongSerializer::class) val blockNumber: Long,
    @Serializable(with = HexLongSerializer::class) val blockTimestamp: Long = -1L,
    val transactionHash: Hash,
    @Serializable(with = HexIntSerializer::class) val transactionIndex: Int,
    @Serializable(with = HexIntSerializer::class) val logIndex: Int,
    val removed: Boolean = false,
)
