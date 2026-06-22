package io.ethers.core.json

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class JsonElementTest : FunSpec({
    context("isObject") {
        test("true for JSON object") {
            JsonElement("""{"key":"value"}""").isObject shouldBe true
        }

        test("false for JSON array") {
            JsonElement("""[1,2,3]""").isObject shouldBe false
        }

        test("false for JSON string") {
            JsonElement(""""hello"""").isObject shouldBe false
        }

        test("false for JSON number") {
            JsonElement("42").isObject shouldBe false
        }
    }

    context("isArray") {
        test("true for JSON array") {
            JsonElement("""[1,2,3]""").isArray shouldBe true
        }

        test("true for empty JSON array") {
            JsonElement("[]").isArray shouldBe true
        }

        test("false for JSON object") {
            JsonElement("""{"key":"value"}""").isArray shouldBe false
        }

        test("false for JSON string") {
            JsonElement(""""hello"""").isArray shouldBe false
        }
    }

    context("isString") {
        test("true for JSON string") {
            JsonElement(""""hello"""").isString shouldBe true
        }

        test("true for empty JSON string") {
            JsonElement("\"\"").isString shouldBe true
        }

        test("false for JSON object") {
            JsonElement("""{"key":"value"}""").isString shouldBe false
        }

        test("false for JSON number") {
            JsonElement("42").isString shouldBe false
        }
    }

    context("toString") {
        test("returns raw JSON string") {
            val json = """{"key":"value"}"""
            JsonElement(json).toString() shouldBe json
        }
    }

    context("equality") {
        test("equal for same JSON content") {
            JsonElement("""{"a":1}""") shouldBe JsonElement("""{"a":1}""")
        }

        test("not equal for different JSON content") {
            (JsonElement("""{"a":1}""") == JsonElement("""{"a":2}""")) shouldBe false
        }

        test("same hashCode for same JSON content") {
            JsonElement("42").hashCode() shouldBe JsonElement("42").hashCode()
        }
    }
})
