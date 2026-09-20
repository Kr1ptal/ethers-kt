package io.ethers.solana.providers.middleware

import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.isFailure
import io.ethers.core.success
import io.ethers.core.unwrapOrReturn
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.RpcError
import io.ethers.providers.types.RpcCall
import io.ethers.providers.types.RpcRequest
import io.ethers.providers.types.RpcSubscribe
import io.ethers.providers.types.SuppliedRpcRequest
import io.ethers.solana.providers.AccountFilter
import io.ethers.solana.providers.BlockFilter
import io.ethers.solana.providers.ConfirmationTracking
import io.ethers.solana.providers.LogsFilter
import io.ethers.solana.providers.PendingSolanaTransaction
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.providers.confirmationTracking
import io.ethers.solana.providers.decode
import io.ethers.solana.providers.decodeAccount
import io.ethers.solana.providers.decodeConfirmationTracking
import io.ethers.solana.providers.decodeContext
import io.ethers.solana.providers.decodeU64
import io.ethers.solana.providers.rpcInteger
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.rpc.AccountInfo
import io.ethers.solana.types.rpc.BlockCommitment
import io.ethers.solana.types.rpc.BlockNotification
import io.ethers.solana.types.rpc.BlockProduction
import io.ethers.solana.types.rpc.BlockTransactionDetails
import io.ethers.solana.types.rpc.ClusterNode
import io.ethers.solana.types.rpc.Commitment
import io.ethers.solana.types.rpc.ContextValue
import io.ethers.solana.types.rpc.EpochInfo
import io.ethers.solana.types.rpc.EpochSchedule
import io.ethers.solana.types.rpc.InflationGovernor
import io.ethers.solana.types.rpc.InflationRate
import io.ethers.solana.types.rpc.InflationReward
import io.ethers.solana.types.rpc.LargestAccount
import io.ethers.solana.types.rpc.LargestAccountsFilter
import io.ethers.solana.types.rpc.LargestTokenAccount
import io.ethers.solana.types.rpc.LatestBlockhash
import io.ethers.solana.types.rpc.LogsNotification
import io.ethers.solana.types.rpc.PerformanceSample
import io.ethers.solana.types.rpc.PrioritizationFee
import io.ethers.solana.types.rpc.ProgramAccount
import io.ethers.solana.types.rpc.SignatureNotification
import io.ethers.solana.types.rpc.SignatureStatus
import io.ethers.solana.types.rpc.SlotNotification
import io.ethers.solana.types.rpc.SlotRange
import io.ethers.solana.types.rpc.SlotUpdateNotification
import io.ethers.solana.types.rpc.SnapshotSlot
import io.ethers.solana.types.rpc.SolanaAccountConfig
import io.ethers.solana.types.rpc.SolanaBlock
import io.ethers.solana.types.rpc.SolanaNodeHealth
import io.ethers.solana.types.rpc.SolanaNodeIdentity
import io.ethers.solana.types.rpc.SolanaNodeVersion
import io.ethers.solana.types.rpc.SolanaRPCTransaction
import io.ethers.solana.types.rpc.SolanaReadConfig
import io.ethers.solana.types.rpc.SolanaSendConfig
import io.ethers.solana.types.rpc.SolanaSimulationConfig
import io.ethers.solana.types.rpc.Supply
import io.ethers.solana.types.rpc.TokenAmount
import io.ethers.solana.types.rpc.TransactionSignature
import io.ethers.solana.types.rpc.TransactionSimulation
import io.ethers.solana.types.rpc.VoteAccounts
import io.ethers.solana.types.rpc.VoteNotification
import io.ethers.solana.types.transaction.AddressLookupTableAccount
import io.ethers.solana.types.transaction.SolanaTransaction
import io.ethers.solana.types.transaction.SolanaTransactionCompiled
import io.ethers.solana.types.transaction.SolanaTransactionError
import io.ethers.solana.types.transaction.SolanaTransactionRequest
import io.ethers.solana.types.transaction.SolanaTransactionSigned
import io.ethers.solana.types.transaction.SolanaTransactionUnsigned
import io.ethers.solana.types.transaction.SolanaTxType
import io.ethers.solana.utils.U32_MAX
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64

/**
 * Solana JSON-RPC capabilities, composable over an existing ethers JsonRpcClient.
 * Explicit convenience overloads make default arguments available to Java callers and all implementations.
 * Results retain the RPC response shape, including context when supplied by the node.
 * Request parameters are forwarded without local validation; node rejections remain [RpcError] results.
 *
 * Customize behaviour by delegating to the layer beneath and overriding only what you need, the same
 * way the EVM `Middleware` is composed:
 *
 * ```kotlin
 * class RetryingApi(override val inner: SolanaApi) : SolanaApi by inner {
 *     override fun getBalance(address: SolanaAddress, commitment: Commitment) =
 *         inner.getBalance(address, commitment).map { ... }
 * }
 * ```
 *
 * Override every overload of a call you intend to intercept. Kotlin generates a forwarder for each
 * member the class does not override, so `getBalance(address)` would resolve against [inner] and reach
 * [inner]'s two-argument version rather than yours, and the interception would silently not happen.
 */
interface SolanaApi {
    val client: JsonRpcClient

    /** Immutable fallback for commitment-aware calls; explicit request arguments take precedence. */
    val defaultCommitment: Commitment

    /** The layer beneath this one, or null for the provider at the bottom. */
    val inner: SolanaApi?
        get() = null

    /** The provider at the bottom of the chain, which owns the subscription client. */
    val provider: SolanaProvider
        get() = inner?.provider ?: throw IllegalStateException("SolanaApi implementations must provide a provider")

    //-----------------------------------------------------------------------------------------------------------------
    //                                  Subscriptions
    //-----------------------------------------------------------------------------------------------------------------

    fun subscribeAccount(address: SolanaAddress): RpcSubscribe<ContextValue<AccountInfo?>, RpcError> = subscribeAccount(address, defaultCommitment)
    fun subscribeAccount(address: SolanaAddress, commitment: Commitment): RpcSubscribe<ContextValue<AccountInfo?>, RpcError> = provider.subscribeAccount(address, commitment)

    fun subscribeProgram(program: SolanaAddress): RpcSubscribe<ContextValue<ProgramAccount>, RpcError> = subscribeProgram(program, emptyList(), defaultCommitment)
    fun subscribeProgram(program: SolanaAddress, filters: List<AccountFilter>): RpcSubscribe<ContextValue<ProgramAccount>, RpcError> = subscribeProgram(program, filters, defaultCommitment)
    fun subscribeProgram(program: SolanaAddress, filters: List<AccountFilter>, commitment: Commitment): RpcSubscribe<ContextValue<ProgramAccount>, RpcError> = provider.subscribeProgram(program, filters, commitment)

    fun subscribeLogs(): RpcSubscribe<ContextValue<LogsNotification>, RpcError> = subscribeLogs(LogsFilter.All, defaultCommitment)
    fun subscribeLogs(filter: LogsFilter): RpcSubscribe<ContextValue<LogsNotification>, RpcError> = subscribeLogs(filter, defaultCommitment)
    fun subscribeLogs(commitment: Commitment): RpcSubscribe<ContextValue<LogsNotification>, RpcError> = subscribeLogs(LogsFilter.All, commitment)
    fun subscribeLogs(filter: LogsFilter, commitment: Commitment): RpcSubscribe<ContextValue<LogsNotification>, RpcError> = provider.subscribeLogs(filter, commitment)

    /** The stream closes after its status event; received notifications are non-terminal. */
    fun subscribeSignature(signature: SolanaSignature): RpcSubscribe<ContextValue<SignatureNotification>, RpcError> = subscribeSignature(signature, defaultCommitment, false)
    fun subscribeSignature(signature: SolanaSignature, commitment: Commitment): RpcSubscribe<ContextValue<SignatureNotification>, RpcError> = subscribeSignature(signature, commitment, false)
    fun subscribeSignature(signature: SolanaSignature, enableReceivedNotification: Boolean): RpcSubscribe<ContextValue<SignatureNotification>, RpcError> = subscribeSignature(signature, defaultCommitment, enableReceivedNotification)
    fun subscribeSignature(signature: SolanaSignature, commitment: Commitment, enableReceivedNotification: Boolean): RpcSubscribe<ContextValue<SignatureNotification>, RpcError> = provider.subscribeSignature(signature, commitment, enableReceivedNotification)

    /**
     * Blocks as they are confirmed. Unstable, and only available where the validator was started with
     * `--rpc-pubsub-enable-block-subscription`, which most public endpoints are not.
     */
    fun subscribeBlock(): RpcSubscribe<ContextValue<BlockNotification>, RpcError> = subscribeBlock(BlockFilter.All, defaultCommitment)
    fun subscribeBlock(filter: BlockFilter): RpcSubscribe<ContextValue<BlockNotification>, RpcError> = subscribeBlock(filter, defaultCommitment)
    fun subscribeBlock(filter: BlockFilter, commitment: Commitment): RpcSubscribe<ContextValue<BlockNotification>, RpcError> = provider.subscribeBlock(filter, commitment)

    /** Each step of a slot's progress through the node, which is finer grained than [subscribeSlot]. */
    fun subscribeSlotsUpdates(): RpcSubscribe<SlotUpdateNotification, RpcError> = provider.subscribeSlotsUpdates()

    /**
     * Votes as they are seen in gossip, which have not necessarily landed on chain. Unstable, and only
     * available where the validator was started with `--rpc-pubsub-enable-vote-subscription`.
     */
    fun subscribeVote(): RpcSubscribe<VoteNotification, RpcError> = provider.subscribeVote()

    fun subscribeSlot(): RpcSubscribe<SlotNotification, RpcError> = provider.subscribeSlot()
    fun subscribeRoot(): RpcSubscribe<BigInteger, RpcError> = provider.subscribeRoot()

    fun getBalance(address: SolanaAddress): RpcRequest<ContextValue<BigInteger>, RpcError> = getBalance(address, defaultCommitment)
    fun getBalance(address: SolanaAddress, commitment: Commitment): RpcRequest<ContextValue<BigInteger>, RpcError> = getBalance(address, SolanaReadConfig(commitment = commitment))
    fun getBalance(address: SolanaAddress, config: SolanaReadConfig): RpcRequest<ContextValue<BigInteger>, RpcError> = rpc("getBalance", address.toString(), readConfig(config)) { decodeContext(it, ::decodeU64) }

    /**
     * The token-amount reads take only a commitment: the node accepts a `minContextSlot` alongside
     * these and answers regardless, so [SolanaReadConfig] would promise a guarantee they do not keep.
     */
    fun getTokenAccountBalance(address: SolanaAddress): RpcRequest<ContextValue<TokenAmount>, RpcError> = getTokenAccountBalance(address, defaultCommitment)
    fun getTokenAccountBalance(address: SolanaAddress, commitment: Commitment): RpcRequest<ContextValue<TokenAmount>, RpcError> = rpc("getTokenAccountBalance", address.toString(), commitmentConfig(commitment)) { decode(it) }
    fun getTokenSupply(mint: SolanaAddress): RpcRequest<ContextValue<TokenAmount>, RpcError> = getTokenSupply(mint, defaultCommitment)
    fun getTokenSupply(mint: SolanaAddress, commitment: Commitment): RpcRequest<ContextValue<TokenAmount>, RpcError> = rpc("getTokenSupply", mint.toString(), commitmentConfig(commitment)) { decode(it) }
    fun getLatestBlockhash(): RpcRequest<ContextValue<LatestBlockhash>, RpcError> = getLatestBlockhash(defaultCommitment)
    fun getLatestBlockhash(commitment: Commitment): RpcRequest<ContextValue<LatestBlockhash>, RpcError> = getLatestBlockhash(SolanaReadConfig(commitment = commitment))
    fun getLatestBlockhash(config: SolanaReadConfig): RpcRequest<ContextValue<LatestBlockhash>, RpcError> = rpc("getLatestBlockhash", readConfig(config)) { decode(it) }

    fun isBlockhashValid(blockhash: SolanaBlockhash): RpcRequest<ContextValue<Boolean>, RpcError> = isBlockhashValid(blockhash, defaultCommitment)
    fun isBlockhashValid(blockhash: SolanaBlockhash, commitment: Commitment): RpcRequest<ContextValue<Boolean>, RpcError> = isBlockhashValid(blockhash, SolanaReadConfig(commitment = commitment))
    fun isBlockhashValid(blockhash: SolanaBlockhash, config: SolanaReadConfig): RpcRequest<ContextValue<Boolean>, RpcError> = rpc("isBlockhashValid", blockhash.toString(), readConfig(config)) { decodeContext(it) { v -> v.jsonPrimitive.boolean } }

    fun getHealth(): RpcRequest<SolanaNodeHealth, RpcError> = rpc("getHealth") { if (it.jsonPrimitive.content == "ok") SolanaNodeHealth.OK else SolanaNodeHealth.ERROR }
    fun getEpochInfo(): RpcRequest<EpochInfo, RpcError> = getEpochInfo(defaultCommitment)
    fun getEpochInfo(commitment: Commitment): RpcRequest<EpochInfo, RpcError> = getEpochInfo(SolanaReadConfig(commitment = commitment))
    fun getEpochInfo(config: SolanaReadConfig): RpcRequest<EpochInfo, RpcError> = rpc("getEpochInfo", readConfig(config)) { decode(it) }

    fun getIdentity(): RpcRequest<SolanaNodeIdentity, RpcError> = rpc("getIdentity") { decode(it) }
    fun getVersion(): RpcRequest<SolanaNodeVersion, RpcError> = rpc("getVersion") { decode(it) }
    fun getTransactionCount(): RpcRequest<BigInteger, RpcError> = getTransactionCount(defaultCommitment)
    fun getTransactionCount(commitment: Commitment): RpcRequest<BigInteger, RpcError> = getTransactionCount(SolanaReadConfig(commitment = commitment))
    fun getTransactionCount(config: SolanaReadConfig): RpcRequest<BigInteger, RpcError> = rpc("getTransactionCount", readConfig(config), decoder = ::decodeU64)

    fun getTransaction(signature: SolanaSignature): RpcRequest<SolanaRPCTransaction?, RpcError> = getTransaction(signature, defaultCommitment, 255)
    fun getTransaction(signature: SolanaSignature, commitment: Commitment): RpcRequest<SolanaRPCTransaction?, RpcError> = getTransaction(signature, commitment, 255)
    fun getTransaction(signature: SolanaSignature, maxSupportedTransactionVersion: Int): RpcRequest<SolanaRPCTransaction?, RpcError> = getTransaction(signature, defaultCommitment, maxSupportedTransactionVersion)

    /**
     * Read a confirmed transaction without requiring support for its message version. The default ceiling
     * accepts the full RPC u8 version range, not just versions this library can sign. Missing transactions
     * return null; server errors (including unsupported versions) remain [RpcError]s.
     */
    fun getTransaction(signature: SolanaSignature, commitment: Commitment = this.defaultCommitment, maxSupportedTransactionVersion: Int = 255): RpcRequest<SolanaRPCTransaction?, RpcError> {
        val options = buildJsonObject {
            put("commitment", commitment.toString())
            put("encoding", "json")
            put("maxSupportedTransactionVersion", maxSupportedTransactionVersion)
        }
        return rpc("getTransaction", signature.toString(), options) { if (it == JsonNull) null else decode<SolanaRPCTransaction>(it) }
    }
    fun getAccountInfo(address: SolanaAddress): RpcRequest<ContextValue<AccountInfo?>, RpcError> = getAccountInfo(address, defaultCommitment)
    fun getAccountInfo(address: SolanaAddress, commitment: Commitment): RpcRequest<ContextValue<AccountInfo?>, RpcError> = getAccountInfo(address, SolanaAccountConfig(commitment = commitment))
    fun getMultipleAccounts(addresses: List<SolanaAddress>): RpcRequest<ContextValue<List<AccountInfo?>>, RpcError> = getMultipleAccounts(addresses, defaultCommitment)
    fun getMultipleAccounts(addresses: List<SolanaAddress>, commitment: Commitment): RpcRequest<ContextValue<List<AccountInfo?>>, RpcError> = getMultipleAccounts(addresses, SolanaAccountConfig(commitment = commitment))

    /** As above, slicing the data read and refusing a slot older than the config names. */
    fun getAccountInfo(address: SolanaAddress, config: SolanaAccountConfig): RpcRequest<ContextValue<AccountInfo?>, RpcError> = rpc("getAccountInfo", address.toString(), accountConfig(config)) { decodeContext(it, ::decodeAccount) }

    /** As above, slicing the data read and refusing a slot older than the config names. */
    fun getMultipleAccounts(addresses: List<SolanaAddress>, config: SolanaAccountConfig): RpcRequest<ContextValue<List<AccountInfo?>>, RpcError> = rpc("getMultipleAccounts", addresses.map { it.toString() }, accountConfig(config)) { decodeContext(it) { v -> v.jsonArray.map(::decodeAccount) } }
    fun getAddressLookupTable(address: SolanaAddress): RpcRequest<ContextValue<AddressLookupTableAccount?>, RpcError> = getAddressLookupTable(address, defaultCommitment)

    /**
     * Fetch and decode a lookup table, for compiling v0 transactions against it. Null when no account
     * exists; an account that is not a lookup table fails the request.
     */
    fun getAddressLookupTable(address: SolanaAddress, commitment: Commitment): RpcRequest<ContextValue<AddressLookupTableAccount?>, RpcError> = getAddressLookupTable(address, SolanaReadConfig(commitment = commitment))
    fun getAddressLookupTable(address: SolanaAddress, config: SolanaReadConfig): RpcRequest<ContextValue<AddressLookupTableAccount?>, RpcError> {
        return getAccountInfo(address, SolanaAccountConfig(commitment = config.commitment, minContextSlot = config.minContextSlot)).map { response ->
            ContextValue(response.context, response.value?.let { AddressLookupTableAccount.decode(address, it.data.asByteArray()).unwrap() })
        }
    }

    fun decompileTransaction(transaction: SolanaTransaction): RpcRequest<SolanaTransactionRequest, RpcError> = decompileTransaction(transaction, defaultCommitment)

    /**
     * Resolve a transaction back into an editable [SolanaTransactionRequest], fetching whatever lookup
     * tables it draws on.
     *
     * Nothing is fetched when the message needs no help: a legacy or v1 message loads no addresses,
     * and a `getTransaction` response carries the ones the node already resolved, which are preferred
     * over anything fetched here because a table's contents can change after inclusion. Only a
     * transaction decoded from bytes, or a response without metadata, costs a `getMultipleAccounts`.
     */
    fun decompileTransaction(transaction: SolanaTransaction, commitment: Commitment): RpcRequest<SolanaTransactionRequest, RpcError> = decompileTransaction(transaction, SolanaReadConfig(commitment = commitment))
    fun decompileTransaction(transaction: SolanaTransaction, config: SolanaReadConfig): RpcRequest<SolanaTransactionRequest, RpcError> = SuppliedRpcRequest {
        when (val direct = transaction.toRequest()) {
            is Result.Success -> success(direct.value)
            is Result.Failure -> {
                val keys = transaction.addressLookupTables.map { it.key }
                // a failure with nothing to look up is not one fetching can fix
                if (keys.isEmpty()) {
                    failure(direct.error.toRpcError())
                } else {
                    val accounts = getMultipleAccounts(keys, SolanaAccountConfig(commitment = config.commitment, minContextSlot = config.minContextSlot)).send()
                        .unwrapOrReturn { return@SuppliedRpcRequest failure(it) }
                        .value
                    val tables = ArrayList<AddressLookupTableAccount>(keys.size)
                    keys.forEachIndexed { index, key ->
                        val data = accounts[index]
                            ?: return@SuppliedRpcRequest failure(RpcError(INVALID_PARAMS, "Lookup table $key does not exist, so this transaction cannot be resolved"))
                        tables.add(
                            AddressLookupTableAccount.decode(key, data.data.asByteArray())
                                .unwrapOrReturn { return@SuppliedRpcRequest failure(it.toRpcError()) },
                        )
                    }
                    transaction.toRequest(tables).mapError { it.toRpcError() }
                }
            }
        }
    }

    fun getMinimumBalanceForRentExemption(space: BigInteger): RpcRequest<BigInteger, RpcError> = getMinimumBalanceForRentExemption(space, defaultCommitment)
    fun getMinimumBalanceForRentExemption(space: BigInteger, commitment: Commitment): RpcRequest<BigInteger, RpcError> = rpc("getMinimumBalanceForRentExemption", rpcInteger(space), commitmentConfig(commitment), decoder = ::decodeU64)
    fun getMinimumBalanceForRentExemption(space: Long): RpcRequest<BigInteger, RpcError> = getMinimumBalanceForRentExemption(space, defaultCommitment)
    fun getMinimumBalanceForRentExemption(space: Long, commitment: Commitment): RpcRequest<BigInteger, RpcError> = getMinimumBalanceForRentExemption(bigIntegerOf(space), commitment)
    fun requestAirdrop(address: SolanaAddress, lamports: BigInteger): RpcRequest<SolanaSignature, RpcError> = requestAirdrop(address, lamports, defaultCommitment)
    fun requestAirdrop(address: SolanaAddress, lamports: BigInteger, commitment: Commitment): RpcRequest<SolanaSignature, RpcError> = rpc("requestAirdrop", address.toString(), rpcInteger(lamports), commitmentConfig(commitment)) { SolanaSignature(it.jsonPrimitive.content) }
    fun requestAirdrop(address: SolanaAddress, lamports: Long): RpcRequest<SolanaSignature, RpcError> = requestAirdrop(address, lamports, defaultCommitment)
    fun requestAirdrop(address: SolanaAddress, lamports: Long, commitment: Commitment): RpcRequest<SolanaSignature, RpcError> = requestAirdrop(address, bigIntegerOf(lamports), commitment)

    /**
     * Submit a transaction, answering with a handle that waits for the cluster to confirm it, as the
     * EVM `sendRawTransaction` answers with a `PendingTransaction`. The signature the RPC returns is
     * [PendingSolanaTransaction.signature].
     */
    fun sendTransaction(transaction: SolanaTransactionSigned): RpcRequest<PendingSolanaTransaction, RpcError> = sendTransaction(transaction, defaultCommitment)
    fun sendTransaction(transaction: SolanaTransactionSigned, preflightCommitment: Commitment = this.defaultCommitment): RpcRequest<PendingSolanaTransaction, RpcError> = sendTransaction(transaction, SolanaSendConfig(preflightCommitment = preflightCommitment))
    fun sendTransaction(transaction: ByteArray): RpcRequest<PendingSolanaTransaction, RpcError> = sendTransaction(transaction, defaultCommitment)

    /** Forward raw wire bytes unchanged; transaction validation is performed by the RPC node. */
    fun sendTransaction(transaction: ByteArray, preflightCommitment: Commitment = this.defaultCommitment): RpcRequest<PendingSolanaTransaction, RpcError> = sendTransaction(transaction, SolanaSendConfig(preflightCommitment = preflightCommitment))

    /** Infer blockhash or durable-nonce tracking from the signed message. */
    fun sendTransaction(transaction: SolanaTransactionSigned, options: SolanaSendConfig): RpcRequest<PendingSolanaTransaction, RpcError> = submit(transaction.serialize(), options, transaction.confirmationTracking())

    /**
     * Submit with explicit options; see [SolanaSendConfig].
     *
     * Decode the envelope to infer confirmation tracking, without verifying signatures. Malformed
     * or unknown envelopes use status-only tracking and are forwarded unchanged. Typed submissions
     * infer tracking directly from their fields and never pass through this decoding path.
     */
    fun sendTransaction(transaction: ByteArray, options: SolanaSendConfig): RpcRequest<PendingSolanaTransaction, RpcError> = submit(transaction, options, decodeConfirmationTracking(transaction))

    private fun submit(transaction: ByteArray, options: SolanaSendConfig, tracking: ConfirmationTracking): RpcRequest<PendingSolanaTransaction, RpcError> {
        return rpc(
            "sendTransaction",
            Base64.encode(transaction),
            buildJsonObject {
                put("encoding", "base64")
                put("preflightCommitment", (options.preflightCommitment ?: defaultCommitment).toString())
                if (options.skipPreflight) put("skipPreflight", true)
                options.maxRetries?.let { put("maxRetries", it) }
                options.minContextSlot?.let { put("minContextSlot", rpcInteger(it)) }
            },
        ) { PendingSolanaTransaction(SolanaSignature(it.jsonPrimitive.content), this, tracking) }
    }
    fun simulateTransaction(transaction: SolanaTransactionCompiled): RpcRequest<ContextValue<TransactionSimulation>, RpcError> = simulateTransaction(transaction, SolanaSimulationConfig())
    fun simulateTransaction(transaction: SolanaTransactionCompiled, commitment: Commitment): RpcRequest<ContextValue<TransactionSimulation>, RpcError> = simulateTransaction(transaction, SolanaSimulationConfig(commitment = commitment))
    fun simulateTransaction(transaction: SolanaTransactionCompiled, config: SolanaSimulationConfig): RpcRequest<ContextValue<TransactionSimulation>, RpcError> = simulateTransaction(transaction.serializeForSimulation(), config)
    fun simulateTransaction(transaction: ByteArray): RpcRequest<ContextValue<TransactionSimulation>, RpcError> = simulateTransaction(transaction, SolanaSimulationConfig())
    fun simulateTransaction(transaction: ByteArray, commitment: Commitment): RpcRequest<ContextValue<TransactionSimulation>, RpcError> = simulateTransaction(transaction, SolanaSimulationConfig(commitment = commitment))

    /** Simulate with explicit options; see [SolanaSimulationConfig]. */
    fun simulateTransaction(transaction: ByteArray, config: SolanaSimulationConfig): RpcRequest<ContextValue<TransactionSimulation>, RpcError> = rpc("simulateTransaction", Base64.encode(transaction), simulationConfig(config)) { decode(it) }

    fun simulateTransaction(request: SolanaTransactionRequest): RpcRequest<ContextValue<TransactionSimulation>, RpcError> = simulateTransaction(request, emptyList(), defaultCommitment)

    /**
     * Simulate a request whose version is chosen for it, as [SolanaTransactionRequest.compile] does.
     * Signatures and a live blockhash are not needed: the node ignores the former and substitutes the latter.
     */
    fun simulateTransaction(
        request: SolanaTransactionRequest,
        lookupTables: List<AddressLookupTableAccount>,
        commitment: Commitment = this.defaultCommitment,
    ): RpcRequest<ContextValue<TransactionSimulation>, RpcError> {
        val tx = request.forSimulation().compile(lookupTables).unwrapOrReturn { return failedRequest(it) }
        return simulateTransaction(tx, SolanaSimulationConfig.READ_ONLY.copy(commitment = commitment))
    }

    fun simulateTransaction(request: SolanaTransactionRequest, type: SolanaTxType): RpcRequest<ContextValue<TransactionSimulation>, RpcError> = simulateTransaction(request, type, emptyList(), defaultCommitment)

    /** Simulate a request compiled to an explicitly chosen version. */
    fun simulateTransaction(
        request: SolanaTransactionRequest,
        type: SolanaTxType,
        lookupTables: List<AddressLookupTableAccount> = emptyList(),
        commitment: Commitment = this.defaultCommitment,
    ): RpcRequest<ContextValue<TransactionSimulation>, RpcError> {
        val tx = request.compileForSimulation(type, lookupTables).unwrapOrReturn { return failedRequest(it) }
        return simulateTransaction(tx, SolanaSimulationConfig.READ_ONLY.copy(commitment = commitment))
    }

    fun fillTransaction(request: SolanaTransactionRequest): RpcRequest<SolanaTransactionUnsigned, RpcError> = fillTransaction(request, emptyList(), defaultCommitment, DEFAULT_COMPUTE_UNIT_MARGIN_PERCENT)

    /** Complete [request] and compile it to whichever of legacy and v0 is smaller. */
    fun fillTransaction(
        request: SolanaTransactionRequest,
        lookupTables: List<AddressLookupTableAccount>,
        commitment: Commitment = this.defaultCommitment,
        computeUnitMarginPercent: Int = DEFAULT_COMPUTE_UNIT_MARGIN_PERCENT,
    ): RpcRequest<SolanaTransactionUnsigned, RpcError> = fill(request, null, lookupTables, commitment, computeUnitMarginPercent)

    fun fillTransaction(request: SolanaTransactionRequest, type: SolanaTxType): RpcRequest<SolanaTransactionUnsigned, RpcError> = fillTransaction(request, type, emptyList(), defaultCommitment, DEFAULT_COMPUTE_UNIT_MARGIN_PERCENT)

    fun fillTransaction(
        request: SolanaTransactionRequest,
        type: SolanaTxType,
        lookupTables: List<AddressLookupTableAccount>,
    ): RpcRequest<SolanaTransactionUnsigned, RpcError> = fillTransaction(request, type, lookupTables, defaultCommitment, DEFAULT_COMPUTE_UNIT_MARGIN_PERCENT)

    /**
     * Complete [request] and compile it, the counterpart of an EVM `fillTransaction`.
     *
     * Only absent values are filled, so anything already set is honoured: a blockhash from
     * `getLatestBlockhash`, a compute unit price from the median of `getRecentPrioritizationFees` for
     * the accounts this transaction writes, and a compute unit limit from simulating it, raised by
     * [computeUnitMarginPercent] so a slightly costlier execution still fits.
     */
    fun fillTransaction(
        request: SolanaTransactionRequest,
        type: SolanaTxType,
        lookupTables: List<AddressLookupTableAccount> = emptyList(),
        commitment: Commitment = this.defaultCommitment,
        computeUnitMarginPercent: Int = DEFAULT_COMPUTE_UNIT_MARGIN_PERCENT,
    ): RpcRequest<SolanaTransactionUnsigned, RpcError> = fill(request, type, lookupTables, commitment, computeUnitMarginPercent)

    /** A null [type] lets [SolanaTransactionRequest.compile] choose between legacy and v0. */
    private fun fill(
        request: SolanaTransactionRequest,
        type: SolanaTxType?,
        lookupTables: List<AddressLookupTableAccount>,
        commitment: Commitment,
        computeUnitMarginPercent: Int,
    ): RpcRequest<SolanaTransactionUnsigned, RpcError> = SuppliedRpcRequest {
        val filled = SolanaTransactionRequest(request)

        // the blockhash and the fee samples do not depend on each other
        val fetched = coroutineScope {
            val blockhash = if (filled.blockhash == null) async { getLatestBlockhash(commitment).send() } else null
            val fees = if (filled.computeUnitPrice == null && filled.priorityFee == null) async { getRecentPrioritizationFees(filled.writableAccounts()).send() } else null
            val blockhashResult = blockhash?.await()
            val feesResult = fees?.await()
            when {
                blockhashResult != null && blockhashResult.isFailure() -> failure(blockhashResult.unwrapError())
                feesResult != null && feesResult.isFailure() -> failure(feesResult.unwrapError())
                else -> {
                    blockhashResult?.let { filled.blockhash(it.unwrap().value.blockhash) }
                    success(feesResult?.let { medianPrioritizationFee(it.unwrap()) })
                }
            }
        }
        val price = fetched.unwrapOrReturn { return@SuppliedRpcRequest failure(it) }

        // the limit is estimated before the price is applied, because a v1 price needs a limit to
        // become a priority fee, and simulating is what tells us the limit
        if (filled.computeUnitLimit == null) {
            val simulation = simulateFor(filled, type, lookupTables, commitment).send()
                .unwrapOrReturn { return@SuppliedRpcRequest failure(it) }
                .value
            val simulationError = simulation.err
            if (simulationError != null) {
                return@SuppliedRpcRequest failure(RpcError(SIMULATION_FAILED, "Transaction simulation failed: $simulationError"))
            }
            val consumed = simulation.unitsConsumed
                ?: return@SuppliedRpcRequest failure(RpcError(SIMULATION_FAILED, "Node did not report consumed compute units"))
            val withMargin = consumed.multiply(bigIntegerOf(100L + computeUnitMarginPercent)).divide(bigIntegerOf(100))
            filled.computeUnitLimit(withMargin.min(bigIntegerOf(U32_MAX)).toLong())
        }
        price?.let { filled.computeUnitPrice(it) }

        filled.compileFor(type, lookupTables).mapError { it.toRpcError() }
    }

    private fun simulateFor(
        request: SolanaTransactionRequest,
        type: SolanaTxType?,
        lookupTables: List<AddressLookupTableAccount>,
        commitment: Commitment,
    ): RpcRequest<ContextValue<TransactionSimulation>, RpcError> = when (type) {
        null -> simulateTransaction(request, lookupTables, commitment)
        else -> simulateTransaction(request, type, lookupTables, commitment)
    }

    fun getFeeForMessage(message: SolanaTransactionCompiled): RpcRequest<ContextValue<BigInteger?>, RpcError> = getFeeForMessage(message, defaultCommitment)
    fun getFeeForMessage(message: SolanaTransactionCompiled, commitment: Commitment): RpcRequest<ContextValue<BigInteger?>, RpcError> = getFeeForMessage(message, SolanaReadConfig(commitment = commitment))
    fun getFeeForMessage(message: SolanaTransactionCompiled, config: SolanaReadConfig): RpcRequest<ContextValue<BigInteger?>, RpcError> = getFeeForMessage(message.serializeMessage(), config)
    fun getFeeForMessage(message: ByteArray): RpcRequest<ContextValue<BigInteger?>, RpcError> = getFeeForMessage(message, defaultCommitment)
    fun getFeeForMessage(message: ByteArray, commitment: Commitment): RpcRequest<ContextValue<BigInteger?>, RpcError> = getFeeForMessage(message, SolanaReadConfig(commitment = commitment))
    fun getFeeForMessage(message: ByteArray, config: SolanaReadConfig): RpcRequest<ContextValue<BigInteger?>, RpcError> = rpc("getFeeForMessage", Base64.encode(message), readConfig(config)) { decodeContext(it) { v -> if (v == JsonNull) null else decodeU64(v) } }

    fun getRecentPrioritizationFees(): RpcRequest<List<PrioritizationFee>, RpcError> = getRecentPrioritizationFees(emptyList())
    fun getRecentPrioritizationFees(addresses: List<SolanaAddress> = emptyList()): RpcRequest<List<PrioritizationFee>, RpcError> {
        return rpc("getRecentPrioritizationFees", addresses.map { it.toString() }) { decode(it) }
    }

    fun getSlot(): RpcRequest<BigInteger, RpcError> = getSlot(defaultCommitment)
    fun getSlot(commitment: Commitment): RpcRequest<BigInteger, RpcError> = getSlot(SolanaReadConfig(commitment = commitment))
    fun getSlot(config: SolanaReadConfig): RpcRequest<BigInteger, RpcError> = rpc("getSlot", readConfig(config), decoder = ::decodeU64)

    fun getBlockHeight(): RpcRequest<BigInteger, RpcError> = getBlockHeight(defaultCommitment)
    fun getBlockHeight(commitment: Commitment): RpcRequest<BigInteger, RpcError> = getBlockHeight(SolanaReadConfig(commitment = commitment))
    fun getBlockHeight(config: SolanaReadConfig): RpcRequest<BigInteger, RpcError> = rpc("getBlockHeight", readConfig(config), decoder = ::decodeU64)

    /**
     * Progress of each signature, in the order given, with null for one the node has never seen.
     *
     * Nodes keep only their recent status cache, so a signature older than that reads as null unless
     * [searchTransactionHistory] is set, which is markedly slower.
     */
    fun getSignatureStatuses(signatures: List<SolanaSignature>): RpcRequest<ContextValue<List<SignatureStatus?>>, RpcError> = getSignatureStatuses(signatures, false)
    fun getSignatureStatuses(signature: SolanaSignature): RpcRequest<ContextValue<List<SignatureStatus?>>, RpcError> = getSignatureStatuses(listOf(signature), false)
    fun getSignatureStatuses(signatures: List<SolanaSignature>, searchTransactionHistory: Boolean): RpcRequest<ContextValue<List<SignatureStatus?>>, RpcError> {
        require(signatures.size <= 256) { "At most 256 signatures can be queried at once, got ${signatures.size}" }
        val options = buildJsonObject { put("searchTransactionHistory", searchTransactionHistory) }
        return rpc("getSignatureStatuses", signatures.map { it.toString() }, options) { element ->
            decodeContext(element) { value -> value.jsonArray.map { if (it == JsonNull) null else decode<SignatureStatus>(it) } }
        }
    }

    /**
     * Every account owned by [program], narrowed by [filters] as programSubscribe is.
     *
     * A program's whole account set can take a node seconds to answer, so the [RpcContext] naming the
     * slot it answered at is always requested. This method is alone in having to ask for it.
     */
    fun getProgramAccounts(program: SolanaAddress): RpcRequest<ContextValue<List<ProgramAccount>>, RpcError> = getProgramAccounts(program, emptyList(), defaultCommitment)
    fun getProgramAccounts(program: SolanaAddress, filters: List<AccountFilter>): RpcRequest<ContextValue<List<ProgramAccount>>, RpcError> = getProgramAccounts(program, filters, defaultCommitment)
    fun getProgramAccounts(program: SolanaAddress, filters: List<AccountFilter>, commitment: Commitment): RpcRequest<ContextValue<List<ProgramAccount>>, RpcError> = getProgramAccounts(program, filters, SolanaAccountConfig(commitment = commitment))

    /** As above, slicing the data read and refusing a slot older than the config names. */
    fun getProgramAccounts(program: SolanaAddress, filters: List<AccountFilter>, config: SolanaAccountConfig): RpcRequest<ContextValue<List<ProgramAccount>>, RpcError> {
        return rpc("getProgramAccounts", program.toString(), accountConfig(config, filters, withContext = true)) { element ->
            decodeContext(element) { it.jsonArray.map { account -> decode<ProgramAccount>(account) } }
        }
    }

    /** Token accounts [owner] holds, for one mint or across one token program. */
    fun getTokenAccountsByOwner(owner: SolanaAddress, mint: SolanaAddress): RpcRequest<ContextValue<List<ProgramAccount>>, RpcError> = tokenAccountsByOwner(owner, "mint", mint, defaultCommitment)
    fun getTokenAccountsByOwner(owner: SolanaAddress, mint: SolanaAddress, commitment: Commitment): RpcRequest<ContextValue<List<ProgramAccount>>, RpcError> = tokenAccountsByOwner(owner, "mint", mint, commitment)
    fun getTokenAccountsByOwnerForProgram(owner: SolanaAddress, program: SolanaAddress): RpcRequest<ContextValue<List<ProgramAccount>>, RpcError> = tokenAccountsByOwner(owner, "programId", program, defaultCommitment)
    fun getTokenAccountsByOwnerForProgram(owner: SolanaAddress, program: SolanaAddress, commitment: Commitment): RpcRequest<ContextValue<List<ProgramAccount>>, RpcError> = tokenAccountsByOwner(owner, "programId", program, commitment)

    /** As above, slicing the data read and refusing a slot older than the config names. */
    fun getTokenAccountsByOwner(owner: SolanaAddress, mint: SolanaAddress, config: SolanaAccountConfig): RpcRequest<ContextValue<List<ProgramAccount>>, RpcError> = tokenAccountsBy("getTokenAccountsByOwner", owner, "mint", mint, config)

    /** As above, slicing the data read and refusing a slot older than the config names. */
    fun getTokenAccountsByOwnerForProgram(owner: SolanaAddress, program: SolanaAddress, config: SolanaAccountConfig): RpcRequest<ContextValue<List<ProgramAccount>>, RpcError> = tokenAccountsBy("getTokenAccountsByOwner", owner, "programId", program, config)

    private fun tokenAccountsByOwner(owner: SolanaAddress, key: String, value: SolanaAddress, commitment: Commitment): RpcRequest<ContextValue<List<ProgramAccount>>, RpcError> {
        return tokenAccountsBy("getTokenAccountsByOwner", owner, key, value, SolanaAccountConfig(commitment = commitment))
    }

    /** Slots with confirmed blocks in `[start, end]`, capped by the node at 500,000 slots. */
    fun getBlocks(start: BigInteger, end: BigInteger): RpcRequest<List<BigInteger>, RpcError> = getBlocks(start, end, defaultCommitment)
    fun getBlocks(start: BigInteger, end: BigInteger, commitment: Commitment): RpcRequest<List<BigInteger>, RpcError> = getBlocks(start, end, SolanaReadConfig(commitment = commitment))
    fun getBlocks(start: BigInteger, end: BigInteger, config: SolanaReadConfig): RpcRequest<List<BigInteger>, RpcError> = rpc("getBlocks", rpcInteger(start), rpcInteger(end), readConfig(config)) { element -> element.jsonArray.map(::decodeU64) }

    /** A confirmed block, or null when the slot was skipped. [details] selects how much of each transaction is returned. */
    fun getBlock(slot: BigInteger): RpcRequest<SolanaBlock?, RpcError> = getBlock(slot, BlockTransactionDetails.FULL, defaultCommitment)
    fun getBlock(slot: BigInteger, details: BlockTransactionDetails): RpcRequest<SolanaBlock?, RpcError> = getBlock(slot, details, defaultCommitment)
    fun getBlock(slot: BigInteger, details: BlockTransactionDetails, commitment: Commitment): RpcRequest<SolanaBlock?, RpcError> = getBlock(slot, details, commitment, false)

    /**
     * As above, with [rewards] asking for the block's own rewards - the leader's fee share and rent
     * collection, which [SolanaBlock.rewards] then carries. They are left out by default, since a busy
     * block's rewards are a large part of its response.
     */
    fun getBlock(slot: BigInteger, details: BlockTransactionDetails, commitment: Commitment, rewards: Boolean): RpcRequest<SolanaBlock?, RpcError> {
        val options = buildJsonObject {
            put("commitment", commitment.toString())
            put("encoding", "json")
            put("transactionDetails", details.toString())
            put("maxSupportedTransactionVersion", 255)
            put("rewards", rewards)
        }
        return rpc("getBlock", rpcInteger(slot), options) { if (it == JsonNull) null else decode<SolanaBlock>(it) }
    }

    //-----------------------------------------------------------------------------------------------------------------
    //                                  Accounts and tokens
    //-----------------------------------------------------------------------------------------------------------------

    /** Token accounts [delegate] may spend from, for one mint or across one token program. */
    fun getTokenAccountsByDelegate(delegate: SolanaAddress, mint: SolanaAddress): RpcRequest<ContextValue<List<ProgramAccount>>, RpcError> = tokenAccountsBy("getTokenAccountsByDelegate", delegate, "mint", mint, SolanaAccountConfig())
    fun getTokenAccountsByDelegate(delegate: SolanaAddress, mint: SolanaAddress, commitment: Commitment): RpcRequest<ContextValue<List<ProgramAccount>>, RpcError> = tokenAccountsBy("getTokenAccountsByDelegate", delegate, "mint", mint, SolanaAccountConfig(commitment = commitment))
    fun getTokenAccountsByDelegateForProgram(delegate: SolanaAddress, program: SolanaAddress): RpcRequest<ContextValue<List<ProgramAccount>>, RpcError> = tokenAccountsBy("getTokenAccountsByDelegate", delegate, "programId", program, SolanaAccountConfig())
    fun getTokenAccountsByDelegateForProgram(delegate: SolanaAddress, program: SolanaAddress, commitment: Commitment): RpcRequest<ContextValue<List<ProgramAccount>>, RpcError> = tokenAccountsBy("getTokenAccountsByDelegate", delegate, "programId", program, SolanaAccountConfig(commitment = commitment))

    /** The 20 largest holders of [mint]. */
    fun getTokenLargestAccounts(mint: SolanaAddress): RpcRequest<ContextValue<List<LargestTokenAccount>>, RpcError> = getTokenLargestAccounts(mint, defaultCommitment)
    fun getTokenLargestAccounts(mint: SolanaAddress, commitment: Commitment): RpcRequest<ContextValue<List<LargestTokenAccount>>, RpcError> = rpc("getTokenLargestAccounts", mint.toString(), commitmentConfig(commitment)) { element -> decodeContext(element) { it.jsonArray.map { account -> decode<LargestTokenAccount>(account) } } }

    /** The 20 largest accounts by lamports, optionally restricted to circulating supply or not. */
    fun getLargestAccounts(): RpcRequest<ContextValue<List<LargestAccount>>, RpcError> = getLargestAccounts(null, defaultCommitment)
    fun getLargestAccounts(filter: LargestAccountsFilter?): RpcRequest<ContextValue<List<LargestAccount>>, RpcError> = getLargestAccounts(filter, defaultCommitment)
    fun getLargestAccounts(filter: LargestAccountsFilter?, commitment: Commitment): RpcRequest<ContextValue<List<LargestAccount>>, RpcError> {
        val options = buildJsonObject {
            put("commitment", commitment.toString())
            filter?.let { put("filter", it.toString()) }
        }
        return rpc("getLargestAccounts", options) { element -> decodeContext(element) { it.jsonArray.map { account -> decode<LargestAccount>(account) } } }
    }

    /** As above, slicing the data read and refusing a slot older than the config names. */
    fun getTokenAccountsByDelegate(delegate: SolanaAddress, mint: SolanaAddress, config: SolanaAccountConfig): RpcRequest<ContextValue<List<ProgramAccount>>, RpcError> = tokenAccountsBy("getTokenAccountsByDelegate", delegate, "mint", mint, config)

    /** As above, slicing the data read and refusing a slot older than the config names. */
    fun getTokenAccountsByDelegateForProgram(delegate: SolanaAddress, program: SolanaAddress, config: SolanaAccountConfig): RpcRequest<ContextValue<List<ProgramAccount>>, RpcError> = tokenAccountsBy("getTokenAccountsByDelegate", delegate, "programId", program, config)

    private fun tokenAccountsBy(method: String, owner: SolanaAddress, key: String, value: SolanaAddress, config: SolanaAccountConfig): RpcRequest<ContextValue<List<ProgramAccount>>, RpcError> {
        return rpc(method, owner.toString(), buildJsonObject { put(key, value.toString()) }, accountConfig(config)) { element ->
            decodeContext(element) { it.jsonArray.map { account -> decode<ProgramAccount>(account) } }
        }
    }

    //-----------------------------------------------------------------------------------------------------------------
    //                                  Blocks and ledger
    //-----------------------------------------------------------------------------------------------------------------

    /** Up to [limit] slots with confirmed blocks, starting at [start]; the node caps the limit at 500,000. */
    fun getBlocksWithLimit(start: BigInteger, limit: Int): RpcRequest<List<BigInteger>, RpcError> = getBlocksWithLimit(start, limit, defaultCommitment)
    fun getBlocksWithLimit(start: BigInteger, limit: Int, commitment: Commitment): RpcRequest<List<BigInteger>, RpcError> = getBlocksWithLimit(start, limit, SolanaReadConfig(commitment = commitment))
    fun getBlocksWithLimit(start: BigInteger, limit: Int, config: SolanaReadConfig): RpcRequest<List<BigInteger>, RpcError> = rpc("getBlocksWithLimit", rpcInteger(start), limit, readConfig(config)) { element -> element.jsonArray.map(::decodeU64) }

    /** When a block was produced, as a Unix timestamp, or null for a slot the node cannot answer for. */
    fun getBlockTime(slot: BigInteger): RpcRequest<Long?, RpcError> = rpc("getBlockTime", rpcInteger(slot)) { if (it == JsonNull) null else it.jsonPrimitive.long }

    /** The lowest slot the node still has a block for, which rises as the ledger is pruned. */
    fun getFirstAvailableBlock(): RpcRequest<BigInteger, RpcError> = rpc("getFirstAvailableBlock", decoder = ::decodeU64)

    /** The lowest slot the node still has any ledger data for. */
    fun minimumLedgerSlot(): RpcRequest<BigInteger, RpcError> = rpc("minimumLedgerSlot", decoder = ::decodeU64)

    /** Stake that voted on a block, by lockout depth, against the cluster total. */
    fun getBlockCommitment(slot: BigInteger): RpcRequest<BlockCommitment, RpcError> = rpc("getBlockCommitment", rpcInteger(slot)) { decode(it) }

    /** Slots assigned and produced per validator over a slot range, which is how skip rate is measured. */
    fun getBlockProduction(): RpcRequest<ContextValue<BlockProduction>, RpcError> = getBlockProduction(null, null, defaultCommitment)
    fun getBlockProduction(identity: SolanaAddress?): RpcRequest<ContextValue<BlockProduction>, RpcError> = getBlockProduction(identity, null, defaultCommitment)
    fun getBlockProduction(identity: SolanaAddress?, range: SlotRange?, commitment: Commitment): RpcRequest<ContextValue<BlockProduction>, RpcError> {
        val options = buildJsonObject {
            put("commitment", commitment.toString())
            identity?.let { put("identity", it.toString()) }
            range?.let { r ->
                put(
                    "range",
                    buildJsonObject {
                        put("firstSlot", rpcInteger(r.firstSlot))
                        r.lastSlot?.let { put("lastSlot", rpcInteger(it)) }
                    },
                )
            }
        }
        return rpc("getBlockProduction", options) { decodeContext(it) { value -> decode(value) } }
    }

    /** Recent throughput samples, most recent first, which is what observed TPS is computed from. */
    fun getRecentPerformanceSamples(): RpcRequest<List<PerformanceSample>, RpcError> = rpc("getRecentPerformanceSamples") { decode(it) }
    fun getRecentPerformanceSamples(limit: Int): RpcRequest<List<PerformanceSample>, RpcError> = rpc("getRecentPerformanceSamples", limit) { decode(it) }

    //-----------------------------------------------------------------------------------------------------------------
    //                                  Cluster and validators
    //-----------------------------------------------------------------------------------------------------------------

    /** Every node the cluster gossips about, with whichever ports each exposes. */
    fun getClusterNodes(): RpcRequest<List<ClusterNode>, RpcError> = rpc("getClusterNodes") { decode(it) }

    /** Vote accounts, split into those voting and those lagging behind. */
    fun getVoteAccounts(): RpcRequest<VoteAccounts, RpcError> = getVoteAccounts(null, defaultCommitment)
    fun getVoteAccounts(votePubkey: SolanaAddress?): RpcRequest<VoteAccounts, RpcError> = getVoteAccounts(votePubkey, defaultCommitment)
    fun getVoteAccounts(votePubkey: SolanaAddress?, commitment: Commitment): RpcRequest<VoteAccounts, RpcError> = getVoteAccounts(votePubkey, commitment, false, null)

    /**
     * As above, with the node's delinquency reporting under the caller's control.
     *
     * [keepUnstakedDelinquents] keeps delinquent validators that hold no stake, which the node drops
     * by default. [delinquentSlotDistance] is how far a validator's last vote must lag the tip to
     * count as delinquent; null leaves the node's own threshold, which is what its operators tuned.
     */
    fun getVoteAccounts(votePubkey: SolanaAddress?, commitment: Commitment, keepUnstakedDelinquents: Boolean, delinquentSlotDistance: BigInteger?): RpcRequest<VoteAccounts, RpcError> {
        val options = buildJsonObject {
            put("commitment", commitment.toString())
            votePubkey?.let { put("votePubkey", it.toString()) }
            if (keepUnstakedDelinquents) put("keepUnstakedDelinquents", true)
            delinquentSlotDistance?.let { put("delinquentSlotDistance", rpcInteger(it)) }
        }
        return rpc("getVoteAccounts", options) { decode(it) }
    }

    /**
     * Which validator leads each slot of an epoch, keyed by identity. Null when the epoch is unknown
     * to the node. This is what tells a sender where the next leader's TPU is.
     */
    fun getLeaderSchedule(): RpcRequest<Map<String, List<BigInteger>>?, RpcError> = getLeaderSchedule(null, null, defaultCommitment)
    fun getLeaderSchedule(slot: BigInteger?): RpcRequest<Map<String, List<BigInteger>>?, RpcError> = getLeaderSchedule(slot, null, defaultCommitment)
    fun getLeaderSchedule(slot: BigInteger?, identity: SolanaAddress?, commitment: Commitment): RpcRequest<Map<String, List<BigInteger>>?, RpcError> {
        val options = buildJsonObject {
            put("commitment", commitment.toString())
            identity?.let { put("identity", it.toString()) }
        }
        val params = if (slot == null) arrayOf<Any>(JsonNull, options) else arrayOf<Any>(rpcInteger(slot), options)
        return rpc("getLeaderSchedule", *params) { element ->
            if (element == JsonNull) {
                null
            } else {
                element.jsonObject.mapValues { (_, slots) -> slots.jsonArray.map(::decodeU64) }
            }
        }
    }

    /** Which validator leads the current slot. */
    fun getSlotLeader(): RpcRequest<SolanaAddress, RpcError> = getSlotLeader(defaultCommitment)
    fun getSlotLeader(commitment: Commitment): RpcRequest<SolanaAddress, RpcError> = getSlotLeader(SolanaReadConfig(commitment = commitment))
    fun getSlotLeader(config: SolanaReadConfig): RpcRequest<SolanaAddress, RpcError> = rpc("getSlotLeader", readConfig(config)) { SolanaAddress(it.jsonPrimitive.content) }

    /** Leaders for [limit] slots from [start], which is how a sender targets upcoming leaders. */
    fun getSlotLeaders(start: BigInteger, limit: Int): RpcRequest<List<SolanaAddress>, RpcError> = rpc("getSlotLeaders", rpcInteger(start), limit) { element -> element.jsonArray.map { SolanaAddress(it.jsonPrimitive.content) } }

    /** How slots map onto epochs, including the shorter warm-up epochs at genesis. */
    fun getEpochSchedule(): RpcRequest<EpochSchedule, RpcError> = rpc("getEpochSchedule") { decode(it) }

    /** The genesis hash, which identifies the cluster. */
    fun getGenesisHash(): RpcRequest<SolanaBlockhash, RpcError> = rpc("getGenesisHash") { SolanaBlockhash(it.jsonPrimitive.content) }

    /** Highest slots the node has snapshots for, which a new node would bootstrap from. */
    fun getHighestSnapshotSlot(): RpcRequest<SnapshotSlot, RpcError> = rpc("getHighestSnapshotSlot") { decode(it) }

    /** Highest slot the node has received a shred for. */
    fun getMaxShredInsertSlot(): RpcRequest<BigInteger, RpcError> = rpc("getMaxShredInsertSlot", decoder = ::decodeU64)

    /** Highest slot the node has retransmitted a shred for. */
    fun getMaxRetransmitSlot(): RpcRequest<BigInteger, RpcError> = rpc("getMaxRetransmitSlot", decoder = ::decodeU64)

    //-----------------------------------------------------------------------------------------------------------------
    //                                  Supply, inflation and staking
    //-----------------------------------------------------------------------------------------------------------------

    /** Total and circulating supply in lamports; the account list is omitted unless asked for. */
    fun getSupply(): RpcRequest<ContextValue<Supply>, RpcError> = getSupply(false, defaultCommitment)
    fun getSupply(includeNonCirculatingAccounts: Boolean): RpcRequest<ContextValue<Supply>, RpcError> = getSupply(includeNonCirculatingAccounts, defaultCommitment)
    fun getSupply(includeNonCirculatingAccounts: Boolean, commitment: Commitment): RpcRequest<ContextValue<Supply>, RpcError> {
        val options = buildJsonObject {
            put("commitment", commitment.toString())
            put("excludeNonCirculatingAccountsList", !includeNonCirculatingAccounts)
        }
        return rpc("getSupply", options) { decodeContext(it) { value -> decode(value) } }
    }

    /** The inflation schedule's parameters. */
    fun getInflationGovernor(): RpcRequest<InflationGovernor, RpcError> = getInflationGovernor(defaultCommitment)
    fun getInflationGovernor(commitment: Commitment): RpcRequest<InflationGovernor, RpcError> = rpc("getInflationGovernor", commitmentConfig(commitment)) { decode(it) }

    /** Inflation in force for the current epoch. */
    fun getInflationRate(): RpcRequest<InflationRate, RpcError> = rpc("getInflationRate") { decode(it) }

    /** Staking rewards per address, in the order given, with null where the address earned none. */
    fun getInflationReward(addresses: List<SolanaAddress>): RpcRequest<List<InflationReward?>, RpcError> = getInflationReward(addresses, null, defaultCommitment)
    fun getInflationReward(addresses: List<SolanaAddress>, epoch: BigInteger?): RpcRequest<List<InflationReward?>, RpcError> = getInflationReward(addresses, epoch, defaultCommitment)
    fun getInflationReward(addresses: List<SolanaAddress>, epoch: BigInteger?, commitment: Commitment): RpcRequest<List<InflationReward?>, RpcError> = getInflationReward(addresses, epoch, SolanaReadConfig(commitment = commitment))
    fun getInflationReward(addresses: List<SolanaAddress>, epoch: BigInteger?, config: SolanaReadConfig): RpcRequest<List<InflationReward?>, RpcError> {
        val options = buildJsonObject {
            readConfig(config).forEach { (key, value) -> put(key, value) }
            epoch?.let { put("epoch", rpcInteger(it)) }
        }
        return rpc("getInflationReward", addresses.map { it.toString() }, options) { element ->
            element.jsonArray.map { if (it == JsonNull) null else decode<InflationReward>(it) }
        }
    }

    /** The smallest stake delegation the runtime accepts, in lamports. */
    fun getStakeMinimumDelegation(): RpcRequest<ContextValue<BigInteger>, RpcError> = getStakeMinimumDelegation(defaultCommitment)
    fun getStakeMinimumDelegation(commitment: Commitment): RpcRequest<ContextValue<BigInteger>, RpcError> = getStakeMinimumDelegation(SolanaReadConfig(commitment = commitment))
    fun getStakeMinimumDelegation(config: SolanaReadConfig): RpcRequest<ContextValue<BigInteger>, RpcError> = rpc("getStakeMinimumDelegation", readConfig(config)) { decodeContext(it, ::decodeU64) }

    fun getSignaturesForAddress(address: SolanaAddress): RpcRequest<List<TransactionSignature>, RpcError> = getSignaturesForAddress(address, 1000, defaultCommitment, null, null)
    fun getSignaturesForAddress(address: SolanaAddress, limit: Int): RpcRequest<List<TransactionSignature>, RpcError> = getSignaturesForAddress(address, limit, defaultCommitment, null, null)
    fun getSignaturesForAddress(address: SolanaAddress, commitment: Commitment): RpcRequest<List<TransactionSignature>, RpcError> = getSignaturesForAddress(address, 1000, commitment, null, null)
    fun getSignaturesForAddress(address: SolanaAddress, limit: Int, commitment: Commitment): RpcRequest<List<TransactionSignature>, RpcError> = getSignaturesForAddress(address, limit, commitment, null, null)
    fun getSignaturesForAddress(address: SolanaAddress, limit: Int, commitment: Commitment, before: SolanaSignature?): RpcRequest<List<TransactionSignature>, RpcError> = getSignaturesForAddress(address, limit, commitment, before, null)
    fun getSignaturesForAddress(address: SolanaAddress, limit: Int, before: SolanaSignature?, until: SolanaSignature?): RpcRequest<List<TransactionSignature>, RpcError> = getSignaturesForAddress(address, limit, defaultCommitment, before, until)
    fun getSignaturesForAddress(address: SolanaAddress, limit: Int = 1000, commitment: Commitment = this.defaultCommitment, before: SolanaSignature? = null, until: SolanaSignature? = null): RpcRequest<List<TransactionSignature>, RpcError> = getSignaturesForAddress(address, limit, SolanaReadConfig(commitment = commitment), before, until)
    fun getSignaturesForAddress(address: SolanaAddress, limit: Int = 1000, config: SolanaReadConfig, before: SolanaSignature? = null, until: SolanaSignature? = null): RpcRequest<List<TransactionSignature>, RpcError> {
        val options = buildJsonObject {
            readConfig(config).forEach { (key, value) -> put(key, value) }
            put("limit", limit)
            before?.let { put("before", it.toString()) }
            until?.let { put("until", it.toString()) }
        }
        return rpc("getSignaturesForAddress", address.toString(), options) { decode(it) }
    }
}

/** JSON-RPC "invalid params": the caller's request cannot form a transaction, so nothing was sent. */
private const val INVALID_PARAMS = -32602

/** No JSON-RPC code covers a transaction that ran and failed, so reuse the server-error range. */
private const val SIMULATION_FAILED = -32000

private const val DEFAULT_COMPUTE_UNIT_MARGIN_PERCENT = 10

/** Keep the typed cause reachable while satisfying the RPC error type these requests report. */
private fun SolanaTransactionError.toRpcError() = RpcError(INVALID_PARAMS, message ?: toString(), cause = toException())

private fun <T> failedRequest(error: SolanaTransactionError): RpcRequest<T, RpcError> = SuppliedRpcRequest { failure(error.toRpcError()) }

/** Simulating needs a compiled transaction, and a blockhash the node will replace anyway. */
private fun SolanaTransactionRequest.compileForSimulation(type: SolanaTxType, lookupTables: List<AddressLookupTableAccount>): Result<SolanaTransactionUnsigned, SolanaTransactionError> = forSimulation().compileFor(type, lookupTables)

/**
 * Fill in a placeholder blockhash so a request that has none can still be serialized.
 *
 * A compiled message always carries a 32-byte blockhash field, so there is no way to produce bytes
 * without one. The value is never executed against: [SolanaSimulationConfig.READ_ONLY] asks the node
 * to substitute its own blockhash first.
 */
internal fun SolanaTransactionRequest.forSimulation(): SolanaTransactionRequest = if (blockhash != null) this else SolanaTransactionRequest(this).blockhash(SolanaBlockhash(ByteArray(32)))

private fun SolanaTransactionRequest.compileFor(type: SolanaTxType?, lookupTables: List<AddressLookupTableAccount>): Result<SolanaTransactionUnsigned, SolanaTransactionError> = when (type) {
    null -> compile(lookupTables)
    SolanaTxType.Legacy -> compileLegacy()
    SolanaTxType.V0 -> compileV0(lookupTables)
    SolanaTxType.V1 -> compileV1()
    is SolanaTxType.Unsupported -> failure(SolanaTransactionError.UnsupportedVersion(type.version))
}

/** Accounts the transaction writes, which is the locality getRecentPrioritizationFees prices. */
private fun SolanaTransactionRequest.writableAccounts(): List<SolanaAddress> = instructions.flatMap { instruction -> instruction.keys.filter { it.writable }.map { it.publicKey } }.distinct()

/** The median is steadier than the mean against the occasional very high recent fee. */
private fun medianPrioritizationFee(fees: List<PrioritizationFee>): BigInteger {
    if (fees.isEmpty()) return bigIntegerOf(0)
    val sorted = fees.map { it.prioritizationFee }.sorted()
    return sorted[sorted.size / 2]
}

private fun SolanaApi.simulationConfig(config: SolanaSimulationConfig) = buildJsonObject {
    putReadOptions(config.commitment ?: defaultCommitment, config.minContextSlot)
    put("encoding", "base64")
    if (config.sigVerify) put("sigVerify", true)
    if (config.replaceRecentBlockhash) put("replaceRecentBlockhash", true)
    if (config.innerInstructions) put("innerInstructions", true)
    if (config.accounts.isNotEmpty()) {
        putJsonObject("accounts") {
            put("encoding", "base64")
            putJsonArray("addresses") { config.accounts.forEach { add(it.toString()) } }
        }
    }
}

/** Options for methods that enforce a minimum context slot. */
private fun SolanaApi.readConfig(config: SolanaReadConfig) = buildJsonObject {
    putReadOptions(config.commitment ?: defaultCommitment, config.minContextSlot)
}

/** Account-read options, with [defaultCommitment] standing in for a config that names none. */
private fun SolanaApi.accountConfig(
    config: SolanaAccountConfig,
    filters: List<AccountFilter> = emptyList(),
    withContext: Boolean = false,
) = buildJsonObject {
    putReadOptions(config.commitment ?: defaultCommitment, config.minContextSlot)
    put("encoding", "base64")
    config.dataSlice?.let { slice ->
        putJsonObject("dataSlice") {
            put("offset", slice.offset)
            put("length", slice.length)
        }
    }
    if (filters.isNotEmpty()) put("filters", JsonArray(filters.map { it.toJson() }))
    // getProgramAccounts is the one account read whose context envelope is opt-in
    if (withContext) put("withContext", true)
}

private fun <T> SolanaApi.rpc(method: String, vararg params: Any?, decoder: (JsonElement) -> T): RpcRequest<T, RpcError> = RpcCall(client, method, params, decoder)

private fun commitmentConfig(commitment: Commitment) = buildJsonObject {
    put("commitment", commitment.toString())
}

private fun JsonObjectBuilder.putReadOptions(commitment: Commitment, minContextSlot: BigInteger?) {
    put("commitment", commitment.toString())
    minContextSlot?.let { put("minContextSlot", rpcInteger(it)) }
}
