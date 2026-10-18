package io.ethers.core

import io.ethers.core.bigInteger
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotStartWith
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

class FastHexTest : FunSpec({
    test("encode/decode as string without prefix") {
        Arb.byteArray(Arb.int(0..512), Arb.byte()).checkAll { bytes ->
            val hex = FastHex.encodeWithoutPrefix(bytes)
            val decoded = FastHex.decode(hex)

            hex shouldBe bytes.toHexString()
            decoded shouldBe bytes
        }
    }

    test("encode/decode as string with 0x prefix") {
        Arb.byteArray(Arb.int(0..512), Arb.byte()).checkAll { bytes ->
            val hex = FastHex.encodeWithPrefix(bytes)
            val decoded = FastHex.decode(hex)

            hex shouldBe "0x${bytes.toHexString()}"
            decoded shouldBe bytes
        }
    }

    context("encode numerical values") {
        test("integer") {
            Arb.int(min = 0).checkAll { value ->
                val hex = FastHex.encodeWithPrefix(value)

                hex shouldBe ("0x" + value.toString(16))

                if (value == 0) {
                    hex shouldBe "0x0"
                } else {
                    hex shouldNotStartWith "0x0"
                }
            }
        }
        test("long") {
            Arb.long(min = 0).checkAll { value ->
                val hex = FastHex.encodeWithPrefix(value)

                hex shouldBe ("0x" + value.toString(16))

                if (value == 0L) {
                    hex shouldBe "0x0"
                } else {
                    hex shouldNotStartWith "0x0"
                }
            }
        }
        test("big integer") {
            Arb.bigInteger(80).checkAll { value ->
                val hex = FastHex.encodeWithPrefix(value)

                hex shouldBe ("0x" + value.toString(16))

                if (value == bigIntegerOf(0)) {
                    hex shouldBe "0x0"
                } else {
                    hex shouldNotStartWith "0x0"
                }
            }
        }
    }

    context("encode/decode as bytes") {
        test("without prefix") {
            Arb.byteArray(Arb.int(0..512), Arb.byte()).checkAll { bytes ->
                val hex = FastHex.encodeAsBytes(bytes)
                val decoded = FastHex.decode(hex)
                val decodedUnsafe = FastHex.decodeUnsafe(hex)

                hex shouldBe bytes.toHexString().encodeToByteArray()
                decoded shouldBe bytes
                decodedUnsafe shouldBe bytes
            }
        }

        test("with 0x prefix") {
            Arb.byteArray(Arb.int(0..512), Arb.byte()).checkAll { bytes ->
                val hex = "0x".encodeToByteArray() + FastHex.encodeAsBytes(bytes)
                val decoded = FastHex.decode(hex)
                val decodedUnsafe = FastHex.decodeUnsafe(hex)

                hex shouldBe "0x${bytes.toHexString()}".encodeToByteArray()
                decoded shouldBe bytes
                decodedUnsafe shouldBe bytes
            }
        }

        test("odd length hex (without leading 0)") {
            Arb.byteArray(Arb.int(0..512), Arb.byte()).checkAll { bytes ->
                // Append 0x0f without leading zero
                val hex = byteArrayOf('f'.code.toByte()) + FastHex.encodeAsBytes(bytes)

                val decoded = FastHex.decode(hex)
                val decodedUnsafe = FastHex.decodeUnsafe(hex)

                val expectedResult = (byteArrayOf(0x0f) + bytes)
                decoded shouldBe expectedResult
                decodedUnsafe shouldBe expectedResult
            }
        }
    }

    context("encode/decode as chars") {
        test("without prefix") {
            Arb.byteArray(Arb.int(0..512), Arb.byte()).checkAll { bytes ->
                val hex = FastHex.encodeWithoutPrefix(bytes).toCharArray()
                val decoded = FastHex.decode(hex)
                val decodedUnsafe = FastHex.decodeUnsafe(hex)

                hex shouldBe bytes.toHexString().toCharArray()
                decoded shouldBe bytes
                decodedUnsafe shouldBe decoded
            }
        }

        test("with 0x prefix") {
            Arb.byteArray(Arb.int(0..512), Arb.byte()).checkAll { bytes ->
                val hex = FastHex.encodeWithPrefix(bytes).toCharArray()
                val decoded = FastHex.decode(hex)
                val decodedUnsafe = FastHex.decodeUnsafe(hex)

                hex shouldBe "0x${bytes.toHexString()}".toCharArray()
                decoded shouldBe bytes
                decodedUnsafe shouldBe decoded
            }
        }

        test("odd length hex (without leading 0)") {
            Arb.byteArray(Arb.int(0..512), Arb.byte()).checkAll { bytes ->
                // Append 0x0f without leading zero
                val hex = charArrayOf('f') + FastHex.encodeWithoutPrefix(bytes).toCharArray()
                val decoded = FastHex.decode(hex)
                val decodedUnsafe = FastHex.decodeUnsafe(hex)

                val expectedResult = (byteArrayOf(0x0f) + bytes)
                decoded shouldBe expectedResult
            }
        }
    }

    context("invalid hex validation should fail") {
        listOf(
            "",
            " ",
            "0x///",
            "0xabcdefg",
            "zzhhkkllmm",
        ).forEach { hex ->
            val testName = hex.ifEmpty { "empty string" }.ifBlank { "blank string" }
            test(testName) { FastHex.isValidHex(hex) shouldBe false }
        }
    }

    context("valid hex validation should pass") {
        listOf(
            "0",
            "0x",
            "0x0",
            "abcdef01924354541243",
            "0xabcdef01924354541243",
            "0X",
            "0Xabcdef",
            "0XABCDEF",
            "ABCDEF0123456789",
        ).forEach { hex ->
            test(hex) { FastHex.isValidHex(hex) shouldBe true }
        }
    }

    context("decode should throw on invalid hex characters") {
        listOf(
            "0xabcdefg", // 'g' is invalid
            "0x///", // '/' is invalid
            "zzhhkkllmm", // all are invalid
            "0xabcdez", // 'z' is invalid
            "abcde@", // '@' is invalid
        ).forEach { invalidHex ->
            test("decode('$invalidHex') should throw IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    FastHex.decode(invalidHex)
                }
            }
        }

        test("decode with ByteArray should throw on invalid characters") {
            shouldThrow<IllegalArgumentException> {
                FastHex.decode("0xabcdefg".encodeToByteArray())
            }
        }

        test("decode with CharArray should throw on invalid characters") {
            shouldThrow<IllegalArgumentException> {
                FastHex.decode("0xabcdefg".toCharArray())
            }
        }

        test("decode odd-length invalid hex should throw") {
            shouldThrow<IllegalArgumentException> {
                FastHex.decode("g") // Invalid single character
            }
        }

        // Regression: these indexed the one-byte decode tables out of bounds instead of rejecting the input
        test("decode rejects chars above one byte as IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> { FastHex.decode("\u01010") }
            shouldThrow<IllegalArgumentException> { FastHex.decode("0\u0101") }
            shouldThrow<IllegalArgumentException> { FastHex.decode("\u0130") } // odd length, maps to '0' if truncated to a byte
            shouldThrow<IllegalArgumentException> { FastHex.decode("\u01010".toCharArray()) }
        }

        test("decode rejects bytes above 0x7f as IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> { FastHex.decode(byteArrayOf(0x80.toByte(), 0x30)) }
            shouldThrow<IllegalArgumentException> { FastHex.decode(byteArrayOf(0xb0.toByte())) }
        }

        test("decodeUnsafe never throws for chars or bytes outside the tables") {
            FastHex.decodeUnsafe("\u01010").size shouldBe 1
            FastHex.decodeUnsafe(byteArrayOf(0x80.toByte(), 0x30)).size shouldBe 1
        }
    }

    context("ranges outside the input throw IndexOutOfBoundsException") {
        val hex = "abcd"
        withData(
            nameFn = { (offset, length) -> "offset $offset, length $length" },
            -1 to 2,
            0 to -1,
            1 to 4,
            5 to 0,
            Int.MAX_VALUE to 2,
        ) { (offset, length) ->
            shouldThrow<IndexOutOfBoundsException> { FastHex.decode(hex, offset, length) }
            shouldThrow<IndexOutOfBoundsException> { FastHex.decode(hex.encodeToByteArray(), offset, length) }
            shouldThrow<IndexOutOfBoundsException> { FastHex.decode(hex.toCharArray(), offset, length) }
            shouldThrow<IndexOutOfBoundsException> { FastHex.decodeUnsafe(hex, offset, length) }
            shouldThrow<IndexOutOfBoundsException> { FastHex.encodeWithPrefix(hex.encodeToByteArray(), offset, length) }
            shouldThrow<IndexOutOfBoundsException> { FastHex.encodeWithoutPrefix(hex.encodeToByteArray(), offset, length) }
        }

        test("the empty range at the end is valid") {
            FastHex.decode(hex, 4, 0).size shouldBe 0
            FastHex.encodeWithoutPrefix(hex.encodeToByteArray(), 4, 0) shouldBe ""
        }
    }

    context("encode with offset and length") {
        test("encodeWithPrefix with offset and length encodes subrange") {
            val buffer = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
            val hex = FastHex.encodeWithPrefix(buffer, 1, 3)
            hex shouldBe "0x020304"
        }

        test("encodeWithoutPrefix with offset and length encodes subrange") {
            val buffer = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte())
            val hex = FastHex.encodeWithoutPrefix(buffer, 1, 2)
            hex shouldBe "bbcc"
        }

        test("encodeAsBytes with prefix") {
            val buffer = byteArrayOf(0x0A, 0x0B)
            val result = FastHex.encodeAsBytes(buffer, withPrefix = true)
            result.decodeToString() shouldBe "0x0a0b"
        }

        test("encodeAsBytes without prefix") {
            val buffer = byteArrayOf(0x0A, 0x0B)
            val result = FastHex.encodeAsBytes(buffer, withPrefix = false)
            result.decodeToString() shouldBe "0a0b"
        }
    }

    context("decode with offset and length") {
        test("CharSequence with explicit offset and length") {
            val hex = "##0xaabbccdd##"
            // decode from offset 4 (after "##0x"), length 8 ("aabbccdd")
            val decoded = FastHex.decode(hex, offset = 4, length = 8)
            decoded shouldBe byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte())
        }

        test("ByteArray with explicit offset and length") {
            val hex = "PPaabbccddPP".encodeToByteArray()
            val decoded = FastHex.decode(hex, offset = 2, length = 8)
            decoded shouldBe byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte())
        }

        test("CharArray with explicit offset and length") {
            val hex = "PPaabbccddPP".toCharArray()
            val decoded = FastHex.decode(hex, offset = 2, length = 8)
            decoded shouldBe byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte())
        }

        test("decodeUnsafe CharSequence with explicit offset and length") {
            val hex = "##aabbccdd##"
            val decoded = FastHex.decodeUnsafe(hex, offset = 2, length = 8)
            decoded shouldBe byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte())
        }

        test("decodeUnsafe ByteArray with explicit offset and length") {
            val hex = "PPaabbccddPP".encodeToByteArray()
            val decoded = FastHex.decodeUnsafe(hex, offset = 2, length = 8)
            decoded shouldBe byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte())
        }

        test("decodeUnsafe CharArray with explicit offset and length") {
            val hex = "PPaabbccddPP".toCharArray()
            val decoded = FastHex.decodeUnsafe(hex, offset = 2, length = 8)
            decoded shouldBe byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte())
        }
    }

    context("uppercase hex decoding") {
        // Tests decode and decodeUnsafe across all input types (CharSequence, ByteArray, CharArray).
        // Note: ByteArray decode only handles lowercase '0x' prefix, not '0X'.
        withData(
            nameFn = { it.first },
            "0xABCDEF" to byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte()),
            "0xAaBbCcDdEeFf" to byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte()),
            "0Xaabb" to byteArrayOf(0xAA.toByte(), 0xBB.toByte()),
        ) { (hex, expected) ->
            FastHex.decode(hex) shouldBe expected
            FastHex.decode(hex.toCharArray()) shouldBe expected
            FastHex.decodeUnsafe(hex) shouldBe expected
            FastHex.decodeUnsafe(hex.toCharArray()) shouldBe expected

            if (!hex.startsWith("0X")) {
                FastHex.decode(hex.encodeToByteArray()) shouldBe expected
                FastHex.decodeUnsafe(hex.encodeToByteArray()) shouldBe expected
            }
        }
    }

    context("single-nibble decode") {
        withData(
            nameFn = { it.first },
            "f" to byteArrayOf(0x0f),
            "A" to byteArrayOf(0x0A),
            "0xf" to byteArrayOf(0x0f),
        ) { (hex, expected) ->
            FastHex.decode(hex) shouldBe expected
            FastHex.decode(hex.encodeToByteArray()) shouldBe expected
            FastHex.decode(hex.toCharArray()) shouldBe expected
        }
    }

    context("empty and prefix-only inputs") {
        test("decode empty string returns empty array") {
            FastHex.decode("") shouldBe byteArrayOf()
        }

        test("decode '0x' returns empty array") {
            FastHex.decode("0x") shouldBe byteArrayOf()
        }

        test("decode '0X' returns empty array") {
            FastHex.decode("0X") shouldBe byteArrayOf()
        }

        test("decode empty ByteArray returns empty array") {
            FastHex.decode(byteArrayOf()) shouldBe byteArrayOf()
        }

        test("decode '0x' ByteArray returns empty array") {
            FastHex.decode("0x".encodeToByteArray()) shouldBe byteArrayOf()
        }

        test("decode empty CharArray returns empty array") {
            FastHex.decode(charArrayOf()) shouldBe byteArrayOf()
        }

        test("decode '0x' CharArray returns empty array") {
            FastHex.decode("0x".toCharArray()) shouldBe byteArrayOf()
        }

        test("decode '0X' CharArray returns empty array") {
            FastHex.decode("0X".toCharArray()) shouldBe byteArrayOf()
        }
    }

    context("encode empty and zero-length subranges") {
        test("encodeWithPrefix") {
            FastHex.encodeWithPrefix(ByteArray(0)) shouldBe "0x"
            FastHex.encodeWithPrefix(byteArrayOf(0x01, 0x02, 0x03), 1, 0) shouldBe "0x"
        }

        test("encodeWithoutPrefix") {
            FastHex.encodeWithoutPrefix(ByteArray(0)) shouldBe ""
            FastHex.encodeWithoutPrefix(byteArrayOf(0x01, 0x02, 0x03), 1, 0) shouldBe ""
        }

        test("encodeAsBytes empty ByteArray") {
            FastHex.encodeAsBytes(ByteArray(0)) shouldBe byteArrayOf()
        }
    }

    context("decodeUnsafe should not throw on invalid hex characters, but default to 0xff") {
        listOf(
            "0xabcdefg" to "0xabcdeff", // 'g' is invalid
            "0x///" to "0x0fff", // '/' is invalid
            "zzhhkkllmm" to "0xffffffffff", // all are invalid
            "0xabcdez" to "0xabcdef", // 'z' is invalid
            "abcde@" to "abcdef", // '@' is invalid
        ).forEach { (invalidHex, expected) ->
            test("decodeUnsafe('$invalidHex') should replace invalid chars with '0xff'") {
                val unsafeString = FastHex.decodeUnsafe(invalidHex)
                val unsafeBytes = FastHex.decodeUnsafe(invalidHex.encodeToByteArray())
                val unsafeChars = FastHex.decodeUnsafe(invalidHex.toCharArray())

                unsafeString shouldBe FastHex.decode(expected)
                unsafeBytes shouldBe FastHex.decode(expected)
                unsafeChars shouldBe FastHex.decode(expected)
            }
        }
    }
})
