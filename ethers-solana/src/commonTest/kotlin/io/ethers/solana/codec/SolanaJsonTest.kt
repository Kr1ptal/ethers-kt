package io.ethers.solana.codec

import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.minus
import io.github.artificialpb.bignum.plus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

/** A two-field value with a hand-written JSON form, as generated code writes one. */
private data class Point(val x: Int, val y: BigInteger)

private object PointJson : SolanaJsonSerializer<Point>("test.Point") {
    private val FIELDS = setOf("x", "y")

    override fun toJson(value: Point): JsonElement = SolanaJson.encodeStruct("x" to SolanaJson.encodeI32(value.x), "y" to SolanaJson.encodeU128(value.y))

    override fun fromJson(element: JsonElement, ignoreUnknownKeys: Boolean): Point {
        val fields = SolanaJson.decodeStruct(element, "Point", FIELDS, ignoreUnknownKeys)
        return Point(SolanaJson.field(fields, "x", false) { SolanaJson.decodeI32(it) }, SolanaJson.field(fields, "y", false) { SolanaJson.decodeU128(it) })
    }
}

private object NotJsonEncoder : AbstractEncoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()
}

class SolanaJsonTest : FunSpec({
    test("the serializer encodes through any Json instance, and honours its ignoreUnknownKeys") {
        val point = Point(-1, BigInteger("340282366920938463463374607431768211455"))
        val text = Json.encodeToString(PointJson, point)
        text shouldBe """{"x":-1,"y":"340282366920938463463374607431768211455"}"""
        Json.decodeFromString(PointJson, text) shouldBe point
        Json { prettyPrint = true }.encodeToString(PointJson, point) shouldContain "\n"

        val extra = """{"x":1,"y":"2","z":3}"""
        shouldThrow<SolanaJsonException> { Json.decodeFromString(PointJson, extra) }.message shouldBe "Unknown field 'z' of Point"
        Json { ignoreUnknownKeys = true }.decodeFromString(PointJson, extra) shouldBe Point(1, BigInteger("2"))
        PointJson.descriptor.serialName shouldBe "test.Point"
    }

    test("formats other than JSON are refused rather than mis-encoded") {
        shouldThrow<SerializationException> { NotJsonEncoder.encodeSerializableValue(PointJson, Point(1, BigInteger("1"))) }.message shouldBe
            "test.Point can only be encoded as JSON"
    }

    test("small integers are JSON integers, checked against their width both ways") {
        SolanaJson.decodeU8(SolanaJson.encodeU8(255)) shouldBe 255
        SolanaJson.decodeU16(SolanaJson.encodeU16(65535)) shouldBe 65535
        SolanaJson.decodeU32(SolanaJson.encodeU32(4294967295L)) shouldBe 4294967295L
        SolanaJson.decodeI8(SolanaJson.encodeI8(Byte.MIN_VALUE)) shouldBe Byte.MIN_VALUE
        SolanaJson.decodeI16(SolanaJson.encodeI16(Short.MIN_VALUE)) shouldBe Short.MIN_VALUE
        SolanaJson.decodeI32(SolanaJson.encodeI32(Int.MIN_VALUE)) shouldBe Int.MIN_VALUE
        SolanaJson.encodeU32(4294967295L) shouldBe JsonPrimitive(4294967295L)

        shouldThrow<IllegalArgumentException> { SolanaJson.encodeU8(-1) }
        shouldThrow<IllegalArgumentException> { SolanaJson.encodeU16(65536) }
        shouldThrow<IllegalArgumentException> { SolanaJson.encodeU32(-1) }
        shouldThrow<SolanaJsonException> { SolanaJson.decodeI8(json("-129")) }.reason shouldBe "Value -129 is out of range for i8"
        shouldThrow<SolanaJsonException> { SolanaJson.decodeI32(json("2147483648")) }
        shouldThrow<SolanaJsonException> { SolanaJson.decodeU32(json("99999999999999999999")) }
        shouldThrow<SolanaJsonException> { SolanaJson.decodeU8(json("1e2")) }
        shouldThrow<SolanaJsonException> { SolanaJson.decodeU8(JsonNull) }
    }

    test("wide integers are canonical decimal strings at their exact extremes") {
        val cases = listOf(
            Triple(SolanaJson::encodeU64, SolanaJson::decodeU64, listOf("0", "18446744073709551615")),
            Triple(SolanaJson::encodeU128, SolanaJson::decodeU128, listOf("0", "340282366920938463463374607431768211455")),
            Triple(
                SolanaJson::encodeU256,
                SolanaJson::decodeU256,
                listOf("0", "115792089237316195423570985008687907853269984665640564039457584007913129639935"),
            ),
            Triple(SolanaJson::encodeI128, SolanaJson::decodeI128, listOf("-170141183460469231731687303715884105728", "170141183460469231731687303715884105727")),
            Triple(
                SolanaJson::encodeI256,
                SolanaJson::decodeI256,
                listOf(
                    "-57896044618658097711785492504343953926634992332820282019728792003956564819968",
                    "57896044618658097711785492504343953926634992332820282019728792003956564819967",
                ),
            ),
        )
        for ((encode, decode, extremes) in cases) {
            for (text in extremes) {
                val value = BigInteger(text)
                encode(value) shouldBe JsonPrimitive(text)
                decode(JsonPrimitive(text)) shouldBe value
            }
            val (min, max) = extremes.map(::BigInteger)
            shouldThrow<IllegalArgumentException> { encode(max + BigInteger("1")) }
            shouldThrow<IllegalArgumentException> { encode(min - BigInteger("1")) }
            shouldThrow<SolanaJsonException> { decode(JsonPrimitive((max + BigInteger("1")).toString())) }
            shouldThrow<SolanaJsonException> { decode(JsonPrimitive((min - BigInteger("1")).toString())) }
        }
        SolanaJson.decodeI64(SolanaJson.encodeI64(Long.MIN_VALUE)) shouldBe Long.MIN_VALUE
        SolanaJson.encodeI64(Long.MAX_VALUE) shouldBe JsonPrimitive("9223372036854775807")
        for (text in listOf("00", "-0", "+1", " 1", "1 ", "0x10", "1.0", "")) {
            shouldThrow<SolanaJsonException> { SolanaJson.decodeU64(JsonPrimitive(text)) }.reason shouldContain "canonical decimal"
        }
        // a JSON number may already have been rounded through a double
        shouldThrow<SolanaJsonException> { SolanaJson.decodeU64(json("18446744073709551615")) }.reason shouldContain "Expected a decimal string"
    }

    test("floats use their shortest exact form, and strings for values JSON numbers lack") {
        SolanaJson.encodeF32(0.1f).toString() shouldBe "0.1"
        SolanaJson.decodeF32(json("0.1")) shouldBe 0.1f
        SolanaJson.decodeF64(SolanaJson.encodeF64(0.1 + 0.2)) shouldBe 0.1 + 0.2
        SolanaJson.decodeF64(SolanaJson.encodeF64(Double.MIN_VALUE)) shouldBe Double.MIN_VALUE
        SolanaJson.decodeF64(json("1E+2")) shouldBe 100.0
        SolanaJson.decodeF32(SolanaJson.encodeF32(Float.NaN)).isNaN() shouldBe true
        SolanaJson.encodeF64(Double.NEGATIVE_INFINITY) shouldBe JsonPrimitive("-Infinity")
        SolanaJson.decodeF64(JsonPrimitive("Infinity")) shouldBe Double.POSITIVE_INFINITY
        shouldThrow<SolanaJsonException> { SolanaJson.decodeF64(JsonPrimitive("nan")) }
        shouldThrow<SolanaJsonException> { SolanaJson.decodeF64(json("1e400")) }.reason shouldBe "Value is out of range for f64"
        shouldThrow<SolanaJsonException> { SolanaJson.decodeF32(JsonNull) }
    }

    test("addresses are base58 and bytes standard base64") {
        val address = SolanaAddress("11111111111111111111111111111111")
        SolanaJson.encodeAddress(address) shouldBe JsonPrimitive("11111111111111111111111111111111")
        SolanaJson.decodeAddress(JsonPrimitive("11111111111111111111111111111111")) shouldBe address
        shouldThrow<SolanaJsonException> { SolanaJson.decodeAddress(JsonPrimitive("111")) }.reason shouldContain "Invalid base58 public key"

        val bytes = SolanaBytes.fromBytes(byteArrayOf(-5, -1))
        SolanaJson.encodeBytes(bytes) shouldBe JsonPrimitive("+/8=")
        SolanaJson.decodeBytes(JsonPrimitive("+/8=")) shouldBe bytes
        SolanaJson.decodeBytes(JsonPrimitive("")) shouldBe SolanaBytes.EMPTY
        shouldThrow<SolanaJsonException> { SolanaJson.decodeBytes(JsonPrimitive("-_8=")) }
        shouldThrow<SolanaJsonException> { SolanaJson.decodeFixedBytes(JsonPrimitive("+/8="), 3) }.reason shouldBe "Expected 3 bytes, got 2"
    }

    test("decoding failures report the path to the offending value") {
        val error = shouldThrow<SolanaJsonException> {
            SolanaJson.at("outer") { SolanaJson.decodeVec(json("""[1, [2, "x"]]""")) { item -> SolanaJson.decodeVec(item) { SolanaJson.decodeU8(it) } } }
        }
        error.path shouldBe "outer[0]"
        val nested = shouldThrow<SolanaJsonException> {
            SolanaJson.decodeVec(json("""[[1], [2, "x"]]""")) { item -> SolanaJson.decodeVec(item) { SolanaJson.decodeU8(it) } }
        }
        nested.path shouldBe "[1][1]"
        nested.message shouldBe "[1][1]: Expected a JSON integer for u8, got a string"

        // other argument failures are attributed to the value they came from
        shouldThrow<SolanaJsonException> { SolanaJson.at("field") { require(false) { "bad" } } }.message shouldBe "field: bad"
    }
})
