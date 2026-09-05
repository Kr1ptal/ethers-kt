package io.ethers.solana.types

import io.ethers.solana.utils.requireU64
import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

data class RPCTokenBalance(val raw: JsonElement) {
    private val fields = RPCFields(raw)
    val accountIndex: Int? = fields.u8("accountIndex")
    val mint: SolanaAddress? = fields.string("mint")?.let(::SolanaAddress)
    val owner: SolanaAddress? = fields.string("owner")?.let(::SolanaAddress)
    val programId: SolanaAddress? = fields.string("programId")?.let(::SolanaAddress)
    val uiTokenAmount: RPCTokenAmount? = fields.value("uiTokenAmount")?.let(::RPCTokenAmount)
    val otherFields: Map<String, JsonElement> = fields.otherFields()
}

data class RPCTokenAmount(val raw: JsonElement) {
    private val fields = RPCFields(raw)
    val amount: BigInteger? = fields.string("amount")?.let { requireU64(BigInteger(it)) }
    val decimals: Int? = fields.u8("decimals")

    /** Exact decimal representation of the JSON number; use amount/decimals for token arithmetic. */
    val uiAmount: BigDecimal? = fields.value("uiAmount")?.jsonPrimitive?.also { require(!it.isString) }?.content?.let(::BigDecimal)
    val uiAmountString: String? = fields.string("uiAmountString")
    val otherFields: Map<String, JsonElement> = fields.otherFields()
}
