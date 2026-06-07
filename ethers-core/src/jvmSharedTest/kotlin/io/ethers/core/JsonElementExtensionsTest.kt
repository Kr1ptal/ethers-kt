package io.ethers.core

import io.ethers.core.types.Address
import io.ethers.core.types.Bloom
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigInteger

class JsonElementExtensionsTest : FunSpec({
    context("asHexByteArray") {
        test("decodes hex string") {
            JsonPrimitive("0xaabb").asHexByteArray() shouldBe byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        }

        test("returns empty for 0x") {
            JsonPrimitive("0x").asHexByteArray() shouldBe byteArrayOf()
        }

        test("returns empty for empty string") {
            JsonPrimitive("").asHexByteArray() shouldBe byteArrayOf()
        }
    }

    context("asHexBigInteger") {
        test("decodes hex string to BigInteger") {
            JsonPrimitive("0xff").asHexBigInteger() shouldBe BigInteger("255")
        }

        test("returns BigInteger.ZERO for empty hex") {
            JsonPrimitive("0x").asHexBigInteger() shouldBe BigInteger.ZERO
        }
    }

    context("asHexLong") {
        test("decodes hex string to Long") {
            JsonPrimitive("0x5208").asHexLong() shouldBe 21000L
        }

        test("returns 0 for empty hex") {
            JsonPrimitive("0x").asHexLong() shouldBe 0L
        }
    }

    context("asHexInt") {
        test("decodes hex string to Int") {
            JsonPrimitive("0x0a").asHexInt() shouldBe 10
        }

        test("returns 0 for empty hex") {
            JsonPrimitive("0x").asHexInt() shouldBe 0
        }
    }

    context("asAnyLong") {
        test("reads hex value") {
            JsonPrimitive("0x5208").asAnyLong() shouldBe 21000L
        }

        test("reads numeric value") {
            JsonPrimitive(21000L).asAnyLong() shouldBe 21000L
        }
    }

    context("asAddress") {
        test("decodes hex to Address") {
            JsonPrimitive("0xdafea492d9c6733ae3d56b7ed1adb60692c98bc5").asAddress() shouldBe
                Address("0xDAFEA492D9c6733ae3d56b7Ed1ADB60692c98Bc5")
        }

        test("returns Address.ZERO for empty hex") {
            JsonPrimitive("0x").asAddress() shouldBe Address.ZERO
        }
    }

    context("asHash") {
        test("decodes hex to Hash") {
            val hex = "0xdafea492d9c6733ae3d56b7ed1adb60692c98bc5dafea492d9c6733ae3d56b7e"
            JsonPrimitive(hex).asHash() shouldBe Hash(hex)
        }
    }

    context("asBytes") {
        test("decodes hex to Bytes") {
            JsonPrimitive("0xabcd").asBytes() shouldBe Bytes("0xabcd")
        }

        test("returns Bytes.EMPTY for empty hex") {
            JsonPrimitive("0x").asBytes() shouldBe Bytes.EMPTY
        }
    }

    context("asBytesOrNull") {
        test("returns null for empty hex") {
            JsonPrimitive("0x").asBytesOrNull() shouldBe null
        }

        test("returns Bytes for non-empty hex") {
            JsonPrimitive("0xab").asBytesOrNull() shouldBe Bytes("0xab")
        }
    }

    context("asBloom") {
        test("decodes hex to Bloom") {
            val hex = "0x" + "00".repeat(256)
            JsonPrimitive(hex).asBloom() shouldBe Bloom(ByteArray(256))
        }
    }

    context("ifNotNull") {
        test("returns null for JsonNull") {
            val result = JsonNull.ifNotNull { asHexLong() }
            result shouldBe null
        }

        test("applies block for non-null primitive") {
            val result = JsonPrimitive("0x5208").ifNotNull { asHexLong() }
            result shouldBe 21000L
        }
    }

    context("getOrNull") {
        test("returns null for missing key") {
            val obj = JsonObject(emptyMap())
            obj.getOrNull("key") { jsonPrimitive.asHexLong() } shouldBe null
        }

        test("returns null for JsonNull value") {
            val obj = JsonObject(mapOf("key" to JsonNull))
            obj.getOrNull("key") { jsonPrimitive.asHexLong() } shouldBe null
        }

        test("applies block for present non-null value") {
            val obj = JsonObject(mapOf("key" to JsonPrimitive("0x0a")))
            obj.getOrNull("key") { jsonPrimitive.asHexLong() } shouldBe 10L
        }
    }

    context("asBoolean") {
        test("reads true") {
            JsonPrimitive(true).asBoolean shouldBe true
        }

        test("reads false") {
            JsonPrimitive(false).asBoolean shouldBe false
        }
    }

    context("asBooleanOrNull") {
        test("returns null for non-boolean string") {
            JsonPrimitive("notabool").asBooleanOrNull shouldBe null
        }

        test("returns Boolean for boolean string") {
            JsonPrimitive("true").asBooleanOrNull shouldBe true
        }
    }

    context("asDouble") {
        test("reads double value") {
            JsonPrimitive(3.14).asDouble shouldBe 3.14
        }
    }
})
