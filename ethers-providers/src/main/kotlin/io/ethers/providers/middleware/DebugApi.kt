package io.ethers.providers.middleware

import io.ethers.core.types.BlockId
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.IntoCallRequest
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
     * Execute [call] on given [blockNumber] and trace its execution with given tracer [config].
     */
    fun <T> traceCall(call: IntoCallRequest, blockNumber: Long, config: TracerConfig<T>): RpcRequest<T, RpcError> {
        return traceCall(call, BlockId.Number(blockNumber), config)
    }

    /**
     * Execute [call] on given [blockHash] and trace its execution with given tracer [config].
     */
    fun <T> traceCall(call: IntoCallRequest, blockHash: Hash, config: TracerConfig<T>): RpcRequest<T, RpcError> {
        return traceCall(call, BlockId.Hash(blockHash), config)
    }

    /**
     * Execute [call] on given [blockId] and trace its execution with given tracer [config].
     */
    fun <T> traceCall(call: IntoCallRequest, blockId: BlockId, config: TracerConfig<T>): RpcRequest<T, RpcError>

    /**
     * Execute arbitrary number of [calls] starting at an arbitrary [transactionIndex] in the block, and tracing their
     * execution with the given tracer [config].
     *
     * @param blockNumber the block state on which to execute the calls on.
     * @param calls the list of transactions/calls to execute.
     * @param config the tracer configuration to use for tracing the calls.
     * @param transactionIndex the index of where in the block to start executing the calls at, with -1 meaning
     * at the end of the block.
     * */
    fun <T> traceCallMany(
        blockNumber: Long,
        calls: List<IntoCallRequest>,
        config: TracerConfig<T>,
        transactionIndex: Int = -1,
    ): RpcRequest<List<T>, RpcError> {
        return traceCallMany(BlockId.Number(blockNumber), calls, config, transactionIndex)
    }

    /**
     * Execute arbitrary number of [calls] starting at an arbitrary [transactionIndex] in the block, and tracing their
     * execution with the given tracer [config].
     *
     * @param blockHash the block state on which to execute the calls on.
     * @param calls the list of transactions/calls to execute.
     * @param config the tracer configuration to use for tracing the calls.
     * @param transactionIndex the index of where in the block to start executing the calls at, with -1 meaning
     * at the end of the block.
     * */
    fun <T> traceCallMany(
        blockHash: Hash,
        calls: List<IntoCallRequest>,
        config: TracerConfig<T>,
        transactionIndex: Int = -1,
    ): RpcRequest<List<T>, RpcError> {
        return traceCallMany(BlockId.Hash(blockHash), calls, config, transactionIndex)
    }

    /**
     * Execute arbitrary number of [calls] starting at an arbitrary [transactionIndex] in the block, and tracing their
     * execution with the given tracer [config].
     *
     * @param blockId the block state on which to execute the calls on.
     * @param calls the list of transactions/calls to execute.
     * @param config the tracer configuration to use for tracing the calls.
     * @param transactionIndex the index of where in the block to start executing the calls at, with -1 meaning
     * at the end of the block.
     * */
    fun <T> traceCallMany(
        blockId: BlockId,
        calls: List<IntoCallRequest>,
        config: TracerConfig<T>,
        transactionIndex: Int = -1,
    ): RpcRequest<List<T>, RpcError>

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
