package io.ethers.abi

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class Utf8Test : FunSpec({
    data class Utf8Case(val label: String, val input: String, val expectedLength: Int)

    context("encodedLength") {
        withData(
            nameFn = { it.label },
            Utf8Case("empty string", "", 0),
            Utf8Case("pure ASCII", "hello", 5),
            Utf8Case("ASCII with spaces", "hello world", 11),
            Utf8Case("single ASCII char", "a", 1),
            // 2-byte chars (0x80-0x7FF)
            Utf8Case("2-byte: é (U+00E9)", "é", 2),
            Utf8Case("2-byte: ñ (U+00F1)", "ñ", 2),
            Utf8Case("2-byte: ü (U+00FC)", "ü", 2),
            Utf8Case("2-byte: Ω (U+03A9)", "Ω", 2),
            // 3-byte chars (0x800-0xFFFF)
            Utf8Case("3-byte: 中 (U+4E2D)", "中", 3),
            Utf8Case("3-byte: € (U+20AC)", "€", 3),
            Utf8Case("3-byte: ♠ (U+2660)", "♠", 3),
            // 4-byte chars (surrogate pairs)
            Utf8Case("4-byte: 𝄞 (U+1D11E)", "𝄞", 4),
            Utf8Case("4-byte: 😀 (U+1F600)", "😀", 4),
            // Mixed content
            Utf8Case("mixed ASCII + 2-byte", "café", 5),
            Utf8Case("mixed ASCII + 3-byte", "hi中", 5),
            Utf8Case("mixed all byte sizes", "a€𝄞", 8),
            Utf8Case("mixed 2-byte + 3-byte", "éé中", 7),
        ) { (_, input, expectedLength) ->
            Utf8.encodedLength(input) shouldBe expectedLength
        }

        test("matches toByteArray(UTF_8).size for all test cases") {
            val strings = listOf(
                "",
                "hello",
                "é", "ñ", "ü", "Ω",
                "中", "€", "♠",
                "𝄞", "😀",
                "café",
                "hi中",
                "a€𝄞",
                "éé中",
            )
            for (s in strings) {
                Utf8.encodedLength(s) shouldBe s.toByteArray(Charsets.UTF_8).size
            }
        }

        test("unpaired high surrogate throws IllegalArgumentException") {
            val highSurrogate = "\uD800"
            val exception = shouldThrow<IllegalArgumentException> {
                Utf8.encodedLength(highSurrogate)
            }
            exception.message shouldContain "Unpaired surrogate"
        }

        test("unpaired low surrogate at start of 3-byte range throws or counts correctly") {
            // A lone low surrogate (without preceding high surrogate)
            val lowSurrogate = "\uDC00"
            val exception = shouldThrow<IllegalArgumentException> {
                Utf8.encodedLength(lowSurrogate)
            }
            exception.message shouldContain "Unpaired surrogate"
        }

        test("high surrogate followed by non-surrogate throws") {
            // High surrogate followed by regular ASCII - not a valid pair
            val invalid = "\uD800A"
            val exception = shouldThrow<IllegalArgumentException> {
                Utf8.encodedLength(invalid)
            }
            exception.message shouldContain "Unpaired surrogate"
        }
    }
})
