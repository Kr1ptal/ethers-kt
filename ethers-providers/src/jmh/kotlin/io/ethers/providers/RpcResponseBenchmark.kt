package io.ethers.providers

import io.ethers.core.Kotlinx
import io.ethers.core.types.BlockWithTransactions
import io.ethers.core.types.Log
import io.ethers.solana.types.rpc.AccountInfo
import io.ethers.solana.types.rpc.ContextValue
import io.ethers.solana.types.rpc.RpcContext
import kotlinx.io.Buffer
import kotlinx.io.readString
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.io.decodeFromSource
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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
import kotlin.random.Random

/**
 * Turning a JSON-RPC response body into the result a provider returns, by each route kotlinx offers.
 *
 * Every route starts from the body in a kotlinx-io [Buffer], as Ktor 3 holds it, and produces the same value:
 * - `current`: what `HttpClient` and `WsClient` do: the body as a `String`, parsed into a `JsonElement` tree, whose
 *   `result` the provider's decoder turns into the value
 * - `source`: the same tree, parsed from the bytes with `kotlinx-serialization-json-io` instead of a `String`
 * - `direct`: the body as a `String`, decoded straight into the value with a typed envelope, without a tree
 * - `sourceDirect`: decoded straight into the value from the bytes
 * - `text`: only the `String` step of `current` and `direct`, for scale
 */
@Fork(value = 1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
open class RpcResponseBenchmark {
    /**
     * - `block`: `eth_getBlockByNumber` with 150 full transactions, about 200 KB
     * - `logs`: `eth_getLogs` with 500 logs, about 400 KB
     * - `accounts`: Solana `getMultipleAccounts` of 99 mainnet pool, pair and bin array accounts, about 600 KB, most
     *   of it base64 account data
     */
    @Param("block", "logs", "accounts")
    lateinit var fixture: String

    private lateinit var body: ByteArray
    private lateinit var fromTree: (JsonElement) -> Any?
    private lateinit var envelope: DeserializationStrategy<Any?>

    @Setup
    fun setup() {
        val json = Kotlinx.DEFAULT
        when (fixture) {
            "block" -> {
                body = response(blockJson())
                // Provider.getBlock: decodeAsOrNull(BlockWithTransactions.serializer())
                fromTree = { if (it is JsonNull) null else json.decodeFromJsonElement(BlockWithTransactions.serializer(), it) }
                envelope = envelope(BlockWithTransactions.serializer().nullable)
            }
            "logs" -> {
                body = response(logsJson())
                // Provider.getLogs: decodeListAs<Log>()
                fromTree = { result -> result.jsonArray.map { json.decodeFromJsonElement(Log.serializer(), it) } }
                envelope = envelope(ListSerializer(Log.serializer()))
            }
            "accounts" -> {
                body = response(accountsJson())
                // SolanaApi.getMultipleAccounts: decodeContext(it) { v -> v.jsonArray.map(::decodeAccount) }
                fromTree = { result ->
                    val obj = result.jsonObject
                    ContextValue(
                        json.decodeFromJsonElement(RpcContext.serializer(), obj.getValue("context")),
                        obj.getValue("value").jsonArray.map { if (it == JsonNull) null else json.decodeFromJsonElement(AccountInfo.serializer(), it) },
                    )
                }
                envelope = envelope(ContextValue.serializer(ListSerializer(AccountInfo.serializer().nullable)))
            }
            else -> error("Unknown fixture $fixture")
        }
        val expected = current()
        check(expected != null)
        check(source() == expected) { "source differs for $fixture" }
        check(direct() == expected) { "direct differs for $fixture" }
        check(sourceDirect() == expected) { "sourceDirect differs for $fixture" }
    }

    private fun bodyBuffer() = Buffer().apply { write(body) }

    @Benchmark
    fun current(): Any? = fromTree(Kotlinx.DEFAULT.parseToJsonElement(bodyBuffer().readString()).jsonObject.getValue("result"))

    @Benchmark
    fun source(): Any? = fromTree(Kotlinx.DEFAULT.decodeFromSource(JsonElement.serializer(), bodyBuffer()).jsonObject.getValue("result"))

    @Benchmark
    fun direct(): Any? = Kotlinx.DEFAULT.decodeFromString(envelope, bodyBuffer().readString())

    @Benchmark
    fun sourceDirect(): Any? = Kotlinx.DEFAULT.decodeFromSource(envelope, bodyBuffer())

    @Benchmark
    fun text(): String = bodyBuffer().readString()

    /** Reads `{"jsonrpc", "id", "result"}` and returns the result decoded with [result], skipping other keys. */
    private class Envelope<T>(private val result: KSerializer<T>) : DeserializationStrategy<T> {
        override val descriptor = buildClassSerialDescriptor("RpcEnvelope") {
            element<String>("jsonrpc")
            element<Long>("id")
            element("result", result.descriptor)
        }

        override fun deserialize(decoder: Decoder): T = decoder.decodeStructure(descriptor) {
            var value: T? = null
            var seen = false
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> decodeStringElement(descriptor, 0)
                    1 -> decodeLongElement(descriptor, 1)
                    2 -> {
                        value = decodeSerializableElement(descriptor, 2, result)
                        seen = true
                    }
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index $index")
                }
            }
            check(seen) { "Response has no result" }
            @Suppress("UNCHECKED_CAST")
            value as T
        }
    }

    companion object {
        private const val RAYDIUM_CLMM = "CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK"
        private const val METEORA_DLMM = "LBUZKhRxPF3XUpBCjp4YzTKgLccjZhTSDM9YuVaPwxo"

        @Suppress("UNCHECKED_CAST")
        private fun envelope(result: KSerializer<*>) = Envelope(result) as DeserializationStrategy<Any?>

        private fun response(result: String) = """{"jsonrpc":"2.0","result":$result,"id":1}""".encodeToByteArray()

        private fun Random.hex(bytes: Int) = "0x" + nextBytes(bytes).toHexString()

        /** A mainnet-shaped block: the header of the `BlockTest` sample and 150 EIP-1559 transactions with calldata. */
        private fun blockJson(): String {
            val random = Random(1)
            val hash = random.hex(32)
            val transactions = List(150) { index ->
                """{"blockHash":"$hash","blockNumber":"0x117c277","from":"${random.hex(20)}","gas":"0x6ddd0",""" +
                    """"gasPrice":"0x1cd5d2c16","maxFeePerGas":"0x26f45990a","maxPriorityFeePerGas":"0x5f5e100",""" +
                    """"hash":"${random.hex(32)}","input":"${random.hex(random.nextInt(4, 600))}","nonce":"0x${random.nextInt(1 shl 20).toString(16)}",""" +
                    """"to":"${random.hex(20)}","transactionIndex":"0x${index.toString(16)}","value":"0x2386f26fc10000","type":"0x2",""" +
                    """"accessList":[{"address":"${random.hex(20)}","storageKeys":["${random.hex(32)}","${random.hex(32)}"]}],""" +
                    """"chainId":"0x1","v":"0x1","r":"${random.hex(32)}","s":"${random.hex(32)}","yParity":"0x1"}"""
            }
            return """{"difficulty":"0x0","extraData":"0x626f62612d6275696c6465722e636f6d","gasLimit":"0x1c9c364",""" +
                """"gasUsed":"0xb085d9","hash":"$hash","logsBloom":"${random.hex(256)}","mixHash":"${random.hex(32)}",""" +
                """"nonce":"0x0000000000000000","number":"0x117c277","parentHash":"${random.hex(32)}",""" +
                """"receiptsRoot":"${random.hex(32)}","sha3Uncles":"${random.hex(32)}","size":"0x103a3",""" +
                """"stateRoot":"${random.hex(32)}","timestamp":"0x6527e5fb","totalDifficulty":"0xc70d815d562d3cfa955",""" +
                """"transactionsRoot":"${random.hex(32)}","withdrawalsRoot":"${random.hex(32)}","baseFeePerGas":"0x2e22eacf",""" +
                """"miner":"${random.hex(20)}","transactions":[${transactions.joinToString(",")}],"uncles":[],""" +
                """"withdrawals":[${List(16) { """{"index":"0x${(20_000_000 + it).toString(16)}","validatorIndex":"0x92592","address":"${random.hex(20)}","amount":"0x369db71"}""" }.joinToString(",")}]}"""
        }

        /** 500 logs with one to four topics and up to 160 bytes of data. */
        private fun logsJson(): String {
            val random = Random(2)
            val logs = List(500) { index ->
                val topics = List(random.nextInt(1, 5)) { "\"${random.hex(32)}\"" }.joinToString(",")
                """{"address":"${random.hex(20)}","topics":[$topics],"data":"${random.hex(32 * random.nextInt(0, 6))}",""" +
                    """"blockNumber":"0x117c277","blockTimestamp":"0x6624e154","transactionHash":"${random.hex(32)}",""" +
                    """"transactionIndex":"0x${(index / 4).toString(16)}","blockHash":"${random.hex(32)}",""" +
                    """"logIndex":"0x${index.toString(16)}","removed":false}"""
            }
            return "[${logs.joinToString(",")}]"
        }

        /** 99 accounts, a third each of the pool, pair and bin array, with every field the RPC returns. */
        private fun accountsJson(): String {
            val fixtures = listOf(RAYDIUM_POOL to RAYDIUM_CLMM, METEORA_LB_PAIR to METEORA_DLMM, METEORA_BIN_ARRAY to METEORA_DLMM)
            val accounts = List(99) { index ->
                val (data, owner) = fixtures[index % 3]
                val size = data.length / 4 * 3 - data.takeLast(2).count { it == '=' }
                """{"data":["$data","base64"],"executable":false,"lamports":${72_161_280 + index},"owner":"$owner",""" +
                    """"rentEpoch":18446744073709551615,"space":$size}"""
            }
            return """{"context":{"apiVersion":"2.1.21","slot":341197053},"value":[${accounts.joinToString(",")}]}"""
        }

        @JvmStatic
        fun main(args: Array<String>) {
            val options = OptionsBuilder()
                .include(RpcResponseBenchmark::class.java.simpleName)
                .addProfiler(GCProfiler::class.java)
                .build()

            Runner(options).run()
        }
    }
}
