package io.ethers.providers

import io.ethers.core.FastHex
import io.ethers.core.HexBigIntegerSerializer
import io.ethers.core.HexLongSerializer
import io.ethers.core.Kotlinx
import io.ethers.core.Result
import io.ethers.core.ThrowableError
import io.ethers.core.failure
import io.ethers.core.isFailure
import io.ethers.core.success
import io.ethers.core.types.Address
import io.ethers.core.types.Block
import io.ethers.core.types.BlockId
import io.ethers.core.types.BlockOverride
import io.ethers.core.types.BlockWithHashes
import io.ethers.core.types.BlockWithTransactions
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.core.types.CallRequestSerializer
import io.ethers.core.types.CreateAccessList
import io.ethers.core.types.FeeHistory
import io.ethers.core.types.Hash
import io.ethers.core.types.IntoCallRequest
import io.ethers.core.types.Log
import io.ethers.core.types.LogFilter
import io.ethers.core.types.RPCTransaction
import io.ethers.core.types.StateOverride
import io.ethers.core.types.SyncStatus
import io.ethers.core.types.SyncStatusSerializer
import io.ethers.core.types.TransactionReceipt
import io.ethers.core.types.TxpoolContent
import io.ethers.core.types.TxpoolContentFromAddress
import io.ethers.core.types.TxpoolInspectResult
import io.ethers.core.types.TxpoolStatus
import io.ethers.core.types.tracers.TracerConfig
import io.ethers.core.types.tracers.TracerConfigSerializer
import io.ethers.core.types.tracers.TxTraceResult
import io.ethers.core.types.transaction.TransactionUnsigned
import io.ethers.providers.middleware.Middleware
import io.ethers.providers.types.CallFailedError
import io.ethers.providers.types.CallManyBundle
import io.ethers.providers.types.CallManyContext
import io.ethers.providers.types.FilterPoller
import io.ethers.providers.types.PendingTransaction
import io.ethers.providers.types.RpcCall
import io.ethers.providers.types.RpcParams
import io.ethers.providers.types.RpcRequest
import io.ethers.providers.types.RpcSubscribe
import io.ethers.providers.types.RpcSubscribeCall
import io.ethers.providers.types.SuppliedRpcRequest
import io.ethers.providers.types.buildRpcParams
import io.ethers.providers.types.rpcParams
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.github.artificialpb.bignum.div
import io.github.artificialpb.bignum.plus
import io.github.artificialpb.bignum.times
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.jvm.JvmStatic

@Suppress("MoveLambdaOutsideParentheses")
class Provider(override val client: JsonRpcClient, override val chainId: Long) : Middleware {
    // it's ever only set to false if eth_fillTransaction is not supported, so it's fine if it's a bit racy
    private var supportsFillTransaction = true
    private val fillTransactionFeeHistory = getFeeHistory(10, BlockId.LATEST, doubleArrayOf(20.0))

    override val inner: Middleware?
        get() = null

    override val provider: Provider
        get() = this

    //-----------------------------------------------------------------------------------------------------------------
    //                                  EthApi implementation
    //-----------------------------------------------------------------------------------------------------------------
    override fun getBlockNumber(): RpcRequest<Long, RpcError> {
        return RpcCall(client, "eth_blockNumber", RpcParams.EMPTY, HexLongSerializer)
    }

    override fun getBalance(address: Address, blockId: BlockId): RpcRequest<BigInteger, RpcError> {
        return RpcCall(client, "eth_getBalance", rpcParams(address, blockId.id), HexBigIntegerSerializer)
    }

    override fun getBlockHeader(blockId: BlockId): RpcRequest<BlockWithHashes?, RpcError> {
        val params = rpcParams(blockId.id)
        val method = when (blockId) {
            is BlockId.Hash -> "eth_getHeaderByHash"
            is BlockId.Number, is BlockId.Name -> "eth_getHeaderByNumber"
        }
        return RpcCall(client, method, params, BlockWithHashes.serializer().nullable)
    }

    override fun getBlockWithHashes(blockId: BlockId): RpcRequest<BlockWithHashes?, RpcError> {
        return getBlock(blockId, false, BlockWithHashes.serializer())
    }

    override fun getBlockWithTransactions(blockId: BlockId): RpcRequest<BlockWithTransactions?, RpcError> {
        return getBlock(blockId, true, BlockWithTransactions.serializer())
    }

    private fun <T, B : Block<T>> getBlock(
        blockId: BlockId,
        fullTransactions: Boolean,
        responseSerializer: KSerializer<B>,
    ): RpcRequest<B?, RpcError> {
        val params = rpcParams(blockId.id, fullTransactions)
        val method = when (blockId) {
            is BlockId.Hash -> "eth_getBlockByHash"
            is BlockId.Number, is BlockId.Name -> "eth_getBlockByNumber"
        }
        return RpcCall(client, method, params, responseSerializer.nullable)
    }

    override fun getUncleBlockHeader(blockId: BlockId, index: Long): RpcRequest<BlockWithHashes?, RpcError> {
        val params = rpcParams(blockId.id, FastHex.encodeWithPrefix(index))
        val method = when (blockId) {
            is BlockId.Hash -> "eth_getUncleByBlockHashAndIndex"
            is BlockId.Number, is BlockId.Name -> "eth_getUncleByBlockNumberAndIndex"
        }
        return RpcCall(client, method, params, BlockWithHashes.serializer().nullable)
    }

    override fun getUncleBlocksCount(blockId: BlockId): RpcRequest<Long, RpcError> {
        val params = rpcParams(blockId.id)
        val method = when (blockId) {
            is BlockId.Hash -> "eth_getUncleCountByBlockHash"
            is BlockId.Number, is BlockId.Name -> "eth_getUncleCountByBlockNumber"
        }
        return RpcCall(client, method, params, HexLongSerializer)
    }

    override fun getCode(address: Address, blockId: BlockId): RpcRequest<Bytes, RpcError> {
        val params = rpcParams(address, blockId.id)
        return RpcCall(client, "eth_getCode", params, Bytes.serializer())
    }

    override fun getStorage(address: Address, key: Hash, blockId: BlockId): RpcRequest<Hash, RpcError> {
        val params = rpcParams(address, key, blockId.id)
        return RpcCall(client, "eth_getStorageAt", params, Hash.serializer())
    }

    override fun call(
        call: IntoCallRequest,
        blockId: BlockId,
        stateOverride: StateOverride?,
        blockOverride: BlockOverride?,
    ): RpcRequest<Bytes, RpcError> {
        // create minimal params array - some RPC's don't support stateOverride or blockOverride
        val params = when {
            blockOverride != null -> rpcParams(call.toCallRequest(), blockId.id, stateOverride, blockOverride)
            stateOverride != null -> rpcParams(call.toCallRequest(), blockId.id, stateOverride)
            else -> rpcParams(call.toCallRequest(), blockId.id)
        }

        return RpcCall(client, "eth_call", params, Bytes.serializer())
    }

    override fun callMany(
        blockId: BlockId,
        calls: List<IntoCallRequest>,
        transactionIndex: Int,
        stateOverride: StateOverride?,
        blockOverride: BlockOverride?,
    ): RpcRequest<List<Result<Bytes, CallFailedError>>, RpcError> {
        val bundle = CallManyBundle(calls.map { it.toCallRequest() }, blockOverride)
        val ctx = CallManyContext(blockId, transactionIndex)

        val params = rpcParams(bundle, ctx, stateOverride)
        return RpcCall(client, "eth_callMany", params, ListSerializer(CallManyResultSerializer))
    }

    override fun estimateGas(call: IntoCallRequest, blockId: BlockId): RpcRequest<Long, RpcError> {
        val params = rpcParams(call.toCallRequest(), blockId.id)
        return RpcCall(client, "eth_estimateGas", params, HexLongSerializer)
    }

    override fun createAccessList(call: IntoCallRequest, blockId: BlockId): RpcRequest<CreateAccessList, RpcError> {
        val params = rpcParams(call.toCallRequest(), blockId.id)
        return RpcCall(client, "eth_createAccessList", params, CreateAccessList.serializer())
    }

    override fun getGasPrice(): RpcRequest<BigInteger, RpcError> {
        return RpcCall(client, "eth_gasPrice", RpcParams.EMPTY, HexBigIntegerSerializer)
    }

    override fun getBlobBaseFee(): RpcRequest<BigInteger, RpcError> {
        return RpcCall(client, "eth_blobBaseFee", RpcParams.EMPTY, HexBigIntegerSerializer)
    }

    override fun getMaxPriorityFeePerGas(): RpcRequest<BigInteger, RpcError> {
        return RpcCall(client, "eth_maxPriorityFeePerGas", RpcParams.EMPTY, HexBigIntegerSerializer)
    }

    override fun getFeeHistory(
        blockCount: Long,
        lastBlockName: BlockId.Name,
        rewardPercentiles: DoubleArray,
    ): RpcRequest<FeeHistory, RpcError> {
        val params = rpcParams(FastHex.encodeWithPrefix(blockCount), lastBlockName.id, rewardPercentiles.toPercentilesJson())
        return RpcCall(client, "eth_feeHistory", params, FeeHistory.serializer())
    }

    override fun getFeeHistory(
        blockCount: Long,
        lastBlockNumber: BlockId.Number,
        rewardPercentiles: DoubleArray,
    ): RpcRequest<FeeHistory, RpcError> {
        val params = rpcParams(FastHex.encodeWithPrefix(blockCount), lastBlockNumber.id, rewardPercentiles.toPercentilesJson())
        return RpcCall(client, "eth_feeHistory", params, FeeHistory.serializer())
    }

    override fun isNodeSyncing(): RpcRequest<SyncStatus, RpcError> {
        return RpcCall(client, "eth_syncing", RpcParams.EMPTY, SyncStatusSerializer)
    }

    override fun getBlockTransactionCount(blockId: BlockId): RpcRequest<Long, RpcError> {
        val params = rpcParams(blockId.id)
        val method = when (blockId) {
            is BlockId.Hash -> "eth_getBlockTransactionCountByHash"
            is BlockId.Number, is BlockId.Name -> "eth_getBlockTransactionCountByNumber"
        }
        return RpcCall(client, method, params, HexLongSerializer)
    }

    override fun getTransactionByBlockAndIndex(blockId: BlockId, index: Long): RpcRequest<RPCTransaction, RpcError> {
        val params = rpcParams(blockId.id, FastHex.encodeWithPrefix(index))
        val method = when (blockId) {
            is BlockId.Hash -> "eth_getTransactionByBlockHashAndIndex"
            is BlockId.Number, is BlockId.Name -> "eth_getTransactionByBlockNumberAndIndex"
        }
        return RpcCall(client, method, params, RPCTransaction.serializer())
    }

    override fun getRawTransactionByBlockAndIndex(blockId: BlockId, index: Long): RpcRequest<Bytes, RpcError> {
        val params = rpcParams(blockId.id, FastHex.encodeWithPrefix(index))
        val method = when (blockId) {
            is BlockId.Hash -> "eth_getRawTransactionByBlockHashAndIndex"
            is BlockId.Number, is BlockId.Name -> "eth_getRawTransactionByBlockNumberAndIndex"
        }
        return RpcCall(client, method, params, Bytes.serializer())
    }

    override fun getTransactionCount(address: Address, blockId: BlockId): RpcRequest<Long, RpcError> {
        return RpcCall(client, "eth_getTransactionCount", rpcParams(address, blockId.id), HexLongSerializer)
    }

    override fun getTransactionByHash(hash: Hash): RpcRequest<RPCTransaction?, RpcError> {
        return RpcCall(
            client,
            "eth_getTransactionByHash",
            rpcParams(hash),
            RPCTransaction.serializer().nullable,
        )
    }

    override fun getTransactionReceipt(hash: Hash): RpcRequest<TransactionReceipt?, RpcError> {
        return RpcCall(
            client,
            "eth_getTransactionReceipt",
            rpcParams(hash),
            TransactionReceipt.serializer().nullable,
        )
    }

    override fun getBlockReceipts(blockId: BlockId): RpcRequest<List<TransactionReceipt>?, RpcError> {
        return RpcCall(
            client,
            "eth_getBlockReceipts",
            rpcParams(blockId.id),
            ListSerializer(TransactionReceipt.serializer()).nullable,
        )
    }

    override fun sendRawTransaction(signedTransaction: ByteArray): RpcRequest<PendingTransaction, RpcError> {
        return RpcCall(
            client,
            "eth_sendRawTransaction",
            // wrapped without a copy, so it's sent as a hex string by Bytes' own serializer
            rpcParams(Bytes(signedTransaction)),
            Hash.serializer().map { PendingTransaction(it, this) },
        )
    }

    override fun fillTransaction(call: IntoCallRequest): RpcRequest<TransactionUnsigned, RpcError> {
        val callRequest = call.toCallRequest()
        if (!supportsFillTransaction) {
            return SuppliedRpcRequest { manuallyFillTransaction(callRequest) }
        }

        return RpcCall(
            client,
            "eth_fillTransaction",
            rpcParams(callRequest),
            FilledTransactionDeserializer,
        ).orElse { err ->
            when {
                // If eth_fillTransaction is not supported, fallback to manually filling the transaction.
                err.isMethodNotFound -> {
                    supportsFillTransaction = false
                    manuallyFillTransaction(callRequest)
                }

                else -> failure(err)
            }
        }
    }

    private suspend fun manuallyFillTransaction(original: CallRequest): Result<TransactionUnsigned, RpcError> {
        val request = if (original.chainId == -1L) original.copy { chainId = provider.chainId } else original

        var unsigned = request.toUnsignedTransactionOrNull()
        if (unsigned != null) {
            return success(unsigned)
        }

        if (request.blobVersionedHashes != null && request.to == null) {
            return failure(
                RpcError(
                    RpcError.CODE_CALL_FAILED,
                    "Cannot fill blob transaction, missing 'to' field",
                ),
            )
        }

        val sender = request.from
        val needsNonce = request.nonce < 0L
        if (needsNonce && sender == null) {
            return failure(
                RpcError(
                    RpcError.CODE_CALL_FAILED,
                    "Cannot estimate nonce, 'from' field is not set",
                ),
            )
        }

        val txFeesSet = request.gasPrice != null || (request.gasTipCap != null && request.gasFeeCap != null)
        val blobFeesSet = request.blobVersionedHashes == null || request.blobFeeCap != null

        // request the missing pieces concurrently
        val (nonceResult, gasLimitResult, feeHistoryResult) = coroutineScope {
            val nonceFut = if (needsNonce) {
                async { provider.getTransactionCount(sender!!, BlockId.LATEST).send() }
            } else {
                null
            }

            val gasLimitFut = when {
                request.gas >= 21000L -> null
                else -> async { provider.estimateGas(request, BlockId.LATEST).send() }
            }

            val feeHistoryFut = when {
                txFeesSet && blobFeesSet -> null
                else -> async { fillTransactionFeeHistory.send() }
            }

            Triple(nonceFut?.await(), gasLimitFut?.await(), feeHistoryFut?.await())
        }

        val call = request.toBuilder()
        if (nonceResult != null) {
            if (nonceResult.isFailure()) {
                return nonceResult
            }

            call.nonce(nonceResult.unwrap())
        }

        if (gasLimitResult != null) {
            if (gasLimitResult.isFailure()) {
                return gasLimitResult
            }

            call.gas(gasLimitResult.unwrap())
        }

        if (feeHistoryResult != null) {
            if (feeHistoryResult.isFailure()) {
                return feeHistoryResult
            }

            val feeHistory = feeHistoryResult.unwrap()
            if (!txFeesSet) {
                val rewards = feeHistory.rewards!!.mapNotNull {
                    val r = it.firstOrNull()
                    if (r == null || r <= bigIntegerOf(0)) null else r
                }.sorted()

                val medianReward = when {
                    rewards.isEmpty() -> bigIntegerOf(1)
                    rewards.size % 2 == 0 -> (rewards[rewards.size / 2 - 1] + rewards[rewards.size / 2]) / BIGINT_TWO
                    else -> rewards[rewards.size / 2]
                }.max(bigIntegerOf(1))

                when {
                    // if eip1559 is supported, fill its fields
                    feeHistory.nextBaseFeePerGas > bigIntegerOf(0) -> {
                        call.gasFeeCap(feeHistory.nextBaseFeePerGas + medianReward)
                        call.gasTipCap(medianReward)
                    }

                    // else fallback to legacy gas price
                    else -> call.gasPrice(medianReward)
                }
            }

            if (!blobFeesSet) {
                // Blob gas has no priority fee - the base fee is fully burned and the cap only limits what we're
                // willing to pay, so overshooting is free. Double it for headroom: the blob base fee can keep rising
                // between now and inclusion, and a blob transaction that ends up underpriced needs a 100% fee bump
                // to be replaced (unlike the usual 10%), so it's effectively stuck until fees fall back.
                call.blobFeeCap(feeHistory.nextBaseFeePerBlobGas * BIGINT_TWO)
            }
        }

        val filled = call.build()
        unsigned = filled.toUnsignedTransactionOrNull()
        if (unsigned != null) {
            return success(unsigned)
        }

        val data = Kotlinx.DEFAULT.encodeToJsonElement(CallRequestSerializer, filled)
        return failure(RpcError(RpcError.CODE_CALL_FAILED, "Failed to manually fill transaction", data))
    }

    override fun getLogs(filter: LogFilter): RpcRequest<List<Log>, RpcError> {
        return RpcCall(
            client,
            "eth_getLogs",
            rpcParams(filter),
            ListSerializer(Log.serializer()),
        )
    }

    override fun watchLogs(filter: LogFilter): RpcRequest<FilterPoller<Log>, RpcError> {
        return RpcCall(
            client,
            "eth_newFilter",
            rpcParams(filter),
            String.serializer().map { id -> FilterPoller(id, this, ListSerializer(Log.serializer())) },
        )
    }

    override fun watchNewBlockHashes(): RpcRequest<FilterPoller<Hash>, RpcError> {
        return RpcCall(
            client,
            "eth_newBlockFilter",
            RpcParams.EMPTY,
            String.serializer().map { id -> FilterPoller(id, this, ListSerializer(Hash.serializer())) },
        )
    }

    override fun watchNewPendingTransactionHashes(): RpcRequest<FilterPoller<Hash>, RpcError> {
        return RpcCall(
            client,
            "eth_newPendingTransactionFilter",
            RpcParams.EMPTY,
            String.serializer().map { id -> FilterPoller(id, this, ListSerializer(Hash.serializer())) },
        )
    }

    override fun watchNewPendingTransactions(): RpcRequest<FilterPoller<RPCTransaction>, RpcError> {
        return RpcCall(
            client,
            "eth_newPendingTransactionFilter",
            rpcParams(true),
            String.serializer().map { id -> FilterPoller(id, this, ListSerializer(RPCTransaction.serializer())) },
        )
    }

    override fun subscribeNewPendingTransactionHashes(): RpcSubscribe<Hash, RpcError> {
        return RpcSubscribeCall(client, rpcParams("newPendingTransactions"), Hash.serializer())
    }

    override fun subscribeNewPendingTransactions(): RpcSubscribe<RPCTransaction, RpcError> {
        return RpcSubscribeCall(
            client,
            rpcParams("newPendingTransactions", true),
            RPCTransaction.serializer(),
        )
    }

    override fun subscribeNewHeads(): RpcSubscribe<BlockWithHashes, RpcError> {
        return RpcSubscribeCall(client, rpcParams("newHeads"), BlockWithHashes.serializer())
    }

    override fun subscribeLogs(filter: LogFilter): RpcSubscribe<Log, RpcError> {
        return RpcSubscribeCall(client, rpcParams("logs", filter), Log.serializer())
    }

    //-----------------------------------------------------------------------------------------------------------------
    //                                  DebugApi implementation
    //-----------------------------------------------------------------------------------------------------------------
    override fun getRawBlockHeader(blockId: BlockId): RpcRequest<Bytes, RpcError> {
        val params = rpcParams(blockId.id)
        return RpcCall(client, "debug_getRawHeader", params, Bytes.serializer())
    }

    override fun getRawBlockWithTransactions(blockId: BlockId): RpcRequest<Bytes, RpcError> {
        val params = rpcParams(blockId.id)
        return RpcCall(client, "debug_getRawBlock", params, Bytes.serializer())
    }

    override fun getRawReceipts(blockId: BlockId): RpcRequest<List<Bytes>, RpcError> {
        val params = rpcParams(blockId.id)
        return RpcCall(client, "debug_getRawReceipts", params, ListSerializer(Bytes.serializer()))
    }

    override fun getRawTransaction(hash: Hash): RpcRequest<Bytes?, RpcError> {
        return RpcCall(
            client,
            "debug_getRawTransaction",
            rpcParams(hash),
            // empty bytes mean the node has no such transaction, as null does
            Bytes.serializer().nullable.map { bytes -> bytes?.takeIf { it.size > 0 } },
        )
    }

    override fun printBlock(number: Long): RpcRequest<String, RpcError> {
        val params = rpcParams(number)
        return RpcCall(client, "debug_printBlock", params, String.serializer())
    }

    override fun <T : Any> traceCall(
        call: IntoCallRequest,
        blockId: BlockId,
        config: TracerConfig<T>,
    ): RpcRequest<T, RpcError> {
        // T isn't reified here, so TracerConfig<T>'s serializer is passed explicitly
        val params = buildRpcParams {
            add(call.toCallRequest())
            add(blockId.id)
            add(config, TracerConfigSerializer)
        }
        return RpcCall(client, "debug_traceCall", params, config.tracer.resultSerializer)
    }

    override fun <T : Any> traceCallMany(
        blockId: BlockId,
        calls: List<IntoCallRequest>,
        config: TracerConfig<T>,
        transactionIndex: Int,
    ): RpcRequest<List<T>, RpcError> {
        val bundle = CallManyBundle(calls.map { it.toCallRequest() }, config.blockOverrides)
        val ctx = CallManyContext(blockId, transactionIndex)

        // one list of traces per bundle, and the call sends a single bundle
        val traces = ListSerializer(ListSerializer(config.tracer.resultSerializer))
        val params = buildRpcParams {
            add(listOf(bundle))
            add(ctx)
            add(config, TracerConfigSerializer)
        }
        return RpcCall(client, "debug_traceCallMany", params, traces.map { it.firstOrNull() ?: emptyList() })
    }

    override fun <T : Any> traceTransaction(txHash: Hash, config: TracerConfig<T>): RpcRequest<T, RpcError> {
        val params = buildRpcParams {
            add(txHash)
            add(config, TracerConfigSerializer)
        }
        return RpcCall(client, "debug_traceTransaction", params, config.tracer.resultSerializer)
    }

    override fun <T : Any> traceBlock(
        blockId: BlockId,
        config: TracerConfig<T>,
    ): RpcRequest<List<TxTraceResult<T>>, RpcError> {
        val params = buildRpcParams {
            add(blockId.id)
            add(config, TracerConfigSerializer)
        }
        val method = when (blockId) {
            is BlockId.Hash -> "debug_traceBlockByHash"
            is BlockId.Number, is BlockId.Name -> "debug_traceBlockByNumber"
        }
        return RpcCall(client, method, params, ListSerializer(TxTraceResult.serializer(config.tracer.resultSerializer)))
    }

    //-----------------------------------------------------------------------------------------------------------------
    //                                  NetApi implementation
    //-----------------------------------------------------------------------------------------------------------------
    override fun isListening(): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "net_listening", RpcParams.EMPTY, Boolean.serializer())
    }

    override fun getPeerCount(): RpcRequest<Long, RpcError> {
        return RpcCall(client, "net_peerCount", RpcParams.EMPTY, HexLongSerializer)
    }

    override fun getVersion(): RpcRequest<String, RpcError> {
        return RpcCall(client, "net_version", RpcParams.EMPTY, String.serializer())
    }

    //-----------------------------------------------------------------------------------------------------------------
    //                                  TxpoolApi implementation
    //-----------------------------------------------------------------------------------------------------------------
    override fun txpoolContent(): RpcRequest<TxpoolContent, RpcError> {
        return RpcCall(client, "txpool_content", RpcParams.EMPTY, TxpoolContent.serializer())
    }

    override fun txpoolContentFrom(address: Address): RpcRequest<TxpoolContentFromAddress, RpcError> {
        val params = rpcParams(address)
        return RpcCall(client, "txpool_contentFrom", params, TxpoolContentFromAddress.serializer())
    }

    override fun txpoolStatus(): RpcRequest<TxpoolStatus, RpcError> {
        return RpcCall(client, "txpool_status", RpcParams.EMPTY, TxpoolStatus.serializer())
    }

    override fun txpoolInspect(): RpcRequest<TxpoolInspectResult, RpcError> {
        return RpcCall(client, "txpool_inspect", RpcParams.EMPTY, TxpoolInspectResult.serializer())
    }

    //-----------------------------------------------------------------------------------------------------------------
    //                                       Web3Api implementation
    //-----------------------------------------------------------------------------------------------------------------

    override fun getClientVersion(): RpcRequest<String, RpcError> {
        return RpcCall(client, "web3_clientVersion", RpcParams.EMPTY, String.serializer())
    }

    //-----------------------------------------------------------------------------------------------------------------
    //                                       AutoCloseable implementation
    //-----------------------------------------------------------------------------------------------------------------

    override fun close() {
        client.close()
    }

    /**
     * Error returned when creating a [Provider] using [fromUrl] fails.
     * */
    sealed interface Error : ThrowableError

    /**
     * Error indicating the provided [url] has an unsupported protocol.
     * */
    data class UnsupportedUrlProtocol(val url: String) : Error

    /**
     * Error indicating the chain id could not be obtained from the [url] due to [error].
     * */
    data class UnableToGetChainId(val url: String, val error: RpcError) : Error

    companion object {
        private val BIGINT_TWO = bigIntegerOf(2L)

        /**
         * Start building a [Provider] for [url].
         *
         * Supported URL protocols:
         * - http/https
         * - ws/wss
         *
         * @see ProviderBuilder
         * */
        @JvmStatic
        fun builder(url: String): ProviderBuilder = ProviderBuilder(url)
    }
}

// Private inline extension functions for common decoding patterns

/** Decodes the transaction in an `eth_fillTransaction` result, from its RLP-encoded `raw` field. */
private object FilledTransactionDeserializer : DeserializationStrategy<TransactionUnsigned> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("io.ethers.providers.FilledTransaction") {
        element("raw", Bytes.serializer().descriptor, isOptional = true)
    }

    override fun deserialize(decoder: Decoder): TransactionUnsigned {
        var raw: Bytes? = null
        decoder.decodeStructure(descriptor) {
            while (true) {
                val index = decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) break
                raw = decodeSerializableElement(descriptor, index, Bytes.serializer())
            }
        }
        return raw?.let { TransactionUnsigned.rlpDecode(it.asByteArray()) } ?: throw IllegalStateException("Invalid response")
    }
}

/**
 * Decodes one transaction's outcome in an `eth_callMany` result: its output, under whichever of `output`, `result`
 * or `value` the node uses, or its `error`. The last of them in the object wins, so the object is read as a map,
 * whose entries both the text and the tree decoder visit in order; other entries are skipped.
 */
private object CallManyResultSerializer : KSerializer<Result<Bytes, CallFailedError>> {
    override val descriptor: SerialDescriptor = MapSerializer(String.serializer(), JsonElement.serializer()).descriptor

    override fun serialize(encoder: Encoder, value: Result<Bytes, CallFailedError>) = throw UnsupportedOperationException()

    override fun deserialize(decoder: Decoder): Result<Bytes, CallFailedError> {
        var result: Result<Bytes, CallFailedError>? = null
        decoder.decodeStructure(descriptor) {
            while (true) {
                val keyIndex = decodeElementIndex(descriptor)
                if (keyIndex == CompositeDecoder.DECODE_DONE) break
                val key = decodeStringElement(descriptor, keyIndex)
                // a map's value has its own index, which also moves the decoder past the key
                val valueIndex = decodeElementIndex(descriptor)
                when (key) {
                    "output", "result", "value" -> result = success(decodeSerializableElement(descriptor, valueIndex, Bytes.serializer()))
                    // any primitive's content, as nodes have sent numbers or null for the message
                    "error" -> result = failure(CallFailedError(decodeSerializableElement(descriptor, valueIndex, JsonPrimitive.serializer()).content))
                    else -> decodeSerializableElement(descriptor, valueIndex, JsonElement.serializer())
                }
            }
        }
        return result ?: throw IllegalStateException("No result or error found in response")
    }
}

// JSON-RPC spec defines reward percentiles as JSON numbers. Clients like geth and reth reject any other
// representation (e.g. strings) with "invalid params".
private fun DoubleArray.toPercentilesJson(): JsonArray = JsonArray(map(::JsonPrimitive))
