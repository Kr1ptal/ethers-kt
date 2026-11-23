package io.ethers.aa.bundle

import io.ethers.aa.EntryPoint
import io.ethers.aa.contracts.EntryPointV09
import io.ethers.core.Result
import io.ethers.core.ThrowableError
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.Hash
import io.ethers.core.types.Log
import io.ethers.core.types.LogFilter
import io.ethers.core.types.tracers.CallTracer
import io.ethers.core.types.tracers.TracerConfig
import io.ethers.core.unwrapOrReturn
import io.ethers.providers.RpcError
import io.ethers.providers.middleware.Middleware
import io.ethers.providers.types.RpcRequest
import io.ethers.providers.types.SuppliedRpcRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Failure to read bundles from a node.
 */
sealed class BundleReadError : ThrowableError {
    /** The node rejected an RPC call. */
    data class Rpc(val error: RpcError) : BundleReadError() {
        override val message: String
            get() = error.message

        override fun toException(): RuntimeException = error.toException()
    }

    /** The bundle data could not be decoded. */
    data class Decoding(val error: BundleDecodingError) : BundleReadError() {
        override val message: String
            get() = error.message
    }

    /** The transaction [transactionHash] is not known to the node, or not included yet. */
    data class TransactionNotFound(val transactionHash: Hash) : BundleReadError() {
        override val message: String
            get() = "Transaction $transactionHash not found"
    }
}

/**
 * Reads executed bundles and user operations from a node, decoding them with [decoder].
 *
 * @param provider the node to read from.
 * @param decoder how to decode bundles, including which EntryPoints to consider.
 * @param useTrace whether to fetch a `callTracer` trace of each bundle transaction with `debug_traceTransaction`.
 * Traces attribute every log and call frame to its operation, see [BundleDecoder], but need a node with the debug
 * API enabled.
 */
class BundleReader(
    private val provider: Middleware,
    private val decoder: BundleDecoder = BundleDecoder(),
    private val useTrace: Boolean = false,
    private val entryPoints: List<EntryPoint> = EntryPoint.CANONICAL,
) {
    /**
     * Read the bundles executed by transaction [transactionHash].
     */
    fun getBundles(transactionHash: Hash): RpcRequest<List<Bundle>, BundleReadError> {
        return SuppliedRpcRequest { readBundles(transactionHash) }
    }

    /**
     * Find the user operation [userOpHash] in blocks [fromBlock] to [toBlock] and decode it, or return null if it was
     * not executed in that range.
     */
    fun getExecutedUserOperation(
        userOpHash: Hash,
        fromBlock: BlockId.Number,
        toBlock: BlockId,
    ): RpcRequest<ExecutedUserOperation?, BundleReadError> {
        return SuppliedRpcRequest {
            val filter = blockRange(LogFilter(), fromBlock, toBlock)
                .address(entryPoints.map { it.address })
                .topic0(EntryPointV09.UserOperationEvent.abi.topicId)
                .topic1(userOpHash)

            val logs = getLogs(filter).unwrapOrReturn { return@SuppliedRpcRequest failure(it) }
            val log = logs.firstOrNull { !it.removed } ?: return@SuppliedRpcRequest success(null)

            val bundles = readBundles(log.transactionHash).unwrapOrReturn { return@SuppliedRpcRequest failure(it) }
            success(bundles.firstNotNullOfOrNull { bundle -> bundle.userOperations.firstOrNull { it.userOpHash == userOpHash } })
        }
    }

    /**
     * Find and decode all user operations [sender] executed in blocks [fromBlock] to [toBlock], in execution order.
     */
    fun getUserOperationsBySender(
        sender: Address,
        fromBlock: BlockId.Number,
        toBlock: BlockId,
    ): RpcRequest<List<ExecutedUserOperation>, BundleReadError> {
        return SuppliedRpcRequest {
            val filter = blockRange(LogFilter(), fromBlock, toBlock)
                .address(entryPoints.map { it.address })
                .topic0(EntryPointV09.UserOperationEvent.abi.topicId)
                .topic2(Hash(sender))

            val logs = getLogs(filter).unwrapOrReturn { return@SuppliedRpcRequest failure(it) }
            val bundles = readBundlesOf(logs).unwrapOrReturn { return@SuppliedRpcRequest failure(it) }
            success(bundles.flatMap { bundle -> bundle.userOperations.filter { it.sender == sender } })
        }
    }

    /**
     * Read all bundles executed in block [blockId], in execution order.
     */
    fun getBundlesInBlock(blockId: BlockId): RpcRequest<List<Bundle>, BundleReadError> {
        return SuppliedRpcRequest {
            val filter = LogFilter()
                .atBlock(blockId)
                .address(entryPoints.map { it.address })
                .topic0(EntryPointV09.BeforeExecution.abi.topicId)

            val logs = getLogs(filter).unwrapOrReturn { return@SuppliedRpcRequest failure(it) }
            readBundlesOf(logs)
        }
    }

    private suspend fun readBundlesOf(logs: List<Log>): Result<List<Bundle>, BundleReadError> = coroutineScope {
        val transactions = logs.filter { !it.removed }
            .sortedWith(compareBy({ it.blockNumber }, { it.transactionIndex }))
            .map { it.transactionHash }
            .distinct()

        val results = transactions.map { async { readBundles(it) } }.awaitAll()
        val ret = ArrayList<Bundle>()
        for (result in results) {
            ret.addAll(result.unwrapOrReturn { return@coroutineScope failure(it) })
        }
        success(ret)
    }

    private suspend fun readBundles(transactionHash: Hash): Result<List<Bundle>, BundleReadError> = coroutineScope {
        val receiptFut = async { provider.getTransactionReceipt(transactionHash).send() }
        val transactionFut = async { provider.getTransactionByHash(transactionHash).send() }
        val traceFut = if (useTrace) {
            async { provider.traceTransaction(transactionHash, TracerConfig(CallTracer(onlyTopCall = false, withLog = true))).send() }
        } else {
            null
        }

        val receipt = receiptFut.await().unwrapOrReturn { return@coroutineScope failure(BundleReadError.Rpc(it)) }
            ?: return@coroutineScope failure(BundleReadError.TransactionNotFound(transactionHash))
        val transaction = transactionFut.await().unwrapOrReturn { return@coroutineScope failure(BundleReadError.Rpc(it)) }
        val trace = traceFut?.await()?.unwrapOrReturn { return@coroutineScope failure(BundleReadError.Rpc(it)) }

        decoder.decode(receipt, transaction, trace).mapError { BundleReadError.Decoding(it) }
    }

    private suspend fun getLogs(filter: LogFilter): Result<List<Log>, BundleReadError> {
        return provider.getLogs(filter).send().mapError { BundleReadError.Rpc(it) }
    }

    private fun blockRange(filter: LogFilter, from: BlockId.Number, to: BlockId): LogFilter = when (to) {
        is BlockId.Number -> filter.blockRange(from, to)
        is BlockId.Name -> filter.blockRange(from, to)
        is BlockId.Hash -> throw IllegalArgumentException("toBlock must be a block number or name")
    }
}
