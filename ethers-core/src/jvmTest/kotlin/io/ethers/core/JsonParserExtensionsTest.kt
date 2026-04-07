package io.ethers.core

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigInteger

class JsonParserExtensionsTest : FunSpec({
    val factory = JsonFactory()

    fun parserFor(json: String): JsonParser {
        return factory.createParser(json).apply { initForReading() }
    }

    fun parserAtField(json: String): JsonParser {
        val p = factory.createParser(json)
        p.nextToken() // START_OBJECT
        p.nextToken() // FIELD_NAME
        p.nextToken() // VALUE
        return p
    }

    fun mapperParserAtField(json: String): JsonParser {
        val p = Jackson.MAPPER.createParser(json)
        p.nextToken() // START_OBJECT
        p.nextToken() // FIELD_NAME
        p.nextToken() // VALUE
        return p
    }

    context("readOrNull") {
        test("returns null when current token is VALUE_NULL") {
            val p = parserAtField("""{"f": null}""")
            p.currentToken shouldBe JsonToken.VALUE_NULL
            val result = p.readOrNull { readAddress() }
            result shouldBe null
        }

        test("returns value when current token is not null") {
            val p = parserAtField("""{"f": "0xdafea492d9c6733ae3d56b7ed1adb60692c98bc5"}""")
            val result = p.readOrNull { readAddress() }
            result shouldBe Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5")
        }
    }

    context("readValueOrNull") {
        test("returns null when current token is VALUE_NULL") {
            val p = mapperParserAtField("""{"f": null}""")
            p.currentToken shouldBe JsonToken.VALUE_NULL
            val result = p.readValueOrNull(String::class.java)
            result shouldBe null
        }

        test("returns value when current token is not null") {
            val p = mapperParserAtField("""{"f": "hello"}""")
            val result = p.readValueOrNull(String::class.java)
            result shouldBe "hello"
        }
    }

    context("readAddress") {
        test("reads address from hex string") {
            val p = parserAtField("""{"f": "0xdafea492d9c6733ae3d56b7ed1adb60692c98bc5"}""")
            val result = p.readAddress()
            result shouldBe Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5")
        }

        test("returns Address.ZERO for empty hex") {
            val p = parserAtField("""{"f": "0x"}""")
            val result = p.readAddress()
            result shouldBe Address.ZERO
        }
    }

    context("readBytes") {
        test("reads bytes from hex string") {
            val p = parserAtField("""{"f": "0xabcd"}""")
            val result = p.readBytes()
            result shouldBe Bytes("0xabcd")
        }

        test("returns Bytes.EMPTY for empty hex") {
            val p = parserAtField("""{"f": "0x"}""")
            val result = p.readBytes()
            result shouldBe Bytes.EMPTY
        }
    }

    context("readBytesEmptyAsNull") {
        test("returns null for empty hex") {
            val p = parserAtField("""{"f": "0x"}""")
            val result = p.readBytesEmptyAsNull()
            result shouldBe null
        }

        test("returns Bytes for non-empty hex") {
            val p = parserAtField("""{"f": "0xab"}""")
            val result = p.readBytesEmptyAsNull()
            result shouldBe Bytes("0xab")
        }
    }

    context("readHexBigInteger") {
        test("reads BigInteger from hex") {
            val p = parserAtField("""{"f": "0xff"}""")
            val result = p.readHexBigInteger()
            result shouldBe BigInteger("255")
        }

        test("returns BigInteger.ZERO for empty hex") {
            val p = parserAtField("""{"f": "0x"}""")
            val result = p.readHexBigInteger()
            result shouldBe BigInteger.ZERO
        }
    }

    context("readHexLong") {
        test("reads Long from hex") {
            val p = parserAtField("""{"f": "0x5208"}""")
            val result = p.readHexLong()
            result shouldBe 21000L
        }

        test("returns 0 for empty hex") {
            val p = parserAtField("""{"f": "0x"}""")
            val result = p.readHexLong()
            result shouldBe 0L
        }
    }

    context("readHexInt") {
        test("reads Int from hex") {
            val p = parserAtField("""{"f": "0x0a"}""")
            val result = p.readHexInt()
            result shouldBe 10
        }

        test("returns 0 for empty hex") {
            val p = parserAtField("""{"f": "0x"}""")
            val result = p.readHexInt()
            result shouldBe 0
        }
    }

    context("readAnyLong") {
        test("reads hex value") {
            val p = parserAtField("""{"f": "0x5208"}""")
            val result = p.readAnyLong()
            result shouldBe 21000L
        }

        test("reads numeric value") {
            val p = parserAtField("""{"f": 21000}""")
            val result = p.readAnyLong()
            result shouldBe 21000L
        }
    }

    context("readListOf") {
        test("returns empty list when token is VALUE_NULL") {
            val p = parserAtField("""{"f": null}""")
            val result = p.readListOf { readHash() }
            result shouldBe emptyList()
        }

        test("returns empty list for empty array") {
            val p = parserAtField("""{"f": []}""")
            val result = p.readListOf { readHash() }
            result shouldBe emptyList()
        }

        test("reads list of elements") {
            val p = parserAtField("""{"f": ["0xdafea492d9c6733ae3d56b7ed1adb60692c98bc5dafea492d9c6733ae3d56b7e"]}""")
            val result = p.readListOf { readHash() }
            result.size shouldBe 1
        }
    }

    context("readMapOf with keyParser and valueClass") {
        test("returns empty map when token is VALUE_NULL") {
            val p = mapperParserAtField("""{"f": null}""")
            val result = p.readMapOf({ it }, String::class.java)
            result shouldBe emptyMap()
        }

        test("returns empty map for empty object") {
            val p = mapperParserAtField("""{"f": {}}""")
            val result = p.readMapOf({ it }, String::class.java)
            result shouldBe emptyMap()
        }

        test("reads map entries") {
            val p = mapperParserAtField("""{"f": {"key1": "val1", "key2": "val2"}}""")
            val result = p.readMapOf({ it }, String::class.java)
            result shouldBe mapOf("key1" to "val1", "key2" to "val2")
        }
    }

    context("readMapOf with keyParser and valueParser") {
        test("returns empty map when token is VALUE_NULL") {
            val p = mapperParserAtField("""{"f": null}""")
            val result = p.readMapOf({ it }) { readValueAs(String::class.java) }
            result shouldBe emptyMap()
        }

        test("returns empty map for empty object") {
            val p = mapperParserAtField("""{"f": {}}""")
            val result = p.readMapOf({ it }) { readValueAs(String::class.java) }
            result shouldBe emptyMap()
        }

        test("reads map entries with custom parser") {
            val p = mapperParserAtField("""{"f": {"a": "1", "b": "2"}}""")
            val result = p.readMapOf({ it }) { readValueAs(String::class.java) }
            result shouldBe mapOf("a" to "1", "b" to "2")
        }
    }

    context("isField") {
        test("returns true when field name matches") {
            val p = factory.createParser("""{"myField": "value"}""")
            p.nextToken() // START_OBJECT
            p.nextToken() // FIELD_NAME "myField"
            p.isField("myField") shouldBe true
            // after isField, token should be advanced to VALUE
            p.currentToken shouldBe JsonToken.VALUE_STRING
        }

        test("returns false when field name does not match") {
            val p = factory.createParser("""{"other": "value"}""")
            p.nextToken() // START_OBJECT
            p.nextToken() // FIELD_NAME "other"
            p.isField("myField") shouldBe false
        }
    }

    context("ifNotNull") {
        test("does not execute block when token is VALUE_NULL") {
            val p = parserAtField("""{"f": null}""")
            var called = false
            p.ifNotNull { called = true }
            called shouldBe false
        }

        test("executes block when token is not null") {
            val p = parserAtField("""{"f": "value"}""")
            var called = false
            p.ifNotNull { called = true }
            called shouldBe true
        }
    }

    context("readHexByteArray") {
        test("reads hex from text without text characters") {
            // Use string-based parser (hasTextCharacters = false for string input)
            val p = parserAtField("""{"f": "0xaabb"}""")
            val result = p.readHexByteArray()
            result shouldBe byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        }

        test("returns empty for empty string") {
            val p = parserAtField("""{"f": ""}""")
            val result = p.readHexByteArray()
            result shouldBe byteArrayOf()
        }

        test("returns empty for bare 0x") {
            val p = parserAtField("""{"f": "0x"}""")
            val result = p.readHexByteArray()
            result shouldBe byteArrayOf()
        }

        test("returns empty for bare 0X") {
            val p = parserAtField("""{"f": "0X"}""")
            val result = p.readHexByteArray()
            result shouldBe byteArrayOf()
        }
    }

    context("isHexValue") {
        test("returns true for 0x prefixed string") {
            val p = parserAtField("""{"f": "0x1a"}""")
            p.isHexValue() shouldBe true
        }

        test("returns true for 0X prefixed string") {
            val p = parserAtField("""{"f": "0X1a"}""")
            p.isHexValue() shouldBe true
        }

        test("returns false for numeric value") {
            val p = parserAtField("""{"f": 123}""")
            p.isHexValue() shouldBe false
        }

        test("returns false for non-hex string") {
            val p = parserAtField("""{"f": "hello"}""")
            p.isHexValue() shouldBe false
        }
    }

    context("initForReading") {
        test("advances to first token if not pointing to one") {
            val p = factory.createParser("""{"f": 1}""")
            // currentToken is null before first read
            p.currentToken shouldBe null
            p.initForReading()
            p.currentToken shouldBe JsonToken.START_OBJECT
        }

        test("returns parser as-is if already at a token") {
            val p = factory.createParser("""{"f": 1}""")
            p.nextToken()
            p.currentToken shouldBe JsonToken.START_OBJECT
            p.initForReading()
            p.currentToken shouldBe JsonToken.START_OBJECT
        }

        test("throws on end-of-input") {
            val p = factory.createParser("")
            shouldThrow<Exception> { p.initForReading() }
        }
    }

    context("forEachObjectField") {
        test("iterates over all fields") {
            val p = parserFor("""{"a": 1, "b": 2}""")
            val fields = mutableListOf<String>()
            p.forEachObjectField { name ->
                fields.add(name)
                p.skipChildren()
            }
            fields shouldBe listOf("a", "b")
        }
    }

    context("forEachArrayElement") {
        test("iterates over array elements") {
            val p = parserFor("""[1, 2, 3]""")
            val values = mutableListOf<Int>()
            p.forEachArrayElement {
                values.add(p.intValue)
            }
            values shouldBe listOf(1, 2, 3)
        }
    }

    context("handleUnknownField") {
        test("skips field in non-strict mode") {
            val p = parserAtField("""{"unknown": {"nested": true}}""")
            // should not throw in default mode
            p.handleUnknownField()
        }
    }
})
