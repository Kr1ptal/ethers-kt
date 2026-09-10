package io.ethers.solana

import io.ethers.core.FastHex
import io.ethers.solana.serialization.SolanaMessageDecoder
import io.ethers.solana.serialization.SolanaMessageEncoder
import io.github.artificialpb.bignum.BigInteger
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class SolanaMessageCodecTest : FunSpec({
    test("shortvec boundary values use canonical compact-u16 bytes") {
        val fixtures = mapOf(
            0 to "00",
            1 to "01",
            127 to "7f",
            128 to "8001",
            255 to "ff01",
            16383 to "ff7f",
            16384 to "808001",
            65535 to "ffff03",
        )
        for ((value, hex) in fixtures) {
            val expected = FastHex.decode(hex)
            SolanaMessageEncoder().writeShortVecLength(value).toByteArray() shouldBe expected
            val decoder = SolanaMessageDecoder(expected)
            decoder.readShortVecLength() shouldBe value
            decoder.requireDone()
        }
    }

    test("every shortvec value roundtrips in a growing buffer") {
        val encoder = SolanaMessageEncoder()
        for (value in 0..65535) encoder.writeShortVecLength(value)
        val decoder = SolanaMessageDecoder(encoder.toByteArray())
        for (value in 0..65535) decoder.readShortVecLength() shouldBe value
        decoder.requireDone()
    }

    test("shortvec rejects truncated, overlong and overflowing encodings") {
        // malformed input is recorded rather than thrown, so decoding untrusted bytes costs no stack trace
        for (hex in listOf("", "80", "8080", "8000", "8100", "808000", "ffff00", "808004", "ffff04", "808080", "ffffff", "80808000")) {
            val decoder = SolanaMessageDecoder(FastHex.decode(hex))
            decoder.readShortVecLength() shouldBe 0
            decoder.failed shouldBe true
            decoder.error shouldNotBe null
        }
        val encoder = SolanaMessageEncoder().writeByte(42)
        for (value in listOf(-1, 65536, Int.MAX_VALUE)) {
            shouldThrow<IllegalArgumentException> { encoder.writeShortVecLength(value) }
        }
        for (value in listOf(-1, 256, Int.MAX_VALUE)) {
            shouldThrow<IllegalArgumentException> { encoder.writeByte(value) }
        }
        encoder.toByteArray() shouldBe byteArrayOf(42)
    }

    test("byte and bulk writes preserve position across growth and return independent snapshots") {
        val prefix = ByteArray(128) { it.toByte() }
        val payload = ByteArray(1000) { (it % 256).toByte() }
        val encoder = SolanaMessageEncoder().writeBytes(prefix)
        val first = encoder.toByteArray()
        encoder.writeByte(255).writeBytes(payload).writeBytes(byteArrayOf()).writeByte(128)
        val expected = prefix + byteArrayOf(255.toByte()) + payload + byteArrayOf(128.toByte())
        first shouldBe prefix
        first[0] = 99
        prefix.fill(0)
        payload.fill(0)
        encoder.toByteArray() shouldBe expected
        encoder.toByteArray() shouldBe expected
        encoder.writeByte(1).toByteArray() shouldBe expected + byteArrayOf(1)
    }

    test("decoder tracks remaining bytes, copies results and validates bounds before reading") {
        val source = byteArrayOf(0, 127, 128.toByte(), 255.toByte(), 42)
        val decoder = SolanaMessageDecoder(source)
        decoder.remaining shouldBe 5
        decoder.readByte() shouldBe 0
        val copied = decoder.readBytes(2)
        copied shouldBe byteArrayOf(127, 128.toByte())
        source[1] = 0
        copied shouldBe byteArrayOf(127, 128.toByte())
        decoder.remaining shouldBe 2
        decoder.requireDone()
        decoder.failed shouldBe true

        // the first failure sticks: later reads reveal nothing and consume nothing
        val bounded = SolanaMessageDecoder(byteArrayOf(1, 2))
        for (count in listOf(-1, 3, Int.MAX_VALUE)) {
            val fresh = SolanaMessageDecoder(byteArrayOf(1, 2))
            fresh.readBytes(count).size shouldBe if (count in 0..SolanaMessageDecoder.MAX_ZERO_FILL) count else 0
            fresh.failed shouldBe true
            fresh.remaining shouldBe 2
        }
        bounded.readByte() shouldBe 1
        bounded.readByte() shouldBe 2
        bounded.readBytes(0) shouldBe byteArrayOf()
        bounded.requireDone()
        bounded.failed shouldBe false
        bounded.readByte() shouldBe 0
        bounded.failed shouldBe true
        bounded.error shouldBe "Truncated binary payload"
        SolanaMessageDecoder(byteArrayOf()).requireDone()
    }

    test("unsigned instruction values are little endian and preserve the full u64 range") {
        val decoder = SolanaMessageDecoder(FastHex.decode("01020304ffffffff0102030405060708ffffffffffffffff"))
        decoder.readUnsignedLittleEndian(4) shouldBe BigInteger("67305985")
        decoder.readUnsignedLittleEndian(4) shouldBe BigInteger("4294967295")
        decoder.readUnsignedLittleEndian(8) shouldBe BigInteger("578437695752307201")
        decoder.readUnsignedLittleEndian(8) shouldBe BigInteger("18446744073709551615")
        decoder.requireDone()
        decoder.readUnsignedLittleEndian(4) shouldBe BigInteger("0")
        decoder.failed shouldBe true
    }
})
