package io.ethers.solana.codec

import io.ethers.core.FastHex
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private fun hex(value: String): ByteArray = FastHex.decode(value.replace(" ", ""))

private val PROGRAM = SolanaAddress("11111111111111111111111111111112")

private data class Counter(val authority: SolanaAddress, val count: BigInteger) {
    companion object : SolanaCodec<Counter> {
        override fun read(reader: BorshReader) = Counter(reader.readAddress(), reader.readU64())

        override fun write(writer: BorshWriter, value: Counter) {
            writer.writeAddress(value.authority)
            writer.writeU64(value.count)
        }
    }
}

/** A generated event would look like this: fields and context on one value, the factory on its companion. */
private data class Incremented(val by: Long, override val context: SolanaEventContext) : SolanaEvent {
    companion object : SolanaEventFactory<Incremented> {
        override val name = "Incremented"
        override val discriminator = SolanaDiscriminator.of(1, 2, 3, 4, 5, 6, 7, 8)

        override fun read(reader: BorshReader, context: SolanaEventContext) = Incremented(reader.readU32(), context)

        override fun write(writer: BorshWriter, event: Incremented) = writer.writeU32(event.by)
    }
}

private enum class CounterError(override val code: Long, override val message: String?) : SolanaProgramError {
    Overflow(6000, "Counter overflowed"),
    Unauthorized(6001, null),
}

class ProgramDescriptorTest : FunSpec({
    context("account factory") {
        val factory = SolanaAccountFactory("Counter", SolanaDiscriminator.of(0xff, 0, 1, 2, 3, 4, 5, 6), Counter)
        val counter = Counter(PROGRAM, bigIntegerOf(9))
        val encoded = hex("ff00010203040506") + PROGRAM.asByteArray() + hex("0900000000000000")

        test("encodes the discriminator and payload") {
            factory.encode(counter) shouldBe encoded
            factory.decode(encoded).unwrap() shouldBe counter
        }

        test("ignores allocation padding after the value") {
            factory.decode(encoded + ByteArray(64)).unwrap() shouldBe counter
            factory.decode(SolanaBytes.fromBytes(encoded + ByteArray(1))).unwrap() shouldBe counter
        }

        test("a different or truncated discriminator is unrecognized and keeps the data") {
            val other = hex("0000000000000000") + PROGRAM.asByteArray()
            val error = factory.decode(other).unwrapError().shouldBeInstanceOf<SolanaDecodeError.Unrecognized>()
            error.data shouldBe SolanaBytes.fromBytes(other)
            factory.decode(hex("ff0001")).unwrapError().shouldBeInstanceOf<SolanaDecodeError.Unrecognized>()
            factory.matches(hex("ff0001")) shouldBe false
        }

        test("a matching discriminator with a truncated payload is malformed, not unrecognized") {
            val error = factory.decode(encoded.copyOf(20)).unwrapError().shouldBeInstanceOf<SolanaDecodeError.Malformed>()
            error.reason shouldBe SolanaDecodeError.Reason.TRUNCATED
            error.offset shouldBe 8
        }
    }

    context("instruction descriptor") {
        val increment = SolanaInstructionDescriptor.Returning(
            "increment",
            SolanaDiscriminator.of(0x0b, 0x12),
            SolanaCodec.of({ readU32() }, { writeU32(it) }),
            SolanaCodec.of({ readU64() }, { writeU64(it) }),
        )

        test("encodes and decodes the arguments through the same layout") {
            val data = increment.encodeData(5)
            data shouldBe hex("0b12 05000000")
            increment.matches(data) shouldBe true
            increment.decodeData(data).unwrap() shouldBe 5L
            increment.decodeData(SolanaBytes.fromBytes(data)).unwrap() shouldBe 5L
        }

        test("rejects trailing argument bytes") {
            val error = increment.decodeData(hex("0b12 05000000 00")).unwrapError().shouldBeInstanceOf<SolanaDecodeError.Malformed>()
            error.reason shouldBe SolanaDecodeError.Reason.TRAILING_DATA
        }

        test("an unknown tag is unrecognized") {
            increment.decodeData(hex("0c12 05000000")).unwrapError().shouldBeInstanceOf<SolanaDecodeError.Unrecognized>()
        }

        test("instructions without arguments use the unit codec") {
            val reset = SolanaInstructionDescriptor("reset", SolanaDiscriminator.of(9), SolanaCodec.UNIT)
            reset.encodeData(Unit) shouldBe hex("09")
            reset.decodeData(hex("09")).unwrap() shouldBe Unit
        }

        test("decodes declared return data exactly") {
            increment.decodeReturn(hex("0a00000000000000")).unwrap() shouldBe bigIntegerOf(10)
            increment.decodeReturn(hex("0a")).unwrapError().shouldBeInstanceOf<SolanaDecodeError.Malformed>()
        }
    }

    context("event factory") {
        val context = SolanaEventContext(
            PROGRAM,
            SolanaEventSource.Log(instructionIndex = 1, logIndex = 4, stackHeight = 1),
            SolanaExecutionContext(slot = 10, status = SolanaExecutionStatus.SUCCEEDED),
        )

        test("decodes fields and attaches the context to the occurrence") {
            val event = Incremented.decode(hex("0102030405060708 03000000"), context).unwrap()
            event.by shouldBe 3L
            event.context shouldBe context
        }

        test("the same factory decodes a CPI occurrence with its own context") {
            val cpi = SolanaEventContext(PROGRAM, SolanaEventSource.Cpi(instructionIndex = 0, innerIndex = 2, stackHeight = null))
            Incremented.decode(hex("0102030405060708 07000000"), cpi).unwrap() shouldBe Incremented(7, cpi)
        }

        test("encodes only the payload, never the context") {
            Incremented.encode(Incremented(3, context)) shouldBe hex("0102030405060708 03000000")
        }

        test("an unmatched discriminator is unrecognized; a malformed match is not") {
            Incremented.decode(hex("0000000000000000 03000000"), context).unwrapError()
                .shouldBeInstanceOf<SolanaDecodeError.Unrecognized>()
            Incremented.decode(hex("0102030405060708 0300"), context).unwrapError()
                .shouldBeInstanceOf<SolanaDecodeError.Malformed>().reason shouldBe SolanaDecodeError.Reason.TRUNCATED
        }

        test("a standalone context claims no execution") {
            val standalone = SolanaEventContext(PROGRAM)
            standalone.source shouldBe SolanaEventSource.Standalone
            standalone.execution.signature.shouldBeNull()
            standalone.execution.slot.shouldBeNull()
            standalone.execution.status shouldBe SolanaExecutionStatus.UNKNOWN
        }
    }

    context("program errors") {
        val decoder = SolanaProgramErrorDecoder.of(CounterError.entries)

        test("maps declared codes and leaves unknown codes unresolved") {
            decoder.decode(6000) shouldBe CounterError.Overflow
            decoder.decode(6001)?.name shouldBe "Unauthorized"
            decoder.decode(6002).shouldBeNull()
        }

        test("two programs may declare the same code") {
            val other = object : SolanaProgramError {
                override val code = 6000L
                override val name = "Other"
                override val message: String? = null
            }
            SolanaProgramErrorDecoder.of(listOf(other)).decode(6000) shouldBe other
            decoder.decode(6000) shouldBe CounterError.Overflow
        }

        test("rejects duplicate codes within a program and codes outside u32") {
            val duplicate = object : SolanaProgramError {
                override val code = 6000L
                override val name = "Duplicate"
                override val message: String? = null
            }
            shouldThrow<IllegalArgumentException> { SolanaProgramErrorDecoder.of(listOf(CounterError.Overflow, duplicate)) }
            val negative = object : SolanaProgramError {
                override val code = -1L
                override val name = "Negative"
                override val message: String? = null
            }
            shouldThrow<IllegalArgumentException> { SolanaProgramErrorDecoder.of(listOf(negative)) }
        }
    }

    test("discriminators reject empty and out-of-range bytes") {
        shouldThrow<IllegalArgumentException> { SolanaDiscriminator(ByteArray(0)) }
        shouldThrow<IllegalArgumentException> { SolanaDiscriminator.of(256) }
        SolanaDiscriminator.of(0xff, 1) shouldBe SolanaDiscriminator(hex("ff01"))
    }
})
