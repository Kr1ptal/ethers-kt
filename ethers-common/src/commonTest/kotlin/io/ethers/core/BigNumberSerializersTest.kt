package io.ethers.core

import io.github.artificialpb.bignum.bigDecimalOf
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

class BigNumberSerializersTest : FunSpec({
    val json = Kotlinx.DEFAULT

    context("BigIntegerNumberSerializer") {
        test("encodes a JSON number, not a string") {
            val element = json.encodeToJsonElement(BigIntegerNumberSerializer, bigIntegerOf(20L))

            element.jsonPrimitive.isString shouldBe false
            element.toString() shouldBe "20"
        }

        test("keeps every digit beyond Long and Double precision") {
            val value = "123456789012345678901234567890123456789"
            val element = json.encodeToJsonElement(BigIntegerNumberSerializer, bigIntegerOf(value))

            element.toString() shouldBe value
            // the literal must survive a round-trip through a JSON parser as the same number
            Json.parseToJsonElement(element.toString()).jsonPrimitive.content shouldBe value
            json.encodeToString(BigIntegerNumberSerializer, bigIntegerOf(value)) shouldBe value
        }

        test("encodes negative and zero values as JSON numbers") {
            json.encodeToJsonElement(BigIntegerNumberSerializer, bigIntegerOf(-42L)).toString() shouldBe "-42"
            json.encodeToJsonElement(BigIntegerNumberSerializer, bigIntegerOf(0L)).toString() shouldBe "0"
        }

        test("encodes JSON numbers nested in collections and maps") {
            json.encodeToString(ListSerializer(BigIntegerNumberSerializer), listOf(bigIntegerOf(25L), bigIntegerOf(75L))) shouldBe
                "[25,75]"
            json.encodeToString(MapSerializer(String.serializer(), BigIntegerNumberSerializer), mapOf("limit" to bigIntegerOf(10L))) shouldBe
                """{"limit":10}"""
        }

        test("decodes a JSON number back") {
            val value = "123456789012345678901234567890123456789"
            json.decodeFromString(BigIntegerNumberSerializer, value) shouldBe bigIntegerOf(value)
        }
    }

    context("BigDecimalNumberSerializer") {
        test("encodes a plain JSON number without exponent notation") {
            json.encodeToJsonElement(BigDecimalNumberSerializer, bigDecimalOf("12.50")).toString() shouldBe "12.50"
            json.encodeToJsonElement(BigDecimalNumberSerializer, bigDecimalOf("1E+3")).toString() shouldBe "1000"
            json.encodeToJsonElement(BigDecimalNumberSerializer, bigDecimalOf("-0.000001")).toString() shouldBe "-0.000001"
            json.encodeToJsonElement(BigDecimalNumberSerializer, bigDecimalOf("12.5")).jsonPrimitive.isString shouldBe false
        }

        test("decodes a JSON number back") {
            json.decodeFromString(BigDecimalNumberSerializer, "12.50") shouldBe bigDecimalOf("12.50")
        }
    }
})
