package io.ethers.solana.types

import io.ethers.core.types.Bytes
import io.ethers.crypto.Base58
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Compiled, partially decoded, or parsed instruction. Compiled instructions use [programIdIndex] and
 * [accountIndices]; partially decoded instructions use [programId] and [accounts]. Program-specific
 * parsed content remains JSON in [parsed]. Unknown instruction layouts keep [raw] and any recognizable fields.
 */
data class RPCInstruction(val raw: JsonElement) {
    private val fields = RPCFields(raw)
    val programIdIndex: Int? = fields.u8("programIdIndex")
    val programId: SolanaAddress? = fields.string("programId")?.let(::SolanaAddress)
    val program: String? = fields.string("program")
    private val rawAccounts = fields.value("accounts")
    val accountIndices: List<Int>? = (rawAccounts as? JsonArray)?.takeIf { programIdIndex != null || (programId == null && it.all { item -> item is JsonPrimitive && !item.isString }) }?.map(::rpcU8)
    val accounts: List<SolanaAddress>? = (rawAccounts as? JsonArray)?.takeIf { accountIndices == null && it.all { item -> item is JsonPrimitive && item.isString } }?.map(::rpcAddress)
    val data: Bytes? = fields.string("data")?.let { Bytes(Base58.decode(it)) }
    val parsed: JsonElement? = fields.value("parsed")
    val stackHeight: Long? = fields.u32("stackHeight")
    val otherFields: Map<String, JsonElement> = fields.otherFields()
}
