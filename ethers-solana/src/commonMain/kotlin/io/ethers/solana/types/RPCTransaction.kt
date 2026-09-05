package io.ethers.solana.types

import io.ethers.solana.types.transaction.SolanaTxType
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * A getTransaction response, independent of the signable transaction hierarchy.
 * [raw] retains every field, including unknown nested fields and explicit nulls, without decoding the
 * message or verifying signatures. Serialization reproduces this JSON, not a binary transaction envelope.
 * The RPC node must still support the requested version and have the transaction available.
 */
@Serializable(with = RPCTransactionSerializer::class)
data class RPCTransaction(val raw: JsonObject) {
    val slot: BigInteger = Json.decodeFromJsonElement(U64Serializer, raw.getValue("slot"))
    val blockTime: Long? = raw["blockTime"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.long

    /** The full JSON or encoded transaction payload; its structure is deliberately not version-constrained. */
    val transaction: JsonElement = raw.getValue("transaction")
    val meta: JsonElement? = raw["meta"]?.takeUnless { it == JsonNull }

    /** Null means the node omitted the version, returned null, or used an unrecognized representation. */
    val type: SolanaTxType? = when (val version = raw["version"]) {
        JsonPrimitive("legacy") -> SolanaTxType.Legacy
        is JsonPrimitive -> if (version.isString) null else version.intOrNull?.takeIf { it >= 0 }?.let(SolanaTxType::fromVersion)
        else -> null
    }

    val otherFields: Map<String, JsonElement> = raw.filterKeys { it !in KNOWN_FIELDS }
}

object RPCTransactionSerializer : KSerializer<RPCTransaction> {
    override val descriptor = buildClassSerialDescriptor("SolanaRPCTransaction")
    override fun deserialize(decoder: Decoder): RPCTransaction = RPCTransaction((decoder as JsonDecoder).decodeJsonElement().jsonObject)
    override fun serialize(encoder: Encoder, value: RPCTransaction) = (encoder as JsonEncoder).encodeJsonElement(value.raw)
}

private val KNOWN_FIELDS = setOf("slot", "blockTime", "transaction", "meta", "version")
