package io.ethers.solana.codec

import io.ethers.core.FastHex
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private fun hex(value: String): ByteArray = FastHex.decode(value.replace(" ", ""))

private fun encode(write: BorshWriter.() -> Unit): ByteArray = BorshWriter().apply(write).toByteArray()

/** Decode [data] as exactly one value read by [read], through the whole-payload path. */
private fun <T> decode(data: String, read: BorshReader.() -> T): T = SolanaCodec.of(read) { }.decode(hex(data)).unwrap()

private fun malformed(data: String, read: BorshReader.() -> Any?): SolanaDecodeError.Malformed {
    return SolanaCodec.of(read) { }.decode(hex(data)).unwrapError().shouldBeInstanceOf<SolanaDecodeError.Malformed>()
}

private fun <T> SolanaCodec<T>.malformed(data: String): SolanaDecodeError.Malformed {
    return decode(hex(data)).unwrapError().shouldBeInstanceOf<SolanaDecodeError.Malformed>()
}

/** Encodings are computed by hand from the little-endian two's complement definition. */
private fun <T> roundTrips(value: T, encoded: String, read: BorshReader.() -> T, write: BorshWriter.(T) -> Unit) {
    encode { write(value) } shouldBe hex(encoded)
    decode(encoded, read) shouldBe value
}

/**
 * A struct codec in the shape generated code takes: fields read in order through one cursor, with
 * containers composed through the inline reader and writer helpers.
 */
private data class Transfer(val amount: BigInteger, val memo: String?, val flags: List<Boolean>) {
    companion object : SolanaCodec<Transfer> {
        override fun read(reader: BorshReader) = Transfer(
            reader.readU64(),
            reader.readOption { readString() },
            reader.readVec { readBool() },
        )

        override fun write(writer: BorshWriter, value: Transfer) {
            writer.writeU64(value.amount)
            writer.writeOption(value.memo) { writeString(it) }
            writer.writeVec(value.flags) { writeBool(it) }
        }
    }
}

class BorshCodecTest : FunSpec({
    context("fixed-width integers") {
        test("unsigned") {
            roundTrips(255, "ff", BorshReader::readU8, BorshWriter::writeU8)
            roundTrips(0x0102, "0201", BorshReader::readU16, BorshWriter::writeU16)
            roundTrips(4294967295L, "ffffffff", BorshReader::readU32, BorshWriter::writeU32)
            roundTrips(BigInteger("18446744073709551615"), "ffffffffffffffff", BorshReader::readU64, BorshWriter::writeU64)
            roundTrips(bigIntegerOf(0x0102030405060708L), "0807060504030201", BorshReader::readU64, BorshWriter::writeU64)
            roundTrips(bigIntegerOf(1), "01" + "00".repeat(15), BorshReader::readU128, BorshWriter::writeU128)
            roundTrips(BigInteger("340282366920938463463374607431768211455"), "ff".repeat(16), BorshReader::readU128, BorshWriter::writeU128)
            roundTrips(bigIntegerOf(1).shiftLeft(255), "00".repeat(31) + "80", BorshReader::readU256, BorshWriter::writeU256)
        }

        test("signed") {
            roundTrips((-1).toByte(), "ff", BorshReader::readI8, BorshWriter::writeI8)
            roundTrips((-2).toShort(), "feff", BorshReader::readI16, BorshWriter::writeI16)
            roundTrips(Int.MIN_VALUE, "00000080", BorshReader::readI32, BorshWriter::writeI32)
            roundTrips(Long.MIN_VALUE, "0000000000000080", BorshReader::readI64, BorshWriter::writeI64)
            roundTrips(bigIntegerOf(-1), "ff".repeat(16), BorshReader::readI128, BorshWriter::writeI128)
            roundTrips(bigIntegerOf(1).shiftLeft(127).negate(), "00".repeat(15) + "80", BorshReader::readI128, BorshWriter::writeI128)
            roundTrips(bigIntegerOf(1).shiftLeft(127).subtract(bigIntegerOf(1)), "ff".repeat(15) + "7f", BorshReader::readI128, BorshWriter::writeI128)
            roundTrips(bigIntegerOf(-256), "00" + "ff".repeat(31), BorshReader::readI256, BorshWriter::writeI256)
        }

        test("floats") {
            roundTrips(1.5f, "0000c03f", BorshReader::readF32, BorshWriter::writeF32)
            roundTrips(-2.0, "00000000000000c0", BorshReader::readF64, BorshWriter::writeF64)
        }

        test("values outside the width are rejected on write") {
            shouldThrow<IllegalArgumentException> { encode { writeU8(256) } }
            shouldThrow<IllegalArgumentException> { encode { writeU16(-1) } }
            shouldThrow<IllegalArgumentException> { encode { writeU32(4294967296L) } }
            shouldThrow<IllegalArgumentException> { encode { writeU64(bigIntegerOf(1).shiftLeft(64)) } }
            shouldThrow<IllegalArgumentException> { encode { writeU64(bigIntegerOf(-1)) } }
            shouldThrow<IllegalArgumentException> { encode { writeI128(bigIntegerOf(1).shiftLeft(127)) } }
            shouldThrow<IllegalArgumentException> { encode { writeI128(bigIntegerOf(1).shiftLeft(127).negate().subtract(bigIntegerOf(1))) } }
        }

        test("NaN is rejected in both directions") {
            shouldThrow<IllegalArgumentException> { encode { writeF32(Float.NaN) } }
            shouldThrow<IllegalArgumentException> { encode { writeF64(Double.NaN) } }
            malformed("0000c07f") { readF32() }.reason shouldBe SolanaDecodeError.Reason.INVALID_VALUE
            malformed("000000000000f87f") { readF64() }.reason shouldBe SolanaDecodeError.Reason.INVALID_VALUE
        }
    }

    context("booleans and options") {
        test("bool accepts only 0 and 1") {
            decode("01") { readBool() } shouldBe true
            decode("00") { readBool() } shouldBe false
            malformed("02") { readBool() }.reason shouldBe SolanaDecodeError.Reason.INVALID_BOOL
        }

        test("option") {
            encode { writeOption<Int>(null) { writeU8(it) } } shouldBe hex("00")
            encode { writeOption(5) { writeU8(it) } } shouldBe hex("0105")
            decode("00") { readOption { readU8() } } shouldBe null
            decode("0105") { readOption { readU8() } } shouldBe 5
            malformed("0205") { readOption { readU8() } }.reason shouldBe SolanaDecodeError.Reason.INVALID_OPTION_TAG
        }

        test("nested options keep Some(None) distinct from None") {
            decode("00") { readExplicitOption { readOption { readU8() } } } shouldBe SolanaOption.None
            decode("0100") { readExplicitOption { readOption { readU8() } } } shouldBe SolanaOption.Some(null)
            decode("010107") { readExplicitOption { readOption { readU8() } } } shouldBe SolanaOption.Some(7)
            encode { writeExplicitOption(SolanaOption.Some<Int?>(null)) { inner -> writeOption(inner) { writeU8(it) } } } shouldBe hex("0100")
            encode { writeExplicitOption(SolanaOption.None) { _: Int? -> } } shouldBe hex("00")
        }
    }

    context("variable-length values") {
        test("string is u32-length-prefixed UTF-8") {
            encode { writeString("héllo") } shouldBe hex("06000000 68 c3a9 6c6c6f")
            decode("06000000 68 c3a9 6c6c6f") { readString() } shouldBe "héllo"
            decode("00000000") { readString() } shouldBe ""
        }

        test("invalid UTF-8 is malformed at the string bytes") {
            val error = malformed("02000000 c328") { readString() }
            error.reason shouldBe SolanaDecodeError.Reason.INVALID_UTF8
            error.offset shouldBe 4
        }

        test("vector and array") {
            encode { writeVec(listOf(1, 2)) { writeU16(it) } } shouldBe hex("02000000 0100 0200")
            decode("02000000 0100 0200") { readVec { readU16() } } shouldBe listOf(1, 2)
            decode("010203") { readArray(3) { readU8() } } shouldBe listOf(1, 2, 3)
            shouldThrow<IllegalArgumentException> { encode { writeArray(listOf(1, 2), 3) { writeU8(it) } } }
            shouldThrow<IllegalArgumentException> { BorshReader(hex("")).readArray(-1) { readU8() } }
        }

        test("nested vectors") {
            val points = listOf(listOf(1, 2), listOf(3))
            val encoded = encode { writeVec(points) { inner -> writeVec(inner) { writeU16(it) } } }
            encoded shouldBe hex("02000000 02000000 0100 0200 01000000 0300")
            BorshReader(encoded).readVec { readVec { readU16() } } shouldBe points
        }

        test("bytes and fixed bytes") {
            decode("03000000 aabbcc") { readBytes() } shouldBe SolanaBytes.fromHex("aabbcc")
            encode { writeBytes(SolanaBytes.fromHex("aabbcc")) } shouldBe hex("03000000 aabbcc")
            decode("aabb") { readFixedBytes(2) } shouldBe SolanaBytes.fromHex("aabb")
            shouldThrow<IllegalArgumentException> { encode { writeFixedBytes(SolanaBytes.fromHex("aa"), 2) } }
        }

        test("a length beyond the remaining bytes is rejected before reading elements") {
            var reads = 0
            val error = malformed("ffffffff 01") { readVec { reads++ } }
            error.reason shouldBe SolanaDecodeError.Reason.LENGTH_OUT_OF_RANGE
            error.offset shouldBe 0
            reads shouldBe 0
            malformed("05000000 6869") { readString() }.reason shouldBe SolanaDecodeError.Reason.LENGTH_OUT_OF_RANGE
        }

        test("pubkey") {
            val address = SolanaAddress("11111111111111111111111111111112")
            encode { writeAddress(address) } shouldBe address.asByteArray()
            SolanaCodec.of({ readAddress() }, { writeAddress(it) }).decode(address.asByteArray()).unwrap() shouldBe address
        }
    }

    context("whole-payload decoding") {
        test("truncated data reports the offset of the missing value and keeps the input") {
            val error = malformed("0102") { readU32() }
            error.reason shouldBe SolanaDecodeError.Reason.TRUNCATED
            error.offset shouldBe 0
            error.data shouldBe SolanaBytes.fromHex("0102")
        }

        test("trailing bytes are rejected") {
            val error = malformed("0102") { readU8() }
            error.reason shouldBe SolanaDecodeError.Reason.TRAILING_DATA
            error.offset shouldBe 1
        }

        test("unwrap throws the typed exception") {
            val e = shouldThrow<SolanaDecodeException> { decode("") { readU8() } }
            e.error.shouldBeInstanceOf<SolanaDecodeError.Malformed>().reason shouldBe SolanaDecodeError.Reason.TRUNCATED
        }

        test("an embedded struct consumes only its own fields") {
            val value = Transfer(bigIntegerOf(1000), "hi", listOf(true, false))
            val encoded = hex("e803000000000000 01 02000000 6869 02000000 01 00")
            Transfer.encode(value) shouldBe encoded

            // a struct followed by an unrelated field, read through one cursor
            val reader = BorshReader(encoded + hex("2a"))
            Transfer.read(reader) shouldBe value
            reader.readU8() shouldBe 42
            reader.remaining shouldBe 0
        }

        test("a malformed nested field reports its absolute offset") {
            // the flags vector holds a 2 where a bool belongs
            val error = Transfer.malformed("e803000000000000 00 01000000 02")
            error.reason shouldBe SolanaDecodeError.Reason.INVALID_BOOL
            error.offset shouldBe 13
        }

        test("an undeclared enum variant is reported at its tag") {
            val error = malformed("03") { readU8().also { if (it > 1) invalidVariant("Side", it) } }
            error.reason shouldBe SolanaDecodeError.Reason.INVALID_VARIANT
            error.offset shouldBe 0
        }
    }

    test("the unit codec reads and writes nothing") {
        SolanaCodec.UNIT.encode(Unit) shouldBe ByteArray(0)
        SolanaCodec.UNIT.decode(ByteArray(0)).unwrap() shouldBe Unit
        SolanaCodec.UNIT.malformed("00").reason shouldBe SolanaDecodeError.Reason.TRAILING_DATA
    }

    test("raw writes grow the buffer") {
        val address = SolanaAddress(ByteArray(32) { 7 })
        val bytes = SolanaBytes.fromHex("aa".repeat(20))

        // each write starts past the buffer's capacity, so must grow it
        fun grown(write: BorshWriter.() -> Unit): ByteArray = BorshWriter(8).apply { writeU64(bigIntegerOf(1)) }.apply(write).toByteArray()
        val prefix = "0100000000000000"
        grown { writeRaw(ByteArray(40) { 1 }) } shouldBe hex(prefix + "01".repeat(40))
        grown { writeAddress(address) } shouldBe hex(prefix + "07".repeat(32))
        grown { writeFixedBytes(bytes, 20) } shouldBe hex(prefix + "aa".repeat(20))
        grown { writeBytes(bytes) } shouldBe hex(prefix + "14000000" + "aa".repeat(20))
        grown { writeString("x".repeat(100)) } shouldBe hex(prefix + "64000000" + "78".repeat(100))
    }

    test("writer returns independent arrays") {
        val writer = BorshWriter(8)
        writer.writeI64(1)
        val first = writer.toByteArray()
        writer.writeU8(2)
        first shouldBe hex("0100000000000000")
        writer.toByteArray() shouldBe hex("0100000000000000 02")
    }
})
