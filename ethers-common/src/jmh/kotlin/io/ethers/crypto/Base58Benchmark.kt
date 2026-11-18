package io.ethers.crypto

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

@Fork(value = 1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
open class Base58Benchmark {
    /**
     * - `8`: account discriminator in a memcmp filter
     * - `32`: Solana address / blockhash
     * - `64`: Solana signature
     * - `82`: BIP-32 extended key with checksum
     * - `256`..`8192`: larger payloads, e.g. instruction or account data
     */
    @Param("8", "32", "64", "82", "256", "1024", "4096", "8192")
    var size: Int = 0

    /** Leading zero bytes, encoded as leading '1' characters (e.g. the system program address is all zeros). */
    @Param("0", "4")
    var leadingZeros: Int = 0

    private lateinit var bytes: ByteArray
    private lateinit var encoded: String

    @Setup
    fun setup() {
        bytes = Random(42).nextBytes(size)
        bytes.fill(0, 0, leadingZeros)
        // make sure the first non-zero byte is non-zero, so the parameter means exactly what it says
        if (leadingZeros < size) bytes[leadingZeros] = (bytes[leadingZeros].toInt() or 1).toByte()
        encoded = Base58.encode(bytes)
        check(Base58.decode(encoded).contentEquals(bytes)) { "Base58 does not round-trip" }
    }

    @Benchmark
    fun encode(): String = Base58.encode(bytes)

    @Benchmark
    fun decode(): ByteArray = Base58.decode(encoded)

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val options = OptionsBuilder()
                .include(Base58Benchmark::class.java.simpleName)
                .addProfiler(GCProfiler::class.java)
                .build()

            Runner(options).run()
        }
    }
}
