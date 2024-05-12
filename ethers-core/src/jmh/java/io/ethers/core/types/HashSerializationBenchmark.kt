package io.ethers.core.types

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OperationsPerInvocation
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import org.openjdk.jmh.profile.GCProfiler
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.options.OptionsBuilder
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@Fork(value = 1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class HashSerializationBenchmark {

    @Benchmark
    @OperationsPerInvocation(1024 * 10)
    fun toStringRandom(bh: Blackhole, state: BenchState) {
        for (address in state.random) {
            bh.consume(address)
        }
    }

    @Benchmark
    @OperationsPerInvocation(1024 * 10)
    fun toStringDuplicate50(bh: Blackhole, state: BenchState) {
        for (address in state.duplicate50) {
            bh.consume(address.toString())
        }
    }

    @State(Scope.Benchmark)
    open class BenchState {
        private val duplicate = Hash.random()
        val random = Array(1024 * 10) { Hash.random() }
        val duplicate50 = Array(1024 * 2) { Hash.random() } + Array(1024 * 2) { duplicate } +
            Array(1024 * 2) { Hash.random() } + Array(1024 * 2) { duplicate } +
            Array(1024 * 2) { Hash.random() }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val options = OptionsBuilder()
                .include(HashSerializationBenchmark::class.java.simpleName)
                .addProfiler(GCProfiler::class.java)
                .warmupIterations(3)
                .measurementIterations(3)
                .build()
            Runner(options).run()
        }
    }
}

fun Hash.Companion.random(): Hash {
    return Hash(Random.nextBytes(ByteArray(32)))
}
