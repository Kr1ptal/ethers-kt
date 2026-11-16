package io.ethers.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import kotlin.io.encoding.Base64
import kotlin.random.Random

private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

/**
 * Characters to substitute into otherwise valid input: the alphabet and padding, ASCII and Latin-1 neighbours of
 * the alphabet, URL-safe and whitespace characters, and chars above 255 whose low byte is an alphabet or padding
 * character, which a decoder that masks or truncates the char code would accept.
 */
private val PROBES: List<Char> = buildList {
    for (code in 0..511) add(code.toChar())
    for (c in "$ALPHABET=") {
        for (high in listOf(0x100, 0x1000, 0xff00)) add((c.code + high).toChar())
    }
    addAll(listOf('\ud800', '\udfff', 'Ａ', '�', '￿'))
}

/** The stdlib decoder this one replaces is the oracle: same bytes for valid input, the same exception otherwise. */
private fun shouldMatchReference(input: CharSequence, start: Int = 0, end: Int = input.length) {
    val expected = runCatching { Base64.decode(input, start, end) }
    val actual = runCatching { FastBase64.decode(input, start, end) }
    withClue("\"$input\" [$start, $end)") {
        val error = expected.exceptionOrNull()
        if (error == null) {
            actual.exceptionOrNull() shouldBe null
            actual.getOrThrow() shouldBe expected.getOrThrow()
        } else {
            (actual.exceptionOrNull()?.let { it::class }) shouldBe error::class
        }
    }
}

class FastBase64Test : FunSpec({
    context("RFC 4648 vectors") {
        withData(
            mapOf(
                "empty" to ("" to ""),
                "f" to ("f" to "Zg=="),
                "fo" to ("fo" to "Zm8="),
                "foo" to ("foo" to "Zm9v"),
                "foob" to ("foob" to "Zm9vYg=="),
                "fooba" to ("fooba" to "Zm9vYmE="),
                "foobar" to ("foobar" to "Zm9vYmFy"),
            ),
        ) { (plain, encoded) ->
            FastBase64.decode(encoded) shouldBe plain.encodeToByteArray()
        }
    }

    test("decodes every length up to 300 bytes and sizes up to 64 KiB, as the stdlib encodes them") {
        val random = Random(64)
        for (size in (0..300) + listOf(1023, 1024, 1025, 10_136, 65_535, 65_536)) {
            for (bytes in listOf(random.nextBytes(size), ByteArray(size), ByteArray(size) { -1 })) {
                val encoded = Base64.encode(bytes)
                FastBase64.decode(encoded) shouldBe bytes
                if (size <= 300) shouldMatchReference(encoded)
            }
        }
    }

    test("decodes every 3-byte group") {
        // all 2^24 groups, 2^16 groups per call
        val bytes = ByteArray(3 shl 16)
        for (high in 0 until 256) {
            for (low in 0 until (1 shl 16)) {
                bytes[3 * low] = high.toByte()
                bytes[3 * low + 1] = (low shr 8).toByte()
                bytes[3 * low + 2] = low.toByte()
            }
            FastBase64.decode(Base64.encode(bytes)) shouldBe bytes
        }
    }

    test("every probe character at every position matches the stdlib, around each kind of padding") {
        val valid = listOf("TWFu", "TWE=", "TQ==", "QUJDTWFu", "QUJDTWE=", "QUJDTQ==", "QUJDREVGR0hJ")
        for (base in valid) {
            for (position in base.indices) {
                for (probe in PROBES) {
                    shouldMatchReference(StringBuilder(base).apply { set(position, probe) })
                }
            }
        }
    }

    test("every string of up to 6 characters from the alphabet edges, padding and invalid characters matches the stdlib") {
        // the first and last of the alphabet, values with low bits set or clear, padding, and characters outside it
        val symbols = "AQg/=-\n"
        fun each(length: Int, prefix: String) {
            if (prefix.length == length) {
                shouldMatchReference(prefix)
                return
            }
            for (c in symbols) each(length, prefix + c)
        }
        for (length in 0..6) each(length, "")
    }

    test("every string of 7 and 8 characters with padding anywhere matches the stdlib") {
        val symbols = "AQ/="
        fun each(length: Int, prefix: String) {
            if (prefix.length == length) {
                shouldMatchReference(prefix)
                return
            }
            for (c in symbols) each(length, prefix + c)
        }
        each(7, "")
        each(8, "")
    }

    test("randomly corrupted input matches the stdlib") {
        val random = Random(46)
        val noise = "$ALPHABET= \n\r\t-_.\u0000ÿĀŁＡ"
        repeat(20_000) {
            val encoded = StringBuilder(Base64.encode(random.nextBytes(random.nextInt(0, 40))))
            repeat(random.nextInt(1, 4)) {
                val position = random.nextInt(0, encoded.length + 1)
                val c = noise[random.nextInt(noise.length)]
                when (random.nextInt(4)) {
                    0 -> if (position < encoded.length) encoded.set(position, c)
                    1 -> encoded.insert(position, c)
                    2 -> if (position < encoded.length) encoded.deleteAt(position)
                    else -> encoded.setLength(position)
                }
            }
            shouldMatchReference(encoded)
        }
    }

    test("decodes a range of a longer sequence, ignoring the characters around it") {
        val random = Random(7)
        for (size in 0..40) {
            val bytes = random.nextBytes(size)
            val encoded = Base64.encode(bytes)
            // characters around the range that would fail if decoded
            val text = "-=*$encoded=*-"
            FastBase64.decode(text, 3, 3 + encoded.length) shouldBe bytes
            shouldMatchReference(text, 3, 3 + encoded.length)
        }
        FastBase64.decode("abc", 1, 1) shouldBe ByteArray(0)
    }

    test("a range outside the input throws IndexOutOfBoundsException and a reversed one IllegalArgumentException, as the stdlib does") {
        for ((start, end) in listOf(-1 to 4, 0 to 5, 5 to 5, -1 to -1, -1 to 9)) {
            shouldThrow<IndexOutOfBoundsException> { FastBase64.decode("Zm9v", start, end) }
            shouldMatchReference("Zm9v", start, end)
        }
        for ((start, end) in listOf(4 to 0, 2 to 1, 5 to 2)) {
            shouldThrow<IllegalArgumentException> { FastBase64.decode("Zm9v", start, end) }.message shouldBe "Start $start is after end $end"
            shouldMatchReference("Zm9v", start, end)
        }
    }

    context("invalid input names the problem and where it is") {
        withData(
            mapOf(
                "illegal character" to ("Zm-v" to "Illegal base64 character '-' at 2"),
                "illegal character in the last quantum" to ("Zm9vZm9 " to "Illegal base64 character ' ' at 7"),
                "padding inside the input" to ("Zg==Zm9v" to "Illegal base64 character '=' at 2"),
                "missing padding" to ("Zm9vYg" to "Input of 6 characters is not padded to a multiple of 4"),
                "a char above 255 whose low byte is in the alphabet" to ("Zm9Ŷ" to "Illegal base64 character 'Ŷ' at 3"),
                "non-zero bits before one padding character" to ("Zm9=" to "Non-zero pad bits before the padding at 3"),
                "non-zero bits before two padding characters" to ("Zh==" to "Non-zero pad bits before the padding at 2"),
            ),
        ) { (input, message) ->
            shouldThrow<IllegalArgumentException> { FastBase64.decode(input) }.message shouldBe message
        }
    }

    context("encode") {
        context("RFC 4648 vectors") {
            withData(
                mapOf(
                    "empty" to ("" to ""),
                    "f" to ("f" to "Zg=="),
                    "fo" to ("fo" to "Zm8="),
                    "foo" to ("foo" to "Zm9v"),
                    "foob" to ("foob" to "Zm9vYg=="),
                    "fooba" to ("fooba" to "Zm9vYmE="),
                    "foobar" to ("foobar" to "Zm9vYmFy"),
                ),
            ) { (plain, encoded) ->
                FastBase64.encode(plain.encodeToByteArray()) shouldBe encoded
            }
        }

        test("encodes every length up to 300 bytes and sizes up to 64 KiB as the stdlib does, and round-trips") {
            val random = Random(65)
            for (size in (0..300) + listOf(1023, 1024, 1025, 10_136, 65_535, 65_536)) {
                for (bytes in listOf(random.nextBytes(size), ByteArray(size), ByteArray(size) { -1 })) {
                    val encoded = FastBase64.encode(bytes)
                    encoded shouldBe Base64.encode(bytes)
                    FastBase64.decode(encoded) shouldBe bytes
                }
            }
        }

        test("encodes every 3-byte group, and every 1- and 2-byte tail, as the stdlib does") {
            // all 2^24 groups, 2^16 groups per call; odd high bytes leave a 1- or 2-byte tail behind them
            val bytes = ByteArray(3 shl 16)
            for (high in 0 until 256) {
                for (low in 0 until (1 shl 16)) {
                    bytes[3 * low] = high.toByte()
                    bytes[3 * low + 1] = (low shr 8).toByte()
                    bytes[3 * low + 2] = low.toByte()
                }
                FastBase64.encode(bytes) shouldBe Base64.encode(bytes)
            }
            for (first in 0 until 256) {
                FastBase64.encode(byteArrayOf(first.toByte())) shouldBe Base64.encode(byteArrayOf(first.toByte()))
                for (second in 0 until 256) {
                    val tail = byteArrayOf(first.toByte(), second.toByte())
                    FastBase64.encode(tail) shouldBe Base64.encode(tail)
                }
            }
        }

        test("encodes a range of a longer array, ignoring the bytes around it, as the stdlib does") {
            val random = Random(8)
            for (size in 0..40) {
                val bytes = random.nextBytes(size + 10)
                for (start in 0..5) {
                    val end = start + size
                    FastBase64.encode(bytes, start, end) shouldBe Base64.encode(bytes, start, end)
                    FastBase64.encode(bytes, start, end) shouldBe FastBase64.encode(bytes.copyOfRange(start, end))
                }
            }
            FastBase64.encode(byteArrayOf(1, 2, 3), 1, 1) shouldBe ""
        }

        test("a range outside the input throws IndexOutOfBoundsException and a reversed one IllegalArgumentException, as the stdlib does") {
            val bytes = "foo".encodeToByteArray()
            for ((start, end) in listOf(-1 to 3, 0 to 4, 4 to 4, -1 to -1, -1 to 9)) {
                shouldThrow<IndexOutOfBoundsException> { FastBase64.encode(bytes, start, end) }
                shouldThrow<IndexOutOfBoundsException> { Base64.encode(bytes, start, end) }
            }
            for ((start, end) in listOf(3 to 0, 2 to 1)) {
                shouldThrow<IllegalArgumentException> { FastBase64.encode(bytes, start, end) }.message shouldBe "Start $start is after end $end"
                shouldThrow<IllegalArgumentException> { Base64.encode(bytes, start, end) }
            }
        }
    }
})
