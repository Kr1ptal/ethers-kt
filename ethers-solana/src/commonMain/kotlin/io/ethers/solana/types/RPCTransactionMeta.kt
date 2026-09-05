package io.ethers.solana.types

import io.ethers.core.types.Bytes
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.json.JsonElement

/** Execution metadata is decoded independently of the transaction's message layout and version. */
data class RPCTransactionMeta(val raw: JsonElement) {
    private val fields = RPCFields(raw)
    val err: RPCTransactionError? = fields.value("err")?.let(::RPCTransactionError)

    /** Null when the node did not report an execution result. */
    val isSuccess: Boolean? = if (fields.contains("err")) err == null else null
    val fee: BigInteger? = fields.u64("fee")
    val preBalances: List<BigInteger>? = fields.list("preBalances", ::rpcU64)
    val postBalances: List<BigInteger>? = fields.list("postBalances", ::rpcU64)
    val innerInstructions: List<RPCInnerInstructions>? = fields.list("innerInstructions", ::RPCInnerInstructions)
    val logMessages: List<String>? = fields.list("logMessages", ::rpcString)
    val preTokenBalances: List<RPCTokenBalance>? = fields.list("preTokenBalances", ::RPCTokenBalance)
    val postTokenBalances: List<RPCTokenBalance>? = fields.list("postTokenBalances", ::RPCTokenBalance)
    val rewards: List<RPCReward>? = fields.list("rewards", ::RPCReward)
    val loadedAddresses: RPCLoadedAddresses? = fields.value("loadedAddresses")?.let(::RPCLoadedAddresses)
    val returnData: RPCReturnData? = fields.value("returnData")?.let(::RPCReturnData)
    val computeUnitsConsumed: BigInteger? = fields.u64("computeUnitsConsumed")
    val costUnits: BigInteger? = fields.u64("costUnits")

    // The obsolete status field is preserved in otherFields; err is the authoritative result.
    val otherFields: Map<String, JsonElement> = fields.otherFields()
}

data class RPCInnerInstructions(val raw: JsonElement) {
    private val fields = RPCFields(raw)
    val index: Int? = fields.u8("index")
    val instructions: List<RPCInstruction>? = fields.list("instructions", ::RPCInstruction)
    val otherFields: Map<String, JsonElement> = fields.otherFields()
}

data class RPCLoadedAddresses(val raw: JsonElement) {
    private val fields = RPCFields(raw)
    val writable: List<SolanaAddress>? = fields.list("writable", ::rpcAddress)
    val readonly: List<SolanaAddress>? = fields.list("readonly", ::rpcAddress)
    val otherFields: Map<String, JsonElement> = fields.otherFields()
}

data class RPCReturnData(val raw: JsonElement) {
    private val fields = RPCFields(raw)
    val programId: SolanaAddress? = fields.string("programId")?.let(::SolanaAddress)
    private val rawData = fields.value("data")
    val encoding: String? = rawData?.let(::rpcEncoding)
    val data: Bytes? = rawData?.let { rpcEncodedBytes(it, encoding) }
    val otherFields: Map<String, JsonElement> = fields.otherFields()
}

data class RPCReward(val raw: JsonElement) {
    private val fields = RPCFields(raw)
    val pubkey: SolanaAddress? = fields.string("pubkey")?.let(::SolanaAddress)

    /** Signed change in lamports; rewards can be negative. */
    val lamports: Long? = fields.i64("lamports")
    val postBalance: BigInteger? = fields.u64("postBalance")

    /** String rather than a closed enum so future reward types remain readable. */
    val rewardType: String? = fields.string("rewardType")
    val commission: Int? = fields.u8("commission")
    val otherFields: Map<String, JsonElement> = fields.otherFields()
}
