package io.ethers.core.types.tracers

import io.ethers.core.asBytes
import io.ethers.core.asHash
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.reflect.KClass

/**
 * Default tracer. Cannot be combined with other tracers (e.g. in [MuxTracer]).
 *
 * @param enableMemory enable memory capture
 * @param disableStack disable stack capture
 * @param disableStorage disable storage capture
 * @param enableReturnData enable return data capture
 * @param debug print output during capture end
 * @param limit maximum length of output, zero means unlimited
 * @param overrides chain overrides, can be used to execute a trace using future fork rules
 */
data class StructTracer(
    val enableMemory: Boolean = false,
    val disableStack: Boolean = false,
    val disableStorage: Boolean = false,
    val enableReturnData: Boolean = false,
    val debug: Boolean = false,
    val limit: Int = 0,
    val overrides: Map<String, Any> = emptyMap(),
) : AnyTracer<StructTracer.ExecutionResult> {
    override val resultType: KClass<ExecutionResult>
        get() = ExecutionResult::class

    override val config: Map<String, Any?> = buildMap {
        if (enableMemory) put("enableMemory", true)
        if (disableStack) put("disableStack", true)
        if (disableStorage) put("disableStorage", true)
        if (enableReturnData) put("enableReturnData", true)
        if (debug) put("debug", true)
        if (limit != 0) put("limit", limit)
        if (overrides.isNotEmpty()) put("overrides", overrides)
    }

    override fun decodeResult(json: Json, element: JsonElement): ExecutionResult {
        return json.decodeFromJsonElement(ExecutionResultSerializer, element)
    }

    @Serializable(with = ExecutionResultSerializer::class)
    data class ExecutionResult(
        val gas: Long,
        val failed: Boolean,
        val returnValue: Bytes,
        val structLogs: List<StructLog>,
    )

    @Serializable(with = StructLogSerializer::class)
    data class StructLog(
        val pc: Int,
        val op: String,
        val gas: Long,
        val gasCost: Long,
        val depth: Int,
        val error: String?,
        val stack: List<Bytes>?,
        val memory: List<Bytes>?,
        val storage: Map<Hash, Hash>?,
        val refundCounter: Long,
    )

    object ExecutionResultSerializer : KSerializer<ExecutionResult> {
        override val descriptor = buildClassSerialDescriptor("StructTracer.ExecutionResult")

        override fun serialize(encoder: Encoder, value: ExecutionResult) = throw UnsupportedOperationException()

        override fun deserialize(decoder: Decoder): ExecutionResult {
            val jsonDecoder = decoder as JsonDecoder
            val obj = jsonDecoder.decodeJsonElement().jsonObject

            var gas = -1L
            var failed = false
            lateinit var returnValue: Bytes
            var structLogs: List<StructLog>? = null

            for ((key, element) in obj.entries) {
                when (key) {
                    "gas" -> gas = element.jsonPrimitive.long
                    "failed" -> failed = element.jsonPrimitive.boolean
                    "returnValue" -> returnValue = element.jsonPrimitive.asBytes()
                    "structLogs" -> structLogs = element.jsonArray.map {
                        jsonDecoder.json.decodeFromJsonElement(StructLogSerializer, it)
                    }
                }
            }

            return ExecutionResult(gas, failed, returnValue, structLogs!!)
        }
    }

    object StructLogSerializer : KSerializer<StructLog> {
        override val descriptor = buildClassSerialDescriptor("StructTracer.StructLog")

        override fun serialize(encoder: Encoder, value: StructLog) = throw UnsupportedOperationException()

        override fun deserialize(decoder: Decoder): StructLog {
            val obj = (decoder as JsonDecoder).decodeJsonElement().jsonObject

            var pc = -1
            lateinit var op: String
            var gas = -1L
            var gasCost = -1L
            var depth = -1
            var error: String? = null
            var stack: List<Bytes>? = null
            var memory: List<Bytes>? = null
            var storage: Map<Hash, Hash>? = null
            var refundCounter = 0L

            for ((key, element) in obj.entries) {
                when (key) {
                    "pc" -> pc = element.jsonPrimitive.int
                    "op" -> op = element.jsonPrimitive.content
                    "gas" -> gas = element.jsonPrimitive.long
                    "gasCost" -> gasCost = element.jsonPrimitive.long
                    "depth" -> depth = element.jsonPrimitive.int
                    "error" -> error = if (element is JsonNull) null else element.jsonPrimitive.content
                    "stack" -> stack = if (element is JsonNull) null
                    else element.jsonArray.map { it.jsonPrimitive.asBytes() }
                    "memory" -> memory = if (element is JsonNull) null
                    else element.jsonArray.map { it.jsonPrimitive.asBytes() }
                    "storage" -> storage = if (element is JsonNull) null
                    else element.jsonObject.entries.associate { (k, v) -> Hash(k) to v.jsonPrimitive.asHash() }
                    "refundCounter" -> refundCounter = element.jsonPrimitive.long
                }
            }

            return StructLog(pc, op, gas, gasCost, depth, error, stack, memory, storage, refundCounter)
        }
    }
}
