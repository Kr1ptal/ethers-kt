package io.ethers.rlp

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.profile.GCProfiler
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.options.OptionsBuilder
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class RlpEncoderBenchmark {
    @Benchmark
    fun encodeExactSize() {
        val encoderSize = with(RlpEncoder) {
            sizeOf(LARGE_BIGINT) +
                sizeOf(LARGE_LONG) +
                sizeOf(BYTE_ARRAY) +
                sizeOfList(LIST_DATA) +
                sizeOfList(
                    sizeOf(LARGE_BIGINT) +
                        sizeOf(LARGE_LONG) +
                        sizeOf(BYTE_ARRAY) +
                        sizeOfList(LIST_DATA),
                )
        }

        val rlp = RlpEncoder(encoderSize, isExactSize = true)
        rlp.encode(LARGE_BIGINT)
        rlp.encode(LARGE_LONG)
        rlp.encode(BYTE_ARRAY)
        rlp.encodeList(RlpEncoder.sizeOfList(LIST_DATA)) {
            for (element in LIST_DATA_ARRAY) {
                rlp.encode(element)
            }
        }

        val bodySize = with(RlpEncoder) {
            sizeOf(LARGE_BIGINT) +
                sizeOf(LARGE_LONG) +
                sizeOf(BYTE_ARRAY) +
                sizeOfList(LIST_DATA)
        }

        rlp.encodeList(bodySize) {
            rlp.encode(LARGE_BIGINT)
            rlp.encode(LARGE_LONG)
            rlp.encode(BYTE_ARRAY)
            rlp.encodeList {
                for (element in LIST_DATA_ARRAY) {
                    rlp.encode(element)
                }
            }
        }
    }

    @Benchmark
    fun encodeDynamicSize() {
        val rlp = RlpEncoder()
        rlp.encode(LARGE_BIGINT)
        rlp.encode(LARGE_LONG)
        rlp.encode(BYTE_ARRAY)
        rlp.encodeList {
            for (element in LIST_DATA_ARRAY) {
                rlp.encode(element)
            }
        }

        rlp.encodeList {
            rlp.encode(LARGE_BIGINT)
            rlp.encode(LARGE_LONG)
            rlp.encode(BYTE_ARRAY)
            rlp.encodeList {
                for (element in LIST_DATA_ARRAY) {
                    rlp.encode(element)
                }
            }
        }
    }

    private class EncodableBytes(private val bytes: ByteArray) : RlpEncodable {
        override fun rlpSize(): Int = RlpEncoder.sizeOf(bytes)

        override fun rlpEncode(rlp: RlpEncoder) {
            rlp.encode(bytes)
        }
    }

    companion object {
        private val LARGE_BIGINT = "7124128831414985880012941251".toBigInteger()
        private const val LARGE_LONG = 88746621323991243L
        private val BYTE_ARRAY = Random.nextBytes(512)
        private val LIST_DATA = arrayListOf(
            "measurementIterations",
            "simpleName",
            "warmupIterations",
            "include",
            "addProfiler",
            "build",
            "run",
        ).map { EncodableBytes(it.toByteArray()) }

        private val LIST_DATA_ARRAY = LIST_DATA.toTypedArray()

        @JvmStatic
        fun main(args: Array<String>) {
            val options = OptionsBuilder()
                .include(RlpEncoderBenchmark::class.java.simpleName)
                .addProfiler(GCProfiler::class.java)
                //.addProfiler(JavaFlightRecorderProfiler::class.java)
                .warmupIterations(3)
                .measurementIterations(3)
                .build()

            Runner(options).run()
        }
    }
}
