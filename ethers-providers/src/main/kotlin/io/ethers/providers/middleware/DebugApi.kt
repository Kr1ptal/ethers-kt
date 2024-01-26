package io.ethers.providers.middleware

import io.ethers.core.types.BlockId
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.core.types.Hash
import io.ethers.core.types.tracers.TracerConfig
import io.ethers.core.types.tracers.TxTraceResult
import io.ethers.providers.RpcError
import io.ethers.providers.types.RpcRequest
import java.util.Optional

interface DebugApi {
    /**
     * Get RLP encoded block header by [hash].
     */
    fun getRawBlockHeader(hash: Hash) = getRawBlockHeader(BlockId.Hash(hash))

    /**
     * Get RLP encoded block header by [number].
     */
    fun getRawBlockHeader(number: Long) = getRawBlockHeader(BlockId.Number(number))

    /**
     * Get RLP encoded block header by [blockId].
     */
    fun getRawBlockHeader(blockId: BlockId): RpcRequest<Bytes, RpcError>

    /**
     * Get RLP encoded block by [hash] with full transaction objects.
     */
    fun getRawBlockWithTransactions(hash: Hash) = getRawBlockWithTransactions(BlockId.Hash(hash))

    /**
     * Get RLP encoded block by [number] with full transaction objects.
     */
    fun getRawBlockWithTransactions(number: Long) = getRawBlockWithTransactions(BlockId.Number(number))

    /**
     * Get RLP encoded block by [blockId] with full transaction objects.
     */
    fun getRawBlockWithTransactions(blockId: BlockId): RpcRequest<Bytes, RpcError>

    /**
     * Get binary encoded receipts of all transactions in block by [hash].
     */
    fun getRawReceipts(hash: Hash) = getRawReceipts(BlockId.Hash(hash))

    /**
     * Get binary encoded receipts of all transactions in block by [number].
     */
    fun getRawReceipts(number: Long) = getRawReceipts(BlockId.Number(number))

    /**
     * Get binary encoded receipts of all transactions in block by [blockId].
     */
    fun getRawReceipts(blockId: BlockId): RpcRequest<List<Bytes>, RpcError>

    /**
     * Get RLP encoded transactions by [hash].
     */
    fun getRawTransaction(hash: Hash): RpcRequest<Optional<Bytes>, RpcError>

    /**
     * Pretty print block by [number].
     */
    fun printBlock(number: Long): RpcRequest<String, RpcError>

    /**
     * Execute [call] on given [blockId] and trace its execution with given tracer [config].
     */
    fun <T> traceCall(call: CallRequest, blockId: BlockId, config: TracerConfig<T>): RpcRequest<T, RpcError>

    /**
     * Trace [txHash] transaction execution with given tracer [config].
     */
    fun <T> traceTransaction(txHash: Hash, config: TracerConfig<T>): RpcRequest<T, RpcError>

    /**
     * Trace all transactions within block with given tracer [config].
     * */
    fun <T> traceBlock(blockHash: Hash, config: TracerConfig<T>) = traceBlock(BlockId.Hash(blockHash), config)

    /**
     * Trace all transactions within block with given tracer [config].
     * */
    fun <T> traceBlock(blockNumber: Long, config: TracerConfig<T>) = traceBlock(BlockId.Number(blockNumber), config)

    /**
     * Trace all transactions within block with given tracer [config].
     * */
    fun <T> traceBlock(blockId: BlockId, config: TracerConfig<T>): RpcRequest<List<TxTraceResult<T>>, RpcError>
}
