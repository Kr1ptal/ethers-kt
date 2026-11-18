package io.ethers.solana.idl

import io.ethers.core.Kotlinx
import io.ethers.solana.codec.SolanaAccountFactory
import io.ethers.solana.idl.test.AmmV3
import io.ethers.solana.idl.test.LbClmm
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.rpc.AccountInfo
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
import java.util.Base64 as JdkBase64

/**
 * Generated account bindings over mainnet accounts, next to the steps that bring their data in over RPC: parsing the
 * JSON `getAccountInfo` value, of which decoding the base64 data is a part.
 */
@Fork(value = 1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
open class GeneratedAccountBenchmark {
    /**
     * - `pool`: Raydium CLMM `PoolState`, 1,544 bytes
     * - `lbPair`: Meteora DLMM `LbPair`, 904 bytes
     * - `binArray`: Meteora DLMM `BinArray`, 10,136 bytes of 70 bins
     */
    @Param("pool", "lbPair", "binArray")
    lateinit var fixture: String

    private lateinit var factory: SolanaAccountFactory<Any>
    private lateinit var base64: String
    private lateinit var json: String
    private lateinit var data: ByteArray
    private lateinit var value: Any

    @Setup
    fun setup() {
        val (factory, base64, owner) = when (fixture) {
            "pool" -> Triple(AmmV3.PoolState.ACCOUNT, RAYDIUM_POOL, AmmV3.PROGRAM_ID)
            "lbPair" -> Triple(LbClmm.LbPair.ACCOUNT, METEORA_LB_PAIR, LbClmm.PROGRAM_ID)
            "binArray" -> Triple(LbClmm.BinArray.ACCOUNT, METEORA_BIN_ARRAY, LbClmm.PROGRAM_ID)
            else -> error("Unknown fixture $fixture")
        }
        @Suppress("UNCHECKED_CAST")
        this.factory = factory as SolanaAccountFactory<Any>
        this.base64 = base64
        data = Base64.decode(base64)
        json = accountInfoJson(base64, data.size, owner)
        value = this.factory.decode(data).unwrap()!!
        check(this.factory.encode(value).contentEquals(data)) { "Fixture $fixture does not round-trip" }
        check(rpcJson().data.asByteArray().contentEquals(data)) { "Fixture $fixture JSON does not carry its data" }
    }

    /** Decode the account data into the generated type. */
    @Benchmark
    fun decode(): Any = factory.decode(data).unwrap()!!

    /** Encode the generated type back into account data. */
    @Benchmark
    fun encode(): ByteArray = factory.encode(value)

    /** Decode the base64 account data the RPC returns, which [rpcJson] includes. */
    @Benchmark
    fun base64(): ByteArray = Base64.decode(base64)

    /** [base64] with the JDK's decoder, for comparison: the provider uses the multiplatform one. */
    @Benchmark
    fun base64Jdk(): ByteArray = JdkBase64.getDecoder().decode(base64)

    /** Parse a `getAccountInfo` value as the provider does: into a JSON tree, then into [AccountInfo]. */
    @Benchmark
    fun rpcJson(): AccountInfo = Kotlinx.DEFAULT.decodeFromJsonElement(AccountInfo.serializer(), Kotlinx.DEFAULT.parseToJsonElement(json))

    companion object {
        private fun accountInfoJson(base64: String, size: Int, owner: SolanaAddress) = """{"data":["$base64","base64"],"executable":false,"lamports":72161280,"owner":"$owner","rentEpoch":18446744073709551615,"space":$size}"""

        @JvmStatic
        fun main(args: Array<String>) {
            val options = OptionsBuilder()
                .include(GeneratedAccountBenchmark::class.java.simpleName)
                .addProfiler(GCProfiler::class.java)
                .build()

            Runner(options).run()
        }
    }
}
