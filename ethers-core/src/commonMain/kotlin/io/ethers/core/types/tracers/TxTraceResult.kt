package io.ethers.core.types.tracers

import io.ethers.core.types.Hash
import kotlinx.serialization.Serializable

/**
 * Result of traced transactions when tracing the entire block.
 *
 * [txHash] is null if working with older node versions. In that case, the proper way to match
 * trace to transaction is to also do a call to get the same block with hashes, and match the
 * tracing result to the transaction by index.
 *
 * Decoded with `TxTraceResult.serializer(tracer.resultSerializer)`; a missing field decodes as null.
 * */
@Serializable
data class TxTraceResult<T>(
    /**
     * Hash of traced transactions, or null on older nodes.
     * */
    val txHash: Hash? = null,

    /**
     * Tracer result of the transaction, or null if [error] is not null.
     * */
    val result: T? = null,

    /**
     * Error message, or null if [result] is not null.
     * */
    val error: String? = null,
)
