package io.ethers.core.types.tracers

import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
        return json.decodeFromJsonElement(ExecutionResult.serializer(), element)
    }

    @Serializable
    data class ExecutionResult(
        val gas: Long,
        val failed: Boolean,
        val returnValue: Bytes,
        val structLogs: List<StructLog>,
    )

    @Serializable
    data class StructLog(
        val pc: Int,
        val op: String,
        val gas: Long,
        val gasCost: Long,
        val depth: Int,
        val error: String? = null,
        val stack: List<Bytes>? = null,
        val memory: List<Bytes>? = null,
        val storage: Map<Hash, Hash>? = null,
        val refundCounter: Long = 0L,
    )
}
