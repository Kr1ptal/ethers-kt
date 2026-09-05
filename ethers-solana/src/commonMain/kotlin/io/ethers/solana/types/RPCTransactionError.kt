package io.ethers.solana.types

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** An extensible RPC error variant, including instruction errors with their transaction instruction index. */
data class RPCTransactionError(val raw: JsonElement) {
    val kind: String? = rpcErrorKind(raw)
    private val instruction = (raw as? JsonObject)?.get("InstructionError") as? JsonArray
    val instructionIndex: Int? = instruction?.takeIf { it.size == 2 }?.get(0)?.let(::rpcU8)
    val instructionError: RPCInstructionError? = instruction?.takeIf { it.size == 2 }?.get(1)?.let(::RPCInstructionError)
    val otherFields: Map<String, JsonElement> = (raw as? JsonObject)?.filterKeys { it != "InstructionError" || instructionIndex == null } ?: emptyMap()
}

/** Error names are open-ended; program-specific Custom codes are decoded as u32 values. */
data class RPCInstructionError(val raw: JsonElement) {
    val kind: String? = rpcErrorKind(raw)
    val customCode: Long? = (raw as? JsonObject)?.get("Custom")?.let(::rpcU32)
    val otherFields: Map<String, JsonElement> = (raw as? JsonObject)?.filterKeys { it != "Custom" } ?: emptyMap()
}

private fun rpcErrorKind(raw: JsonElement): String? = when (raw) {
    is JsonPrimitive -> if (raw.isString) raw.content else null
    is JsonObject -> raw.keys.singleOrNull()
    else -> null
}
