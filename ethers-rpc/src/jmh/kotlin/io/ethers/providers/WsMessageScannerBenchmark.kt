package io.ethers.providers

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
 * [WsMessageScanner] alone: what `WsClient` reads of each message before decoding it, to pick its deserializer.
 */
@Fork(value = 1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
open class WsMessageScannerBenchmark {
    /**
     * - `response`: an `eth_blockNumber` response
     * - `logs-id-first`: an `eth_getLogs` response with 500 logs, about 400 KB, `id` before `result` as geth writes it
     * - `logs-id-last`: the same with `id` after `result`, as Nethermind writes it
     * - `accounts-id-last`: a Solana `getProgramAccounts` response with 100 accounts of 165 bytes, about 40 KB, `id`
     *   after `result` as Agave writes it
     * - `log`: an `eth_subscription` notification of one log, about 700 bytes
     * - `account`: a Solana `accountNotification` of a 10,136-byte account, about 14 KB, `subscription` last
     */
    @Param("response", "logs-id-first", "logs-id-last", "accounts-id-last", "log", "account")
    lateinit var fixture: String

    private lateinit var message: String
    private val position = WsMessageScanner.NotificationPosition()

    @Setup
    fun setup() {
        val logs by lazy { Random(1).let { random -> List(500) { random.log(it) }.joinToString(",", "[", "]") } }
        message = when (fixture) {
            "response" -> """{"jsonrpc":"2.0","id":42,"result":"0x117c277"}"""
            "logs-id-first" -> """{"jsonrpc":"2.0","id":42,"result":$logs}"""
            "logs-id-last" -> """{"jsonrpc":"2.0","result":$logs,"id":42}"""
            "accounts-id-last" -> {
                val random = Random(2)
                val accounts = List(100) {
                    """{"account":{"data":["${Base64.encode(random.nextBytes(165))}","base64"],"executable":false,""" +
                        """"lamports":2039280,"owner":"TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA","rentEpoch":18446744073709551615,""" +
                        """"space":165},"pubkey":"${Base64.encode(random.nextBytes(32))}"}"""
                }
                """{"jsonrpc":"2.0","result":${accounts.joinToString(",", "[", "]")},"id":42}"""
            }
            "log" ->
                """{"jsonrpc":"2.0","method":"eth_subscription","params":""" +
                    """{"subscription":"0x9cef478923ff08bf67fde6c64013158d","result":${Random(3).log(7)}}}"""
            "account" -> {
                val data = Base64.encode(Random(4).nextBytes(10_136))
                """{"jsonrpc":"2.0","method":"accountNotification","params":{"result":""" +
                    """{"context":{"slot":341197053},"value":{"data":["$data","base64"],"executable":false,""" +
                    """"lamports":72161280,"owner":"LBUZKhRxPF3XUpBCjp4YzTKgLccjZhTSDM9YuVaPwxo",""" +
                    """"rentEpoch":18446744073709551615,"space":10136}},"subscription":23784}}"""
            }
            else -> error("Unknown fixture $fixture")
        }
        check(scan() != WsMessageScanner.NONE) { "nothing found in $fixture" }
    }

    /** What WsClient scans: a response's id, or else a notification's method and subscription. */
    @Benchmark
    fun scan(): Long {
        val id = WsMessageScanner.responseId(message)
        if (id != WsMessageScanner.NONE) return id
        if (!WsMessageScanner.notification(message, position)) return WsMessageScanner.NONE
        return position.subscriptionId(message)!!.hashCode().toLong()
    }

    companion object {
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
                .include(WsMessageScannerBenchmark::class.java.simpleName)
                .addProfiler(GCProfiler::class.java)
                .build()

            Runner(options).run()
        }
    }
}
