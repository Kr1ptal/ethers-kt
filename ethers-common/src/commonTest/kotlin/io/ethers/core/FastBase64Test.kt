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
    for (code in 0..255) add(code.toChar())
    for (c in "$ALPHABET=") {
        for (high in listOf(0x100, 0x1000, 0xff00)) add((c.code + high).toChar())
    }
    addAll(listOf('\ud800', '\udfff', 'Ａ', '�', '￿'))
}

/**
 * 3-byte groups holding every pair of adjacent bytes: the first 2^16 run through every value of the first two bytes
 * and the next 2^16 through every value of the last two, with the remaining byte random. So each 12-bit half of a
 * group, which is two base64 characters, takes every value beside many different others, in 1/128 the size of all
 * 2^24 groups, whose stdlib round trips take most of a minute in a Kotlin/Native debug binary.
 */
private val ADJACENT_BYTE_PAIRS = ByteArray(6 shl 16).also { bytes ->
    val random = Random(24)
    for (pair in 0 until (1 shl 16)) {
        val first = 3 * pair
        bytes[first] = (pair shr 8).toByte()
        bytes[first + 1] = pair.toByte()
        bytes[first + 2] = random.nextInt().toByte()

        val last = 3 * ((1 shl 16) + pair)
        bytes[last] = random.nextInt().toByte()
        bytes[last + 1] = (pair shr 8).toByte()
        bytes[last + 2] = pair.toByte()
    }
}

/** Every string of [length] characters from [symbols], passed to [block] in turn. */
private fun eachString(symbols: String, length: Int, block: (String) -> Unit) {
    fun each(prefix: String) {
        if (prefix.length == length) {
            block(prefix)
            return
        }
        for (c in symbols) each(prefix + c)
    }
    each("")
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

    test("decodes every pair of adjacent bytes in a 3-byte group") {
        FastBase64.decode(Base64.encode(ADJACENT_BYTE_PAIRS)) shouldBe ADJACENT_BYTE_PAIRS
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

    test("every string of up to 4 characters from the alphabet edges, padding and invalid characters, and of 5 and 6 from a few of them, matches the stdlib") {
        // the first and last of the alphabet, values with low bits set or clear, padding, and characters outside it
        for (length in 0..4) eachString("AQg/=-\n", length, ::shouldMatchReference)
        // lengths that are not a multiple of 4 are rejected whatever their characters, so fewer of them will do
        for (length in 5..6) eachString("A/=-", length, ::shouldMatchReference)
    }

    test("every string of 8 characters with padding anywhere in one quantum matches the stdlib, beside a full, a padded and an invalid quantum") {
        // the other quantum is either decoded before this one or is the last, so each is tried on both sides
        val others = listOf("AQ/A", "AQ==", "A/=A")
        eachString("AQ/=", 4) { quantum ->
            for (other in others) {
                shouldMatchReference(quantum + other)
                shouldMatchReference(other + quantum)
            }
        }
    }

    test("randomly corrupted input matches the stdlib") {
        val random = Random(46)
        val noise = "$ALPHABET= \n\r\t-_.\u0000ÿĀŁＡ"
        repeat(5_000) {
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

        test("encodes every pair of adjacent bytes in a 3-byte group, and every 1- and 2-byte tail, as the stdlib does") {
            FastBase64.encode(ADJACENT_BYTE_PAIRS) shouldBe Base64.encode(ADJACENT_BYTE_PAIRS)
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
