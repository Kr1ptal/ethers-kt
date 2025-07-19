package io.ethers.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotStartWith
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bigInt
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import java.math.BigInteger

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
            Arb.bigInt(80).checkAll { value ->
                val hex = FastHex.encodeWithPrefix(value)

                hex shouldBe ("0x" + value.toString(16))

                if (value == BigInteger.ZERO) {
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

                hex shouldBe bytes.toHexString().toByteArray()
                decoded shouldBe bytes
            }
        }

        test("with 0x prefix") {
            Arb.byteArray(Arb.int(0..512), Arb.byte()).checkAll { bytes ->
                val hex = "0x".toByteArray() + FastHex.encodeAsBytes(bytes)
                val decoded = FastHex.decode(hex)

                hex shouldBe "0x${bytes.toHexString()}".toByteArray()
                decoded shouldBe bytes
            }
        }

        test("odd length hex (without leading 0)") {
            Arb.byteArray(Arb.int(0..512), Arb.byte()).checkAll { bytes ->
                var hex = FastHex.encodeAsBytes(bytes)
                hex = byteArrayOf('f'.code.toByte()) + hex // Append 0x0f without leading zero
                val decoded = FastHex.decode(hex)

                val expectedResult = (byteArrayOf(0x0f) + bytes)
                decoded shouldBe expectedResult
            }
        }
    }

    context("encode/decode as chars") {
        test("without prefix") {
            Arb.byteArray(Arb.int(0..512), Arb.byte()).checkAll { bytes ->
                val hex = FastHex.encodeWithoutPrefix(bytes).toCharArray()
                val decoded = FastHex.decode(hex)

                hex shouldBe bytes.toHexString().toCharArray()
                decoded shouldBe bytes
            }
        }

        test("with 0x prefix") {
            Arb.byteArray(Arb.int(0..512), Arb.byte()).checkAll { bytes ->
                val hex = FastHex.encodeWithPrefix(bytes).toCharArray()
                val decoded = FastHex.decode(hex)

                hex shouldBe "0x${bytes.toHexString()}".toCharArray()
                decoded shouldBe bytes
            }
        }

        test("odd length hex (without leading 0)") {
            Arb.byteArray(Arb.int(0..512), Arb.byte()).checkAll { bytes ->
                var hex = FastHex.encodeWithoutPrefix(bytes).toCharArray()
                hex = charArrayOf('f') + hex // Append 0x0f without leading zero
                val decoded = FastHex.decode(hex)

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
        ).forEach { hex ->
            test(hex) { FastHex.isValidHex(hex) shouldBe true }
        }
    }

    context("decode should throw on invalid hex characters") {
        listOf(
            "0xabcdefg", // 'g' is invalid
            "0x///", // '/' is invalid
            "zzhhkkllmm", // 'z' and 'h' are invalid
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
                FastHex.decode("0xabcdefg".toByteArray())
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
    }
})
