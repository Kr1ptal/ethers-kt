package io.ethers.providers

import io.ethers.core.Kotlinx
import io.ethers.core.Result
import io.ethers.core.types.Log
import io.ethers.solana.types.rpc.AccountInfo
import io.ethers.solana.types.rpc.ContextValue
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.profile.GCProfiler
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.options.OptionsBuilder
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64
import kotlin.random.Random

/**
 * Turning a WebSocket text message into the value its call or subscription receives.
 *
 * - `tree`: what `WsClient.handleMessage` does: the message parsed into a `JsonElement` tree, its `id`, `method`,
 *   `params`, `result` and `error` looked up, and the result decoded from the tree with the call's deserializer
 * - `direct`: the lower bound, decoding the message straight into the value with a deserializer known up front
 * - `client`: what `WsClient` does now: [WsMessageScanner] picks the route, a response to a call is decoded straight
 *   into its result, and anything else goes through `tree`
 */
@Fork(value = 1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
open class WsMessageBenchmark {
    /**
     * - `logs`: an `eth_getLogs` response with 500 logs, about 400 KB, `id` first as geth sends it
     * - `log`: an `eth_subscription` notification of one log, about 700 bytes
     * - `account`: a Solana `accountNotification` of a 10,136-byte account, about 14 KB, with `result` before
     *   `subscription` as Agave sends it
     */
    @Param("logs", "log", "account")
    lateinit var fixture: String

    private lateinit var message: String
    private lateinit var deserializer: DeserializationStrategy<Any?>
    private lateinit var envelope: DeserializationStrategy<Any?>

    @Setup
    fun setup() {
        when (fixture) {
            "logs" -> {
                val random = Random(1)
                message = """{"jsonrpc":"2.0","id":1,"result":[${List(500) { random.log(it) }.joinToString(",")}]}"""
                deserializer = erase(ListSerializer(Log.serializer()))
                envelope = erase(RpcResponseDeserializer(deserializer))
            }
            "log" -> {
                message = """{"jsonrpc":"2.0","method":"eth_subscription","params":""" +
                    """{"subscription":"0x9cef478923ff08bf67fde6c64013158d","result":${Random(2).log(7)}}}"""
                deserializer = erase(Log.serializer())
                envelope = erase(NotificationResult(deserializer))
            }
            "account" -> {
                val data = Base64.encode(Random(3).nextBytes(10_136))
                message = """{"jsonrpc":"2.0","method":"accountNotification","params":{"result":""" +
                    """{"context":{"slot":341197053},"value":{"data":["$data","base64"],"executable":false,""" +
                    """"lamports":72161280,"owner":"LBUZKhRxPF3XUpBCjp4YzTKgLccjZhTSDM9YuVaPwxo",""" +
                    """"rentEpoch":18446744073709551615,"space":10136}},"subscription":23784}}"""
                deserializer = erase(ContextValue.serializer(AccountInfo.serializer().nullable))
                envelope = erase(NotificationResult(deserializer))
            }
            else -> error("Unknown fixture $fixture")
        }

        val expected = tree()
        check(expected != null)
        check(direct() == expected) { "direct differs for $fixture" }
        check(client() == expected) { "client differs for $fixture" }
    }

    @Benchmark
    fun tree(): Any? {
        val obj = Kotlinx.DEFAULT.parseToJsonElement(message).jsonObject
        val id = obj["id"]?.jsonPrimitive?.longOrNull ?: -1L
        val method = obj["method"]?.jsonPrimitive?.content
        val resultEl = obj["result"]
        val paramsEl = obj["params"]
        val errorEl = obj["error"]
        val error = if (errorEl != null && errorEl !is JsonNull) RpcError.fromJsonObject(errorEl.jsonObject) else null

        return when {
            method != null && paramsEl != null -> {
                val params = paramsEl.jsonObject
                params.getValue("subscription").jsonPrimitive
                Kotlinx.DEFAULT.decodeFromJsonElement(deserializer, params.getValue("result"))
            }
            id != -1L && error == null && resultEl != null -> {
                Kotlinx.DEFAULT.decodeFromJsonElement(deserializer, resultEl)
            }
            else -> error("Unexpected message")
        }
    }

    @Benchmark
    fun direct(): Any? = when (val value = Kotlinx.DEFAULT.decodeFromString(envelope, message)) {
        is Result<*, *> -> value.unwrap()
        else -> value
    }

    @Benchmark
    fun client(): Any? {
        // WsClient also checks the id against its in-flight calls, and decides between result and error
        val id = WsMessageScanner.responseId(message)
        if (id == WsMessageScanner.NONE) return tree()
        val fields = Kotlinx.DEFAULT.decodeFromString(RpcResponseFieldsDeserializer(deserializer), message)
        if (fields.hasMethod || (fields.id as? JsonPrimitive)?.longOrNull != id) return tree()
        return fields.result
    }

    /** Reads `params.result` of a notification with [result], skipping every other key. */
    private class NotificationResult<T>(private val result: DeserializationStrategy<T>) : DeserializationStrategy<T> {
        private val params = object : DeserializationStrategy<T> {
            override val descriptor = buildClassSerialDescriptor("NotificationParams") {
                element("subscription", JsonElement.serializer().descriptor)
                element("result", result.descriptor)
            }

            override fun deserialize(decoder: Decoder): T = decoder.decodeStructure(descriptor) { readElement(descriptor, 1, result) }
        }

        override val descriptor = buildClassSerialDescriptor("Notification") {
            element("params", params.descriptor)
        }

        override fun deserialize(decoder: Decoder): T = decoder.decodeStructure(descriptor) { readElement(descriptor, 0, params) }

        /** Decodes the element at [target] with [strategy] and skips the others, which must be JSON values. */
        private fun <R> CompositeDecoder.readElement(descriptor: SerialDescriptor, target: Int, strategy: DeserializationStrategy<R>): R {
            var value: R? = null
            var seen = false
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    target -> {
                        value = decodeSerializableElement(descriptor, index, strategy)
                        seen = true
                    }
                    CompositeDecoder.DECODE_DONE -> break
                    else -> decodeSerializableElement(descriptor, index, JsonElement.serializer())
                }
            }
            check(seen) { "Missing element $target" }
            @Suppress("UNCHECKED_CAST")
            return value as R
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        private fun erase(strategy: DeserializationStrategy<*>) = strategy as DeserializationStrategy<Any?>

        private fun Random.hex(bytes: Int) = "0x" + nextBytes(bytes).toHexString()

        /** A log with one to four topics and up to 160 bytes of data, as `eth_getLogs` returns it. */
        private fun Random.log(index: Int): String {
            val topics = List(nextInt(1, 5)) { "\"${hex(32)}\"" }.joinToString(",")
            return """{"address":"${hex(20)}","topics":[$topics],"data":"${hex(32 * nextInt(0, 6))}",""" +
                """"blockNumber":"0x117c277","blockTimestamp":"0x6624e154","transactionHash":"${hex(32)}",""" +
                """"transactionIndex":"0x${(index / 4).toString(16)}","blockHash":"${hex(32)}",""" +
                """"logIndex":"0x${index.toString(16)}","removed":false}"""
        }

        @JvmStatic
        fun main(args: Array<String>) {
            val options = OptionsBuilder()
                .include(WsMessageBenchmark::class.java.simpleName)
                .addProfiler(GCProfiler::class.java)
                .build()

            Runner(options).run()
        }
    }
}
