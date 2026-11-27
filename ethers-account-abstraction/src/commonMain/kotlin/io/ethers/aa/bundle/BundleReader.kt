package io.ethers.aa.bundle

import io.ethers.aa.EntryPoint
import io.ethers.aa.contracts.EntryPointV09
import io.ethers.abi.EventFilterBase
import io.ethers.core.Result
import io.ethers.core.ThrowableError
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.Log
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
import kotlin.jvm.JvmOverloads

/**
 * Reads executed bundles and user operations from a node, and decodes them with [BundleDecoder].
 *
 * @param provider the node to read from.
 * @param useTrace whether to fetch a `callTracer` trace of each bundle transaction with `debug_traceTransaction`.
 * Traces attribute every log and call frame to its operation, see [BundleDecoder], but need a node with the debug
 * API enabled.
 * @param entryPoints the EntryPoints whose bundles to look for and decode.
 */
class BundleReader @JvmOverloads constructor(
    private val provider: Middleware,
    private val useTrace: Boolean = false,
    private val entryPoints: List<EntryPoint> = EntryPoint.CANONICAL,
) {
    /**
     * Failure to read bundles from a node.
     */
    sealed class Error : ThrowableError {
        /** The node rejected an RPC call. */
        data class Rpc(val error: RpcError) : Error() {
            override val message: String
                get() = error.message

            override fun toException(): RuntimeException = error.toException()
        }

        /** The bundle data could not be decoded. */
        data class Decoding(val error: BundleDecoder.Error) : Error() {
            override val message: String
                get() = error.message
        }

        /** The transaction [transactionHash] is not known to the node, or not included yet. */
        data class TransactionNotFound(val transactionHash: Hash) : Error() {
            override val message: String
                get() = "Transaction $transactionHash not found"
        }
    }

    /**
     * Read the bundles executed by transaction [transactionHash].
     */
    fun getBundles(transactionHash: Hash): RpcRequest<List<Bundle>, Error> {
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
    ): RpcRequest<ExecutedUserOperation?, Error> {
        return SuppliedRpcRequest {
            val filter = EntryPointV09.UserOperationEvent.filter(provider)
                .address(entryPoints.map { it.address })
                .userOpHash(Bytes(userOpHash.asByteArray()))
                .inRange(fromBlock, toBlock)
                ?: return@SuppliedRpcRequest failure(INVALID_TO_BLOCK)

            val events = filter.query().send().unwrapOrReturn { return@SuppliedRpcRequest failure(Error.Rpc(it)) }
            val log = events.firstOrNull { !it.removed }?.log ?: return@SuppliedRpcRequest success(null)

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
    ): RpcRequest<List<ExecutedUserOperation>, Error> {
        return SuppliedRpcRequest {
            val filter = EntryPointV09.UserOperationEvent.filter(provider)
                .address(entryPoints.map { it.address })
                .sender(sender)
                .inRange(fromBlock, toBlock)
                ?: return@SuppliedRpcRequest failure(INVALID_TO_BLOCK)

            val events = filter.query().send().unwrapOrReturn { return@SuppliedRpcRequest failure(Error.Rpc(it)) }
            val bundles = readBundlesOf(events.map { it.log }).unwrapOrReturn { return@SuppliedRpcRequest failure(it) }
            success(bundles.flatMap { bundle -> bundle.userOperations.filter { it.sender == sender } })
        }
    }

    /**
     * Read all bundles executed in block [blockId], in execution order.
     */
    fun getBundlesInBlock(blockId: BlockId): RpcRequest<List<Bundle>, Error> {
        return SuppliedRpcRequest {
            val events = EntryPointV09.BeforeExecution.filter(provider)
                .address(entryPoints.map { it.address })
                .atBlock(blockId)
                .query().send()
                .unwrapOrReturn { return@SuppliedRpcRequest failure(Error.Rpc(it)) }
            readBundlesOf(events.map { it.log })
        }
    }

    private suspend fun readBundlesOf(logs: List<Log>): Result<List<Bundle>, Error> = coroutineScope {
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

    private suspend fun readBundles(transactionHash: Hash): Result<List<Bundle>, Error> = coroutineScope {
        val receiptFut = async { provider.getTransactionReceipt(transactionHash).send() }
        val transactionFut = async { provider.getTransactionByHash(transactionHash).send() }
        val traceFut = if (useTrace) {
            async { provider.traceTransaction(transactionHash, TracerConfig(CallTracer(onlyTopCall = false, withLog = true))).send() }
        } else {
            null
        }

        val receipt = receiptFut.await().unwrapOrReturn { return@coroutineScope failure(Error.Rpc(it)) }
            ?: return@coroutineScope failure(Error.TransactionNotFound(transactionHash))
        val transaction = transactionFut.await().unwrapOrReturn { return@coroutineScope failure(Error.Rpc(it)) }
        val trace = traceFut?.await()?.unwrapOrReturn { return@coroutineScope failure(Error.Rpc(it)) }

        BundleDecoder.decode(receipt, transaction, trace, entryPoints).mapError { Error.Decoding(it) }
    }

    private fun <F : EventFilterBase<*, F>> F.inRange(from: BlockId.Number, to: BlockId): F? = when (to) {
        is BlockId.Number -> blockRange(from, to)
        is BlockId.Name -> blockRange(from, to)
        is BlockId.Hash -> null
    }

    private fun <F : EventFilterBase<*, F>> F.atBlock(blockId: BlockId): F = when (blockId) {
        is BlockId.Number -> atBlock(blockId)
        is BlockId.Name -> atBlock(blockId)
        is BlockId.Hash -> atBlock(blockId)
    }

    private companion object {
        val INVALID_TO_BLOCK = Error.Rpc(RpcError(RpcError.CODE_INVALID_PARAMS, "toBlock must be a block number or name"))
    }
}
