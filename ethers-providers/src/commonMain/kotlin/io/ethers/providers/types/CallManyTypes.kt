package io.ethers.providers.types

import io.ethers.core.Result
import io.ethers.core.ThrowableError
import io.ethers.core.types.BlockId
import io.ethers.core.types.BlockIdSerializer
import io.ethers.core.types.BlockOverride
import io.ethers.core.types.BlockOverrideSerializer
import io.ethers.core.types.CallRequest
import io.ethers.core.types.CallRequestSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure

/**
 * Error returned for a call in `eth_callMany` that fails.
 * */
data class CallFailedError(val error: String) : ThrowableError

/**
 * Internal type used for correctly serializing `eth_callMany`/`debug_traceCallMany` request arguments.
 * */
@Serializable(with = CallManyBundleSerializer::class)
internal data class CallManyBundle(
    /**
     * Converted when the RPC request is created, so changing the original calls before the request is sent does not
     * change what is sent.
     * */
    val transactions: List<CallRequest>,
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
    private val transactionsSerializer = ListSerializer(CallRequestSerializer)

    private const val TRANSACTIONS = 0
    private const val BLOCK_OVERRIDE = 1

    override val descriptor = buildClassSerialDescriptor("CallManyBundle") {
        element("transactions", transactionsSerializer.descriptor)
        element("blockOverride", BlockOverrideSerializer.descriptor, isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: CallManyBundle) {
        val descriptor = descriptor
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, TRANSACTIONS, transactionsSerializer, value.transactions)
            val blockOverride = value.blockOverride
            if (blockOverride != null) {
                encodeSerializableElement(descriptor, BLOCK_OVERRIDE, BlockOverrideSerializer, blockOverride)
            }
        }
    }

    override fun deserialize(decoder: Decoder): CallManyBundle = throw UnsupportedOperationException()
}

internal object CallManyContextSerializer : KSerializer<CallManyContext> {
    private const val BLOCK_NUMBER = 0
    private const val TRANSACTION_INDEX = 1

    override val descriptor = buildClassSerialDescriptor("CallManyContext") {
        element("blockNumber", BlockIdSerializer.descriptor)
        element<Int>("transactionIndex")
    }

    override fun serialize(encoder: Encoder, value: CallManyContext) {
        val descriptor = descriptor
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, BLOCK_NUMBER, BlockIdSerializer, value.blockNumber)
            encodeIntElement(descriptor, TRANSACTION_INDEX, value.transactionIndex)
        }
    }

    override fun deserialize(decoder: Decoder): CallManyContext = throw UnsupportedOperationException()
}
