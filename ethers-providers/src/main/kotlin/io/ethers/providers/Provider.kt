package io.ethers.providers

import com.fasterxml.jackson.core.JsonParser
import io.ethers.core.FastHex
import io.ethers.core.Result
import io.ethers.core.forEachObjectField
import io.ethers.core.isField
import io.ethers.core.isNextTokenObjectEnd
import io.ethers.core.readBytesEmptyAsNull
import io.ethers.core.readHash
import io.ethers.core.readHexBigInteger
import io.ethers.core.readHexByteArray
import io.ethers.core.readHexLong
import io.ethers.core.readListOf
import io.ethers.core.readListOfHashes
import io.ethers.core.readOptionalValue
import io.ethers.core.success
import io.ethers.core.types.AccountOverride
import io.ethers.core.types.Address
import io.ethers.core.types.Block
import io.ethers.core.types.BlockId
import io.ethers.core.types.BlockOverride
import io.ethers.core.types.BlockWithHashes
import io.ethers.core.types.BlockWithTransactions
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.core.types.CreateAccessList
import io.ethers.core.types.FeeHistory
import io.ethers.core.types.Hash
import io.ethers.core.types.Log
import io.ethers.core.types.LogFilter
import io.ethers.core.types.RPCTransaction
import io.ethers.core.types.SyncStatus
import io.ethers.core.types.TransactionReceipt
import io.ethers.core.types.TxpoolContent
import io.ethers.core.types.TxpoolContentFromAddress
import io.ethers.core.types.TxpoolInspectResult
import io.ethers.core.types.TxpoolStatus
import io.ethers.core.types.tracers.TracerConfig
import io.ethers.core.types.tracers.TxTraceResult
import io.ethers.core.types.transaction.TransactionUnsigned
import io.ethers.providers.middleware.Middleware
import io.ethers.providers.types.FilterPoller
import io.ethers.providers.types.PendingTransaction
import io.ethers.providers.types.RpcCall
import io.ethers.providers.types.RpcRequest
import io.ethers.providers.types.RpcSubscribe
import io.ethers.providers.types.RpcSubscribeCall
import java.math.BigInteger
import java.util.Optional
import java.util.function.Function

@Suppress("MoveLambdaOutsideParentheses")
class Provider(override val client: JsonRpcClient) : Middleware {
    override val inner: Middleware?
        get() = null

    override val provider: Provider
        get() = this

    //-----------------------------------------------------------------------------------------------------------------
    //                                  EthApi implementation
    //-----------------------------------------------------------------------------------------------------------------
    override val chainId = RpcCall(client, "eth_chainId", emptyArray<Any>(), { it.readHexLong() })
        .sendAwait()
        .unwrap()

    override fun getBlockNumber(): RpcRequest<Long, RpcError> {
        return RpcCall(client, "eth_blockNumber", emptyArray<Any>()) { it.readHexLong() }
    }

    override fun getBalance(address: Address, blockId: BlockId): RpcRequest<BigInteger, RpcError> {
        return RpcCall(client, "eth_getBalance", arrayOf(address, blockId.id)) { it.readHexBigInteger() }
    }

    override fun getBlockHeader(blockId: BlockId): RpcRequest<BlockWithHashes, RpcError> {
        val params = arrayOf(blockId.id)
        val method = when (blockId) {
            is BlockId.Hash -> "eth_getHeaderByHash"
            is BlockId.Number, is BlockId.Name -> "eth_getHeaderByNumber"
        }
        return RpcCall(client, method, params, BlockWithHashes::class.java)
    }

    override fun getBlockWithHashes(blockId: BlockId): RpcRequest<BlockWithHashes, RpcError> {
        return getBlock(blockId, false, BlockWithHashes::class.java)
    }

    override fun getBlockWithTransactions(blockId: BlockId): RpcRequest<BlockWithTransactions, RpcError> {
        return getBlock(blockId, true, BlockWithTransactions::class.java)
    }

    protected fun <T, B : Block<T>> getBlock(
        blockId: BlockId,
        fullTransactions: Boolean,
        responseType: Class<B>,
    ): RpcRequest<B, RpcError> {
        val params = arrayOf(blockId.id, fullTransactions)
        val method = when (blockId) {
            is BlockId.Hash -> "eth_getBlockByHash"
            is BlockId.Number, is BlockId.Name -> "eth_getBlockByNumber"
        }
        return RpcCall(client, method, params, responseType)
    }

    override fun getUncleBlockHeader(blockId: BlockId, index: Long): RpcRequest<BlockWithHashes, RpcError> {
        val params = arrayOf(blockId.id, FastHex.encodeWithPrefix(index))
        val method = when (blockId) {
            is BlockId.Hash -> "eth_getUncleByBlockHashAndIndex"
            is BlockId.Number, is BlockId.Name -> "eth_getUncleByBlockNumberAndIndex"
        }
        return RpcCall(client, method, params, BlockWithHashes::class.java)
    }

    override fun getUncleBlocksCount(blockId: BlockId): RpcRequest<Long, RpcError> {
        val params = arrayOf(blockId.id)
        val method = when (blockId) {
            is BlockId.Hash -> "eth_getUncleCountByBlockHash"
            is BlockId.Number, is BlockId.Name -> "eth_getUncleCountByBlockNumber"
        }
        return RpcCall(client, method, params) { it.readHexLong() }
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
        call: CallRequest,
        blockId: BlockId,
        stateOverride: Map<Address, AccountOverride>?,
        blockOverride: BlockOverride?,
    ): RpcRequest<Bytes, RpcError> {
        // create minimal params array - some RPC's don't support stateOverride or blockOverride
        val params = when {
            blockOverride != null -> arrayOf(call, blockId.id, stateOverride, blockOverride)
            stateOverride != null -> arrayOf(call, blockId.id, stateOverride)
            else -> arrayOf(call, blockId.id)
        }

        return RpcCall(client, "eth_call", params, Bytes::class.java)
    }

    override fun estimateGas(call: CallRequest, blockId: BlockId): RpcRequest<BigInteger, RpcError> {
        val params = arrayOf(call, blockId.id)
        return RpcCall(client, "eth_estimateGas", params) { it.readHexBigInteger() }
    }

    override fun createAccessList(call: CallRequest, blockId: BlockId): RpcRequest<CreateAccessList, RpcError> {
        val params = arrayOf(call, blockId.id)
        return RpcCall(client, "eth_createAccessList", params, CreateAccessList::class.java)
    }

    override fun getGasPrice(): RpcRequest<BigInteger, RpcError> {
        return RpcCall(client, "eth_gasPrice", emptyArray<Any>()) { it.readHexBigInteger() }
    }

    override fun getMaxPriorityFeePerGas(): RpcRequest<BigInteger, RpcError> {
        return RpcCall(client, "eth_maxPriorityFeePerGas", emptyArray<Any>()) { it.readHexBigInteger() }
    }

    override fun getFeeHistory(
        blockCount: Long,
        lastBlockNumber: Long,
        rewardPercentiles: List<BigInteger>,
    ): RpcRequest<FeeHistory, RpcError> {
        val params = arrayOf(blockCount, FastHex.encodeWithPrefix(lastBlockNumber), rewardPercentiles)
        return RpcCall(client, "eth_feeHistory", params, FeeHistory::class.java)
    }

    override fun isNodeSyncing(): RpcRequest<SyncStatus, RpcError> {
        return RpcCall(client, "eth_syncing", emptyArray<Any>()) { it.readValueAs(SyncStatus::class.java) }
    }

    override fun getBlockTransactionCount(blockId: BlockId): RpcRequest<Long, RpcError> {
        val params = arrayOf(blockId.id)
        val method = when (blockId) {
            is BlockId.Hash -> "eth_getBlockTransactionCountByHash"
            is BlockId.Number, is BlockId.Name -> "eth_getBlockTransactionCountByNumber"
        }
        return RpcCall(client, method, params) { it.readHexLong() }
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
        return RpcCall(client, "eth_getTransactionCount", arrayOf(address, blockId.id)) { it.readHexLong() }
    }

    override fun getTransactionByHash(hash: Hash): RpcRequest<Optional<RPCTransaction>, RpcError> {
        return RpcCall(
            client,
            "eth_getTransactionByHash",
            arrayOf(hash),
            { it.readOptionalValue(RPCTransaction::class.java) },
        )
    }

    override fun getTransactionReceipt(hash: Hash): RpcRequest<Optional<TransactionReceipt>, RpcError> {
        return RpcCall(
            client,
            "eth_getTransactionReceipt",
            arrayOf(hash),
            { it.readOptionalValue(TransactionReceipt::class.java) },
        )
    }

    override fun sendRawTransaction(signedTransaction: ByteArray): RpcRequest<PendingTransaction, RpcError> {
        return RpcCall(
            client,
            "eth_sendRawTransaction",
            arrayOf(signedTransaction),
            { PendingTransaction(it.readHash(), this) },
        )
    }

    override fun fillTransaction(call: CallRequest): RpcRequest<TransactionUnsigned, RpcError> {
        return RpcCall(
            client,
            "eth_fillTransaction",
            arrayOf(call),
            {
                var ret: TransactionUnsigned? = null
                while (!it.isNextTokenObjectEnd()) {
                    when {
                        it.isField("raw") -> ret = TransactionUnsigned.rlpDecode(it.readHexByteArray(), chainId)
                        else -> {}
                    }
                }

                return@RpcCall ret ?: throw IllegalStateException("Invalid response, should not happen")
            },
        )
    }

    override fun getLogs(filter: LogFilter): RpcRequest<List<Log>, RpcError> {
        return RpcCall(
            client,
            "eth_getLogs",
            arrayOf(filter),
            { it.readListOf(Log::class.java) },
        )
    }

    override fun watchLogs(filter: LogFilter): RpcRequest<FilterPoller<Log>, RpcError> {
        return RpcCall(
            client,
            "eth_newFilter",
            arrayOf(filter),
            { p -> FilterPoller(p.text, this, { it.readListOf(Log::class.java) }) },
        )
    }

    override fun watchNewBlocks(): RpcRequest<FilterPoller<Hash>, RpcError> {
        return RpcCall(
            client,
            "eth_newBlockFilter",
            emptyArray<Any>(),
            { p -> FilterPoller(p.text, this, { it.readListOfHashes() }) },
        )
    }

    override fun watchNewPendingTransactionHashes(): RpcRequest<FilterPoller<Hash>, RpcError> {
        return RpcCall(
            client,
            "eth_newPendingTransactionFilter",
            emptyArray<Any>(),
            { p -> FilterPoller(p.text, this, { it.readListOfHashes() }) },
        )
    }

    override fun watchNewPendingTransactions(): RpcRequest<FilterPoller<RPCTransaction>, RpcError> {
        return RpcCall(
            client,
            "eth_newPendingTransactionFilter",
            arrayOf(true),
            { p -> FilterPoller(p.text, this, { it.readListOf(RPCTransaction::class.java) }) },
        )
    }

    override fun subscribeNewPendingTransactionHashes(): RpcSubscribe<Hash, RpcError> {
        return subscribe(arrayOf("newPendingTransactions"), { it.readHash() })
    }

    override fun subscribeNewPendingTransactions(): RpcSubscribe<RPCTransaction, RpcError> {
        return subscribe(arrayOf("newPendingTransactions", true), { it.readValueAs(RPCTransaction::class.java) })
    }

    override fun subscribeNewHeads(): RpcSubscribe<BlockWithHashes, RpcError> {
        return subscribe(arrayOf("newHeads"), { it.readValueAs(BlockWithHashes::class.java) })
    }

    override fun subscribeLogs(filter: LogFilter): RpcSubscribe<Log, RpcError> {
        return subscribe(arrayOf("logs", filter), { it.readValueAs(Log::class.java) })
    }

    private fun <T> subscribe(
        params: Array<*>,
        decoder: Function<JsonParser, T>,
    ): RpcSubscribe<T, RpcError> {
        if (!isPubSub) {
            throw UnsupportedOperationException("Pub/sub is not supported by this provider")
        }

        return RpcSubscribeCall(client as JsonPubSubClient, params, decoder)
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
        return RpcCall(client, "debug_getRawReceipts", params) { it.readListOf(Bytes::class.java) }
    }

    override fun getRawTransaction(hash: Hash): RpcRequest<Optional<Bytes>, RpcError> {
        return RpcCall(
            client,
            "debug_getRawTransaction",
            arrayOf(hash),
            {
                if (it.currentToken() == null) {
                    return@RpcCall Optional.empty()
                }

                val rlp = it.readBytesEmptyAsNull() ?: return@RpcCall Optional.empty()
                return@RpcCall Optional.of(rlp)
            },
        )
    }

    override fun printBlock(number: Long): RpcRequest<String, RpcError> {
        val params = arrayOf(number)
        return RpcCall(client, "debug_printBlock", params, String::class.java)
    }

    override fun <T> traceCall(call: CallRequest, blockId: BlockId, config: TracerConfig<T>): RpcRequest<T, RpcError> {
        val params = arrayOf(call, blockId.id, config)
        return RpcCall(client, "debug_traceCall", params, { config.tracer.decodeResult(it) })
    }

    override fun <T> traceTransaction(txHash: Hash, config: TracerConfig<T>): RpcRequest<T, RpcError> {
        val params = arrayOf(txHash, config)
        return RpcCall(client, "debug_traceTransaction", params, { config.tracer.decodeResult(it) })
    }

    override fun <T> traceBlock(
        blockId: BlockId,
        config: TracerConfig<T>,
    ): RpcRequest<List<TxTraceResult<T>>, RpcError> {
        val params = arrayOf(blockId.id, config)
        val method = when (blockId) {
            is BlockId.Hash -> "debug_traceBlockByHash"
            is BlockId.Number, is BlockId.Name -> "debug_traceBlockByNumber"
        }
        return RpcCall(client, method, params, {
            it.readListOf {
                var txHash: Hash? = null
                var result: T? = null
                var error: String? = null

                it.forEachObjectField { field ->
                    when (field) {
                        "txHash" -> txHash = it.readHash()
                        "result" -> result = config.tracer.decodeResult(it)
                        "error" -> error = it.valueAsString
                    }
                }
                TxTraceResult(txHash, result, error)
            }
        })
    }

    //-----------------------------------------------------------------------------------------------------------------
    //                                  NetApi implementation
    //-----------------------------------------------------------------------------------------------------------------
    override fun isListening(): RpcRequest<Boolean, RpcError> {
        return RpcCall(client, "net_listening", emptyArray<Any>()) { it.readValueAs(Boolean::class.java) }
    }

    override fun getPeerCount(): RpcRequest<Long, RpcError> {
        return RpcCall(client, "net_peerCount", emptyArray<Any>()) { it.readHexLong() }
    }

    override fun getVersion(): RpcRequest<String, RpcError> {
        return RpcCall(client, "net_version", emptyArray<Any>(), String::class.java)
    }

    //-----------------------------------------------------------------------------------------------------------------
    //                                  TxpoolApi implementation
    //-----------------------------------------------------------------------------------------------------------------
    override fun txpoolContent(): RpcRequest<TxpoolContent, RpcError> {
        return RpcCall(client, "txpool_content", emptyArray<Any>(), TxpoolContent::class.java)
    }

    override fun txpoolContentFrom(address: Address): RpcRequest<TxpoolContentFromAddress, RpcError> {
        val params = arrayOf(address)
        return RpcCall(client, "txpool_contentFrom", params, TxpoolContentFromAddress::class.java)
    }

    override fun txpoolStatus(): RpcRequest<TxpoolStatus, RpcError> {
        return RpcCall(client, "txpool_status", emptyArray<Any>(), TxpoolStatus::class.java)
    }

    override fun txpoolInspect(): RpcRequest<TxpoolInspectResult, RpcError> {
        return RpcCall(client, "txpool_inspect", emptyArray<Any>(), TxpoolInspectResult::class.java)
    }

    /**
     * Error indicating the provided [url] has an unsupported protocol.
     * */
    data class UnsupportedUrlProtocol(val url: String) : Result.Error

    companion object {
        private val PROTO_HTTPS = "^(https?)://.+$".toRegex()
        private val PROTO_WSS = "^(wss?)://.+$".toRegex()

        /**
         * Create a new [Provider] from the given [url]. Supported URL protocols:
         * - http/https
         * - ws/wss
         * */
        @JvmStatic
        fun fromUrl(url: String): Result<Provider, UnsupportedUrlProtocol> {
            return when {
                url.matches(PROTO_HTTPS) -> success(Provider(HttpClient(url)))
                url.matches(PROTO_WSS) -> success(Provider(WsClient(url)))
                else -> throw IllegalArgumentException("URL with unknown protocol: $url")
            }
        }
    }
}
