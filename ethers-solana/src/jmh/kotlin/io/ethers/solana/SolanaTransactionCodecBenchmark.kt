package io.ethers.solana

import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.transaction.SolanaTransactionSigned
import io.ethers.solana.types.transaction.SolanaTransactionUnsigned
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

@Fork(value = 1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
open class SolanaTransactionCodecBenchmark {
    @Param("legacy-typical", "legacy-large", "v0-typical", "v0-large", "v1-typical", "v1-large")
    lateinit var fixture: String

    private lateinit var bytes: ByteArray
    private lateinit var signed: SolanaTransactionSigned
    private lateinit var unsigned: SolanaTransactionUnsigned
    private lateinit var signatures: List<SolanaSignature>

    @Setup
    fun setup() {
        bytes = Base64.decode(SolanaTxFixtures.ENVELOPES.getValue(fixture))
        signed = SolanaTransactionSigned.deserialize(bytes).unwrap()
        unsigned = signed.tx
        signatures = signed.signatures
        check(signed.serialize().contentEquals(bytes)) { "Fixture $fixture does not round-trip" }
    }

    /** Decode only: parse the envelope without verifying signatures. */
    @Benchmark
    fun decodeEnvelope(): SolanaTransactionSigned.Builder = SolanaTransactionSigned.Builder.deserializePartial(bytes).unwrap()

    /** Decode and verify every signature, as [SolanaTransactionSigned.deserialize] does for untrusted input. */
    @Benchmark
    fun decodeVerified(): SolanaTransactionSigned = SolanaTransactionSigned.deserialize(bytes).unwrap()

    /** Encode a newly built message and envelope, bypassing the per-instance message cache. */
    @Benchmark
    fun encodeFresh(): ByteArray = unsigned.withNewBlockhash(unsigned.recentBlockhash).encodeEnvelope(signatures)

    /** Re-encode an envelope whose message bytes are already cached on the instance. */
    @Benchmark
    fun encodeCached(): ByteArray = signed.serialize()

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val options = OptionsBuilder()
                .include(SolanaTransactionCodecBenchmark::class.java.simpleName)
                .addProfiler(GCProfiler::class.java)
                .build()

            Runner(options).run()
        }
    }
}
