package io.ethers.core.types.tracers

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.ethers.core.forEachObjectField
import io.ethers.core.readBytes
import io.ethers.core.readHash
import io.ethers.core.readListOf
import io.ethers.core.readMapOf
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
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

    @JsonDeserialize(using = ExecutionResultDeserializer::class)
    data class ExecutionResult(
        val gas: Long,
        val failed: Boolean,
        val returnValue: Bytes,
        val structLogs: List<StructLog>,
    )

    @JsonDeserialize(using = StructLogDeserializer::class)
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

    private class ExecutionResultDeserializer : JsonDeserializer<ExecutionResult>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ExecutionResult {
            var gas = -1L
            var failed = false
            lateinit var returnValue: Bytes
            var structLogs: List<StructLog>? = null
            p.forEachObjectField {
                when (it) {
                    "gas" -> gas = p.longValue
                    "failed" -> failed = p.booleanValue
                    "returnValue" -> returnValue = p.readBytes()
                    "structLogs" -> structLogs = p.readListOf { readValueAs(StructLog::class.java) }
                }
            }

            return ExecutionResult(gas, failed, returnValue, structLogs!!)
        }
    }

    private class StructLogDeserializer : JsonDeserializer<StructLog>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): StructLog {
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
            p.forEachObjectField {
                when (it) {
                    "pc" -> pc = p.intValue
                    "op" -> op = p.text
                    "gas" -> gas = p.longValue
                    "gasCost" -> gasCost = p.longValue
                    "depth" -> depth = p.intValue
                    "error" -> error = p.text
                    "stack" -> stack = p.readListOf { readBytes() }
                    "memory" -> memory = p.readListOf { readBytes() }
                    "storage" -> storage = p.readMapOf({ key -> Hash(key) }) { readHash() }
                    "refundCounter" -> refundCounter = p.longValue
                }
            }

            return StructLog(pc, op, gas, gasCost, depth, error, stack, memory, storage, refundCounter)
        }
    }
}
