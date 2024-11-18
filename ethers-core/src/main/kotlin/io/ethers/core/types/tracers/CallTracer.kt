package io.ethers.core.types.tracers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.ethers.core.forEachObjectField
import io.ethers.core.readAddress
import io.ethers.core.readBytes
import io.ethers.core.readHash
import io.ethers.core.readHexBigInteger
import io.ethers.core.readHexLong
import io.ethers.core.readListOf
import io.ethers.core.readOrNull
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.Log
import java.math.BigInteger

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

    override fun encodeConfig(gen: JsonGenerator) {
        gen.writeBooleanField("onlyTopCall", onlyTopCall)
        gen.writeBooleanField("withLog", withLog)
    }

    override fun decodeResult(parser: JsonParser): CallFrame {
        return parser.readValueAs(CallFrame::class.java)
    }

    @JsonDeserialize(using = CallFrameDeserializer::class)
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
        val otherFields: Map<String, JsonNode> = emptyMap(),
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
         * Get all [CallLog]s from this and child calls. The logs might not be in the correct order because not
         * enough information is received via tracer to infer the ordering. We make a best-guess effort by
         * first adding logs from child calls, and then adding logs from parent call.
         *
         * Compared to [getAllLogs], this function returns [CallLog]s instead of [Log]s.
         * */
        fun getAllCallLogs(): List<CallLog> {
            return flattenLogs(ArrayList()) { log, _ -> log }
        }

        /**
         * Get all [Log]s from this and child calls. The logs might not be in the correct order because not
         * enough information is received via tracer to infer the ordering. We make a best-guess effort by
         * first adding logs from child calls, and then adding logs from parent call.
         *
         * The logs don't have any block or transaction information, but they do have a log index, which
         * corresponds to the index within the call.
         *
         * Compared to [getAllCallLogs], this function returns [Log]s instead of [CallLog]s.
         * */
        fun getAllLogs(): List<Log> {
            return flattenLogs(ArrayList()) { log, index -> log.toLog(index) }
        }

        private fun <T> flattenLogs(ret: MutableList<T>, mapper: (log: CallLog, index: Int) -> T): List<T> {
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

    @JsonDeserialize(using = CallLogDeserializer::class)
    data class CallLog(
        val address: Address,
        val topics: List<Hash>,
        val data: Bytes,
    ) {
        /**
         * Convert this [CallLog] into an instance of [Log].
         *
         * The [Log] doesn't have any block or transaction information, but they can optionally set [logIndex], if
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
                logIndex,
                false,
            )
        }
    }

    private class CallFrameDeserializer : JsonDeserializer<CallFrame>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): CallFrame {
            lateinit var type: String
            lateinit var from: Address
            var gas: Long = -1L
            var gasUsed: Long = -1L
            var to: Address? = null
            lateinit var input: Bytes
            var output: Bytes? = null
            var error: String? = null
            var revertReason: String? = null
            var calls: List<CallFrame>? = null
            var logs: List<CallLog>? = null
            var value: BigInteger? = null
            var otherFields: MutableMap<String, JsonNode>? = null
            p.forEachObjectField { name ->
                when (name) {
                    "type" -> type = p.text
                    "from" -> from = p.readAddress()
                    "gas" -> gas = p.readHexLong()
                    "gasUsed" -> gasUsed = p.readHexLong()
                    "to" -> to = p.readOrNull { readAddress() }
                    "input" -> input = p.readBytes()
                    "output" -> output = p.readOrNull { readBytes() }
                    "error" -> error = p.readValueAs(String::class.java)
                    "revertReason" -> revertReason = p.readValueAs(String::class.java)
                    "calls" -> calls = p.readListOf { readValueAs(CallFrame::class.java) }
                    "logs" -> logs = p.readListOf { readValueAs(CallLog::class.java) }
                    "value" -> value = p.readHexBigInteger()
                    else -> {
                        if (otherFields == null) {
                            otherFields = HashMap()
                        }
                        otherFields!![p.currentName()] = p.readValueAs(JsonNode::class.java)
                    }
                }
            }

            return CallFrame(
                type,
                from,
                gas,
                gasUsed,
                input,
                to,
                output,
                error,
                revertReason,
                calls,
                logs,
                value,
                otherFields ?: emptyMap(),
            )
        }
    }

    private class CallLogDeserializer : JsonDeserializer<CallLog>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): CallLog {
            lateinit var address: Address
            lateinit var topics: List<Hash>
            lateinit var data: Bytes
            p.forEachObjectField { name ->
                when (name) {
                    "address" -> address = p.readAddress()
                    "topics" -> topics = p.readListOf { readHash() }
                    "data" -> data = p.readBytes()
                }
            }

            return CallLog(address, topics, data)
        }
    }
}
