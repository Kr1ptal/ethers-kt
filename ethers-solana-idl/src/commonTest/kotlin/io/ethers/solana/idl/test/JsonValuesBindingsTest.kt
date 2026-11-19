package io.ethers.solana.idl.test

import io.ethers.solana.codec.SolanaEventContext
import io.ethers.solana.codec.SolanaEventSource
import io.ethers.solana.codec.SolanaJsonException
import io.ethers.solana.codec.SolanaOption
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes
import io.github.artificialpb.bignum.BigInteger
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

private val OWNER = SolanaAddress(ByteArray(32) { (it + 1).toByte() })

private val U64_MAX = "18446744073709551615"
private val U128_MAX = "340282366920938463463374607431768211455"
private val U256_MAX = "115792089237316195423570985008687907853269984665640564039457584007913129639935"
private val I128_MIN = "-170141183460469231731687303715884105728"
private val I128_MAX = "170141183460469231731687303715884105727"
private val I256_MIN = "-57896044618658097711785492504343953926634992332820282019728792003956564819968"
private val I256_MAX = "57896044618658097711785492504343953926634992332820282019728792003956564819967"

private val MAX_INTEGERS = JsonValues.Integers(
    u8Value = 255,
    u16Value = 65535,
    u32Value = 4294967295L,
    u64Value = BigInteger(U64_MAX),
    u128Value = BigInteger(U128_MAX),
    u256Value = BigInteger(U256_MAX),
    i8Value = Byte.MAX_VALUE,
    i16Value = Short.MAX_VALUE,
    i32Value = Int.MAX_VALUE,
    i64Value = Long.MAX_VALUE,
    i128Value = BigInteger(I128_MAX),
    i256Value = BigInteger(I256_MAX),
)

private val MIN_INTEGERS = JsonValues.Integers(
    u8Value = 0,
    u16Value = 0,
    u32Value = 0,
    u64Value = BigInteger("0"),
    u128Value = BigInteger("0"),
    u256Value = BigInteger("0"),
    i8Value = Byte.MIN_VALUE,
    i16Value = Short.MIN_VALUE,
    i32Value = Int.MIN_VALUE,
    i64Value = Long.MIN_VALUE,
    i128Value = BigInteger(I128_MIN),
    i256Value = BigInteger(I256_MIN),
)

private fun misc(
    ratio: Float = 0.1f,
    precise: Double = 0.1,
    nested: SolanaOption<BigInteger?> = SolanaOption.Some(null),
    maybe: BigInteger? = null,
) = JsonValues.Misc(
    flag = true,
    ratio = ratio,
    precise = precise,
    name = "naïve \"quoted\" ✓",
    owner = OWNER,
    blob = SolanaBytes.fromBytes(byteArrayOf(0, -1, 127, -128)),
    raw = listOf(0, 1, 255),
    hash = SolanaBytes.fromBytes(byteArrayOf(1, 2, 3, 4)),
    maybe = maybe,
    nested = nested,
    amounts = listOf(BigInteger("1"), BigInteger(U64_MAX)),
    pair = JsonValues.Pair(BigInteger("7"), OWNER),
    empty = JsonValues.Empty,
    sides = listOf(JsonValues.Side.Bid, JsonValues.Side.Limit(BigInteger("5"))),
)

private val strict = Json
private val lenient = Json { ignoreUnknownKeys = true }

class JsonValuesBindingsTest : FunSpec({
    context("integers") {
        test("every width keeps its extreme values exactly, wide ones as decimal strings") {
            val json = strict.encodeToString(JsonValues.Integers.JsonSerializer, MAX_INTEGERS)
            json shouldBe """{"u8_value":255,"u16_value":65535,"u32_value":4294967295,"u64_value":"$U64_MAX",""" +
                """"u128_value":"$U128_MAX","u256_value":"$U256_MAX","i8_value":127,"i16_value":32767,""" +
                """"i32_value":2147483647,"i64_value":"9223372036854775807","i128_value":"$I128_MAX","i256_value":"$I256_MAX"}"""
            strict.decodeFromString(JsonValues.Integers.JsonSerializer, json) shouldBe MAX_INTEGERS

            val min = strict.encodeToString(JsonValues.Integers.JsonSerializer, MIN_INTEGERS)
            min shouldContain """"i64_value":"-9223372036854775808""""
            min shouldContain """"i128_value":"$I128_MIN""""
            min shouldContain """"i256_value":"$I256_MIN""""
            strict.decodeFromString(JsonValues.Integers.JsonSerializer, min) shouldBe MIN_INTEGERS
        }

        test("values beyond a width, or not written as its canonical form, are rejected with their path") {
            val valid = JsonValues.Integers.JsonSerializer.toJson(MAX_INTEGERS).jsonObject
            fun rejected(field: String, value: String): String {
                val element = JsonObject(valid + (field to Json.parseToJsonElement(value)))
                val error = shouldThrow<SolanaJsonException> { JsonValues.Integers.JsonSerializer.fromJson(element) }
                error.path shouldBe field
                return error.reason
            }
            rejected("u8_value", "256") shouldContain "out of range for u8"
            rejected("u8_value", "-1") shouldContain "out of range for u8"
            rejected("u8_value", "1.0") shouldContain "Expected a JSON integer"
            rejected("u8_value", "\"1\"") shouldContain "Expected a JSON integer"
            rejected("i8_value", "128") shouldContain "out of range for i8"
            rejected("u32_value", "4294967296") shouldContain "out of range for u32"
            rejected("u64_value", "\"18446744073709551616\"") shouldContain "out of range for u64"
            rejected("u64_value", "\"-1\"") shouldContain "out of range for u64"
            // a JSON number could have passed through a double on its way here
            rejected("u64_value", "1") shouldContain "Expected a decimal string"
            rejected("u128_value", "\"01\"") shouldContain "canonical decimal"
            rejected("u128_value", "\"+1\"") shouldContain "canonical decimal"
            rejected("i128_value", "\"-0\"") shouldContain "canonical decimal"
            rejected("i128_value", "\"1e3\"") shouldContain "canonical decimal"
            rejected("i128_value", "\"170141183460469231731687303715884105728\"") shouldContain "out of range for i128"
            rejected("i128_value", "\"-170141183460469231731687303715884105729\"") shouldContain "out of range for i128"
            rejected("i64_value", "\"9223372036854775808\"") shouldContain "out of range for i64"
            rejected("u256_value", "\"$U256_MAX$U256_MAX\"") shouldContain "out of range for u256"
            rejected("i256_value", "null") shouldContain "Expected a decimal string"
        }

        test("encoding a value its IDL type cannot hold throws, as the binary codec does") {
            shouldThrow<IllegalArgumentException> { JsonValues.Integers.JsonSerializer.toJson(MAX_INTEGERS.copy(u8Value = 256)) }
            shouldThrow<IllegalArgumentException> { JsonValues.Integers.JsonSerializer.toJson(MAX_INTEGERS.copy(u16Value = -1)) }
            shouldThrow<IllegalArgumentException> { JsonValues.Integers.JsonSerializer.toJson(MAX_INTEGERS.copy(u64Value = BigInteger("-1"))) }
            shouldThrow<IllegalArgumentException> { JsonValues.Integers.JsonSerializer.toJson(MAX_INTEGERS.copy(i128Value = BigInteger(U128_MAX))) }
        }
    }

    context("other values") {
        test("bytes are base64, Vec<u8> an integer array, options null, nested options a one-element array") {
            val element = JsonValues.Misc.JsonSerializer.toJson(misc()).jsonObject
            element["flag"] shouldBe JsonPrimitive(true)
            element["ratio"].toString() shouldBe "0.1"
            element["precise"].toString() shouldBe "0.1"
            element["owner"] shouldBe JsonPrimitive(OWNER.toBase58())
            element["blob"] shouldBe JsonPrimitive("AP9/gA==")
            element["raw"].toString() shouldBe "[0,1,255]"
            element["hash"] shouldBe JsonPrimitive("AQIDBA==")
            element["maybe"].toString() shouldBe "null"
            element["nested"].toString() shouldBe "[null]"
            element["amounts"].toString() shouldBe """["1","$U64_MAX"]"""
            element["pair"].toString() shouldBe """["7","${OWNER.toBase58()}"]"""
            element["empty"].toString() shouldBe "{}"
            element["sides"].toString() shouldBe """["Bid",{"Limit":["5"]}]"""
            // keys keep the IDL's declaration order
            element.keys.toList() shouldBe listOf("flag", "ratio", "precise", "name", "owner", "blob", "raw", "hash", "maybe", "nested", "amounts", "pair", "empty", "sides")
        }

        test("every value round-trips, including option nesting and non-finite floats") {
            val values = listOf(
                misc(),
                misc(nested = SolanaOption.None, maybe = BigInteger(U64_MAX)),
                misc(nested = SolanaOption.Some(BigInteger(I128_MIN))),
                misc(ratio = Float.NaN, precise = Double.NEGATIVE_INFINITY),
                misc(ratio = Float.MAX_VALUE, precise = Double.MIN_VALUE),
                misc(ratio = -0.0f, precise = 1.0E300),
            )
            for (value in values) {
                val json = strict.encodeToString(JsonValues.Misc.JsonSerializer, value)
                val decoded = strict.decodeFromString(JsonValues.Misc.JsonSerializer, json)
                decoded shouldBe value
                strict.encodeToString(JsonValues.Misc.JsonSerializer, decoded) shouldBe json
            }
            JsonValues.Misc.JsonSerializer.toJson(misc(nested = SolanaOption.Some(BigInteger("-5")))).jsonObject["nested"].toString() shouldBe """["-5"]"""
            JsonValues.Misc.JsonSerializer.toJson(misc(ratio = Float.NaN, precise = Double.POSITIVE_INFINITY)).jsonObject.let {
                it["ratio"] shouldBe JsonPrimitive("NaN")
                it["precise"] shouldBe JsonPrimitive("Infinity")
            }
        }

        test("an absent option field decodes as none, any other absent field fails") {
            val element = JsonValues.Misc.JsonSerializer.toJson(misc(nested = SolanaOption.None)).jsonObject
            JsonValues.Misc.JsonSerializer.fromJson(JsonObject(element - "maybe" - "nested")) shouldBe misc(nested = SolanaOption.None)
            val error = shouldThrow<SolanaJsonException> { JsonValues.Misc.JsonSerializer.fromJson(JsonObject(element - "owner")) }
            error.path shouldBe "owner"
            error.reason shouldBe "Missing field"
        }

        test("malformed values are rejected with the path to them") {
            val valid = JsonValues.Misc.JsonSerializer.toJson(misc()).jsonObject
            fun rejected(field: String, value: String): SolanaJsonException {
                val element = JsonObject(valid + (field to Json.parseToJsonElement(value)))
                return shouldThrow<SolanaJsonException> { JsonValues.Misc.JsonSerializer.fromJson(element) }
            }
            rejected("owner", "\"not base58 0OIl\"").path shouldBe "owner"
            rejected("owner", "\"${SolanaBytes.fromBytes(ByteArray(31) { 1 }).toBase58()}\"").reason shouldContain "Invalid base58 public key"
            rejected("hash", "\"AQID\"").reason shouldBe "Expected 4 bytes, got 3"
            rejected("blob", "\"not base64!\"").reason shouldContain "Invalid base64"
            rejected("blob", "[1, 2]").reason shouldContain "Expected a base64 string"
            rejected("raw", "[1, 256]").path shouldBe "raw[1]"
            rejected("nested", "[1, 2]").reason shouldContain "one-element array"
            rejected("nested", "\"1\"").reason shouldContain "one-element array"
            rejected("nested", "[1]").path shouldBe "nested[0]"
            rejected("ratio", "\"1.5\"").reason shouldContain "Expected a JSON number"
            rejected("ratio", "1e39").reason shouldBe "Value is out of range for f32"
            rejected("precise", "true").reason shouldContain "Expected a JSON number"
            rejected("flag", "\"true\"").reason shouldContain "Expected a JSON boolean"
            rejected("name", "1").reason shouldContain "Expected a JSON string"
            rejected("pair", """["7"]""").reason shouldBe "Expected 2 fields for Pair, got 1"
            rejected("pair", """{"0": "7"}""").reason shouldContain "Expected a JSON array for Pair"
            rejected("sides", """["Bid"]""").reason shouldBe "Expected 2 elements, got 1"
            rejected("sides", """["Bid", {"Limit": ["x"]}]""").path shouldBe "sides[1].Limit[0]"
            rejected("empty", """{"extra": 1}""").reason shouldBe "Unknown field 'extra' of Empty"
            rejected("amounts", "{}").path shouldBe "amounts"
        }

        test("unknown keys fail unless the Json instance ignores them") {
            val json = strict.encodeToString(JsonValues.Misc.JsonSerializer, misc()).dropLast(1) + ""","extra":{"x":1}}"""
            shouldThrow<SerializationException> { strict.decodeFromString(JsonValues.Misc.JsonSerializer, json) }.message shouldBe "Unknown field 'extra' of Misc"
            lenient.decodeFromString(JsonValues.Misc.JsonSerializer, json) shouldBe misc()

            // the setting reaches nested values
            val nested = """{"authority":"${OWNER.toBase58()}","balance":"1","side":{"Stop":{"price":"1","extra":true}}}"""
            shouldThrow<SolanaJsonException> { strict.decodeFromString(JsonValues.Ledger.JsonSerializer, nested) }.path shouldBe "side.Stop"
            lenient.decodeFromString(JsonValues.Ledger.JsonSerializer, nested).side shouldBe JsonValues.Side.Stop(BigInteger("1"))
        }

        test("encoding a fixed-size value of the wrong size throws") {
            shouldThrow<IllegalArgumentException> { JsonValues.Misc.JsonSerializer.toJson(misc().copy(hash = SolanaBytes.fromBytes(ByteArray(3)))) }
            shouldThrow<IllegalArgumentException> { JsonValues.Misc.JsonSerializer.toJson(misc().copy(sides = emptyList())) }
            shouldThrow<IllegalArgumentException> { JsonValues.Misc.JsonSerializer.toJson(misc().copy(raw = listOf(256))) }
        }
    }

    context("enums") {
        val variants = listOf(
            JsonValues.Side.Bid to "\"Bid\"",
            JsonValues.Side.Ask to "\"Ask\"",
            JsonValues.Side.Limit(BigInteger("1")) to """{"Limit":["1"]}""",
            JsonValues.Side.Market(BigInteger("1")) to """{"Market":["1"]}""",
            JsonValues.Side.Stop(BigInteger(U64_MAX)) to """{"Stop":{"price":"$U64_MAX"}}""",
            JsonValues.Side.TakeProfit(BigInteger(U64_MAX)) to """{"TakeProfit":{"price":"$U64_MAX"}}""",
            JsonValues.Side.Nested(null) to """{"Nested":{"inner":null}}""",
            JsonValues.Side.Nested(JsonValues.Side.Nested(JsonValues.Side.Ask)) to """{"Nested":{"inner":{"Nested":{"inner":"Ask"}}}}""",
        )

        test("variants of the same shape stay distinct, tagged by their IDL name") {
            for ((value, json) in variants) {
                strict.encodeToString(JsonValues.Side.JsonSerializer, value) shouldBe json
                strict.decodeFromString(JsonValues.Side.JsonSerializer, json) shouldBe value
            }
            strict.decodeFromString(JsonValues.Side.JsonSerializer, """{"Market":["1"]}""").shouldBeInstanceOf<JsonValues.Side.Market>()
            strict.decodeFromString(JsonValues.Side.JsonSerializer, """{"TakeProfit":{"price":"1"}}""").shouldBeInstanceOf<JsonValues.Side.TakeProfit>()
        }

        test("unknown variants and variants written in the wrong form are rejected") {
            fun rejected(json: String) = shouldThrow<SolanaJsonException> { strict.decodeFromString(JsonValues.Side.JsonSerializer, json) }.message!!
            rejected("\"Hold\"") shouldBe "Unknown variant 'Hold' of Side"
            rejected("\"bid\"") shouldBe "Unknown variant 'bid' of Side"
            rejected("""{"Bid":{}}""") shouldContain "has no fields, so is written as the string \"Bid\""
            rejected("\"Limit\"") shouldContain "has fields"
            rejected("""{"Limit":["1"],"Market":["1"]}""") shouldContain "single variant key"
            rejected("""{"Limit":"1"}""") shouldBe "Limit: Expected a JSON array for Side::Limit, got a string"
            rejected("""{"Stop":{}}""") shouldBe "Stop.price: Missing field"
            rejected("0") shouldContain "Expected a variant name"
        }
    }

    context("accounts, events and instruction arguments") {
        val ledger = JsonValues.Ledger(OWNER, BigInteger(I128_MIN), JsonValues.Side.TakeProfit(BigInteger("9")))

        test("the compiler plugin finds each type's serializer through its @Serializable annotation") {
            val json = strict.encodeToString(ledger)
            json shouldBe """{"authority":"${OWNER.toBase58()}","balance":"$I128_MIN","side":{"TakeProfit":{"price":"9"}}}"""
            strict.decodeFromString<JsonValues.Ledger>(json) shouldBe ledger
            strict.encodeToString(listOf<JsonValues.Side>(JsonValues.Side.Ask, JsonValues.Side.Limit(BigInteger("2")))) shouldBe """["Ask",{"Limit":["2"]}]"""
            strict.decodeFromString<Map<String, JsonValues.Empty>>("""{"a":{}}""") shouldBe mapOf("a" to JsonValues.Empty)
        }

        test("an account's JSON form leaves its binary encoding untouched") {
            val data = JsonValues.Ledger.ACCOUNT.encode(ledger)
            val decoded = JsonValues.Ledger.ACCOUNT.decode(data).unwrap()
            val roundTripped = strict.decodeFromString(JsonValues.Ledger.JsonSerializer, strict.encodeToString(JsonValues.Ledger.JsonSerializer, decoded))
            roundTripped shouldBe ledger
            JsonValues.Ledger.ACCOUNT.encode(roundTripped).contentEquals(data) shouldBe true
        }

        test("an event's JSON holds its payload under the IDL field names, and decodes into the context given") {
            val context = SolanaEventContext(JsonValues.PROGRAM_ID, SolanaEventSource.Log(1, 2, 1))
            val event = JsonValues.Event.Recorded(BigInteger(U64_MAX), "memo", context)
            val json = strict.encodeToString(JsonValues.Event.Recorded.JsonSerializer, event)
            // the IDL's `context` field is a plain payload field, which the Kotlin property renames
            json shouldBe """{"amount":"$U64_MAX","context":"memo"}"""

            val standalone = strict.decodeFromString(JsonValues.Event.Recorded.JsonSerializer, json)
            standalone shouldBe event.copy(context = SolanaEventContext(JsonValues.PROGRAM_ID))
            strict.decodeFromString(JsonValues.Event.Recorded.JsonSerializer.withContext(context), json) shouldBe event
            strict.decodeFromString<JsonValues.Event.Recorded>(json).contextField shouldBe "memo"

            val cleared = JsonValues.Event.Cleared(context)
            strict.encodeToString(JsonValues.Event.Cleared.JsonSerializer, cleared) shouldBe "{}"
            strict.decodeFromString(JsonValues.Event.Cleared.JsonSerializer.withContext(context), "{}") shouldBe cleared
        }

        test("instruction arguments round-trip, keyed by the IDL argument names") {
            val args = JsonValues.RecordArgs(BigInteger(U64_MAX), JsonValues.Side.Market(BigInteger("0")))
            val json = strict.encodeToString(args)
            json shouldBe """{"amount":"$U64_MAX","side":{"Market":["0"]}}"""
            strict.decodeFromString<JsonValues.RecordArgs>(json) shouldBe args
            shouldThrow<SolanaJsonException> { strict.decodeFromString<JsonValues.RecordArgs>("""{"amount":"1"}""") }.message shouldBe "side: Missing field"
        }
    }
})
