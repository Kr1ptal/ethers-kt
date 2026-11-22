package io.ethers.solana.codec

import io.ethers.solana.types.SolanaAddress
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
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
open class BorshCodecBenchmark {
    /**
     * - `account`: an SPL-token-like account with addresses, options, small integers and a name
     * - `integers`: vectors of every integer width, mostly small values as amounts and counters are
     * - `ascii`: ASCII strings, as names, symbols and URIs mostly are
     * - `unicode`: strings with 2-, 3- and 4-byte UTF-8 characters
     */
    @Param("account", "integers", "ascii", "unicode")
    lateinit var fixture: String

    private lateinit var codec: SolanaCodec<Any>
    private lateinit var value: Any
    private lateinit var bytes: ByteArray

    @Setup
    fun setup() {
        @Suppress("UNCHECKED_CAST")
        codec = when (fixture) {
            "account" -> Account
            "integers" -> Integers
            "ascii", "unicode" -> Strings
            else -> error("Unknown fixture $fixture")
        } as SolanaCodec<Any>
        value = when (fixture) {
            "account" -> Account.SAMPLE
            "integers" -> Integers.SAMPLE
            "ascii" -> Strings.ASCII
            else -> Strings.UNICODE
        }
        bytes = codec.encode(value)
        check(codec.decode(bytes).unwrap() == value) { "Fixture $fixture does not round-trip" }
    }

    @Benchmark
    fun decode(): Any = codec.decode(bytes).unwrap()!!

    @Benchmark
    fun encode(): ByteArray = codec.encode(value)

    private data class Account(
        val mint: SolanaAddress,
        val owner: SolanaAddress,
        val amount: BigInteger,
        val delegate: SolanaAddress?,
        val state: Int,
        val isNative: BigInteger?,
        val delegatedAmount: BigInteger,
        val closeAuthority: SolanaAddress?,
        val name: String,
        val symbol: String,
        val decimals: Int,
        val frozen: Boolean,
        val supply: BigInteger,
        val createdAt: Long,
    ) {
        companion object : SolanaCodec<Account> {
            val SAMPLE = Account(
                mint = address(1),
                owner = address(2),
                amount = bigIntegerOf(1_234_567_890_123L),
                delegate = address(3),
                state = 1,
                isNative = null,
                delegatedAmount = bigIntegerOf(500_000L),
                closeAuthority = null,
                name = "Wrapped Staked SOL",
                symbol = "wsSOL",
                decimals = 9,
                frozen = false,
                supply = bigIntegerOf(987_654_321_000_000L),
                createdAt = 1_731_542_400L,
            )

            override fun read(reader: BorshReader) = Account(
                reader.readAddress(),
                reader.readAddress(),
                reader.readU64(),
                reader.readOption { readAddress() },
                reader.readU8(),
                reader.readOption { readU64() },
                reader.readU64(),
                reader.readOption { readAddress() },
                reader.readString(),
                reader.readString(),
                reader.readU8(),
                reader.readBool(),
                reader.readU128(),
                reader.readI64(),
            )

            override fun write(writer: BorshWriter, value: Account) {
                writer.writeAddress(value.mint)
                writer.writeAddress(value.owner)
                writer.writeU64(value.amount)
                writer.writeOption(value.delegate) { writeAddress(it) }
                writer.writeU8(value.state)
                writer.writeOption(value.isNative) { writeU64(it) }
                writer.writeU64(value.delegatedAmount)
                writer.writeOption(value.closeAuthority) { writeAddress(it) }
                writer.writeString(value.name)
                writer.writeString(value.symbol)
                writer.writeU8(value.decimals)
                writer.writeBool(value.frozen)
                writer.writeU128(value.supply)
                writer.writeI64(value.createdAt)
            }
        }
    }

    private data class Integers(
        val u8: List<Int>,
        val u16: List<Int>,
        val u32: List<Long>,
        val u64: List<BigInteger>,
        val i32: List<Int>,
        val i64: List<Long>,
        val u128: List<BigInteger>,
        val i128: List<BigInteger>,
        val f64: List<Double>,
    ) {
        companion object : SolanaCodec<Integers> {
            val SAMPLE = Random(7).let { random ->
                // amounts are mostly far below 2^63; keep a few full-width u64 values such as u64::MAX sentinels
                val u64Max = BigInteger("18446744073709551615")
                Integers(
                    u8 = List(32) { random.nextInt(256) },
                    u16 = List(32) { random.nextInt(65536) },
                    u32 = List(32) { random.nextLong(1L shl 32) },
                    u64 = List(64) { if (it % 16 == 15) u64Max else bigIntegerOf(random.nextLong(1L shl 50)) },
                    i32 = List(32) { random.nextInt() },
                    i64 = List(32) { random.nextLong() },
                    u128 = List(16) { if (it % 8 == 7) u64Max.shiftLeft(40) else bigIntegerOf(random.nextLong(1L shl 60)) },
                    i128 = List(16) { bigIntegerOf(random.nextLong()) },
                    f64 = List(16) { random.nextDouble() },
                )
            }

            override fun read(reader: BorshReader) = Integers(
                reader.readVec { readU8() },
                reader.readVec { readU16() },
                reader.readVec { readU32() },
                reader.readVec { readU64() },
                reader.readVec { readI32() },
                reader.readVec { readI64() },
                reader.readVec { readU128() },
                reader.readVec { readI128() },
                reader.readVec { readF64() },
            )

            override fun write(writer: BorshWriter, value: Integers) {
                writer.writeVec(value.u8) { writeU8(it) }
                writer.writeVec(value.u16) { writeU16(it) }
                writer.writeVec(value.u32) { writeU32(it) }
                writer.writeVec(value.u64) { writeU64(it) }
                writer.writeVec(value.i32) { writeI32(it) }
                writer.writeVec(value.i64) { writeI64(it) }
                writer.writeVec(value.u128) { writeU128(it) }
                writer.writeVec(value.i128) { writeI128(it) }
                writer.writeVec(value.f64) { writeF64(it) }
            }
        }
    }

    private data class Strings(val values: List<String>) {
        companion object : SolanaCodec<Strings> {
            val ASCII = Strings(List(16) { "token-metadata-uri-https://arweave.net/item-$it" })
            val UNICODE = Strings(List(16) { "Žetoni ✓ 🚀 token metadata #$it" })

            override fun read(reader: BorshReader) = Strings(reader.readVec { readString() })

            override fun write(writer: BorshWriter, value: Strings) = writer.writeVec(value.values) { writeString(it) }
        }
    }

    companion object {
        private fun address(seed: Int) = SolanaAddress(Random(seed).nextBytes(32))

        @JvmStatic
        fun main(args: Array<String>) {
            val options = OptionsBuilder()
                .include(BorshCodecBenchmark::class.java.simpleName)
                .addProfiler(GCProfiler::class.java)
                .build()

            Runner(options).run()
        }
    }
}
