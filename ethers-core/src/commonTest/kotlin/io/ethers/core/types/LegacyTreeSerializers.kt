package io.ethers.core.types

import io.ethers.core.FastHex
import io.ethers.core.types.tracers.Tracer
import io.ethers.core.types.tracers.TracerConfig
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// The serializers as they were on master before they were made to write their fields directly: each builds a JSON tree of the
// value and writes that. Kept unchanged, only renamed and pointed at each other, so tests can check that the streaming
// serializers write exactly the same text for the same values.

internal object LegacyCallRequestSerializer : KSerializer<CallRequest> {
    override val descriptor = buildClassSerialDescriptor("CallRequest")

    override fun serialize(encoder: Encoder, value: CallRequest) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                if (value.from != null) {
                    put("from", value.from.toString())
                }
                if (value.to != null) {
                    put("to", value.to.toString())
                }
                if (value.gas != -1L) {
                    put("gas", FastHex.encodeWithPrefix(value.gas))
                }
                if (value.gasPrice != null) {
                    put("gasPrice", FastHex.encodeWithPrefix(value.gasPrice))
                }
                if (value.gasFeeCap != null) {
                    put("maxFeePerGas", FastHex.encodeWithPrefix(value.gasFeeCap))
                }
                if (value.gasTipCap != null) {
                    put("maxPriorityFeePerGas", FastHex.encodeWithPrefix(value.gasTipCap))
                }
                if (value.value != null) {
                    put("value", FastHex.encodeWithPrefix(value.value))
                }
                if (value.nonce != -1L) {
                    put("nonce", FastHex.encodeWithPrefix(value.nonce))
                }
                if (value.data != null) {
                    put("data", value.data.toString())
                }
                if (value.accessList.isNotEmpty()) {
                    put(
                        "accessList",
                        buildJsonArray {
                            for (i in value.accessList.indices) {
                                add(jsonEncoder.json.encodeToJsonElement(AccessList.Item.serializer(), value.accessList[i]))
                            }
                        },
                    )
                }
                if (value.chainId != -1L) {
                    put("chainId", FastHex.encodeWithPrefix(value.chainId))
                }
                val blobFeeCap = value.blobFeeCap
                if (blobFeeCap != null) {
                    put("maxFeePerBlobGas", FastHex.encodeWithPrefix(blobFeeCap))
                }
                val blobVersionedHashes = value.blobVersionedHashes
                if (blobVersionedHashes != null) {
                    put(
                        "blobVersionedHashes",
                        buildJsonArray {
                            for (i in blobVersionedHashes.indices) {
                                add(JsonPrimitive(blobVersionedHashes[i].toString()))
                            }
                        },
                    )
                }
            },
        )
    }

    override fun deserialize(decoder: Decoder): CallRequest = throw UnsupportedOperationException()
}

internal object LegacyTracerConfigSerializer : KSerializer<TracerConfig<*>> {
    override val descriptor = buildClassSerialDescriptor("TracerConfig")

    override fun serialize(encoder: Encoder, value: TracerConfig<*>) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                when (val tracer = value.tracer) {
                    is Tracer<*> -> {
                        put("tracer", tracer.name)
                        put("tracerConfig", tracer.config)
                    }

                    else -> {
                        for ((fieldName, fieldValue) in tracer.config) {
                            put(fieldName, fieldValue)
                        }
                    }
                }

                if (value.timeoutMs >= 0) {
                    put("timeout", "${value.timeoutMs}ms")
                }
                if (value.reexec >= 0) {
                    put("reexec", value.reexec)
                }
                if (!value.stateOverrides.isNullOrEmpty()) {
                    put("stateOverrides", jsonEncoder.json.encodeToJsonElement(LegacyStateOverrideSerializer, value.stateOverrides))
                }
                if (value.blockOverrides != null) {
                    put("blockOverrides", jsonEncoder.json.encodeToJsonElement(LegacyBlockOverrideSerializer, value.blockOverrides))
                }
                if (value.txIndex >= 0) {
                    put("txIndex", FastHex.encodeWithPrefix(value.txIndex))
                }
            },
        )
    }

    override fun deserialize(decoder: Decoder): TracerConfig<*> = throw UnsupportedOperationException()
}

internal object LegacyStateOverrideSerializer : KSerializer<StateOverride> {
    override val descriptor = buildClassSerialDescriptor("StateOverride")

    override fun serialize(encoder: Encoder, value: StateOverride) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                for ((address, override) in value) {
                    put(address.toString(), jsonEncoder.json.encodeToJsonElement(LegacyAccountOverrideSerializer, override))
                }
            },
        )
    }

    override fun deserialize(decoder: Decoder): StateOverride = throw UnsupportedOperationException()
}

internal object LegacyAccountOverrideSerializer : KSerializer<AccountOverride> {
    override val descriptor = buildClassSerialDescriptor("AccountOverride")

    override fun serialize(encoder: Encoder, value: AccountOverride) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                if (value.nonce != -1L) {
                    put("nonce", FastHex.encodeWithPrefix(value.nonce))
                }
                if (value.code != null) {
                    put("code", value.code!!.toString())
                }
                if (value.balance != null) {
                    put("balance", FastHex.encodeWithPrefix(value.balance!!))
                }
                // empty map clears the state
                if (value.state != null) {
                    put(
                        "state",
                        buildJsonObject {
                            value.state!!.forEach { (k, v) -> put(k.toString(), v.toString()) }
                        },
                    )
                }
                if (!value.stateDiff.isNullOrEmpty()) {
                    put(
                        "stateDiff",
                        buildJsonObject {
                            value.stateDiff!!.forEach { (k, v) -> put(k.toString(), v.toString()) }
                        },
                    )
                }
            },
        )
    }

    override fun deserialize(decoder: Decoder): AccountOverride = throw UnsupportedOperationException()
}

internal object LegacyBlockOverrideSerializer : KSerializer<BlockOverride> {
    override val descriptor = buildClassSerialDescriptor("BlockOverride")

    override fun serialize(encoder: Encoder, value: BlockOverride) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                if (value.number != -1L) {
                    put("number", FastHex.encodeWithPrefix(value.number))
                }
                if (value.difficulty != null) {
                    put("difficulty", FastHex.encodeWithPrefix(value.difficulty))
                }
                if (value.time != -1L) {
                    put("time", FastHex.encodeWithPrefix(value.time))
                }
                if (value.gasLimit != -1L) {
                    put("gasLimit", FastHex.encodeWithPrefix(value.gasLimit))
                }
                if (value.coinbase != null) {
                    put("coinbase", value.coinbase.toString())
                }
                if (value.random != null) {
                    put("random", value.random.toString())
                }
                if (value.baseFee != null) {
                    put("baseFee", FastHex.encodeWithPrefix(value.baseFee))
                }
            },
        )
    }

    override fun deserialize(decoder: Decoder): BlockOverride = throw UnsupportedOperationException()
}

internal object LegacyLogFilterSerializer : KSerializer<LogFilter> {
    override val descriptor = buildClassSerialDescriptor("LogFilter")

    override fun serialize(encoder: Encoder, value: LogFilter) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                when (val blocks = value.blocks) {
                    is BlockSelector.Hash -> put("blockHash", blocks.from.id)
                    is BlockSelector.Range -> {
                        put("fromBlock", blocks.from.id)
                        put("toBlock", blocks.to.id)
                    }
                }

                val address = value.addresses
                if (address != null) {
                    put(
                        "address",
                        buildJsonArray {
                            for (i in address.indices) {
                                add(JsonPrimitive(address[i].toString()))
                            }
                        },
                    )
                }

                val lastValidIndex = when {
                    value.topic3 != null -> 3
                    value.topic2 != null -> 2
                    value.topic1 != null -> 1
                    value.topic0 != null -> 0
                    else -> -1
                }
                if (lastValidIndex != -1) {
                    put(
                        "topics",
                        buildJsonArray {
                            for (i in 0..lastValidIndex) {
                                val hashes = when (i) {
                                    0 -> value.topic0
                                    1 -> value.topic1
                                    2 -> value.topic2
                                    else -> value.topic3
                                }
                                when {
                                    hashes == null -> add(JsonNull)
                                    hashes.size == 1 -> add(JsonPrimitive(hashes[0].toString()))
                                    else -> add(
                                        buildJsonArray {
                                            for (j in hashes.indices) {
                                                add(JsonPrimitive(hashes[j].toString()))
                                            }
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            },
        )
    }

    override fun deserialize(decoder: Decoder): LogFilter = throw UnsupportedOperationException()
}
