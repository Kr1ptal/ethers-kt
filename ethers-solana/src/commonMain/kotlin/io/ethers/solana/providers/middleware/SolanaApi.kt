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
import io.ethers.solana.types.Blockhash
import io.ethers.solana.types.Commitment
import io.ethers.solana.types.ContextValue
import io.ethers.solana.types.EpochInfo
import io.ethers.solana.types.Health
import io.ethers.solana.types.LatestBlockhash
import io.ethers.solana.types.PrioritizationFee
import io.ethers.solana.types.Signature
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.TokenAmount
import io.ethers.solana.types.TransactionSignature
import io.ethers.solana.types.TransactionSimulation
import io.ethers.solana.types.Version
import io.ethers.solana.types.transaction.SolanaTransaction
import io.ethers.solana.types.transaction.SolanaTransactionSigned
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

/** Solana JSON-RPC capabilities, composable over an existing ethers JsonRpcClient. */
interface SolanaApi {
    val client: JsonRpcClient
    val commitment: Commitment

    fun getBalance(address: SolanaAddress, commitment: Commitment = this.commitment): RpcRequest<BigInteger, RpcError> = getBalanceWithContext(address, commitment).map { it.value }
    fun getBalanceWithContext(address: SolanaAddress, commitment: Commitment = this.commitment): RpcRequest<ContextValue<BigInteger>, RpcError> = rpc("getBalance", address.toString(), config(commitment)) { decodeContext(it, ::decodeU64) }
    fun getTokenAccountBalance(address: SolanaAddress, commitment: Commitment = this.commitment): RpcRequest<ContextValue<TokenAmount>, RpcError> = rpc("getTokenAccountBalance", address.toString(), config(commitment)) { decode(it) }
    fun getTokenSupply(mint: SolanaAddress, commitment: Commitment = this.commitment): RpcRequest<ContextValue<TokenAmount>, RpcError> = rpc("getTokenSupply", mint.toString(), config(commitment)) { decode(it) }
    fun getLatestBlockhash(commitment: Commitment = this.commitment): RpcRequest<ContextValue<LatestBlockhash>, RpcError> = rpc("getLatestBlockhash", config(commitment)) { decode(it) }
    fun isBlockhashValid(blockhash: Blockhash, commitment: Commitment = this.commitment): RpcRequest<ContextValue<Boolean>, RpcError> = rpc("isBlockhashValid", blockhash.toString(), config(commitment)) { decodeContext(it) { v -> v.jsonPrimitive.boolean } }
    fun getHealth(): RpcRequest<Health, RpcError> = rpc("getHealth") { if (it.jsonPrimitive.content == "ok") Health.OK else Health.ERROR }
    fun getEpochInfo(commitment: Commitment = this.commitment): RpcRequest<EpochInfo, RpcError> = rpc("getEpochInfo", config(commitment)) { decode(it) }
    fun getIdentity(): RpcRequest<SolanaAddress, RpcError> = rpc("getIdentity") { SolanaAddress(it.jsonObject.getValue("identity").jsonPrimitive.content) }
    fun getVersion(): RpcRequest<Version, RpcError> = rpc("getVersion") { decode(it) }
    fun getTransactionCount(commitment: Commitment = this.commitment): RpcRequest<BigInteger, RpcError> = rpc("getTransactionCount", config(commitment), decoder = ::decodeU64)
    fun getAccountInfo(address: SolanaAddress, commitment: Commitment = this.commitment): RpcRequest<ContextValue<AccountInfo?>, RpcError> = rpc("getAccountInfo", address.toString(), config(commitment, "base64")) { decodeContext(it, ::decodeAccount) }
    fun getMultipleAccounts(addresses: List<SolanaAddress>, commitment: Commitment = this.commitment): RpcRequest<ContextValue<List<AccountInfo?>>, RpcError> {
        require(addresses.size <= 100) { "getMultipleAccounts accepts at most 100 addresses" }
        return rpc("getMultipleAccounts", addresses.map { it.toString() }, config(commitment, "base64")) { decodeContext(it) { v -> v.jsonArray.map(::decodeAccount) } }
    }
    fun getMinimumBalanceForRentExemption(space: BigInteger, commitment: Commitment = this.commitment): RpcRequest<BigInteger, RpcError> = rpc("getMinimumBalanceForRentExemption", u64(space), config(commitment), decoder = ::decodeU64)
    fun getMinimumBalanceForRentExemption(space: Long): RpcRequest<BigInteger, RpcError> = getMinimumBalanceForRentExemption(bigIntegerOf(space))
    fun requestAirdrop(address: SolanaAddress, lamports: BigInteger, commitment: Commitment = this.commitment): RpcRequest<Signature, RpcError> = rpc("requestAirdrop", address.toString(), u64(lamports), config(commitment)) { Signature(it.jsonPrimitive.content) }
    fun requestAirdrop(address: SolanaAddress, lamports: Long): RpcRequest<Signature, RpcError> = requestAirdrop(address, bigIntegerOf(lamports))

    fun sendTransaction(transaction: SolanaTransactionSigned, preflightCommitment: Commitment = this.commitment): RpcRequest<Signature, RpcError> = sendTransaction(transaction.serialize(), preflightCommitment)
    fun sendTransaction(transaction: ByteArray, preflightCommitment: Commitment = this.commitment): RpcRequest<Signature, RpcError> {
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
        ) { Signature(it.jsonPrimitive.content) }
    }
    fun simulateTransaction(transaction: SolanaTransaction, commitment: Commitment = this.commitment): RpcRequest<ContextValue<TransactionSimulation>, RpcError> = simulateTransaction(transaction.serializeForSimulation(), commitment)
    fun simulateTransaction(transaction: ByteArray, commitment: Commitment = this.commitment): RpcRequest<ContextValue<TransactionSimulation>, RpcError> = rpc("simulateTransaction", Base64.encode(transaction), config(commitment, "base64")) { decode(it) }
    fun getFeeForMessage(message: SolanaTransaction, commitment: Commitment = this.commitment): RpcRequest<ContextValue<BigInteger?>, RpcError> = getFeeForMessage(message.serializeMessage(), commitment)
    fun getFeeForMessage(message: ByteArray, commitment: Commitment = this.commitment): RpcRequest<ContextValue<BigInteger?>, RpcError> = rpc("getFeeForMessage", Base64.encode(message), config(commitment)) { decodeContext(it) { v -> if (v == JsonNull) null else decodeU64(v) } }
    fun getRecentPrioritizationFees(addresses: List<SolanaAddress> = emptyList()): RpcRequest<List<PrioritizationFee>, RpcError> {
        require(addresses.size <= 128) { "At most 128 account addresses are supported" }
        return rpc("getRecentPrioritizationFees", addresses.map { it.toString() }) { decode(it) }
    }
    fun getSignaturesForAddress(address: SolanaAddress, limit: Int = 1000, commitment: Commitment = this.commitment, before: Signature? = null, until: Signature? = null): RpcRequest<List<TransactionSignature>, RpcError> {
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
