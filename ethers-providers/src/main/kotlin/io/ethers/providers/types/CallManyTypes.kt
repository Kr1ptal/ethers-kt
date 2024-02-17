package io.ethers.providers.types

import io.ethers.core.Result
import io.ethers.core.types.BlockId
import io.ethers.core.types.BlockOverride
import io.ethers.core.types.CallRequest

/**
 * Internal type used for correctly serializing `eth_callMany`/`debug_traceCallMany` request arguments.
 * */
internal data class CallManyBundle(
    val transactions: List<CallRequest>,
    val blockOverride: BlockOverride? = null,
)

/**
 * Internal type used for correctly serializing `eth_callMany`/`debug_traceCallMany` request arguments.
 * */
internal data class CallManyContext(
    val blockNumber: BlockId,
    val transactionIndex: Int,
)

/**
 * Error returned for a call in `eth_callMany` that fails.
 * */
data class CallFailedError(val error: String) : Result.Error