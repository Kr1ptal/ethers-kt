package io.ethers.providers.middleware

import io.ethers.core.Result
import io.ethers.core.types.AccountOverride
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.BlockOverride
import io.ethers.core.types.BlockWithHashes
import io.ethers.core.types.BlockWithTransactions
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.core.types.FeeHistory
import io.ethers.core.types.Hash
import io.ethers.core.types.Log
import io.ethers.core.types.LogFilter
import io.ethers.core.types.RPCTransaction
import io.ethers.core.types.SyncStatus
import io.ethers.core.types.TransactionReceipt
import io.ethers.core.types.transaction.TransactionSigned
import io.ethers.core.types.transaction.TransactionUnsigned
import io.ethers.providers.RpcError
import io.ethers.providers.types.CallFailedError
import io.ethers.providers.types.FilterPoller
import io.ethers.providers.types.PendingTransaction
import io.ethers.providers.types.RpcRequest
import io.ethers.providers.types.RpcSubscribe
import java.math.BigInteger
import java.util.Optional

interface EthApi {
    /**
     * EVM chain id.
     */
    val chainId: Long

    /**
     * Get latest block number.
     */
    fun getBlockNumber(): RpcRequest<Long, RpcError>

    /**
     * Get [address] balance at [hash].
     */
    fun getBalance(address: Address, hash: Hash) = getBalance(address, BlockId.Hash(hash))

    /**
     * Get [address] balance at [number].
     */
    fun getBalance(address: Address, number: Long) = getBalance(address, BlockId.Number(number))

    /**
     * Get [address] balance at [blockId].
     */
    fun getBalance(address: Address, blockId: BlockId): RpcRequest<BigInteger, RpcError>

    /**
     * Get block header by [hash].
     */
    fun getBlockHeader(hash: Hash) = getBlockHeader(BlockId.Hash(hash))

    /**
     * Get block header by [number].
     */
    fun getBlockHeader(number: Long) = getBlockHeader(BlockId.Number(number))

    /**
     * Get block header by [blockId].
     */
    fun getBlockHeader(blockId: BlockId): RpcRequest<BlockWithHashes, RpcError>

    /**
     * Get block by [hash] with transaction hashes.
     */
    fun getBlockWithHashes(hash: Hash) = getBlockWithHashes(BlockId.Hash(hash))

    /**
     * Get block by [number] with transaction hashes.
     */
    fun getBlockWithHashes(number: Long) = getBlockWithHashes(BlockId.Number(number))

    /**
     * Get block by [blockId] with transaction hashes.
     */
    fun getBlockWithHashes(blockId: BlockId): RpcRequest<BlockWithHashes, RpcError>

    /**
     * Get block by [hash] with full transaction objects.
     */
    fun getBlockWithTransactions(hash: Hash) = getBlockWithTransactions(BlockId.Hash(hash))

    /**
     * Get block by [number] with full transaction objects.
     */
    fun getBlockWithTransactions(number: Long) = getBlockWithTransactions(BlockId.Number(number))

    /**
     * Get block by [blockId] with full transaction objects.
     */
    fun getBlockWithTransactions(blockId: BlockId): RpcRequest<BlockWithTransactions, RpcError>

    /**
     * Get uncle block header by [hash] and [index].
     */
    fun getUncleBlockHeader(hash: Hash, index: Long) = getUncleBlockHeader(BlockId.Hash(hash), index)

    /**
     * Get uncle block header by [number] and [index].
     */
    fun getUncleBlockHeader(number: Long, index: Long) = getUncleBlockHeader(BlockId.Number(number), index)

    /**
     * Get uncle block header by [blockId] and [index].
     */
    fun getUncleBlockHeader(blockId: BlockId, index: Long): RpcRequest<BlockWithHashes, RpcError>

    /**
     * Get uncle blocks count by [hash].
     */
    fun getUncleBlocksCount(hash: Hash) = getUncleBlocksCount(BlockId.Hash(hash))

    /**
     * Get uncle blocks count by [number].
     */
    fun getUncleBlocksCount(number: Long) = getUncleBlocksCount(BlockId.Number(number))

    /**
     * Get uncle blocks count by [blockId].
     */
    fun getUncleBlocksCount(blockId: BlockId): RpcRequest<Long, RpcError>

    /**
     * Get code stored at given [address] in the state for given block [hash].
     */
    fun getCode(address: Address, hash: Hash) = getCode(address, BlockId.Hash(hash))

    /**
     * Get code stored at given [address] in the state for given block [number].
     */
    fun getCode(address: Address, number: Long) = getCode(address, BlockId.Number(number))

    /**
     * Get code stored at given [address] in the state for given [blockId].
     */
    fun getCode(address: Address, blockId: BlockId): RpcRequest<Bytes, RpcError>

    /**
     * Get storage value stored at given [address] and [key] in the state for given block [hash].
     */
    fun getStorage(address: Address, key: Hash, hash: Hash) = getStorage(address, key, BlockId.Hash(hash))

    /**
     * Get storage value stored at given [address] and [key] in the state for given block [number].
     */
    fun getStorage(address: Address, key: Hash, number: Long) = getStorage(address, key, BlockId.Number(number))

    /**
     * Get storage value stored at given [address] and [key] in the state for given block [blockId].
     */
    fun getStorage(address: Address, key: Hash, blockId: BlockId): RpcRequest<Hash, RpcError>

    /**
     * Execute [call] on given [blockId].
     */
    fun call(call: CallRequest, blockId: BlockId) = call(call, blockId, null, null)

    /**
     * Execute [call] on given [blockId] with applied state overrides.
     */
    fun call(call: CallRequest, blockId: BlockId, stateOverride: Map<Address, AccountOverride>) =
        call(call, blockId, stateOverride, null)

    /**
     * Execute [call] on given [blockId] with applied block overrides.
     */
    fun call(call: CallRequest, blockId: BlockId, blockOverride: BlockOverride) =
        call(call, blockId, null, blockOverride)

    /**
     * Execute [call] on given [blockId] with applied state and block overrides.
     */
    fun call(
        call: CallRequest,
        blockId: BlockId,
        stateOverride: Map<Address, AccountOverride>? = null,
        blockOverride: BlockOverride? = null,
    ): RpcRequest<Bytes, RpcError>

    /**
     * Execute arbitrary number of [calls] starting at an arbitrary [transactionIndex] in the block, with the
     * option of providing state/block overrides.
     *
     * @param blockNumber the block state on which to execute the calls on.
     * @param calls the list of transactions/calls to execute.
     * @param transactionIndex the index of where in the block to start executing the calls at, with -1 meaning
     * at the end of the block.
     * @param stateOverride a map of account state overrides to apply to the block state.
     * @param blockOverride a block state override to apply to the block execution environment.
     * */
    fun callMany(
        blockNumber: Long,
        calls: List<CallRequest>,
        transactionIndex: Int = -1,
        stateOverride: Map<Address, AccountOverride>? = null,
        blockOverride: BlockOverride? = null,
    ): RpcRequest<List<Result<Bytes, CallFailedError>>, RpcError> {
        return callMany(BlockId.Number(blockNumber), calls, transactionIndex, stateOverride, blockOverride)
    }

    /**
     * Execute arbitrary number of [calls] starting at an arbitrary [transactionIndex] in the block, with the
     * option of providing state/block overrides.
     *
     * @param blockHash the block state on which to execute the calls on.
     * @param calls the list of transactions/calls to execute.
     * @param transactionIndex the index of where in the block to start executing the calls at, with -1 meaning
     * at the end of the block.
     * @param stateOverride a map of account state overrides to apply to the block state.
     * @param blockOverride a block state override to apply to the block execution environment.
     * */
    fun callMany(
        blockHash: Hash,
        calls: List<CallRequest>,
        transactionIndex: Int = -1,
        stateOverride: Map<Address, AccountOverride>? = null,
        blockOverride: BlockOverride? = null,
    ): RpcRequest<List<Result<Bytes, CallFailedError>>, RpcError> {
        return callMany(BlockId.Hash(blockHash), calls, transactionIndex, stateOverride, blockOverride)
    }

    /**
     * Execute arbitrary number of [calls] starting at an arbitrary [transactionIndex] in the block, with the
     * option of providing state/block overrides.
     *
     * @param blockId the block state on which to execute the calls on.
     * @param calls the list of transactions/calls to execute.
     * @param transactionIndex the index of where in the block to start executing the calls at, with -1 meaning
     * at the end of the block.
     * @param stateOverride a map of account state overrides to apply to the block state.
     * @param blockOverride a block state override to apply to the block execution environment.
     * */
    fun callMany(
        blockId: BlockId,
        calls: List<CallRequest>,
        transactionIndex: Int = -1,
        stateOverride: Map<Address, AccountOverride>? = null,
        blockOverride: BlockOverride? = null,
    ): RpcRequest<List<Result<Bytes, CallFailedError>>, RpcError>

    /**
     * Estimate gas required to execute [call] on given block [hash].
     */
    fun estimateGas(call: CallRequest, hash: Hash) = estimateGas(call, BlockId.Hash(hash))

    /**
     * Estimate gas required to execute [call] on given block [number].
     */
    fun estimateGas(call: CallRequest, number: Long) = estimateGas(call, BlockId.Number(number))

    /**
     * Estimate gas required to execute [call] on given [blockId].
     */
    fun estimateGas(call: CallRequest, blockId: BlockId): RpcRequest<BigInteger, RpcError>

    /**
     * Create access list for a given transaction [call] on a given block [hash].
     */
    fun createAccessList(call: CallRequest, hash: Hash) = createAccessList(call, BlockId.Hash(hash))

    /**
     * Create access list for a given transaction [call] on a given block [number].
     */
    fun createAccessList(call: CallRequest, number: Long) = createAccessList(call, BlockId.Number(number))

    /**
     * Create access list for a given transaction [call] on a given block [blockId].
     */
    fun createAccessList(call: CallRequest, blockId: BlockId): RpcRequest<*, RpcError>

    /**
     * Get gas price suggestion for legacy transaction.
     */
    fun getGasPrice(): RpcRequest<BigInteger, RpcError>

    /**
     * Returns the base fee per blob gas in wei.
     * */
    fun getBlobBaseFee(): RpcRequest<BigInteger, RpcError>

    /**
     * Get gas tip cap suggestion for dynamic fee transaction.
     */
    fun getMaxPriorityFeePerGas(): RpcRequest<BigInteger, RpcError>

    /**
     * Get gas fee history for block range between [lastBlockNumber] and ([lastBlockNumber] - [blockCount] + 1).
     */
    fun getFeeHistory(blockCount: Long, lastBlockNumber: Long) = getFeeHistory(blockCount, lastBlockNumber, emptyList())

    /**
     * Get gas fee history for block range between [lastBlockNumber] and ([lastBlockNumber] - [blockCount] + 1).
     *
     * @param [rewardPercentiles] a monotonically increasing list of percentile values to sample from each block's
     * effective priority fees per gas in ascending order, weighted by gas used.
     */
    fun getFeeHistory(
        blockCount: Long,
        lastBlockNumber: Long,
        rewardPercentiles: List<BigInteger> = emptyList(),
    ): RpcRequest<FeeHistory, RpcError>

    /**
     * Check if node is syncing with the network.
     */
    fun isNodeSyncing(): RpcRequest<SyncStatus, RpcError>

    /**
     * Get transaction count in a block by [number].
     */
    fun getBlockTransactionCount(number: Long) = getBlockTransactionCount(BlockId.Number(number))

    /**
     * Get transaction count in a block by [hash].
     */
    fun getBlockTransactionCount(hash: Hash) = getBlockTransactionCount(BlockId.Hash(hash))

    /**
     * Get transaction count in a block by [blockId].
     */
    fun getBlockTransactionCount(blockId: BlockId): RpcRequest<Long, RpcError>

    /**
     * Get transaction at [index] in a given block [number].
     */
    fun getTransactionByBlockAndIndex(number: Long, index: Long) =
        getTransactionByBlockAndIndex(BlockId.Number(number), index)

    /**
     * Get transaction at [index] in a given block [hash].
     */
    fun getTransactionByBlockAndIndex(hash: Hash, index: Long) =
        getTransactionByBlockAndIndex(BlockId.Hash(hash), index)

    /**
     * Get transaction at [index] in a given block [blockId].
     */
    fun getTransactionByBlockAndIndex(blockId: BlockId, index: Long): RpcRequest<RPCTransaction, RpcError>

    /**
     * Get RLP encoded transaction at [index] in a given block [number].
     */
    fun getRawTransactionByBlockAndIndex(number: Long, index: Long) =
        getRawTransactionByBlockAndIndex(BlockId.Number(number), index)

    /**
     * Get RLP encoded transaction at [index] in a given block [hash].
     */
    fun getRawTransactionByBlockAndIndex(hash: Hash, index: Long) =
        getRawTransactionByBlockAndIndex(BlockId.Hash(hash), index)

    /**
     * Get RLP encoded transaction at [index] in a given block [blockId].
     */
    fun getRawTransactionByBlockAndIndex(blockId: BlockId, index: Long): RpcRequest<Bytes, RpcError>

    /**
     * Count the transactions sent by [address] up to and including the current block [number].
     */
    fun getTransactionCount(address: Address, number: Long) = getTransactionCount(address, BlockId.Number(number))

    /**
     * Count the transactions sent by [address] up to and including the current block [hash].
     */
    fun getTransactionCount(address: Address, hash: Hash) = getTransactionCount(address, BlockId.Hash(hash))

    /**
     * Count the transactions sent by [address] up to and including the current block [blockId].
     */
    fun getTransactionCount(address: Address, blockId: BlockId): RpcRequest<Long, RpcError>

    /**
     * Get transaction by [hash], returning empty [Optional] if none exists.
     */
    fun getTransactionByHash(hash: Hash): RpcRequest<Optional<RPCTransaction>, RpcError>

    /**
     * Get transaction receipt by [hash], returning empty [Optional] if none exists.
     */
    fun getTransactionReceipt(hash: Hash): RpcRequest<Optional<TransactionReceipt>, RpcError>

    /**
     * RLP encode and submit [signedTransaction].
     */
    fun sendRawTransaction(signedTransaction: TransactionSigned) = sendRawTransaction(signedTransaction.toRlp())

    /**
     * Submit signed transaction bytes.
     */
    fun sendRawTransaction(signedTransaction: ByteArray): RpcRequest<PendingTransaction, RpcError>

    /**
     * Fill the defaults (nonce, gas, gasPrice or 1559 fields) and return unsigned transaction for further
     * processing (signing + submission).
     */
    fun fillTransaction(call: CallRequest): RpcRequest<TransactionUnsigned, RpcError>

    /**
     * Get logs by block [blockHash].
     */
    fun getLogs(blockHash: Hash): RpcRequest<List<Log>, RpcError> = getLogs(BlockId.Hash(blockHash))

    /**
     * Get logs by block [blockNumber].
     */
    fun getLogs(blockNumber: Long): RpcRequest<List<Log>, RpcError> = getLogs(BlockId.Number(blockNumber))

    /**
     * Get logs by block [blockId].
     */
    fun getLogs(blockId: BlockId): RpcRequest<List<Log>, RpcError> {
        val filter = when (blockId) {
            is BlockId.Hash -> LogFilter().atBlock(blockId)
            is BlockId.Name -> LogFilter().blockRange(blockId, blockId)
            is BlockId.Number -> LogFilter().blockRange(blockId, blockId)
        }
        return getLogs(filter)
    }

    /**
     * Get logs matching [filter].
     */
    fun getLogs(filter: LogFilter): RpcRequest<List<Log>, RpcError>

    /**
     * Watch for logs matching [filter]. Compared to [subscribeLogs], this function installs a filter and
     * intermittently polls it for new logs. It can be used to achieve streaming-like behavior if the provider
     * does not support subscriptions.
     * */
    fun watchLogs(filter: LogFilter): RpcRequest<FilterPoller<Log>, RpcError>

    /**
     * Watch for new blocks. Compared to [subscribeNewHeads], this function installs a filter and intermittently
     * polls it for new logs. It can be used to achieve streaming-like behavior if the provider does not support
     * subscriptions.
     * */
    fun watchNewBlocks(): RpcRequest<FilterPoller<Hash>, RpcError>

    /**
     * Watch for new pending transaction hashes. Compared to [subscribeNewPendingTransactionHashes], this function
     * installs a filter and intermittently polls it for new logs. It can be used to achieve streaming-like behavior
     * if the provider does not support subscriptions.
     * */
    fun watchNewPendingTransactionHashes(): RpcRequest<FilterPoller<Hash>, RpcError>

    /**
     * Watch for new pending transactions. Compared to [subscribeNewPendingTransactions], this function installs
     * a filter and intermittently polls it for new logs. It can be used to achieve streaming-like behavior if the
     * provider does not support subscriptions.
     * */
    fun watchNewPendingTransactions(): RpcRequest<FilterPoller<RPCTransaction>, RpcError>

    /**
     * Subscribe to logs matching [filter]. This function should be used instead of [watchLogs] if the
     * provider supports subscriptions.
     * */
    fun subscribeLogs(filter: LogFilter): RpcSubscribe<Log, RpcError>

    /**
     * Subscribe to new block heads. This function should be used instead of [watchNewBlocks] if the
     * provider supports subscriptions.
     * */
    fun subscribeNewHeads(): RpcSubscribe<BlockWithHashes, RpcError>

    /**
     * Subscribe to new pending transactions. This function should be used instead of [watchNewPendingTransactions]
     * if the provider supports subscriptions.
     * */
    fun subscribeNewPendingTransactions(): RpcSubscribe<RPCTransaction, RpcError>

    /**
     * Subscribe to new pending transaction hashes. This function should be used instead of [watchNewPendingTransactions]
     * if the provider supports subscriptions.
     * */
    fun subscribeNewPendingTransactionHashes(): RpcSubscribe<Hash, RpcError>
}
