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
import io.ethers.providers.types.SuppliedRpcRequest
import io.ethers.solana.providers.decode
import io.ethers.solana.providers.decodeAccount
import io.ethers.solana.providers.decodeContext
import io.ethers.solana.providers.decodeU64
import io.ethers.solana.providers.rpcInteger
import io.ethers.solana.types.AccountInfo
import io.ethers.solana.types.Commitment
import io.ethers.solana.types.ContextValue
import io.ethers.solana.types.EpochInfo
import io.ethers.solana.types.LatestBlockhash
import io.ethers.solana.types.PrioritizationFee
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaNodeHealth
import io.ethers.solana.types.SolanaNodeIdentity
import io.ethers.solana.types.SolanaNodeVersion
import io.ethers.solana.types.SolanaRPCTransaction
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.SolanaSimulationConfig
import io.ethers.solana.types.TokenAmount
import io.ethers.solana.types.TransactionSignature
import io.ethers.solana.types.TransactionSimulation
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64

/**
 * Solana JSON-RPC capabilities, composable over an existing ethers JsonRpcClient.
 * Explicit convenience overloads make default arguments available to Java callers and all implementations.
 * Results retain the RPC response shape, including context when supplied by the node.
 * Request parameters are forwarded without local validation; node rejections remain [RpcError] results.
 */
interface SolanaApi {
    val client: JsonRpcClient

    /** Immutable fallback for commitment-aware calls; explicit request arguments take precedence. */
    val defaultCommitment: Commitment

    fun getBalance(address: SolanaAddress): RpcRequest<ContextValue<BigInteger>, RpcError> = getBalance(address, defaultCommitment)
    fun getBalance(address: SolanaAddress, commitment: Commitment = this.defaultCommitment): RpcRequest<ContextValue<BigInteger>, RpcError> = rpc("getBalance", address.toString(), config(commitment)) { decodeContext(it, ::decodeU64) }
    fun getTokenAccountBalance(address: SolanaAddress): RpcRequest<ContextValue<TokenAmount>, RpcError> = getTokenAccountBalance(address, defaultCommitment)
    fun getTokenAccountBalance(address: SolanaAddress, commitment: Commitment = this.defaultCommitment): RpcRequest<ContextValue<TokenAmount>, RpcError> = rpc("getTokenAccountBalance", address.toString(), config(commitment)) { decode(it) }
    fun getTokenSupply(mint: SolanaAddress): RpcRequest<ContextValue<TokenAmount>, RpcError> = getTokenSupply(mint, defaultCommitment)
    fun getTokenSupply(mint: SolanaAddress, commitment: Commitment = this.defaultCommitment): RpcRequest<ContextValue<TokenAmount>, RpcError> = rpc("getTokenSupply", mint.toString(), config(commitment)) { decode(it) }
    fun getLatestBlockhash(): RpcRequest<ContextValue<LatestBlockhash>, RpcError> = getLatestBlockhash(defaultCommitment)
    fun getLatestBlockhash(commitment: Commitment = this.defaultCommitment): RpcRequest<ContextValue<LatestBlockhash>, RpcError> = rpc("getLatestBlockhash", config(commitment)) { decode(it) }
    fun isBlockhashValid(blockhash: SolanaBlockhash): RpcRequest<ContextValue<Boolean>, RpcError> = isBlockhashValid(blockhash, defaultCommitment)
    fun isBlockhashValid(blockhash: SolanaBlockhash, commitment: Commitment = this.defaultCommitment): RpcRequest<ContextValue<Boolean>, RpcError> = rpc("isBlockhashValid", blockhash.toString(), config(commitment)) { decodeContext(it) { v -> v.jsonPrimitive.boolean } }
    fun getHealth(): RpcRequest<SolanaNodeHealth, RpcError> = rpc("getHealth") { if (it.jsonPrimitive.content == "ok") SolanaNodeHealth.OK else SolanaNodeHealth.ERROR }
    fun getEpochInfo(): RpcRequest<EpochInfo, RpcError> = getEpochInfo(defaultCommitment)
    fun getEpochInfo(commitment: Commitment = this.defaultCommitment): RpcRequest<EpochInfo, RpcError> = rpc("getEpochInfo", config(commitment)) { decode(it) }
    fun getIdentity(): RpcRequest<SolanaNodeIdentity, RpcError> = rpc("getIdentity") { decode(it) }
    fun getVersion(): RpcRequest<SolanaNodeVersion, RpcError> = rpc("getVersion") { decode(it) }
    fun getTransactionCount(): RpcRequest<BigInteger, RpcError> = getTransactionCount(defaultCommitment)
    fun getTransactionCount(commitment: Commitment = this.defaultCommitment): RpcRequest<BigInteger, RpcError> = rpc("getTransactionCount", config(commitment), decoder = ::decodeU64)

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
    fun getAccountInfo(address: SolanaAddress, commitment: Commitment = this.defaultCommitment): RpcRequest<ContextValue<AccountInfo?>, RpcError> = rpc("getAccountInfo", address.toString(), config(commitment, "base64")) { decodeContext(it, ::decodeAccount) }
    fun getMultipleAccounts(addresses: List<SolanaAddress>): RpcRequest<ContextValue<List<AccountInfo?>>, RpcError> = getMultipleAccounts(addresses, defaultCommitment)
    fun getMultipleAccounts(addresses: List<SolanaAddress>, commitment: Commitment = this.defaultCommitment): RpcRequest<ContextValue<List<AccountInfo?>>, RpcError> {
        return rpc("getMultipleAccounts", addresses.map { it.toString() }, config(commitment, "base64")) { decodeContext(it) { v -> v.jsonArray.map(::decodeAccount) } }
    }
    fun getAddressLookupTable(address: SolanaAddress): RpcRequest<ContextValue<AddressLookupTableAccount?>, RpcError> = getAddressLookupTable(address, defaultCommitment)

    /**
     * Fetch and decode a lookup table, for compiling v0 transactions against it. Null when no account
     * exists; an account that is not a lookup table fails the request.
     */
    fun getAddressLookupTable(address: SolanaAddress, commitment: Commitment = this.defaultCommitment): RpcRequest<ContextValue<AddressLookupTableAccount?>, RpcError> {
        return getAccountInfo(address, commitment).map { response ->
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
    fun decompileTransaction(transaction: SolanaTransaction, commitment: Commitment = this.defaultCommitment): RpcRequest<SolanaTransactionRequest, RpcError> = SuppliedRpcRequest {
        when (val direct = transaction.toRequest()) {
            is Result.Success -> success(direct.value)
            is Result.Failure -> {
                val keys = transaction.addressLookupTables.map { it.key }
                // a failure with nothing to look up is not one fetching can fix
                if (keys.isEmpty()) {
                    failure(direct.error.toRpcError())
                } else {
                    val accounts = getMultipleAccounts(keys, commitment).send()
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
    fun getMinimumBalanceForRentExemption(space: BigInteger, commitment: Commitment = this.defaultCommitment): RpcRequest<BigInteger, RpcError> = rpc("getMinimumBalanceForRentExemption", rpcInteger(space), config(commitment), decoder = ::decodeU64)
    fun getMinimumBalanceForRentExemption(space: Long): RpcRequest<BigInteger, RpcError> = getMinimumBalanceForRentExemption(space, defaultCommitment)
    fun getMinimumBalanceForRentExemption(space: Long, commitment: Commitment): RpcRequest<BigInteger, RpcError> = getMinimumBalanceForRentExemption(bigIntegerOf(space), commitment)
    fun requestAirdrop(address: SolanaAddress, lamports: BigInteger): RpcRequest<SolanaSignature, RpcError> = requestAirdrop(address, lamports, defaultCommitment)
    fun requestAirdrop(address: SolanaAddress, lamports: BigInteger, commitment: Commitment = this.defaultCommitment): RpcRequest<SolanaSignature, RpcError> = rpc("requestAirdrop", address.toString(), rpcInteger(lamports), config(commitment)) { SolanaSignature(it.jsonPrimitive.content) }
    fun requestAirdrop(address: SolanaAddress, lamports: Long): RpcRequest<SolanaSignature, RpcError> = requestAirdrop(address, lamports, defaultCommitment)
    fun requestAirdrop(address: SolanaAddress, lamports: Long, commitment: Commitment): RpcRequest<SolanaSignature, RpcError> = requestAirdrop(address, bigIntegerOf(lamports), commitment)

    fun sendTransaction(transaction: SolanaTransactionSigned): RpcRequest<SolanaSignature, RpcError> = sendTransaction(transaction, defaultCommitment)
    fun sendTransaction(transaction: SolanaTransactionSigned, preflightCommitment: Commitment = this.defaultCommitment): RpcRequest<SolanaSignature, RpcError> = sendTransaction(transaction.serialize(), preflightCommitment)
    fun sendTransaction(transaction: ByteArray): RpcRequest<SolanaSignature, RpcError> = sendTransaction(transaction, defaultCommitment)

    /** Forward raw wire bytes unchanged; transaction validation is performed by the RPC node. */
    fun sendTransaction(transaction: ByteArray, preflightCommitment: Commitment = this.defaultCommitment): RpcRequest<SolanaSignature, RpcError> {
        return rpc(
            "sendTransaction",
            Base64.encode(transaction),
            buildJsonObject {
                put("encoding", "base64")
                put("preflightCommitment", preflightCommitment.toString())
            },
        ) { SolanaSignature(it.jsonPrimitive.content) }
    }
    fun simulateTransaction(transaction: SolanaTransactionCompiled): RpcRequest<ContextValue<TransactionSimulation>, RpcError> = simulateTransaction(transaction, defaultCommitment)
    fun simulateTransaction(transaction: SolanaTransactionCompiled, commitment: Commitment = this.defaultCommitment): RpcRequest<ContextValue<TransactionSimulation>, RpcError> = simulateTransaction(transaction.serializeForSimulation(), commitment)
    fun simulateTransaction(transaction: ByteArray): RpcRequest<ContextValue<TransactionSimulation>, RpcError> = simulateTransaction(transaction, defaultCommitment)
    fun simulateTransaction(transaction: ByteArray, commitment: Commitment = this.defaultCommitment): RpcRequest<ContextValue<TransactionSimulation>, RpcError> = rpc("simulateTransaction", Base64.encode(transaction), config(commitment, "base64")) { decode(it) }
    fun simulateTransaction(transaction: SolanaTransactionCompiled, options: SolanaSimulationConfig): RpcRequest<ContextValue<TransactionSimulation>, RpcError> = simulateTransaction(transaction, options, defaultCommitment)

    /** Simulate with explicit options; see [SolanaSimulationConfig]. */
    fun simulateTransaction(
        transaction: SolanaTransactionCompiled,
        options: SolanaSimulationConfig,
        commitment: Commitment = this.defaultCommitment,
    ): RpcRequest<ContextValue<TransactionSimulation>, RpcError> = rpc("simulateTransaction", Base64.encode(transaction.serializeForSimulation()), simulationConfig(commitment, options)) { decode(it) }

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
        return simulateTransaction(tx, SolanaSimulationConfig.READ_ONLY, commitment)
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
        return simulateTransaction(tx, SolanaSimulationConfig.READ_ONLY, commitment)
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
    fun getFeeForMessage(message: SolanaTransactionCompiled, commitment: Commitment = this.defaultCommitment): RpcRequest<ContextValue<BigInteger?>, RpcError> = getFeeForMessage(message.serializeMessage(), commitment)
    fun getFeeForMessage(message: ByteArray): RpcRequest<ContextValue<BigInteger?>, RpcError> = getFeeForMessage(message, defaultCommitment)
    fun getFeeForMessage(message: ByteArray, commitment: Commitment = this.defaultCommitment): RpcRequest<ContextValue<BigInteger?>, RpcError> = rpc("getFeeForMessage", Base64.encode(message), config(commitment)) { decodeContext(it) { v -> if (v == JsonNull) null else decodeU64(v) } }
    fun getRecentPrioritizationFees(): RpcRequest<List<PrioritizationFee>, RpcError> = getRecentPrioritizationFees(emptyList())
    fun getRecentPrioritizationFees(addresses: List<SolanaAddress> = emptyList()): RpcRequest<List<PrioritizationFee>, RpcError> {
        return rpc("getRecentPrioritizationFees", addresses.map { it.toString() }) { decode(it) }
    }
    fun getSignaturesForAddress(address: SolanaAddress): RpcRequest<List<TransactionSignature>, RpcError> = getSignaturesForAddress(address, 1000, defaultCommitment, null, null)
    fun getSignaturesForAddress(address: SolanaAddress, limit: Int): RpcRequest<List<TransactionSignature>, RpcError> = getSignaturesForAddress(address, limit, defaultCommitment, null, null)
    fun getSignaturesForAddress(address: SolanaAddress, commitment: Commitment): RpcRequest<List<TransactionSignature>, RpcError> = getSignaturesForAddress(address, 1000, commitment, null, null)
    fun getSignaturesForAddress(address: SolanaAddress, limit: Int, commitment: Commitment): RpcRequest<List<TransactionSignature>, RpcError> = getSignaturesForAddress(address, limit, commitment, null, null)
    fun getSignaturesForAddress(address: SolanaAddress, limit: Int, commitment: Commitment, before: SolanaSignature?): RpcRequest<List<TransactionSignature>, RpcError> = getSignaturesForAddress(address, limit, commitment, before, null)
    fun getSignaturesForAddress(address: SolanaAddress, limit: Int, before: SolanaSignature?, until: SolanaSignature?): RpcRequest<List<TransactionSignature>, RpcError> = getSignaturesForAddress(address, limit, defaultCommitment, before, until)
    fun getSignaturesForAddress(address: SolanaAddress, limit: Int = 1000, commitment: Commitment = this.defaultCommitment, before: SolanaSignature? = null, until: SolanaSignature? = null): RpcRequest<List<TransactionSignature>, RpcError> {
        val options = buildJsonObject {
            put("commitment", commitment.toString())
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

private fun simulationConfig(commitment: Commitment, config: SolanaSimulationConfig) = buildJsonObject {
    put("commitment", commitment.toString())
    put("encoding", "base64")
    if (config.sigVerify) put("sigVerify", true)
    if (config.replaceRecentBlockhash) put("replaceRecentBlockhash", true)
    if (config.innerInstructions) put("innerInstructions", true)
    config.minContextSlot?.let { put("minContextSlot", rpcInteger(it)) }
    if (config.accounts.isNotEmpty()) {
        putJsonObject("accounts") {
            put("encoding", "base64")
            putJsonArray("addresses") { config.accounts.forEach { add(it.toString()) } }
        }
    }
}

private fun config(commitment: Commitment, encoding: String? = null) = buildJsonObject {
    put("commitment", commitment.toString())
    encoding?.let { put("encoding", it) }
}

private fun <T> SolanaApi.rpc(method: String, vararg params: Any?, decoder: (JsonElement) -> T): RpcRequest<T, RpcError> = RpcCall(client, method, params, decoder)
