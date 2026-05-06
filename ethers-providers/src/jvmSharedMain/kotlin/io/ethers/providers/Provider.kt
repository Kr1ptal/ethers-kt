package io.ethers.providers

import io.ethers.core.FastHex
import io.ethers.core.Kotlinx
import io.ethers.core.Result
import io.ethers.core.asBytes
import io.ethers.core.asBytesOrNull
import io.ethers.core.asHash
import io.ethers.core.asHexBigInteger
import io.ethers.core.asHexByteArray
import io.ethers.core.asHexLong
import io.ethers.core.failure
import io.ethers.core.isFailure
import io.ethers.core.json.JsonElement
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
import io.ethers.core.types.TransactionReceipt
import io.ethers.core.types.TxpoolContent
import io.ethers.core.types.TxpoolContentFromAddress
import io.ethers.core.types.TxpoolInspectResult
import io.ethers.core.types.TxpoolStatus
import io.ethers.core.types.tracers.TracerConfig
import io.ethers.core.types.tracers.TxTraceResult
import io.ethers.core.types.transaction.TransactionUnsigned
import io.ethers.core.unwrapOrReturn
import io.ethers.providers.Provider.Companion.fromUrl
import io.ethers.providers.middleware.Middleware
import io.ethers.providers.types.CallFailedError
import io.ethers.providers.types.CallManyBundle
import io.ethers.providers.types.CallManyContext
import io.ethers.providers.types.FilterPoller
import io.ethers.providers.types.PendingTransaction
import io.ethers.providers.types.RpcCall
import io.ethers.providers.types.RpcRequest
import io.ethers.providers.types.RpcSubscribe
import io.ethers.providers.types.RpcSubscribeCall
import io.ethers.providers.types.SuppliedRpcRequest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import java.math.BigInteger
import kotlinx.serialization.json.JsonElement as KJsonElement

@Suppress("MoveLambdaOutsideParentheses")
class Provider(override val client: JsonRpcClient, override val chainId: Long) : Middleware {
    // it's ever only set to false if eth_fillTransaction is not supported, so it's fine if it's a bit racy
    private var supportsFillTransaction = true
    private val fillTransactionFeeHistory = getFeeHistory(10, BlockId.LATEST, listOf("20".toBigInteger()))

    override val inner: Middleware?
        get() = null

    override val provider: Provider
        get() = this

    //-----------------------------------------------------------------------------------------------------------------
    //                                  EthApi implementation
    //-----------------------------------------------------------------------------------------------------------------
    override fun getChainId(): RpcRequest<Long, RpcError> {
        return RpcCall(client, "eth_chainId", EMPTY_ARRAY) { it.jsonPrimitive.asHexLong() }
    }

    override fun getBlockNumber(): RpcRequest<Long, RpcError> {
        return RpcCall(client, "eth_blockNumber", EMPTY_ARRAY) { it.jsonPrimitive.asHexLong() }
    }

    override fun getBalance(address: Address, blockId: BlockId): RpcRequest<BigInteger, RpcError> {
        return RpcCall(client, "eth_getBalance", arrayOf(address, blockId.id)) { it.jsonPrimitive.asHexBigInteger() }
    }

    override fun getBlockHeader(blockId: BlockId): RpcRequest<BlockWithHashes?, RpcError> {
        val params = arrayOf(blockId.id)
        val method = when (blockId) {
            is BlockId.Hash -> "eth_getHeaderByHash"
            is BlockId.Number, is BlockId.Name -> "eth_getHeaderByNumber"
        }
        return RpcCall(client, method, params, { it.decodeAsOrNull<BlockWithHashes>() })
    }

    override fun getBlockWithHashes(blockId: BlockId): RpcRequest<BlockWithHashes?, RpcError> {
        return getBlock(blockId, false, BlockWithHashes::class.java)
    }

    override fun getBlockWithTransactions(blockId: BlockId): RpcRequest<BlockWithTransactions?, RpcError> {
        return getBlock(blockId, true, BlockWithTransactions::class.java)
    }

    private fun <T, B : Block<T>> getBlock(
        blockId: BlockId,
        fullTransactions: Boolean,
        responseType: Class<B>,
    ): RpcRequest<B?, RpcError> {
        val params = arrayOf<Any>(blockId.id, fullTransactions)
        val method = when (blockId) {
            is BlockId.Hash -> "eth_getBlockByHash"
            is BlockId.Number, is BlockId.Name -> "eth_getBlockByNumber"
        }
        return RpcCall(client, method, params, { it.decodeAsOrNull(responseType) })
    }

    override fun getUncleBlockHeader(blockId: BlockId, index: Long): RpcRequest<BlockWithHashes?, RpcError> {
        val params = arrayOf(blockId.id, FastHex.encodeWithPrefix(index))
        val method = when (blockId) {
            is BlockId.Hash -> "eth_getUncleByBlockHashAndIndex"
            is BlockId.Number, is BlockId.Name -> "eth_getUncleByBlockNumberAndIndex"
        }
        return RpcCall(client, method, params, { it.decodeAsOrNull<BlockWithHashes>() })
    }

    override fun getUncleBlocksCount(blockId: BlockId): RpcRequest<Long, RpcError> {
        val params = arrayOf(blockId.id)
        val method = when (blockId) {
            is BlockId.Hash -> "eth_getUncleCountByBlockHash"
            is BlockId.Number, is BlockId.Name -> "eth_getUncleCountByBlockNumber"
        }
        return RpcCall(client, method, params) { it.jsonPrimitive.asHexLong() }
    }

    override fun getCode(address: Address, blockId: BlockId): RpcRequest<Bytes, RpcError> {
        val params = arrayOf(address, blockId.id)
        return RpcCall(client, "eth_getCode", params, Bytes::class.java)
    }

    override fun getStorage(address: Address, key: Hash, blockId: BlockId): RpcRequest<Hash, RpcError> {
        val params = arrayOf(address, key, blockId.id)
        return RpcCall(client, "eth_getStorageAt", params, Hash::class.java)
    }

    override fun call(
        call: IntoCallRequest,
        blockId: BlockId,
        stateOverride: StateOverride?,
        blockOverride: BlockOverride?,
    ): RpcRequest<Bytes, RpcError> {
        // create minimal params array - some RPC's don't support stateOverride or blockOverride
        val params = when {
            blockOverride != null -> arrayOf(call.toCallRequest(), blockId.id, stateOverride, blockOverride)
            stateOverride != null -> arrayOf(call.toCallRequest(), blockId.id, stateOverride)
            else -> arrayOf(call.toCallRequest(), blockId.id)
        }

        return RpcCall(client, "eth_call", params, Bytes::class.java)
    }

    override fun callMany(
        blockId: BlockId,
        calls: List<IntoCallRequest>,
        transactionIndex: Int,
        stateOverride: StateOverride?,
        blockOverride: BlockOverride?,
    ): RpcRequest<List<Result<Bytes, CallFailedError>>, RpcError> {
        val bundle = CallManyBundle(calls, blockOverride)
        val ctx = CallManyContext(blockId, transactionIndex)

        return RpcCall(client, "eth_callMany", arrayOf(bundle, ctx, stateOverride)) { element ->
            element.jsonArray.map { item ->
                var result: Result<Bytes, CallFailedError>? = null

                item.jsonObject.forEach { (field, value) ->
                    when (field) {
                        "output", "result", "value" -> result = success(value.jsonPrimitive.asBytes())
                        "error" -> result = failure(CallFailedError(value.jsonPrimitive.content))
                    }
                }

                result ?: throw IllegalStateException("No result or error found in response")
            }
        }
    }

    override fun estimateGas(call: IntoCallRequest, blockId: BlockId): RpcRequest<Long, RpcError> {
        val params = arrayOf(call.toCallRequest(), blockId.id)
        return RpcCall(client, "eth_estimateGas", params) { it.jsonPrimitive.asHexLong() }
    }

    override fun createAccessList(call: IntoCallRequest, blockId: BlockId): RpcRequest<CreateAccessList, RpcError> {
        val params = arrayOf(call.toCallRequest(), blockId.id)
        return RpcCall(client, "eth_createAccessList", params, CreateAccessList::class.java)
    }

    override fun getGasPrice(): RpcRequest<BigInteger, RpcError> {
        return RpcCall(client, "eth_gasPrice", EMPTY_ARRAY) { it.jsonPrimitive.asHexBigInteger() }
    }

    override fun getBlobBaseFee(): RpcRequest<BigInteger, RpcError> {
        return RpcCall(client, "eth_blobBaseFee", EMPTY_ARRAY) { it.jsonPrimitive.asHexBigInteger() }
    }

    override fun getMaxPriorityFeePerGas(): RpcRequest<BigInteger, RpcError> {
        return RpcCall(client, "eth_maxPriorityFeePerGas", EMPTY_ARRAY) { it.jsonPrimitive.asHexBigInteger() }
    }

    override fun getFeeHistory(
        blockCount: Long,
        lastBlockName: BlockId.Name,
        rewardPercentiles: List<BigInteger>,
    ): RpcRequest<FeeHistory, RpcError> {
        val params = arrayOf(FastHex.encodeWithPrefix(blockCount), lastBlockName.id, rewardPercentiles)
        return RpcCall(client, "eth_feeHistory", params, FeeHistory::class.java)
    }

    override fun getFeeHistory(
        blockCount: Long,
        lastBlockNumber: BlockId.Number,
        rewardPercentiles: List<BigInteger>,
    ): RpcRequest<FeeHistory, RpcError> {
        val params = arrayOf(FastHex.encodeWithPrefix(blockCount), lastBlockNumber.id, rewardPercentiles)
        return RpcCall(client, "eth_feeHistory", params, FeeHistory::class.java)
    }

    override fun isNodeSyncing(): RpcRequest<SyncStatus, RpcError> {
        return RpcCall(client, "eth_syncing", EMPTY_ARRAY) { it.decodeAs<SyncStatus>() }
    }

    override fun getBlockTransactionCount(blockId: BlockId): RpcRequest<Long, RpcError> {
        val params = arrayOf(blockId.id)
        val method = when (blockId) {
            is BlockId.Hash -> "eth_getBlockTransactionCountByHash"
            is BlockId.Number, is BlockId.Name -> "eth_getBlockTransactionCountByNumber"
        }
        return RpcCall(client, method, params) { it.jsonPrimitive.asHexLong() }
    }

    override fun getTransactionByBlockAndIndex(blockId: BlockId, index: Long): RpcRequest<RPCTransaction, RpcError> {
        val params = arrayOf(blockId.id, FastHex.encodeWithPrefix(index))
        val method = when (blockId) {
            is BlockId.Hash -> "eth_getTransactionByBlockHashAndIndex"
            is BlockId.Number, is BlockId.Name -> "eth_getTransactionByBlockNumberAndIndex"
        }
        return RpcCall(client, method, params, RPCTransaction::class.java)
    }

    override fun getRawTransactionByBlockAndIndex(blockId: BlockId, index: Long): RpcRequest<Bytes, RpcError> {
        val params = arrayOf(blockId.id, FastHex.encodeWithPrefix(index))
        val method = when (blockId) {
            is BlockId.Hash -> "eth_getRawTransactionByBlockHashAndIndex"
            is BlockId.Number, is BlockId.Name -> "eth_getRawTransactionByBlockNumberAndIndex"
        }
        return RpcCall(client, method, params, Bytes::class.java)
    }

    override fun getTransactionCount(address: Address, blockId: BlockId): RpcRequest<Long, RpcError> {
        return RpcCall(client, "eth_getTransactionCount", arrayOf(address, blockId.id)) { it.jsonPrimitive.asHexLong() }
    }

    override fun getTransactionByHash(hash: Hash): RpcRequest<RPCTransaction?, RpcError> {
        return RpcCall(
            client,
            "eth_getTransactionByHash",
            arrayOf(hash),
            { it.decodeAsOrNull<RPCTransaction>() },
        )
    }

    override fun getTransactionReceipt(hash: Hash): RpcRequest<TransactionReceipt?, RpcError> {
        return RpcCall(
            client,
            "eth_getTransactionReceipt",
            arrayOf(hash),
            { it.decodeAsOrNull<TransactionReceipt>() },
        )
    }

    override fun getBlockReceipts(blockId: BlockId): RpcRequest<List<TransactionReceipt>?, RpcError> {
        return RpcCall(
            client,
            "eth_getBlockReceipts",
            arrayOf(blockId.id),
            { if (it is JsonNull) null else it.decodeListAs<TransactionReceipt>() },
        )
    }

    override fun sendRawTransaction(signedTransaction: ByteArray): RpcRequest<PendingTransaction, RpcError> {
        return RpcCall(
            client,
            "eth_sendRawTransaction",
            arrayOf(signedTransaction),
            { PendingTransaction(it.jsonPrimitive.asHash(), this) },
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
            arrayOf(callRequest),
            { element ->
                val raw = element.jsonObject["raw"]
                val ret = if (raw != null) TransactionUnsigned.rlpDecode(raw.jsonPrimitive.asHexByteArray()) else null
                ret ?: throw IllegalStateException("Invalid response")
            },
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

    private fun manuallyFillTransaction(original: CallRequest): Result<TransactionUnsigned, RpcError> {
        val call = CallRequest(original)
        if (call.chainId == -1L) {
            call.chainId = provider.chainId
        }

        var unsigned = call.toUnsignedTransactionOrNull()
        if (unsigned != null) {
            return success(unsigned)
        }

        if (call.blobVersionedHashes != null && call.to == null) {
            return failure(
                RpcError(
                    RpcError.CODE_CALL_FAILED,
                    "Cannot fill blob transaction, missing 'to' field",
                ),
            )
        }

        val sender = call.from
        val nonceFut = when {
            call.nonce >= 0L -> null

            sender == null -> return failure(
                RpcError(
                    RpcError.CODE_CALL_FAILED,
                    "Cannot estimate nonce, 'from' field is not set",
                ),
            )

            else -> provider.getTransactionCount(sender, BlockId.LATEST).sendAsync()
        }

        val gasLimitFut = when {
            call.gas >= 21000L -> null
            else -> provider.estimateGas(call, BlockId.LATEST).sendAsync()
        }

        val txFeesSet = call.gasPrice != null || (call.gasTipCap != null && call.gasFeeCap != null)
        val blobFeesSet = call.blobVersionedHashes == null || call.blobFeeCap != null
        val feeHistoryFut = when {
            txFeesSet && blobFeesSet -> null
            else -> fillTransactionFeeHistory.sendAsync()
        }

        val nonceResult = nonceFut?.get()
        val gasLimitResult = gasLimitFut?.get()
        val feeHistoryResult = feeHistoryFut?.get()

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
                    if (r == null || r <= BigInteger.ZERO) null else r
                }.sorted()

                val medianReward = when {
                    rewards.isEmpty() -> BigInteger.ONE
                    rewards.size % 2 == 0 -> (rewards[rewards.size / 2 - 1] + rewards[rewards.size / 2]) / BigInteger.TWO
                    else -> rewards[rewards.size / 2]
                }.max(BigInteger.ONE)

                when {
                    // if eip1559 is supported, fill its fields
                    feeHistory.nextBaseFeePerGas > BigInteger.ZERO -> {
                        call.gasFeeCap(feeHistory.nextBaseFeePerGas + medianReward)
                        call.gasTipCap(medianReward)
                    }

                    // else fallback to legacy gas price
                    else -> call.gasPrice(medianReward)
                }
            }

            if (!blobFeesSet) {
                call.blobFeeCap(feeHistory.nextBaseFeePerBlobGas)
            }
        }

        unsigned = call.toUnsignedTransactionOrNull()
        if (unsigned != null) {
            return success(unsigned)
        }

        val data = JsonElement(Kotlinx.DEFAULT.encodeToString(CallRequestSerializer, call))
        return failure(RpcError(RpcError.CODE_CALL_FAILED, "Failed to manually fill transaction", data))
    }

    override fun getLogs(filter: LogFilter): RpcRequest<List<Log>, RpcError> {
        return RpcCall(
            client,
            "eth_getLogs",
            arrayOf(filter),
            { it.decodeListAs<Log>() },
        )
    }

    override fun watchLogs(filter: LogFilter): RpcRequest<FilterPoller<Log>, RpcError> {
        return RpcCall(
            client,
            "eth_newFilter",
            arrayOf(filter),
            { element -> FilterPoller(element.jsonPrimitive.content, this, { el -> el.decodeListAs<Log>() }) },
        )
    }

    override fun watchNewBlockHashes(): RpcRequest<FilterPoller<Hash>, RpcError> {
        return RpcCall(
            client,
            "eth_newBlockFilter",
            EMPTY_ARRAY,
            { element -> FilterPoller(element.jsonPrimitive.content, this, { el -> el.jsonArray.map { it.jsonPrimitive.asHash() } }) },
        )
    }

    override fun watchNewPendingTransactionHashes(): RpcRequest<FilterPoller<Hash>, RpcError> {
        return RpcCall(
            client,
            "eth_newPendingTransactionFilter",
            EMPTY_ARRAY,
            { element -> FilterPoller(element.jsonPrimitive.content, this, { el -> el.jsonArray.map { it.jsonPrimitive.asHash() } }) },
        )
    }

    override fun watchNewPendingTransactions(): RpcRequest<FilterPoller<RPCTransaction>, RpcError> {
        return RpcCall(
            client,
            "eth_newPendingTransactionFilter",
            arrayOf(true),
            { element -> FilterPoller(element.jsonPrimitive.content, this, { el -> el.decodeListAs<RPCTransaction>() }) },
        )
    }

    override fun subscribeNewPendingTransactionHashes(): RpcSubscribe<Hash, RpcError> {
        return RpcSubscribeCall(client, arrayOf("newPendingTransactions"), { it.jsonPrimitive.asHash() })
    }

    override fun subscribeNewPendingTransactions(): RpcSubscribe<RPCTransaction, RpcError> {
        return RpcSubscribeCall(
            client,
            arrayOf<Any>("newPendingTransactions", true),
            { it.decodeAs<RPCTransaction>() },
        )
    }

    override fun subscribeNewHeads(): RpcSubscribe<BlockWithHashes, RpcError> {
        return RpcSubscribeCall(client, arrayOf("newHeads"), { it.decodeAs<BlockWithHashes>() })
    }

    override fun subscribeLogs(filter: LogFilter): RpcSubscribe<Log, RpcError> {
        return RpcSubscribeCall(client, arrayOf("logs", filter), { it.decodeAs<Log>() })
    }

    //-----------------------------------------------------------------------------------------------------------------
    //                                  DebugApi implementation
    //-----------------------------------------------------------------------------------------------------------------
    override fun getRawBlockHeader(blockId: BlockId): RpcRequest<Bytes, RpcError> {
        val params = arrayOf(blockId.id)
        return RpcCall(client, "debug_getRawHeader", params, Bytes::class.java)
    }

    override fun getRawBlockWithTransactions(blockId: BlockId): RpcRequest<Bytes, RpcError> {
        val params = arrayOf(blockId.id)
        return RpcCall(client, "debug_getRawBlock", params, Bytes::class.java)
    }

    override fun getRawReceipts(blockId: BlockId): RpcRequest<List<Bytes>, RpcError> {
        val params = arrayOf(blockId.id)
        return RpcCall(client, "debug_getRawReceipts", params) { it.decodeListAs<Bytes>() }
    }

    override fun getRawTransaction(hash: Hash): RpcRequest<Bytes?, RpcError> {
        return RpcCall(
            client,
            "debug_getRawTransaction",
            arrayOf(hash),
            { element ->
                if (element is JsonNull) null
                else element.jsonPrimitive.asBytesOrNull()
            },
        )
    }

    override fun printBlock(number: Long): RpcRequest<String, RpcError> {
        val params = arrayOf(number)
        return RpcCall(client, "debug_printBlock", params, String::class.java)
    }

    override fun <T : Any> traceCall(
        call: IntoCallRequest,
        blockId: BlockId,
        config: TracerConfig<T>,
    ): RpcRequest<T, RpcError> {
        val params = arrayOf(call.toCallRequest(), blockId.id, config)
        return RpcCall(client, "debug_traceCall", params) { config.tracer.decodeResult(Kotlinx.DEFAULT, it) }
    }

    override fun <T : Any> traceCallMany(
        blockId: BlockId,
        calls: List<IntoCallRequest>,
        config: TracerConfig<T>,
        transactionIndex: Int,
    ): RpcRequest<List<T>, RpcError> {
        val bundle = CallManyBundle(calls, config.blockOverrides)
        val ctx = CallManyContext(blockId, transactionIndex)

        return RpcCall(client, "debug_traceCallMany", arrayOf(arrayOf(bundle), ctx, config)) { element ->
            element.jsonArray.firstOrNull()?.jsonArray?.map { config.tracer.decodeResult(Kotlinx.DEFAULT, it) }
                ?: emptyList()
        }
    }

    override fun <T : Any> traceTransaction(txHash: Hash, config: TracerConfig<T>): RpcRequest<T, RpcError> {
        val params = arrayOf(txHash, config)
        return RpcCall(client, "debug_traceTransaction", params) { config.tracer.decodeResult(Kotlinx.DEFAULT, it) }
    }

    override fun <T : Any> traceBlock(
        blockId: BlockId,
        config: TracerConfig<T>,
    ): RpcRequest<List<TxTraceResult<T>>, RpcError> {
        val params = arrayOf(blockId.id, config)
        val method = when (blockId) {
            is BlockId.Hash -> "debug_traceBlockByHash"
            is BlockId.Number, is BlockId.Name -> "debug_traceBlockByNumber"
        }
        return RpcCall(client, method, params) { element ->
            element.jsonArray.map { item ->
                var txHash: Hash? = null
                var result: T? = null
                var error: String? = null

                item.jsonObject.forEach { (field, value) ->
                    when (field) {
                        "txHash" -> txHash = value.jsonPrimitive.asHash()
                        "result" -> result = config.tracer.decodeResult(Kotlinx.DEFAULT, value)
                        "error" -> error = value.jsonPrimitive.content
                    }
                }
                TxTraceResult(txHash, result, error)
            }
        }
    }

    //-----------------------------------------------------------------------------------------------------------------
    //                                  NetApi implementation
    //-----------------------------------------------------------------------------------------------------------------
    override fun isListening(): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "net_listening", EMPTY_ARRAY) { it.jsonPrimitive.boolean }
    }

    override fun getPeerCount(): RpcRequest<Long, RpcError> {
        return RpcCall(client, "net_peerCount", EMPTY_ARRAY) { it.jsonPrimitive.asHexLong() }
    }

    override fun getVersion(): RpcRequest<String, RpcError> {
        return RpcCall(client, "net_version", EMPTY_ARRAY, String::class.java)
    }

    //-----------------------------------------------------------------------------------------------------------------
    //                                  TxpoolApi implementation
    //-----------------------------------------------------------------------------------------------------------------
    override fun txpoolContent(): RpcRequest<TxpoolContent, RpcError> {
        return RpcCall(client, "txpool_content", EMPTY_ARRAY, TxpoolContent::class.java)
    }

    override fun txpoolContentFrom(address: Address): RpcRequest<TxpoolContentFromAddress, RpcError> {
        val params = arrayOf(address)
        return RpcCall(client, "txpool_contentFrom", params, TxpoolContentFromAddress::class.java)
    }

    override fun txpoolStatus(): RpcRequest<TxpoolStatus, RpcError> {
        return RpcCall(client, "txpool_status", EMPTY_ARRAY, TxpoolStatus::class.java)
    }

    override fun txpoolInspect(): RpcRequest<TxpoolInspectResult, RpcError> {
        return RpcCall(client, "txpool_inspect", EMPTY_ARRAY, TxpoolInspectResult::class.java)
    }

    //-----------------------------------------------------------------------------------------------------------------
    //                                       Web3Api implementation
    //-----------------------------------------------------------------------------------------------------------------

    override fun getClientVersion(): RpcRequest<String, RpcError> {
        return RpcCall(client, "web3_clientVersion", EMPTY_ARRAY, String::class.java)
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
    sealed interface Error : Result.Error

    /**
     * Error indicating the provided [url] has an unsupported protocol.
     * */
    data class UnsupportedUrlProtocol(val url: String) : Error

    /**
     * Error indicating the chain id could not be obtained from the [url] due to [error].
     * */
    data class UnableToGetChainId(val url: String, val error: RpcError) : Error

    companion object {
        private val EMPTY_ARRAY = emptyArray<Any>()
        private val PROTO_HTTPS = "^(https?)://.+$".toRegex()
        private val PROTO_WSS = "^(wss?)://.+$".toRegex()

        /**
         * Create a new [Provider] from the given [url] and optional [RpcClientConfig]. If no [chainId] is provided,
         * it tries to fetch it via `eth_chainId` RPC call.
         *
         * Supported URL protocols:
         * - http/https
         * - ws/wss
         * */
        @JvmStatic
        @JvmOverloads
        fun fromUrl(
            url: String,
            config: RpcClientConfig = RpcClientConfig(),
            chainId: Long = -1L,
        ): Result<Provider, Error> {
            val client = when {
                url.matches(PROTO_HTTPS) -> HttpClient(url, config)
                url.matches(PROTO_WSS) -> WsClient(url, config)
                else -> return failure(UnsupportedUrlProtocol(url))
            }

            @Suppress("NAME_SHADOWING")
            var chainId = chainId
            if (chainId == -1L) {
                chainId = getChainId(client).sendAwait().unwrapOrReturn { return failure(UnableToGetChainId(url, it)) }
            }

            return success(Provider(client, chainId))
        }

        private fun getChainId(client: JsonRpcClient): RpcRequest<Long, RpcError> {
            return RpcCall(client, "eth_chainId", EMPTY_ARRAY, { it.jsonPrimitive.asHexLong() })
        }
    }
}

// Private inline extension functions for common decoding patterns
private inline fun <reified T> KJsonElement.decodeAs(): T = io.ethers.core.Kotlinx.DEFAULT.decodeFromJsonElement(serializer(), this)

@Suppress("UNCHECKED_CAST")
private fun <T> KJsonElement.decodeAs(resultType: Class<T>): T = io.ethers.core.Kotlinx.DEFAULT.decodeFromJsonElement(
    serializer(resultType) as kotlinx.serialization.KSerializer<T>,
    this,
)

private inline fun <reified T> KJsonElement.decodeAsOrNull(): T? = if (this is JsonNull) null else decodeAs<T>()

private fun <T> KJsonElement.decodeAsOrNull(resultType: Class<T>): T? = if (this is JsonNull) null else decodeAs(resultType)

private inline fun <reified T> KJsonElement.decodeListAs(): List<T> = jsonArray.map { it.decodeAs<T>() }
