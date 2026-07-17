package io.ethers.providers.types

import io.ethers.core.ThrowingError
import io.ethers.core.types.BlockId
import io.ethers.core.types.BlockIdSerializer
import io.ethers.core.types.BlockOverride
import io.ethers.core.types.BlockOverrideSerializer
import io.ethers.core.types.CallRequestSerializer
import io.ethers.core.types.IntoCallRequest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Error returned for a call in `eth_callMany` that fails.
 * */
data class CallFailedError(val error: String) : ThrowingError

/**
 * Internal type used for correctly serializing `eth_callMany`/`debug_traceCallMany` request arguments.
 * */
@Serializable(with = CallManyBundleSerializer::class)
internal data class CallManyBundle(
    val transactions: List<IntoCallRequest>,
    val blockOverride: BlockOverride? = null,
)

/**
 * Internal type used for correctly serializing `eth_callMany`/`debug_traceCallMany` request arguments.
 * */
@Serializable(with = CallManyContextSerializer::class)
internal data class CallManyContext(
    val blockNumber: BlockId,
    val transactionIndex: Int,
)

internal object CallManyBundleSerializer : KSerializer<CallManyBundle> {
    override val descriptor = buildClassSerialDescriptor("CallManyBundle")

    override fun serialize(encoder: Encoder, value: CallManyBundle) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                put(
                    "transactions",
                    buildJsonArray {
                        for (tx in value.transactions) {
                            add(jsonEncoder.json.encodeToJsonElement(CallRequestSerializer, tx.toCallRequest()))
                        }
                    },
                )
                if (value.blockOverride != null) {
                    put(
                        "blockOverride",
                        jsonEncoder.json.encodeToJsonElement(BlockOverrideSerializer, value.blockOverride),
                    )
                }
            },
        )
    }

    override fun deserialize(decoder: Decoder): CallManyBundle = throw UnsupportedOperationException()
}

internal object CallManyContextSerializer : KSerializer<CallManyContext> {
    override val descriptor = buildClassSerialDescriptor("CallManyContext")

    override fun serialize(encoder: Encoder, value: CallManyContext) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                put("blockNumber", jsonEncoder.json.encodeToJsonElement(BlockIdSerializer, value.blockNumber))
                put("transactionIndex", value.transactionIndex)
            },
        )
    }

    override fun deserialize(decoder: Decoder): CallManyContext = throw UnsupportedOperationException()
}
