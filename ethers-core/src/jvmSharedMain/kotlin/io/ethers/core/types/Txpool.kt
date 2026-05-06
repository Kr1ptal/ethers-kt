package io.ethers.core.types

import io.ethers.core.asHexLong
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Content of the transaction pool, including both pending and queued transactions, grouped by sender address
 * and nonce values.
 */
@Serializable(with = TxpoolContentSerializer::class)
data class TxpoolContent(
    val pending: Map<Address, Map<Long, RPCTransaction>>,
    val queued: Map<Address, Map<Long, RPCTransaction>>,
)

/**
 * Status of the transaction pool, including number of pending and queued transactions.
 */
@Serializable(with = TxpoolStatusSerializer::class)
data class TxpoolStatus(
    val pending: Long,
    val queued: Long,
)

/**
 * Content of the transaction pool for a given address, including both pending and queued transactions,
 * grouped by nonce values.
 */
@Serializable(with = TxpoolContentFromAddressSerializer::class)
data class TxpoolContentFromAddress(
    val pending: Map<Long, RPCTransaction>,
    val queued: Map<Long, RPCTransaction>,
)

/**
 * Flattened transaction pool content for easy inspection, grouped by sender address and nonce values.
 *
 * Transaction is represented as formatted string:
 *   - normal TX:            "addressTo: value + gasLimit + gasPrice"
 *   - contract creation:    "contract creation: value + gasLimit + gasPrice"
 */
@Serializable(with = TxpoolInspectResultSerializer::class)
data class TxpoolInspectResult(
    val pending: Map<Address, Map<Long, String>>,
    val queued: Map<Address, Map<Long, String>>,
)

private fun readRpcTxMap(json: Json, obj: JsonObject): Map<Address, Map<Long, RPCTransaction>> {
    return obj.entries.associate { (addrStr, inner) ->
        val address = Address(addrStr)
        val innerMap = inner.jsonObject.entries.associate { (nonceStr, txElement) ->
            val nonce = nonceStr.toLong()
            val tx = json.decodeFromJsonElement(RPCTransactionSerializer, txElement)
            nonce to tx
        }
        address to innerMap
    }
}

private fun readInspectMap(obj: JsonObject): Map<Address, Map<Long, String>> {
    return obj.entries.associate { (addrStr, inner) ->
        val address = Address(addrStr)
        val innerMap = inner.jsonObject.entries.associate { (nonceStr, strElement) ->
            nonceStr.toLong() to strElement.jsonPrimitive.content
        }
        address to innerMap
    }
}

object TxpoolContentSerializer : KSerializer<TxpoolContent> {
    override val descriptor = buildClassSerialDescriptor("TxpoolContent")

    override fun serialize(encoder: Encoder, value: TxpoolContent) = throw UnsupportedOperationException()

    override fun deserialize(decoder: Decoder): TxpoolContent {
        val jsonDecoder = decoder as JsonDecoder
        val obj = jsonDecoder.decodeJsonElement().jsonObject

        var pending: Map<Address, Map<Long, RPCTransaction>> = emptyMap()
        var queued: Map<Address, Map<Long, RPCTransaction>> = emptyMap()

        for ((key, element) in obj.entries) {
            when (key) {
                "pending" -> pending = readRpcTxMap(jsonDecoder.json, element.jsonObject)
                "queued" -> queued = readRpcTxMap(jsonDecoder.json, element.jsonObject)
            }
        }

        return TxpoolContent(pending, queued)
    }
}

object TxpoolStatusSerializer : KSerializer<TxpoolStatus> {
    override val descriptor = buildClassSerialDescriptor("TxpoolStatus")

    override fun serialize(encoder: Encoder, value: TxpoolStatus) = throw UnsupportedOperationException()

    override fun deserialize(decoder: Decoder): TxpoolStatus {
        val obj = (decoder as JsonDecoder).decodeJsonElement().jsonObject

        var pending: Long? = null
        var queued: Long? = null

        for ((key, element) in obj.entries) {
            when (key) {
                "pending" -> pending = element.jsonPrimitive.asHexLong()
                "queued" -> queued = element.jsonPrimitive.asHexLong()
            }
        }

        return TxpoolStatus(pending!!, queued!!)
    }
}

object TxpoolContentFromAddressSerializer : KSerializer<TxpoolContentFromAddress> {
    override val descriptor = buildClassSerialDescriptor("TxpoolContentFromAddress")

    override fun serialize(encoder: Encoder, value: TxpoolContentFromAddress) = throw UnsupportedOperationException()

    override fun deserialize(decoder: Decoder): TxpoolContentFromAddress {
        val jsonDecoder = decoder as JsonDecoder
        val obj = jsonDecoder.decodeJsonElement().jsonObject

        var pending: Map<Long, RPCTransaction> = emptyMap()
        var queued: Map<Long, RPCTransaction> = emptyMap()

        for ((key, element) in obj.entries) {
            when (key) {
                "pending" -> pending = element.jsonObject.entries.associate { (nonceStr, txElement) ->
                    nonceStr.toLong() to jsonDecoder.json.decodeFromJsonElement(RPCTransactionSerializer, txElement)
                }
                "queued" -> queued = element.jsonObject.entries.associate { (nonceStr, txElement) ->
                    nonceStr.toLong() to jsonDecoder.json.decodeFromJsonElement(RPCTransactionSerializer, txElement)
                }
            }
        }

        return TxpoolContentFromAddress(pending, queued)
    }
}

object TxpoolInspectResultSerializer : KSerializer<TxpoolInspectResult> {
    override val descriptor = buildClassSerialDescriptor("TxpoolInspectResult")

    override fun serialize(encoder: Encoder, value: TxpoolInspectResult) = throw UnsupportedOperationException()

    override fun deserialize(decoder: Decoder): TxpoolInspectResult {
        val obj = (decoder as JsonDecoder).decodeJsonElement().jsonObject

        var pending: Map<Address, Map<Long, String>> = emptyMap()
        var queued: Map<Address, Map<Long, String>> = emptyMap()

        for ((key, element) in obj.entries) {
            when (key) {
                "pending" -> pending = readInspectMap(element.jsonObject)
                "queued" -> queued = readInspectMap(element.jsonObject)
            }
        }

        return TxpoolInspectResult(pending, queued)
    }
}
