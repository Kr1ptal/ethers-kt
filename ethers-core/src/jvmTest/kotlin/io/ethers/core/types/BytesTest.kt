package io.ethers.core.types

import io.ethers.core.Jackson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import java.math.BigInteger

class BytesTest : FunSpec({
    context("equals") {
        val hexString = "1234567890abcdef"
        val bytes = Bytes(hexString)

        test("empty Bytes equals empty CharSequence") {
            Bytes("").equals("") shouldBe true
        }

        context("other CharSequence") {
            withData(
                "0x1234567890abcdef" to true,
                "" to false,
                "1234af" to false,
            ) { (hex, result) ->
                bytes.equals(hex) shouldBe result
            }
        }

        context("odd length CharSequence fails") {
            withData(
                "1234567890abcde",
                "1234a",
            ) { hex ->
                shouldThrow<IllegalArgumentException> {
                    bytes.equals(hex)
                }
            }
        }

        context("other ByteArray") {
            withData(
                "1234567890abcdef" to true,
                "1234af" to false,
            ) { (hex, result) ->
                bytes.equals(BigInteger(hex, 16).toByteArray()) shouldBe result
            }
        }
    }

    context("contains") {
        val hexString = "1234567890abcdef"
        val bytes = Bytes(hexString)

        context("other Bytes") {
            withData(
                Bytes("") to true,
                Bytes("12") to true,
                Bytes("0x1234567890abcdef") to true,
                Bytes("567890") to true,
                Bytes("1234af") to false,
                Bytes("1234567890abcdef123456") to false,
            ) { (hex, result) ->
                bytes.contains(hex) shouldBe result
            }
        }

        context("other CharSequence") {
            withData(
                "" to true,
                "12" to true,
                "0x1234567890abcdef" to true,
                "567890" to true,
                "1234af" to false,
                "1234567890abcdef123456" to false,
            ) { (hex, result) ->
                bytes.contains(hex) shouldBe result
            }
        }

        context("odd length CharSequence fails") {
            withData(
                "1234567890abcde",
                "1234a",
            ) { hex ->
                shouldThrow<IllegalArgumentException> {
                    bytes.contains(hex)
                }
            }
        }

        context("other ByteArray") {
            withData(
                "12" to true,
                "1234567890abcdef" to true,
                "567890" to true,
                "1234af" to false,
                "1234567890abcdef123456" to false,
            ) { (hex, result) ->
                bytes.contains(BigInteger(hex, 16).toByteArray()) shouldBe result
            }
        }

        context("odd length CharSequence fails") {
            withData(
                "1",
                "0x1234567890abcde",
                "56789",
                "1234a",
                "1234567890abcdef12345",
            ) { hex ->
                shouldThrow<IllegalArgumentException> {
                    bytes.contains(hex)
                }
            }
        }
    }

    context("startsWith") {
        val hexString = "1234567890abcdef"
        val bytes = Bytes(hexString)

        context("other Bytes") {
            withData(
                Bytes("") to true,
                Bytes("12") to true,
                Bytes("0x1234567890abcdef") to true,
                Bytes("1234af") to false,
                Bytes("1234567890abcdef123456") to false,
            ) { (hex, result) ->
                bytes.startsWith(hex) shouldBe result
            }
        }

        context("other CharSequence") {
            withData(
                "" to true,
                "12" to true,
                "0x1234567890abcdef" to true,
                "1234af" to false,
                "1234567890abcdef123456" to false,
            ) { (hex, result) ->
                bytes.startsWith(hex) shouldBe result
            }
        }

        context("odd length CharSequence fails") {
            withData(
                "1234567890abcde",
                "1234a",
            ) { hex ->
                shouldThrow<IllegalArgumentException> {
                    bytes.startsWith(hex)
                }
            }
        }

        context("other ByteArray") {
            withData(
                "12" to true,
                "1234567890abcdef" to true,
                "1234af" to false,
                "1234567890abcdef123456" to false,
            ) { (hex, result) ->
                bytes.startsWith(BigInteger(hex, 16).toByteArray()) shouldBe result
            }
        }
    }

    test("serialization / deserialization") {
        val bytes = Bytes("0x1234567890abcdef")
        val json = Jackson.MAPPER.writeValueAsString(bytes)
        val deserialized = Jackson.MAPPER.readValue(json, Bytes::class.java)

        deserialized shouldBe bytes
    }
})
