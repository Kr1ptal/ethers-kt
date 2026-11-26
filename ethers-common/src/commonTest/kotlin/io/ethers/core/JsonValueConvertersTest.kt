package io.ethers.core

import io.github.artificialpb.bignum.bigDecimalOf
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

class JsonValueConvertersTest : FunSpec({
    test("BigInteger is encoded as a JSON number, not a string") {
        val element = bigIntegerOf(20L).toJsonElement()

        element.jsonPrimitive.isString shouldBe false
        element.toString() shouldBe "20"
    }

    test("BigInteger beyond Long and Double precision keeps every digit") {
        val value = "123456789012345678901234567890123456789"
        val element = bigIntegerOf(value).toJsonElement()

        element.toString() shouldBe value
        // the literal must survive a round-trip through a JSON parser as the same number
        Json.parseToJsonElement(element.toString()).jsonPrimitive.content shouldBe value
    }

    test("negative and zero BigInteger are encoded as JSON numbers") {
        bigIntegerOf(-42L).toJsonElement().toString() shouldBe "-42"
        bigIntegerOf(0L).toJsonElement().toString() shouldBe "0"
    }

    test("BigDecimal is encoded as a plain JSON number without exponent notation") {
        bigDecimalOf("12.50").toJsonElement().toString() shouldBe "12.50"
        bigDecimalOf("1E+3").toJsonElement().toString() shouldBe "1000"
        bigDecimalOf("-0.000001").toJsonElement().toString() shouldBe "-0.000001"
        bigDecimalOf("12.5").toJsonElement().jsonPrimitive.isString shouldBe false
    }

    test("BigInteger nested in collections and maps is encoded as a JSON number") {
        listOf(bigIntegerOf(25L), bigIntegerOf(75L)).toJsonElement().toString() shouldBe "[25,75]"
        arrayOf(bigIntegerOf(1L)).toJsonElement().toString() shouldBe "[1]"
        mapOf("limit" to bigIntegerOf(10L)).toJsonElement().toString() shouldBe """{"limit":10}"""
    }

    test("String is still encoded as a JSON string") {
        "20".toJsonElement().jsonPrimitive.isString shouldBe true
    }

    test("JsonSerializable generic class is encoded with its own serializer") {
        Boxed(7).toJsonElement() shouldBe JsonObject(mapOf("boxed" to JsonPrimitive("7")))
    }

    test("JsonSerializable nested in collections and maps is encoded with its own serializer") {
        listOf(Boxed("a")).toJsonElement().toString() shouldBe """[{"boxed":"a"}]"""
        mapOf("k" to Boxed(1L)).toJsonElement().toString() shouldBe """{"k":{"boxed":"1"}}"""
    }

    test("generic @Serializable class without JsonSerializable fails with an actionable message") {
        val e = shouldThrow<IllegalArgumentException> { GenericPlain(1).toJsonElement() }

        e.message shouldContain "GenericPlain"
        e.message shouldContain "JsonSerializable"
    }

    test("non-generic @Serializable class is encoded by class lookup") {
        Plain(3).toJsonElement().toString() shouldBe """{"value":3}"""
    }
})

// generic class with a custom serializer, like TracerConfig: the plugin only generates Boxed.serializer(typeSerial0)
@Serializable(with = BoxedSerializer::class)
private data class Boxed<T>(val value: T) : JsonSerializable<Boxed<*>> {
    override val jsonSerializer get() = BoxedSerializer
}

private object BoxedSerializer : KSerializer<Boxed<*>> {
    override val descriptor = buildClassSerialDescriptor("Boxed")

    override fun serialize(encoder: Encoder, value: Boxed<*>) {
        (encoder as JsonEncoder).encodeJsonElement(JsonObject(mapOf("boxed" to JsonPrimitive(value.value.toString()))))
    }

    override fun deserialize(decoder: Decoder): Boxed<*> = throw UnsupportedOperationException()
}

@Serializable
private data class GenericPlain<T>(val value: T)

@Serializable
private data class Plain(val value: Int)
