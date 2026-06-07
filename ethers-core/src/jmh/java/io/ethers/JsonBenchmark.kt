package io.ethers

import io.ethers.core.FastHex
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.infra.Blackhole
import org.openjdk.jmh.profile.GCProfiler
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.options.OptionsBuilder
import java.util.concurrent.TimeUnit

@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class JsonBenchmark {
    @Benchmark
    fun deserializeKotlinSerialization(bh: Blackhole) {
        bh.consume(Json.decodeFromString<BlockWithHashesReflection>(DATA))
    }

    companion object {
        private const val DATA =
            "{\"baseFeePerGas\":\"0x68acb5d7c\",\"difficulty\":\"0x0\",\"extraData\":\"0x6265617665726275696c642e6f7267\",\"gasLimit\":\"0x1c9c380\",\"gasUsed\":\"0xf48ce8\",\"hash\":\"0xba672570225717f87b3adc6c01bc5d285e1f4527c46b285b9d9d1c044b9144ee\",\"logsBloom\":\"0x2aa30b53412c036975af8719a2fcd539b1962f7dfe94b05dc40d2c05f6a4c9275241f754a8dc7c1ac58e1f6820311b6ef605b14cff3539058a6500c2c5b9e3116c24f038fceb592c7b237bfe2504706682d031aea0449f72a8935472ae6168f2d34e9c116a32858e254d980c2d4aec55aadd1ee902996d45cc5a65dcce4bf2d54f01875490a3881192cfbe02c346bd269c42e6e3cfd310adfd6ca8e28ab380289ab08d2281af6171ea0149ebb899fd970e7541134c0a3ceaa5f556e0882805dc7b91555306938d498320e3b27a27fe44ba78d00e57640f94198417eac89aa2bd6333a39ee922ea4f42b7f6949ae4c967a728957a1fee50f0c11d14385ea05541\",\"miner\":\"0x4675c7e5baafbffbca748158becba61ef3b0a263\",\"mixHash\":\"0x31a76baaeb80db67feec25bde9d397f6a7b2bfa13ef79aaa1aeaf20865fad68d\",\"nonce\":\"0x0000000000000000\",\"number\":\"0x10b7b87\",\"parentHash\":\"0x8d210e4f301a94e97ec79b4361584b7b1697caebca7c8de4a4b158a380ff426f\",\"receiptsRoot\":\"0x8898c4d2a8af3e3633c75e3c597e01edc39d14f9c49bdcce857ea80e1b3fa42e\",\"sha3Uncles\":\"0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347\",\"size\":\"0x143b8\",\"stateRoot\":\"0x080660f501f1ba66a3b33a27dbeb9097955487d2f0deef21442a63063076e27b\",\"timestamp\":\"0x64933d3f\",\"totalDifficulty\":\"0xc70d815d562d3cfa955\",\"transactions\":[],\"transactionsRoot\":\"0xc202b1d741fb893944152e9780754c43054b336fbc1ded86a385a79db98f2e94\",\"uncles\":[],\"withdrawalsRoot\":\"0x649d495a98f54d5e09178709832693ae7f3b0ab6495dff382f8c45f532b31e40\"}"

        @JvmStatic
        fun main(args: Array<String>) {
            val options = OptionsBuilder()
                .include(JsonBenchmark::class.java.simpleName)
                .addProfiler(GCProfiler::class.java)
                .warmupIterations(3)
                .measurementIterations(3)
                .build()

            Runner(options).run()
        }
    }

    @Serializable
    data class BlockWithHashesReflection(
        val baseFeePerGas: String,
        val difficulty: String,
        val extraData: String,
        val gasLimit: String,
        val gasUsed: String,
        val hash: String,
        val logsBloom: String,
        val miner: String,
        val mixHash: String,
        val nonce: String,
        val number: String,
        val parentHash: String,
        val receiptsRoot: String,
        val sha3Uncles: String,
        val size: String,
        val stateRoot: String,
        val timestamp: String,
        val totalDifficulty: String,
        val transactions: List<String>,
        val transactionsRoot: String,
        val uncles: List<String>,
        val withdrawalsRoot: String,
    )

    data class Address(val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Address
            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = bytes.contentHashCode()

        override fun toString(): String = "0x" + FastHex.encodeWithoutPrefix(bytes)
    }
}
