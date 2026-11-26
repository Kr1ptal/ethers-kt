package io.ethers.providers

import io.channels.core.ChannelReceiver
import io.ethers.core.FastHex
import io.ethers.core.Kotlinx
import io.ethers.core.Result
import io.ethers.core.asBytes
import io.ethers.core.asBytesOrNull
import io.ethers.core.asHash
import io.ethers.core.asHexBigInteger
import io.ethers.core.asHexByteArray
import io.ethers.core.asHexLong
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.BlockWithHashes
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.core.types.Hash
import io.ethers.core.types.Log
import io.ethers.core.types.LogFilter
import io.ethers.core.types.RPCTransaction
import io.ethers.core.types.tracers.AnyTracer
import io.ethers.core.types.tracers.CallTracer
import io.ethers.core.types.tracers.FourByteTracer
import io.ethers.core.types.tracers.MuxTracer
import io.ethers.core.types.tracers.NoopTracer
import io.ethers.core.types.tracers.StructTracer
import io.ethers.core.types.tracers.TracerConfig
import io.ethers.core.types.tracers.TxTraceResult
import io.ethers.core.types.transaction.TransactionUnsigned
import io.ethers.core.types.transaction.TxDynamicFee
import io.ethers.providers.types.BatchRpcRequest
import io.ethers.providers.types.CallFailedError
import io.ethers.providers.types.PendingTransaction
import io.ethers.providers.types.RpcParams
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds

/**
 * Every [Provider] call that used to decode its result with a function of the JSON tree is checked here against
 * that function, kept verbatim as the oracle. For each input, the call's deserializer must give the same value, or
 * fail where the function failed, both when decoding the text (as HTTP does) and the tree (as batches and WebSocket
 * do). The few places where the deserializer is deliberately more lenient are listed in each case's `improved`.
 */
class ProviderDecodingTest : FunSpec({
    val client = CapturingClient()
    val provider = Provider(client, 1L)

    suspend fun deserializerOf(call: suspend Provider.() -> Unit): DeserializationStrategy<*> {
        client.captured.clear()
        provider.call()
        return client.captured.single().second
    }

    context("each call decodes as its tree decoder did") {
        for (case in cases()) {
            test(case.name) {
                val deserializer = deserializerOf(case.call)
                for (input in case.inputs) {
                    withClue("${case.name}: $input") {
                        val expected = case.improved[input]?.let(::Decoded) ?: outcome { case.project(case.oracle(Kotlinx.DEFAULT.parseToJsonElement(input))) }
                        outcome { case.project(Kotlinx.DEFAULT.decodeFromString(deserializer, input)) } shouldBe expected
                        outcome { case.project(Kotlinx.DEFAULT.decodeFromJsonElement(deserializer, Kotlinx.DEFAULT.parseToJsonElement(input))) } shouldBe expected
                    }
                }
            }
        }
    }

    context("a filter polls with the id its creation returned, and decodes changes as its tree decoder did") {
        val filters = listOf<Triple<String, suspend Provider.() -> Unit, (JsonElement) -> Any?>>(
            Triple("newFilter", { watchLogs(LogFilter {}).send() }, { el -> el.jsonArray.map { Kotlinx.DEFAULT.decodeFromJsonElement(Log.serializer(), it) } }),
            Triple("newBlockFilter", { watchNewBlockHashes().send() }, { el -> el.jsonArray.map { it.jsonPrimitive.asHash() } }),
            Triple("newPendingTransactionFilter", { watchNewPendingTransactionHashes().send() }, { el -> el.jsonArray.map { it.jsonPrimitive.asHash() } }),
            Triple("newPendingTransactionFilterFull", { watchNewPendingTransactions().send() }, { el -> el.jsonArray.map { Kotlinx.DEFAULT.decodeFromJsonElement(RPCTransaction.serializer(), it) } }),
        )
        for ((name, call, oracle) in filters) {
            test(name) {
                val deserializer = deserializerOf(call)
                for (id in listOf("\"0x1f\"", "31", "\"\"")) {
                    client.captured.clear()
                    val poller = Kotlinx.DEFAULT.decodeFromString(deserializer, id) as ChannelReceiver<*>
                    // the poller's first request names the filter, with the decoder for its changes
                    val (params, changes) = withTimeout(5.seconds) { client.polls.receive() }
                    poller.close()
                    params[0] shouldBe Kotlinx.DEFAULT.parseToJsonElement(id).jsonPrimitive.content

                    for (input in listOf("[]", "[\"0x${"ab".repeat(32)}\"]", HASHES, "null", "{}", "[null]", LOGS, TRANSACTIONS)) {
                        withClue("$name changes: $input") {
                            val expected = outcome { oracle(Kotlinx.DEFAULT.parseToJsonElement(input)) }
                            outcome { Kotlinx.DEFAULT.decodeFromString(changes, input) } shouldBe expected
                        }
                    }
                }
            }
        }
    }
})

private class Case(
    val name: String,
    val call: suspend Provider.() -> Unit,
    val oracle: (JsonElement) -> Any?,
    val inputs: List<String>,
    /** Inputs the tree decoder failed on, or decoded wrongly, that the deserializer decodes; with the value it gives. */
    val improved: Map<String, Any?> = emptyMap(),
    /** The part of a decoded value to compare, for values that cannot be compared directly. */
    val project: (Any?) -> Any? = { it },
)

private fun cases(): List<Case> {
    val address = Address("0x0000000000000000000000000000000000000001")
    val hash = Hash("0x${"ab".repeat(32)}")
    val call = CallRequest.Builder().to(address).build()
    fun <T : Any> trace(tracer: AnyTracer<T>) = TracerConfig(tracer)
    val quantity: (JsonElement) -> Any? = { it.jsonPrimitive.asHexLong() }
    val bigQuantity: (JsonElement) -> Any? = { it.jsonPrimitive.asHexBigInteger() }
    val tx = TxDynamicFee(address, bigIntegerOf(1), 2, 21000, bigIntegerOf(3), bigIntegerOf(4), Bytes("0x1234"), 1, emptyList())
    val raw = FastHex.encodeWithPrefix(tx.toRlp())

    return listOf(
        Case("eth_blockNumber", { getBlockNumber().send() }, quantity, QUANTITIES),
        Case("eth_getUncleCount", { getUncleBlocksCount(BlockId.LATEST).send() }, quantity, QUANTITIES),
        Case("eth_estimateGas", { estimateGas(call, BlockId.LATEST).send() }, quantity, QUANTITIES),
        Case("eth_getTransactionCount", { getTransactionCount(address, BlockId.LATEST).send() }, quantity, QUANTITIES),
        Case("eth_getBlockTransactionCount", { getBlockTransactionCount(BlockId.LATEST).send() }, quantity, QUANTITIES),
        Case("net_peerCount", { getPeerCount().send() }, quantity, QUANTITIES),
        Case("eth_getBalance", { getBalance(address, BlockId.LATEST).send() }, bigQuantity, QUANTITIES),
        Case("eth_gasPrice", { getGasPrice().send() }, bigQuantity, QUANTITIES),
        Case("eth_blobBaseFee", { getBlobBaseFee().send() }, bigQuantity, QUANTITIES),
        Case("eth_maxPriorityFeePerGas", { getMaxPriorityFeePerGas().send() }, bigQuantity, QUANTITIES),
        Case("net_listening", { isListening().send() }, { it.jsonPrimitive.boolean }, BOOLEANS),
        Case(
            "eth_sendRawTransaction",
            { sendRawTransaction(byteArrayOf(1)).send() },
            { it.jsonPrimitive.asHash() },
            HASH_INPUTS,
            project = { (it as? PendingTransaction)?.hash ?: it },
        ),
        Case(
            "eth_callMany",
            { callMany(BlockId.LATEST, listOf(call), -1, null, null).send() },
            { element ->
                element.jsonArray.map { item ->
                    var result: Result<Bytes, CallFailedError>? = null
                    item.jsonObject.forEach { (field, value) ->
                        when (field) {
                            "output", "result", "value" -> result = success(value.jsonPrimitive.asBytes())
                            "error" -> result = failure(CallFailedError(value.jsonPrimitive.content))
                        }
                    }
                    result ?: throw IllegalStateException("No result or error found in response")
                }
            },
            listOf(
                """[{"value":"0x01"},{"error":"boom"},{"output":"0x02","error":"x"},{"error":"x","result":"0x03"}]""",
                """[{"value":"0x","extra":{"a":[1,{"b":null}]}},{"result":""}]""",
                "[]", "[{}]", """[{"value":null}]""", """[{"error":null}]""", """[{"error":5}]""", """[{"error":{}}]""",
                """[{"value":"0x0"}]""", """[{"value":12}]""", "[1]", "null", "{}",
            ),
        ),
        Case(
            "eth_fillTransaction",
            { fillTransaction(call).send() },
            { element ->
                val r = element.jsonObject["raw"]
                val ret = if (r != null) TransactionUnsigned.rlpDecode(r.jsonPrimitive.asHexByteArray()) else null
                ret ?: throw IllegalStateException("Invalid response")
            },
            listOf(
                """{"raw":"$raw","tx":{"nonce":"0x2"}}""", """{"tx":{},"raw":"$raw"}""", """{"tx":{}}""", """{"raw":null}""",
                """{"raw":"0x"}""", """{"raw":"0x12"}""", """{"raw":"0xzz"}""", "null", "\"$raw\"", "{}",
            ),
        ),
        Case(
            "debug_getRawTransaction",
            { getRawTransaction(hash).send() },
            { if (it is JsonNull) null else it.jsonPrimitive.asBytesOrNull() },
            listOf("\"0xdeadbeef\"", "\"0x\"", "\"\"", "null", "\"0xabc\"", "12", "{}", "[]"),
        ),
        Case("debug_traceCall struct", { traceCall(call, BlockId.LATEST, trace(StructTracer())).send() }, structOracle, STRUCT_TRACES),
        Case("debug_traceTransaction struct", { traceTransaction(hash, trace(StructTracer())).send() }, structOracle, STRUCT_TRACES),
        Case(
            "debug_traceTransaction call",
            { traceTransaction(hash, trace(CallTracer(onlyTopCall = false, withLog = true))).send() },
            { Kotlinx.DEFAULT.decodeFromJsonElement(CallTracer(false, true).resultSerializer, it) },
            listOf(CALL_FRAME, "null", "{}", "[]"),
        ),
        Case(
            "debug_traceTransaction mux",
            { traceTransaction(hash, trace(MUX)).send() },
            muxOracle,
            listOf(
                """{"callTracer":$CALL_FRAME,"4byteTracer":{"0x27dc297e-128":1},"noopTracer":{}}""",
                """{"noopTracer":{},"callTracer":$CALL_FRAME}""",
                "{}",
                """{"unknown":{}}""",
                """{"4byteTracer":{"a":"1"}}""",
                """{"4byteTracer":null}""",
                "null",
                "[]",
            ),
        ),
        Case(
            "debug_traceCallMany",
            { traceCallMany(BlockId.LATEST, listOf(call), trace(FourByteTracer), -1).send() },
            { element -> element.jsonArray.firstOrNull()?.jsonArray?.map { fourByte(it) } ?: emptyList<Any>() },
            listOf("""[[{"0x27dc297e-128":1},{}]]""", "[]", "[[]]", """[[{"a":"x"}]]""", "[null]", "[{}]", "null", "{}"),
        ),
        Case(
            "debug_traceBlock",
            { traceBlock(BlockId.LATEST, trace(FourByteTracer)).send() },
            { element ->
                element.jsonArray.map { item ->
                    var txHash: Hash? = null
                    var result: Any? = null
                    var error: String? = null
                    item.jsonObject.forEach { (field, value) ->
                        when (field) {
                            "txHash" -> txHash = value.jsonPrimitive.asHash()
                            "result" -> result = fourByte(value)
                            "error" -> error = value.jsonPrimitive.content
                        }
                    }
                    TxTraceResult(txHash, result, error)
                }
            },
            listOf(
                """[{"txHash":"$hash","result":{"0x27dc297e-128":1}},{"txHash":"$hash","error":"boom"}]""",
                """[{"result":{},"extra":[1]}]""", "[]", "[{}]", """[{"result":{"a":"x"}}]""", """[{"txHash":"0x12","result":{}}]""",
                """[{"txHash":null,"result":{}}]""", """[{"error":null,"result":{}}]""", """[{"result":null,"error":"boom"}]""",
                "null", "{}",
            ),
            improved = mapOf(
                // the tree decoder read null as the text "null", or passed it to the tracer, which failed
                """[{"txHash":null,"result":{}}]""" to listOf(TxTraceResult(null, FourByteTracer.Result(emptyMap()), null)),
                """[{"error":null,"result":{}}]""" to listOf(TxTraceResult(null, FourByteTracer.Result(emptyMap()), null)),
                """[{"result":null,"error":"boom"}]""" to listOf(TxTraceResult<Any>(null, null, "boom")),
            ),
        ),
        Case("newPendingTransactions", { subscribeNewPendingTransactionHashes().send() }, { it.jsonPrimitive.asHash() }, HASH_INPUTS),
        Case("newPendingTransactions full", { subscribeNewPendingTransactions().send() }, { Kotlinx.DEFAULT.decodeFromJsonElement(RPCTransaction.serializer(), it) }, listOf(TRANSACTION, "null", "{}")),
        Case("newHeads", { subscribeNewHeads().send() }, { Kotlinx.DEFAULT.decodeFromJsonElement(BlockWithHashes.serializer(), it) }, listOf("null", "{}", "[]")),
        Case("logs", { subscribeLogs(LogFilter {}).send() }, { Kotlinx.DEFAULT.decodeFromJsonElement(Log.serializer(), it) }, listOf(LOG, "null", "{}")),
    )
}

private val MUX = MuxTracer(CallTracer(onlyTopCall = false, withLog = true), FourByteTracer, NoopTracer)

private val structOracle: (JsonElement) -> Any? = { Kotlinx.DEFAULT.decodeFromJsonElement(StructTracer.ExecutionResult.serializer(), it) }

// the tree decoders of the tracers, as they were
private fun fourByte(element: JsonElement) = FourByteTracer.Result(element.jsonObject.entries.associate { (k, v) -> k to v.jsonPrimitive.int })

private val muxOracle: (JsonElement) -> Any? = { element ->
    val results = arrayOfNulls<Any>(MUX.tracers.size)
    for ((name, value) in element.jsonObject.entries) {
        val i = MUX.tracers.indexOfFirst { it.name == name }
        if (i < 0) throw IllegalArgumentException("Unknown tracer in mux result: $name")
        results[i] = when (val tracer = MUX.tracers[i]) {
            is FourByteTracer -> fourByte(value)
            is NoopTracer -> Kotlinx.DEFAULT.decodeFromJsonElement(NoopTracer.Result.serializer(), value)
            else -> Kotlinx.DEFAULT.decodeFromJsonElement(tracer.resultSerializer, value)
        }
    }
    MuxTracer.Result(MUX.tracers, results)
}

private val QUANTITIES = listOf(
    "\"0x1a\"", "\"0X1A\"", "\"0x0\"", "\"0x\"", "\"\"", "26", "\"26\"", "null", "true", "\"0xzz\"", "\"0x123\"",
    "\"0xffffffffffffffffffff\"", "\"0x00000000000000001a\"", "{}", "[]",
)
private val BOOLEANS = listOf("true", "false", "\"true\"", "\"false\"", "1", "0", "null", "\"yes\"", "{}")
private val HASH_INPUTS = listOf("\"0x${"ab".repeat(32)}\"", "\"0x\"", "\"\"", "null", "\"0x12\"", "12", "{}")
private val HASHES = "[\"0x${"ab".repeat(32)}\",\"0x${"cd".repeat(32)}\"]"
private val STRUCT_TRACES = listOf(
    """{"gas":21000,"failed":false,"returnValue":"0x","structLogs":[{"pc":0,"op":"PUSH1","gas":10,"gasCost":3,"depth":1,"stack":["0x1"]}]}""",
    """{"gas":"21000","failed":"true","returnValue":"","structLogs":[],"extra":1}""",
    "{}",
    "null",
    "[]",
)
private const val CALL_FRAME = """{"type":"CALL","from":"0x0000000000000000000000000000000000000001","gas":"0x10","gasUsed":"0x8","input":"0x","calls":[{"type":"STATICCALL","from":"0x0000000000000000000000000000000000000002","gas":"0x1","gasUsed":"0x1","input":"0x12"}]}"""
private const val LOG = """{"address":"0x0000000000000000000000000000000000000001","topics":[],"data":"0x","blockNumber":"0x1","transactionHash":"0xabababababababababababababababababababababababababababababababab","transactionIndex":"0x0","blockHash":"0xabababababababababababababababababababababababababababababababab","logIndex":"0x0","removed":false}"""
private val LOGS = "[$LOG]"
private const val TRANSACTION = """{"hash":"0xabababababababababababababababababababababababababababababababab","nonce":"0x1","from":"0x0000000000000000000000000000000000000001","to":null,"value":"0x0","gas":"0x5208","gasPrice":"0x1","input":"0x","type":"0x0","v":"0x1b","r":"0x1","s":"0x1","chainId":"0x1"}"""
private val TRANSACTIONS = "[$TRANSACTION]"

private object Failed {
    override fun toString() = "Failed"
}

private data class Decoded(val value: Any?)

private inline fun outcome(block: () -> Any?): Any = try {
    Decoded(block())
} catch (_: Exception) {
    Failed
}

/** Records each request's method, parameters and deserializer, and fails it. */
private class CapturingClient : JsonRpcClient {
    val captured = ArrayList<Pair<String, DeserializationStrategy<*>>>()

    /** The parameters and deserializer of each filter poll. */
    val polls = Channel<Pair<RpcParams, DeserializationStrategy<*>>>(Channel.UNLIMITED)

    override suspend fun <T> request(method: String, params: RpcParams, resultDeserializer: DeserializationStrategy<T>): Result<T, RpcError> {
        if (method == "eth_getFilterChanges") {
            polls.trySend(params to resultDeserializer)
        } else if (method != "eth_uninstallFilter") {
            captured += method to resultDeserializer
        }
        return failure(RpcError(RpcError.CODE_INTERNAL_ERROR, "captured"))
    }

    override suspend fun requestBatch(batch: BatchRpcRequest): Boolean = false

    override suspend fun <T : Any> subscribe(params: RpcParams, resultDeserializer: DeserializationStrategy<T>): Result<ChannelReceiver<T>, RpcError> {
        captured += params[0].toString() to resultDeserializer
        return failure(RpcError(RpcError.CODE_INTERNAL_ERROR, "captured"))
    }

    override fun close() = Unit
}
