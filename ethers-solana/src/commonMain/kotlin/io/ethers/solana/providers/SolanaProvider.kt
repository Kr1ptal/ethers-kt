package io.ethers.solana.providers

import io.ethers.core.Result
import io.ethers.core.ThrowableError
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.providers.HttpClient
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.RpcClientConfig
import io.ethers.providers.RpcError
import io.ethers.providers.WsClient
import io.ethers.providers.types.RpcRequest
import io.ethers.providers.types.RpcSubscribe
import io.ethers.providers.types.RpcSubscribeCall
import io.ethers.solana.providers.middleware.SolanaApi
import io.ethers.solana.types.AccountInfo
import io.ethers.solana.types.Commitment
import io.ethers.solana.types.ContextValue
import io.ethers.solana.types.LatestBlockhash
import io.ethers.solana.types.LogsNotification
import io.ethers.solana.types.ProgramNotification
import io.ethers.solana.types.RpcContext
import io.ethers.solana.types.Signature
import io.ethers.solana.types.SignatureNotification
import io.ethers.solana.types.SlotNotification
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.transaction.SolanaTransaction
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Owns the supplied RPC clients; closing the provider closes each distinct client once.
 * Supplied WebSocket clients must use [SolanaSubscriptionDescriptor]; [builder] configures it automatically.
 */
class SolanaProvider @JvmOverloads constructor(
    override val client: JsonRpcClient,
    override val commitment: Commitment = Commitment.FINALIZED,
    private val subscriptionClient: JsonRpcClient = client,
) : SolanaApi, AutoCloseable {
    // Java-friendly conveniences for the main workflow; the full interface also accepts explicit commitment.
    fun getLatestBlockhash(): RpcRequest<ContextValue<LatestBlockhash>, RpcError> = getLatestBlockhash(commitment)
    fun getBalance(address: SolanaAddress): RpcRequest<BigInteger, RpcError> = getBalance(address, commitment)
    fun getAccountInfo(address: SolanaAddress): RpcRequest<ContextValue<AccountInfo?>, RpcError> = getAccountInfo(address, commitment)
    fun sendTransaction(transaction: SolanaTransaction): RpcRequest<Signature, RpcError> = sendTransaction(transaction, commitment)

    @JvmOverloads
    fun subscribeAccount(address: SolanaAddress, commitment: Commitment = this.commitment): RpcSubscribe<ContextValue<AccountInfo?>, RpcError> = subscribe("account", arrayOf(address.toString(), options(commitment, true))) { decodeContext(it, ::decodeAccount) }

    @JvmOverloads
    fun subscribeProgram(program: SolanaAddress, filters: List<AccountFilter> = emptyList(), commitment: Commitment = this.commitment): RpcSubscribe<ContextValue<ProgramNotification>, RpcError> {
        require(filters.size <= 4) { "At most four account filters are supported" }
        val config = buildJsonObject {
            put("commitment", commitment.toString())
            put("encoding", "base64")
            put("filters", JsonArray(filters.map { it.toJson() }))
        }
        return subscribe("program", arrayOf(program.toString(), config)) { element ->
            decodeContext(element) { value ->
                val obj = value.jsonObject
                ProgramNotification(SolanaAddress(obj.getValue("pubkey").jsonPrimitive.content), requireNotNull(decodeAccount(obj.getValue("account"))))
            }
        }
    }

    @JvmOverloads
    fun subscribeLogs(filter: LogsFilter = LogsFilter.All, commitment: Commitment = this.commitment): RpcSubscribe<ContextValue<LogsNotification>, RpcError> = subscribe("logs", arrayOf(filter.toJson(), options(commitment))) { decode(it) }

    /** The stream closes after its status event; received notifications are non-terminal. */
    @JvmOverloads
    fun subscribeSignature(signature: Signature, commitment: Commitment = this.commitment, enableReceivedNotification: Boolean = false): RpcSubscribe<SignatureNotification, RpcError> {
        val config = buildJsonObject {
            put("commitment", commitment.toString())
            put("enableReceivedNotification", enableReceivedNotification)
        }
        return subscribe("signature", arrayOf(signature.toString(), config)) {
            val obj = it.jsonObject
            val context = decode<RpcContext>(obj.getValue("context"))
            val value = obj.getValue("value")
            if (value is JsonPrimitive) {
                require(value.content == "receivedSignature") { "Unexpected signature notification" }
                SignatureNotification.Received(context)
            } else {
                SignatureNotification.Status(context, value.jsonObject.getValue("err").takeUnless { error -> error == JsonNull })
            }
        }
    }

    fun subscribeSlot(): RpcSubscribe<SlotNotification, RpcError> = subscribe("slot", emptyArray<Any>()) { decode(it) }
    fun subscribeRoot(): RpcSubscribe<BigInteger, RpcError> = subscribe("root", emptyArray<Any>(), ::decodeU64)

    private fun <T : Any> subscribe(method: String, params: Array<*>, decoder: (JsonElement) -> T): RpcSubscribe<T, RpcError> = RpcSubscribeCall(subscriptionClient, arrayOf(method, *params), decoder)

    override fun close() {
        client.close()
        if (subscriptionClient !== client) subscriptionClient.close()
    }

    companion object {
        @JvmStatic fun builder(url: String): SolanaProviderBuilder = SolanaProviderBuilder(url)
        @JvmStatic fun builder(cluster: SolanaCluster): SolanaProviderBuilder = SolanaProviderBuilder(cluster.httpUrl).webSocketUrl(cluster.webSocketUrl)
    }
}

enum class SolanaCluster(val httpUrl: String, val webSocketUrl: String) {
    MAINNET("https://api.mainnet-beta.solana.com", "wss://api.mainnet-beta.solana.com"),
    DEVNET("https://api.devnet.solana.com", "wss://api.devnet.solana.com"),
    TESTNET("https://api.testnet.solana.com", "wss://api.testnet.solana.com"),
}

class SolanaProviderBuilder internal constructor(private val url: String) {
    private var config = RpcClientConfig()
    private var commitment = Commitment.FINALIZED
    private var webSocketUrl: String? = null
    fun config(config: RpcClientConfig) = apply { this.config = config }
    fun commitment(commitment: Commitment) = apply { this.commitment = commitment }
    fun webSocketUrl(url: String) = apply { this.webSocketUrl = url }

    /** Constructs clients without issuing RPC calls or resolving an Ethereum chain id. */
    fun build(): Result<SolanaProvider, SolanaProviderBuildError> {
        val ws = webSocketUrl
        if (!url.matches(Regex("^(https?|wss?)://.+$"))) return failure(SolanaProviderBuildError("Unsupported RPC URL: $url"))
        if (ws != null && !ws.matches(Regex("^wss?://.+$"))) return failure(SolanaProviderBuildError("Unsupported WebSocket URL: $ws"))
        // Do not change the caller's config, which may also be used for an Ethereum client.
        val rpcConfig = RpcClientConfig {
            client = config.client
            requestHeaders = config.requestHeaders
            resubscribeOnReconnect = config.resubscribeOnReconnect
            connectTimeoutMs = config.connectTimeoutMs
            readTimeoutMs = config.readTimeoutMs
            subscriptionDescriptor = SolanaSubscriptionDescriptor
        }
        val client = if (url.startsWith("http")) HttpClient(url, rpcConfig) else WsClient(url, rpcConfig)
        val subscriptions = if (ws == null || ws == url) client else WsClient(ws, rpcConfig)
        return success(SolanaProvider(client, commitment, subscriptions))
    }
}

data class SolanaProviderBuildError(override val message: String) : ThrowableError

private fun options(commitment: Commitment, base64: Boolean = false) = buildJsonObject {
    put("commitment", commitment.toString())
    if (base64) put("encoding", "base64")
}
