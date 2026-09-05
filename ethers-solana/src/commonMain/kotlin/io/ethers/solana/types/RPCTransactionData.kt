package io.ethers.solana.types

import io.ethers.core.types.Bytes
import kotlinx.serialization.json.JsonElement

/**
 * RPC transaction payload, not a signable transaction. JSON fields are decoded independently of version.
 * Encoded payloads expose [data] without parsing the binary message. Unknown layouts retain [raw]; typed
 * fields absent from that layout are null. Present signatures are format-checked, not cryptographically verified.
 */
data class RPCTransactionData(val raw: JsonElement) {
    private val fields = RPCFields(raw)
    val signatures: List<Signature>? = fields.list("signatures") { Signature(rpcString(it)) }
    val message: RPCMessage? = fields.value("message")?.let(::RPCMessage)

    /** Accounts-only RPC representation; ordinary transaction accounts are in [message]. */
    val accountKeys: List<RPCAccountKey>? = fields.list("accountKeys", ::RPCAccountKey)
    val encoding: String? = rpcEncoding(raw)
    val data: Bytes? = rpcEncodedBytes(raw, encoding)
    val otherFields: Map<String, JsonElement> = fields.otherFields()
}
