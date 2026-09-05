package io.ethers.solana.types

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Raw or jsonParsed RPC message. Missing fields remain null; no signing-model invariants are imposed. */
data class RPCMessage(val raw: JsonElement) {
    private val fields = RPCFields(raw)
    val header: RPCMessageHeader? = fields.value("header")?.let(::RPCMessageHeader)
    val accountKeys: List<RPCAccountKey>? = fields.list("accountKeys", ::RPCAccountKey)
    val recentBlockhash: Blockhash? = fields.string("recentBlockhash")?.let(::Blockhash)
    val instructions: List<RPCInstruction>? = fields.list("instructions", ::RPCInstruction)
    val addressTableLookups: List<RPCAddressTableLookup>? = fields.list("addressTableLookups", ::RPCAddressTableLookup)
    val otherFields: Map<String, JsonElement> = fields.otherFields()
}

data class RPCMessageHeader(val raw: JsonElement) {
    private val fields = RPCFields(raw)
    val numRequiredSignatures: Int? = fields.u8("numRequiredSignatures")
    val numReadonlySignedAccounts: Int? = fields.u8("numReadonlySignedAccounts")
    val numReadonlyUnsignedAccounts: Int? = fields.u8("numReadonlyUnsignedAccounts")
    val otherFields: Map<String, JsonElement> = fields.otherFields()
}

/** A plain address or a jsonParsed account with privileges and a forward-compatible source string. */
data class RPCAccountKey(val raw: JsonElement) {
    private val fields = RPCFields(raw)
    val address: SolanaAddress? = if (raw is JsonPrimitive && raw.isString) rpcAddress(raw) else fields.string("pubkey")?.let(::SolanaAddress)
    val signer: Boolean? = fields.bool("signer")
    val writable: Boolean? = fields.bool("writable")
    val source: String? = fields.string("source")
    val otherFields: Map<String, JsonElement> = fields.otherFields()
}

data class RPCAddressTableLookup(val raw: JsonElement) {
    private val fields = RPCFields(raw)
    val accountKey: SolanaAddress? = fields.string("accountKey")?.let(::SolanaAddress)
    val writableIndexes: List<Int>? = fields.list("writableIndexes", ::rpcU8)
    val readonlyIndexes: List<Int>? = fields.list("readonlyIndexes", ::rpcU8)
    val otherFields: Map<String, JsonElement> = fields.otherFields()
}
