package io.ethers.core.types.tracers

import io.ethers.core.asAddress
import io.ethers.core.asBytes
import io.ethers.core.asHash
import io.ethers.core.asHexBigInteger
import io.ethers.core.asHexInt
import io.ethers.core.asHexLong
import io.ethers.core.json.JsonElement
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.Log
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigInteger
import kotlin.reflect.KClass
import kotlinx.serialization.json.JsonElement as KJsonElement

/**
 * Trace transaction call frames.
 *
 * @param onlyTopCall if `true`, call tracer won't collect any subcalls
 * @param withLog if `true`, call tracer will collect event logs
 */
data class CallTracer(
    val onlyTopCall: Boolean,
    val withLog: Boolean,
) : Tracer<CallTracer.CallFrame> {
    override val name: String
        get() = "callTracer"

    override val resultType: KClass<CallFrame>
        get() = CallFrame::class

    override val config: Map<String, Any?> =
        when {
            onlyTopCall && withLog -> CONFIG_TOP_CALL_WITH_LOGS
            onlyTopCall -> CONFIG_TOP_CALL_WITHOUT_LOGS
            withLog -> CONFIG_ALL_CALLS_WITH_LOGS
            else -> CONFIG_ALL_CALLS_NO_LOGS
        }

    override fun decodeResult(json: Json, element: KJsonElement): CallFrame {
        return json.decodeFromJsonElement(CallFrameSerializer, element)
    }

    @Serializable(with = CallFrameSerializer::class)
    data class CallFrame(
        val type: String,
        val from: Address,
        val gas: Long,
        val gasUsed: Long,
        val input: Bytes,
        val to: Address? = null,
        val output: Bytes? = null,
        val error: String? = null,
        val revertReason: String? = null,
        val calls: List<CallFrame>? = null,
        val logs: List<CallLog>? = null,
        val value: BigInteger? = null,
        val otherFields: Map<String, JsonElement> = emptyMap(),
    ) {
        /**
         * Return `true` if this call failed, false otherwise.
         * */
        val isError: Boolean
            get() = error != null || revertReason != null

        /**
         * Flatten this call frame and all sub-calls into a list of call frames. The list is ordered such that
         * parent call comes before child calls.
         * */
        fun flatten(): List<CallFrame> {
            return addAllCallFrames(ArrayList())
        }

        private fun addAllCallFrames(ret: MutableList<CallFrame>): List<CallFrame> {
            // first, add parent call
            ret.add(this)

            // second, add all sub-calls
            calls?.let {
                for (i in it.indices) {
                    it[i].addAllCallFrames(ret)
                }
            }

            return ret
        }

        /**
         * Get all [CallLog]s from this and child calls.The logs might not be in the correct order because not
         * all tracer implementations return log index, which is needed to infer the ordering. If indexes are not
         * returned, we make a best-guess effort by first adding logs from child calls, followed by logs from
         * parent calls.
         *
         * Compared to [getAllLogs], this function returns [CallLog]s instead of [Log]s.
         * */
        fun getAllCallLogs(): List<CallLog> {
            val ret = flattenLogs(ArrayList()) { log, _ -> log }
            ret.sortBy { it.index }
            return ret
        }

        /**
         * Get all [Log]s from this and child calls. The logs might not be in the correct order because not
         * all tracer implementations return log index, which is needed to infer the ordering. If indexes are not
         * returned, we make a best-guess effort by first adding logs from child calls, followed by logs from
         * parent calls.
         *
         * The logs don't have any block or transaction information, but they do have a log index, which
         * corresponds to the index within the call.
         *
         * Compared to [getAllCallLogs], this function returns [Log]s instead of [CallLog]s.
         * */
        fun getAllLogs(): List<Log> {
            val ret = flattenLogs(ArrayList()) { log, index -> log.toLog(index) }
            ret.sortBy { it.logIndex }
            return ret
        }

        private fun <T> flattenLogs(ret: MutableList<T>, mapper: (log: CallLog, index: Int) -> T): MutableList<T> {
            // if the call failed, skip it and all its children logs
            if (isError) {
                return ret
            }

            // first, add logs from child calls since they are emitted before logs from current call
            calls?.let {
                for (i in it.indices) {
                    it[i].flattenLogs(ret, mapper)
                }
            }

            // second, add this call logs, after any child calls finished executing
            logs?.let {
                for (i in it.indices) {
                    ret.add(mapper(it[i], ret.size))
                }
            }

            return ret
        }
    }

    @Serializable(with = CallLogSerializer::class)
    data class CallLog(
        val address: Address,
        val topics: List<Hash>,
        val data: Bytes,
        val position: Int = -1,
        val index: Int = -1,
        val otherFields: Map<String, JsonElement> = emptyMap(),
    ) {
        /**
         * Convert this [CallLog] into an instance of [Log].
         *
         * The [Log] doesn't have any block or transaction information, but you can optionally set [logIndex], if
         * passed as parameter.
         * */
        @JvmOverloads
        fun toLog(logIndex: Int = -1): Log {
            return Log(
                address,
                topics,
                data,
                Hash.ZERO,
                -1L,
                -1L,
                Hash.ZERO,
                -1,
                if (this.index == -1) logIndex else this.index,
                false,
            )
        }
    }

    companion object {
        private val CONFIG_TOP_CALL_WITH_LOGS = mapOf(
            "onlyTopCall" to true,
            "withLog" to true,
        )
        private val CONFIG_TOP_CALL_WITHOUT_LOGS = mapOf(
            "onlyTopCall" to true,
            "withLog" to false,
        )
        private val CONFIG_ALL_CALLS_WITH_LOGS = mapOf(
            "onlyTopCall" to false,
            "withLog" to true,
        )
        private val CONFIG_ALL_CALLS_NO_LOGS = mapOf(
            "onlyTopCall" to false,
            "withLog" to false,
        )
    }
}

object CallFrameSerializer : KSerializer<CallTracer.CallFrame> {
    override val descriptor = buildClassSerialDescriptor("CallFrame")

    override fun serialize(encoder: Encoder, value: CallTracer.CallFrame) = throw UnsupportedOperationException()

    override fun deserialize(decoder: Decoder): CallTracer.CallFrame {
        val jsonDecoder = decoder as JsonDecoder
        val obj = jsonDecoder.decodeJsonElement().jsonObject

        lateinit var type: String
        lateinit var from: Address
        var gas = -1L
        var gasUsed = -1L
        var to: Address? = null
        lateinit var input: Bytes
        var output: Bytes? = null
        var error: String? = null
        var revertReason: String? = null
        var calls: List<CallTracer.CallFrame>? = null
        var logs: List<CallTracer.CallLog>? = null
        var value: BigInteger? = null
        var otherFields: MutableMap<String, JsonElement>? = null

        for ((key, element) in obj.entries) {
            when (key) {
                "type" -> type = element.jsonPrimitive.content
                "from" -> from = element.jsonPrimitive.asAddress()
                "gas" -> gas = element.jsonPrimitive.asHexLong()
                "gasUsed" -> gasUsed = element.jsonPrimitive.asHexLong()
                "to" -> to = if (element is JsonNull) null else element.jsonPrimitive.asAddress()
                "input" -> input = element.jsonPrimitive.asBytes()
                "output" -> output = if (element is JsonNull) null else element.jsonPrimitive.asBytes()
                "error" -> error = element.jsonPrimitive.content
                "revertReason" -> revertReason = element.jsonPrimitive.content
                "calls" -> calls = if (element is JsonNull) null
                else element.jsonArray.map { jsonDecoder.json.decodeFromJsonElement(CallFrameSerializer, it) }
                "logs" -> logs = if (element is JsonNull) null
                else element.jsonArray.map { jsonDecoder.json.decodeFromJsonElement(CallLogSerializer, it) }
                "value" -> value = element.jsonPrimitive.asHexBigInteger()
                else -> {
                    if (otherFields == null) otherFields = HashMap()
                    otherFields[key] = JsonElement(element.toString())
                }
            }
        }

        return CallTracer.CallFrame(
            type, from, gas, gasUsed, input, to, output, error, revertReason,
            calls, logs, value, otherFields ?: emptyMap(),
        )
    }
}

object CallLogSerializer : KSerializer<CallTracer.CallLog> {
    override val descriptor = buildClassSerialDescriptor("CallLog")

    override fun serialize(encoder: Encoder, value: CallTracer.CallLog) = throw UnsupportedOperationException()

    override fun deserialize(decoder: Decoder): CallTracer.CallLog {
        val obj = (decoder as JsonDecoder).decodeJsonElement().jsonObject

        lateinit var address: Address
        var topics: List<Hash> = emptyList()
        lateinit var data: Bytes
        var position = -1
        var index = -1
        var otherFields: MutableMap<String, JsonElement>? = null

        for ((key, element) in obj.entries) {
            when (key) {
                "address" -> address = element.jsonPrimitive.asAddress()
                "topics" -> topics = if (element is JsonNull) emptyList()
                else element.jsonArray.map { it.jsonPrimitive.asHash() }
                "data" -> data = element.jsonPrimitive.asBytes()
                "position" -> position = element.jsonPrimitive.asHexInt()
                "index" -> index = element.jsonPrimitive.asHexInt()
                else -> {
                    if (otherFields == null) otherFields = HashMap()
                    otherFields[key] = JsonElement(element.toString())
                }
            }
        }

        return CallTracer.CallLog(address, topics, data, position, index, otherFields ?: emptyMap())
    }
}
