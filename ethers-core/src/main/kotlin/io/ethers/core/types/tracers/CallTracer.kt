package io.ethers.core.types.tracers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
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
    )

    @JsonDeserialize(using = CallLogDeserializer::class)
    data class CallLog(
        val address: Address,
        val topics: List<Hash>,
        val data: Bytes,
    )

    private class CallFrameDeserializer : JsonDeserializer<CallFrame>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): CallFrame {
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
            p.forEachObjectField { name ->
                when (name) {
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
                }
            }

            return CallFrame(from, gas, gasUsed, input, to, output, error, revertReason, calls, logs, value)
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
