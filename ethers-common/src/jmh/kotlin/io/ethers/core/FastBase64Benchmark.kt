package io.ethers.core

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
import java.util.Base64 as JdkBase64

@Fork(value = 1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
open class FastBase64Benchmark {
    /**
     * Decoded sizes in bytes:
     * - `32`: an address or hash
     * - `165`: an SPL token account
     * - `1544`: a Raydium CLMM pool account
     * - `10136`: a Meteora DLMM bin array account
     * - `65536`: a large account or a batch of transactions
     */
    @Param("32", "165", "1544", "10136", "65536")
    var size: Int = 0

    private lateinit var bytes: ByteArray
    private lateinit var encoded: String

    @Setup
    fun setup() {
        bytes = Random(64).nextBytes(size)
        encoded = Base64.encode(bytes)
        check(FastBase64.decode(encoded).contentEquals(bytes)) { "FastBase64 does not decode what the stdlib encodes" }
        check(FastBase64.encode(bytes) == encoded) { "FastBase64 does not encode as the stdlib does" }
    }

    @Benchmark
    fun decodeFast(): ByteArray = FastBase64.decode(encoded)

    /** The multiplatform stdlib decoder FastBase64 replaces. */
    @Benchmark
    fun decodeStdlib(): ByteArray = Base64.decode(encoded)

    @Benchmark
    fun decodeJdk(): ByteArray = JdkBase64.getDecoder().decode(encoded)

    @Benchmark
    fun encodeFast(): String = FastBase64.encode(bytes)

    /** The multiplatform stdlib encoder FastBase64 replaces. */
    @Benchmark
    fun encodeStdlib(): String = Base64.encode(bytes)

    @Benchmark
    fun encodeJdk(): String = JdkBase64.getEncoder().encodeToString(bytes)

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val options = OptionsBuilder()
                .include(FastBase64Benchmark::class.java.simpleName)
                .addProfiler(GCProfiler::class.java)
                .build()

            Runner(options).run()
        }
    }
}
