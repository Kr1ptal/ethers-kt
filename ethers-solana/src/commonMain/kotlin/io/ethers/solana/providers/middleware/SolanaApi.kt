package io.ethers.solana.providers.middleware

import io.ethers.providers.JsonRpcClient
import io.ethers.providers.RpcError
import io.ethers.providers.types.RpcCall
import io.ethers.providers.types.RpcRequest
import io.ethers.solana.providers.decode
import io.ethers.solana.providers.decodeAccount
import io.ethers.solana.providers.decodeContext
import io.ethers.solana.providers.decodeU64
import io.ethers.solana.providers.u64
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
import io.ethers.solana.types.TokenAmount
import io.ethers.solana.types.TransactionSignature
import io.ethers.solana.types.TransactionSimulation
import io.ethers.solana.types.transaction.SolanaTransaction
import io.ethers.solana.types.transaction.SolanaTransactionSigned
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

/**
 * Solana JSON-RPC capabilities, composable over an existing ethers JsonRpcClient.
 * Results retain the RPC response shape, including context when supplied by the node.
 */
interface SolanaApi {
    val client: JsonRpcClient

    /** Immutable fallback for commitment-aware calls; explicit request arguments take precedence. */
    val defaultCommitment: Commitment

    fun getBalance(address: SolanaAddress, commitment: Commitment = this.defaultCommitment): RpcRequest<ContextValue<BigInteger>, RpcError> = rpc("getBalance", address.toString(), config(commitment)) { decodeContext(it, ::decodeU64) }
    fun getTokenAccountBalance(address: SolanaAddress, commitment: Commitment = this.defaultCommitment): RpcRequest<ContextValue<TokenAmount>, RpcError> = rpc("getTokenAccountBalance", address.toString(), config(commitment)) { decode(it) }
    fun getTokenSupply(mint: SolanaAddress, commitment: Commitment = this.defaultCommitment): RpcRequest<ContextValue<TokenAmount>, RpcError> = rpc("getTokenSupply", mint.toString(), config(commitment)) { decode(it) }
    fun getLatestBlockhash(commitment: Commitment = this.defaultCommitment): RpcRequest<ContextValue<LatestBlockhash>, RpcError> = rpc("getLatestBlockhash", config(commitment)) { decode(it) }
    fun isBlockhashValid(blockhash: SolanaBlockhash, commitment: Commitment = this.defaultCommitment): RpcRequest<ContextValue<Boolean>, RpcError> = rpc("isBlockhashValid", blockhash.toString(), config(commitment)) { decodeContext(it) { v -> v.jsonPrimitive.boolean } }
    fun getHealth(): RpcRequest<SolanaNodeHealth, RpcError> = rpc("getHealth") { if (it.jsonPrimitive.content == "ok") SolanaNodeHealth.OK else SolanaNodeHealth.ERROR }
    fun getEpochInfo(commitment: Commitment = this.defaultCommitment): RpcRequest<EpochInfo, RpcError> = rpc("getEpochInfo", config(commitment)) { decode(it) }
    fun getIdentity(): RpcRequest<SolanaNodeIdentity, RpcError> = rpc("getIdentity") { decode(it) }
    fun getVersion(): RpcRequest<SolanaNodeVersion, RpcError> = rpc("getVersion") { decode(it) }
    fun getTransactionCount(commitment: Commitment = this.defaultCommitment): RpcRequest<BigInteger, RpcError> = rpc("getTransactionCount", config(commitment), decoder = ::decodeU64)

    /**
     * Read a confirmed transaction without requiring support for its message version. The default ceiling
     * accepts the full RPC u8 version range, not just versions this library can sign. Missing transactions
     * return null; server errors (including unsupported versions) remain [RpcError]s.
     */
    fun getTransaction(signature: SolanaSignature, commitment: Commitment = this.defaultCommitment, maxSupportedTransactionVersion: Int = 255): RpcRequest<SolanaRPCTransaction?, RpcError> {
        require(commitment != Commitment.PROCESSED) { "Transaction history requires confirmed or finalized commitment" }
        require(maxSupportedTransactionVersion in 0..255) { "Maximum transaction version must fit in an unsigned byte" }
        val options = buildJsonObject {
            put("commitment", commitment.toString())
            put("encoding", "json")
            put("maxSupportedTransactionVersion", maxSupportedTransactionVersion)
        }
        return rpc("getTransaction", signature.toString(), options) { if (it == JsonNull) null else decode<SolanaRPCTransaction>(it) }
    }
    fun getAccountInfo(address: SolanaAddress, commitment: Commitment = this.defaultCommitment): RpcRequest<ContextValue<AccountInfo?>, RpcError> = rpc("getAccountInfo", address.toString(), config(commitment, "base64")) { decodeContext(it, ::decodeAccount) }
    fun getMultipleAccounts(addresses: List<SolanaAddress>, commitment: Commitment = this.defaultCommitment): RpcRequest<ContextValue<List<AccountInfo?>>, RpcError> {
        require(addresses.size <= 100) { "getMultipleAccounts accepts at most 100 addresses" }
        return rpc("getMultipleAccounts", addresses.map { it.toString() }, config(commitment, "base64")) { decodeContext(it) { v -> v.jsonArray.map(::decodeAccount) } }
    }
    fun getMinimumBalanceForRentExemption(space: BigInteger, commitment: Commitment = this.defaultCommitment): RpcRequest<BigInteger, RpcError> = rpc("getMinimumBalanceForRentExemption", u64(space), config(commitment), decoder = ::decodeU64)
    fun getMinimumBalanceForRentExemption(space: Long): RpcRequest<BigInteger, RpcError> = getMinimumBalanceForRentExemption(bigIntegerOf(space))
    fun requestAirdrop(address: SolanaAddress, lamports: BigInteger, commitment: Commitment = this.defaultCommitment): RpcRequest<SolanaSignature, RpcError> = rpc("requestAirdrop", address.toString(), u64(lamports), config(commitment)) { SolanaSignature(it.jsonPrimitive.content) }
    fun requestAirdrop(address: SolanaAddress, lamports: Long): RpcRequest<SolanaSignature, RpcError> = requestAirdrop(address, bigIntegerOf(lamports))

    fun sendTransaction(transaction: SolanaTransactionSigned, preflightCommitment: Commitment = this.defaultCommitment): RpcRequest<SolanaSignature, RpcError> = sendTransaction(transaction.serialize(), preflightCommitment)
    fun sendTransaction(transaction: ByteArray, preflightCommitment: Commitment = this.defaultCommitment): RpcRequest<SolanaSignature, RpcError> {
        // Validate raw inputs too: partial signing is an explicit offline/simulation operation.
        SolanaTransactionSigned.deserialize(transaction)
        require(transaction.size <= 1232) { "Transaction exceeds Solana's packet size" }
        return rpc(
            "sendTransaction",
            Base64.encode(transaction),
            buildJsonObject {
                put("encoding", "base64")
                put("preflightCommitment", preflightCommitment.toString())
            },
        ) { SolanaSignature(it.jsonPrimitive.content) }
    }
    fun simulateTransaction(transaction: SolanaTransaction, commitment: Commitment = this.defaultCommitment): RpcRequest<ContextValue<TransactionSimulation>, RpcError> = simulateTransaction(transaction.serializeForSimulation(), commitment)
    fun simulateTransaction(transaction: ByteArray, commitment: Commitment = this.defaultCommitment): RpcRequest<ContextValue<TransactionSimulation>, RpcError> = rpc("simulateTransaction", Base64.encode(transaction), config(commitment, "base64")) { decode(it) }
    fun getFeeForMessage(message: SolanaTransaction, commitment: Commitment = this.defaultCommitment): RpcRequest<ContextValue<BigInteger?>, RpcError> = getFeeForMessage(message.serializeMessage(), commitment)
    fun getFeeForMessage(message: ByteArray, commitment: Commitment = this.defaultCommitment): RpcRequest<ContextValue<BigInteger?>, RpcError> = rpc("getFeeForMessage", Base64.encode(message), config(commitment)) { decodeContext(it) { v -> if (v == JsonNull) null else decodeU64(v) } }
    fun getRecentPrioritizationFees(addresses: List<SolanaAddress> = emptyList()): RpcRequest<List<PrioritizationFee>, RpcError> {
        require(addresses.size <= 128) { "At most 128 account addresses are supported" }
        return rpc("getRecentPrioritizationFees", addresses.map { it.toString() }) { decode(it) }
    }
    fun getSignaturesForAddress(address: SolanaAddress, limit: Int = 1000, commitment: Commitment = this.defaultCommitment, before: SolanaSignature? = null, until: SolanaSignature? = null): RpcRequest<List<TransactionSignature>, RpcError> {
        require(limit in 1..1000) { "Limit must be between 1 and 1000" }
        require(commitment != Commitment.PROCESSED) { "Signature history requires confirmed or finalized commitment" }
        val options = buildJsonObject {
            put("commitment", commitment.toString())
            put("limit", limit)
            before?.let { put("before", it.toString()) }
            until?.let { put("until", it.toString()) }
        }
        return rpc("getSignaturesForAddress", address.toString(), options) { decode(it) }
    }
}

private fun config(commitment: Commitment, encoding: String? = null) = buildJsonObject {
    put("commitment", commitment.toString())
    encoding?.let { put("encoding", it) }
}

private fun <T> SolanaApi.rpc(method: String, vararg params: Any?, decoder: (JsonElement) -> T): RpcRequest<T, RpcError> = RpcCall(client, method, params, decoder)
