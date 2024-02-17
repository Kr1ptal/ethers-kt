package io.ethers.providers.types

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.ethers.core.Result
import io.ethers.core.types.BlockId
import io.ethers.core.types.BlockOverride
import io.ethers.core.types.CallRequest

/**
 * Error returned for a call in `eth_callMany` that fails.
 * */
data class CallFailedError(val error: String) : Result.Error

/**
 * Internal type used for correctly serializing `eth_callMany`/`debug_traceCallMany` request arguments.
 * */
@JsonSerialize(using = CallManyBundleSerializer::class)
internal data class CallManyBundle(
    val transactions: List<CallRequest>,
    val blockOverride: BlockOverride? = null,
)

/**
 * Internal type used for correctly serializing `eth_callMany`/`debug_traceCallMany` request arguments.
 * */
@JsonSerialize(using = CallManyContextSerializer::class)
internal data class CallManyContext(
    val blockNumber: BlockId,
    val transactionIndex: Int,
)

private class CallManyBundleSerializer : JsonSerializer<CallManyBundle>() {
    override fun serialize(value: CallManyBundle, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()

        gen.writeArrayFieldStart("transactions")
        for (i in value.transactions.indices) {
            // delegate to CallRequest serializer
            gen.writeObject(value.transactions[i])
        }
        gen.writeEndArray()

        if (value.blockOverride != null) {
            // delegate to BlockOverride serializer
            gen.writeObjectField("blockOverride", value.blockOverride)
        }

        gen.writeEndObject()
    }
}

private class CallManyContextSerializer : JsonSerializer<CallManyContext>() {
    override fun serialize(value: CallManyContext, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()

        // delegate to BlockId serializer
        gen.writeObjectField("blockNumber", value.blockNumber)
        gen.writeNumberField("transactionIndex", value.transactionIndex)

        gen.writeEndObject()
    }
}
