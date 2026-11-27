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
import io.ethers.providers.types.RpcSubscribe
import io.ethers.providers.types.RpcSubscribeCall
import io.ethers.providers.types.buildRpcParams
import io.ethers.solana.providers.middleware.SolanaApi
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.U64Serializer
import io.ethers.solana.types.rpc.AccountInfo
import io.ethers.solana.types.rpc.BlockNotification
import io.ethers.solana.types.rpc.Commitment
import io.ethers.solana.types.rpc.ContextValue
import io.ethers.solana.types.rpc.LogsNotification
import io.ethers.solana.types.rpc.ProgramAccount
import io.ethers.solana.types.rpc.SignatureNotification
import io.ethers.solana.types.rpc.SlotNotification
import io.ethers.solana.types.rpc.SlotUpdateNotification
import io.ethers.solana.types.rpc.SolanaAccountConfig
import io.ethers.solana.types.rpc.VoteNotification
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Owns the supplied RPC clients; closing the provider closes each distinct client once.
 * Supplied WebSocket clients must use [SolanaSubscriptionDescriptor]; [builder] configures it automatically.
 * [defaultCommitment] is immutable; each commitment-aware call can override it independently.
 */
class SolanaProvider @JvmOverloads constructor(
    override val client: JsonRpcClient,
    override val defaultCommitment: Commitment = Commitment.FINALIZED,
    private val subscriptionClient: JsonRpcClient = client,
) : SolanaApi, AutoCloseable {
    override val inner: SolanaApi?
        get() = null

    override val provider: SolanaProvider
        get() = this

    override fun subscribeAccount(address: SolanaAddress, config: SolanaAccountConfig): RpcSubscribe<ContextValue<AccountInfo?>, RpcError> {
        return subscribe("account", JsonPrimitive(address.toString()), accountOptions(config), deserializer = ContextValue.serializer(AccountInfo.serializer().nullable))
    }

    override fun subscribeProgram(program: SolanaAddress, filters: List<AccountFilter>, config: SolanaAccountConfig): RpcSubscribe<ContextValue<ProgramAccount>, RpcError> {
        val options = accountOptions(config) {
            put("filters", JsonArray(filters.map { it.toJson() }))
        }
        return subscribe("program", JsonPrimitive(program.toString()), options, deserializer = ContextValue.serializer(ProgramAccount.serializer()))
    }

    override fun subscribeLogs(filter: LogsFilter, commitment: Commitment): RpcSubscribe<ContextValue<LogsNotification>, RpcError> = subscribe("logs", filter.toJson(), options(commitment), deserializer = ContextValue.serializer(LogsNotification.serializer()))

    /** The stream closes after its status event; received notifications are non-terminal. */
    override fun subscribeSignature(signature: SolanaSignature, commitment: Commitment, enableReceivedNotification: Boolean): RpcSubscribe<ContextValue<SignatureNotification>, RpcError> {
        val config = buildJsonObject {
            put("commitment", commitment.toString())
            put("enableReceivedNotification", enableReceivedNotification)
        }
        return subscribe("signature", JsonPrimitive(signature.toString()), config, deserializer = ContextValue.serializer(SignatureNotification.serializer()))
    }

    override fun subscribeBlock(filter: BlockFilter, commitment: Commitment): RpcSubscribe<ContextValue<BlockNotification>, RpcError> {
        val config = buildJsonObject {
            put("commitment", commitment.toString())
            put("encoding", "json")
            put("transactionDetails", "full")
            put("maxSupportedTransactionVersion", 255)
            put("showRewards", false)
        }
        return subscribe("block", filter.toJson(), config, deserializer = ContextValue.serializer(BlockNotification.serializer()))
    }

    override fun subscribeSlotsUpdates(): RpcSubscribe<SlotUpdateNotification, RpcError> = subscribe("slotsUpdates", deserializer = SlotUpdateNotification.serializer())

    override fun subscribeVote(): RpcSubscribe<VoteNotification, RpcError> = subscribe("vote", deserializer = VoteNotification.serializer())

    override fun subscribeSlot(): RpcSubscribe<SlotNotification, RpcError> = subscribe("slot", deserializer = SlotNotification.serializer())
    override fun subscribeRoot(): RpcSubscribe<BigInteger, RpcError> = subscribe("root", deserializer = U64Serializer)

    /** The stream's name is the first param, which [SolanaSubscriptionDescriptor] turns into the method names. */
    private fun <T : Any> subscribe(stream: String, vararg params: JsonElement, deserializer: DeserializationStrategy<T>): RpcSubscribe<T, RpcError> {
        val rpcParams = buildRpcParams {
            add(stream)
            for (param in params) add(param)
        }
        return RpcSubscribeCall(subscriptionClient, rpcParams, deserializer)
    }

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
    private var defaultCommitment = Commitment.FINALIZED
    private var webSocketUrl: String? = null
    fun config(config: RpcClientConfig) = apply { this.config = config }

    /** Set the fallback for subsequently built providers, without changing providers already built. */
    fun defaultCommitment(defaultCommitment: Commitment) = apply { this.defaultCommitment = defaultCommitment }
    fun webSocketUrl(url: String) = apply { this.webSocketUrl = url }

    /** Constructs clients without issuing RPC calls or resolving an Ethereum chain id. */
    fun build(): Result<SolanaProvider, SolanaProviderBuildError> {
        val ws = webSocketUrl
        if (!url.matches(Regex("^(https?|wss?)://.+$"))) return failure(SolanaProviderBuildError("Unsupported RPC URL: $url"))
        if (ws != null && !ws.matches(Regex("^wss?://.+$"))) return failure(SolanaProviderBuildError("Unsupported WebSocket URL: $ws"))
        // Do not change the caller's config, which may also be used for an Ethereum client.
        val rpcConfig = config.copy()
            .subscriptionDescriptor(SolanaSubscriptionDescriptor)
            .rateLimitPolicy(config.rateLimitPolicy ?: SolanaRateLimitPolicy)
        val client = if (url.startsWith("http")) HttpClient(url, rpcConfig) else WsClient(url, rpcConfig)
        val subscriptions = if (ws == null || ws == url) client else WsClient(ws, rpcConfig)
        return success(SolanaProvider(client, defaultCommitment, subscriptions))
    }
}

data class SolanaProviderBuildError(override val message: String) : ThrowableError

/** Base64 account options with [config]'s commitment, or the provider's default, and its data slice. */
private fun SolanaProvider.accountOptions(config: SolanaAccountConfig, extra: JsonObjectBuilder.() -> Unit = {}) = buildJsonObject {
    put("commitment", (config.commitment ?: defaultCommitment).toString())
    put("encoding", "base64")
    config.dataSlice?.let { slice ->
        putJsonObject("dataSlice") {
            put("offset", slice.offset)
            put("length", slice.length)
        }
    }
    extra()
}

private fun options(commitment: Commitment, base64: Boolean = false) = buildJsonObject {
    put("commitment", commitment.toString())
    if (base64) put("encoding", "base64")
}
