package io.ethers.providers.types

import io.github.artificialpb.bignum.bigDecimalOf
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

class DynamicJsonTest : FunSpec({
    test("BigInteger is encoded as a JSON number, not a string") {
        val element = bigIntegerOf(20L).toDynamicJson()

        element.jsonPrimitive.isString shouldBe false
        element.toString() shouldBe "20"
    }

    test("BigInteger beyond Long and Double precision keeps every digit") {
        val value = "123456789012345678901234567890123456789"
        val element = bigIntegerOf(value).toDynamicJson()

        element.toString() shouldBe value
        // the literal must survive a round-trip through a JSON parser as the same number
        Json.parseToJsonElement(element.toString()).jsonPrimitive.content shouldBe value
    }

    test("negative and zero BigInteger are encoded as JSON numbers") {
        bigIntegerOf(-42L).toDynamicJson().toString() shouldBe "-42"
        bigIntegerOf(0L).toDynamicJson().toString() shouldBe "0"
    }

    test("BigDecimal is encoded as a plain JSON number without exponent notation") {
        bigDecimalOf("12.50").toDynamicJson().toString() shouldBe "12.50"
        bigDecimalOf("1E+3").toDynamicJson().toString() shouldBe "1000"
        bigDecimalOf("-0.000001").toDynamicJson().toString() shouldBe "-0.000001"
        bigDecimalOf("12.5").toDynamicJson().jsonPrimitive.isString shouldBe false
    }

    test("BigInteger nested in collections and maps is encoded as a JSON number") {
        listOf(bigIntegerOf(25L), bigIntegerOf(75L)).toDynamicJson().toString() shouldBe "[25,75]"
        arrayOf(bigIntegerOf(1L)).toDynamicJson().toString() shouldBe "[1]"
        mapOf("limit" to bigIntegerOf(10L)).toDynamicJson().toString() shouldBe """{"limit":10}"""
    }

    test("String is still encoded as a JSON string") {
        "20".toDynamicJson().jsonPrimitive.isString shouldBe true
    }
})
