package io.ethers.providers.types

import io.ethers.core.BigIntegerNumberSerializer
import io.ethers.providers.HexByteArraySerializer
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigDecimalOf
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// stands in for a library type with a custom serializer, like Address
@Serializable(with = AddressSerializer::class)
private class Address(val hex: String) {
    override fun equals(other: Any?) = other is Address && other.hex == hex
    override fun hashCode() = hex.hashCode()
}

private object AddressSerializer : KSerializer<Address> {
    override val descriptor = PrimitiveSerialDescriptor("Address", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Address) = encoder.encodeString(value.hex)
    override fun deserialize(decoder: Decoder) = Address(decoder.decodeString())
}

// encodes a big number as a hex quantity, like HexBigIntegerSerializer
private object HexBigIntegerSerializer : KSerializer<BigInteger> {
    override val descriptor = PrimitiveSerialDescriptor("HexBigInteger", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: BigInteger) = encoder.encodeString("0x" + value.toString(16))
    override fun deserialize(decoder: Decoder): BigInteger = throw UnsupportedOperationException()
}

@Serializable
private data class Filter(val who: Address, val limit: Int)

@Serializable
private data class Page<T>(val items: List<T>, val cursor: T?)

@Serializable
private data class Pair2<A, B>(val first: A, val second: B)

@Serializable
private data class Amounts(@Serializable(with = HexBigIntegerSerializer::class) val wei: BigInteger, val count: Long)

@Serializable
private enum class Mode { FAST, SLOW }

@Serializable
private sealed interface Shape

@Serializable
private data class Circle(val r: Int) : Shape

private data class NotAnnotated(val x: Int)

/** Writes [Counter.value] and counts its encodings; throws on the encodings listed in [failOn]. */
private class Counter(var value: String, private val failOn: Set<Int> = emptySet()) {
    var encodings = 0
        private set

    val serializer = object : SerializationStrategy<Counter> {
        override val descriptor = PrimitiveSerialDescriptor("Counter", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: Counter) {
            val n = ++encodings
            if (n in failOn) throw IllegalStateException("encoding $n fails")
            encoder.encodeString(value.value)
        }
    }

    fun params(): RpcParams = buildRpcParams {
        add("a")
        add(this@Counter, serializer)
    }
}

class RpcParamsTest : FunSpec({
    val addr = Address("0x00000000000000000000000000000000000000aa")

    fun RpcParams.json(): String = encode().toString()

    context("add(value) uses the serializer of the static type") {
        test("basic values") {
            rpcParams(addr, "latest", 5, true).json() shouldBe """["0x00000000000000000000000000000000000000aa","latest",5,true]"""
            rpcParams(1L, 1.5, null as String?).json() shouldBe """[1,1.5,null]"""
            rpcParams().json() shouldBe "[]"
        }

        test("the plain overloads for strings, booleans, numbers and JSON") {
            val params = buildRpcParams {
                add("a")
                add(false)
                add(7)
                add(Long.MAX_VALUE)
                add(buildJsonObject { put("x", 1) })
                add(JsonPrimitive("y"))
            }
            params.json() shouldBe """["a",false,7,9223372036854775807,{"x":1},"y"]"""
        }

        test("nullable values pick the generic overload and encode null") {
            buildRpcParams {
                add(null as String?)
                add(null as Int?)
                add(null as Filter?)
            }.json() shouldBe "[null,null,null]"
        }

        test("a generic type argument resolves to the same serializer as the plain overload") {
            // rpcParams adds its reified A through the generic add, buildRpcParams through the plain overload
            rpcParams("a").json() shouldBe buildRpcParams { add("a") }.json()
            rpcParams(3).json() shouldBe buildRpcParams { add(3) }.json()
            rpcParams<JsonElement>(JsonNull).json() shouldBe buildRpcParams { add(JsonNull) }.json()
        }

        test("@Serializable classes, enums and nullable values") {
            rpcParams(Filter(addr, 10), Mode.FAST, null as Filter?).json() shouldBe
                """[{"who":"0x00000000000000000000000000000000000000aa","limit":10},"FAST",null]"""
        }

        test("generic classes, including nested ones") {
            rpcParams(Page(listOf(Filter(addr, 1)), null)).json() shouldBe
                """[{"items":[{"who":"0x00000000000000000000000000000000000000aa","limit":1}],"cursor":null}]"""
            rpcParams(Page(listOf(1, 2), 3)).json() shouldBe """[{"items":[1,2],"cursor":3}]"""
            rpcParams(Pair2("x", Page(listOf(Mode.SLOW), null))).json() shouldBe
                """[{"first":"x","second":{"items":["SLOW"],"cursor":null}}]"""
        }

        test("typed collections and maps") {
            rpcParams(listOf(Filter(addr, 1)), mapOf("a" to Mode.FAST), setOf(addr)).json() shouldBe
                """[[{"who":"0x00000000000000000000000000000000000000aa","limit":1}],{"a":"FAST"},["0x00000000000000000000000000000000000000aa"]]"""
            rpcParams(mapOf("forking" to mapOf("url" to "http://x"))).json() shouldBe """[{"forking":{"url":"http://x"}}]"""
        }

        test("sealed types follow their static type, as kotlinx does") {
            rpcParams<Shape>(Circle(2)).json() shouldBe """[{"type":"io.ethers.providers.types.Circle","r":2}]"""
            rpcParams(Circle(2)).json() shouldBe """[{"r":2}]"""
        }

        test("a field uses the serializer declared on it") {
            rpcParams(Amounts(bigIntegerOf(20L), 3)).json() shouldBe """[{"wei":"0x14","count":3}]"""
        }

        test("JSON elements pass through") {
            rpcParams(buildJsonObject { put("a", 1) }, JsonPrimitive("x"), JsonNull).json() shouldBe """[{"a":1},"x",null]"""
        }

        test("a byte array uses kotlinx's own serializer, an array of numbers, unless one is passed") {
            rpcParams(byteArrayOf(1, 2)).json() shouldBe """[[1,2]]"""
            buildRpcParams { add(byteArrayOf(1, 2), HexByteArraySerializer) }.json() shouldBe """["0x0102"]"""
        }
    }

    context("a value without a serializer fails where the params are built") {
        test("a class without @Serializable names the type and says how to fix it") {
            val error = shouldThrow<IllegalArgumentException> { rpcParams(NotAnnotated(1)) }
            error.message shouldContain "NotAnnotated"
            error.message shouldContain "@Serializable"
            error.message shouldContain "kotlinx serialization plugin"
            error.message shouldContain "add(value, serializer)"
            error.cause.shouldBeInstanceOf<SerializationException>()
        }

        test("a map of mixed values") {
            val mixed: Map<String, Any> = mapOf("from" to addr, "n" to 3)
            val error = shouldThrow<IllegalArgumentException> { buildRpcParams { add(mixed) } }
            error.message shouldContain "Map"
            error.message shouldContain "@Serializable"
        }

        test("a bare big number") {
            val error = shouldThrow<IllegalArgumentException> { rpcParams(bigIntegerOf(1L)) }
            error.message shouldContain "BigInteger"
            shouldThrow<IllegalArgumentException> { rpcParams(bigDecimalOf("1.5")) }
        }
    }

    context("an explicit serializer gives any other encoding") {
        test("byte arrays as hex strings") {
            buildRpcParams { add(byteArrayOf(1, 2), HexByteArraySerializer) }.json() shouldBe """["0x0102"]"""
        }

        test("big numbers as hex quantities or JSON numbers") {
            val params = buildRpcParams {
                add(bigIntegerOf(20L), HexBigIntegerSerializer)
                add(bigIntegerOf("123456789012345678901234567890"), BigIntegerNumberSerializer)
                add(listOf(bigIntegerOf(20L)), ListSerializer(HexBigIntegerSerializer))
            }
            params.json() shouldBe """["0x14",123456789012345678901234567890,["0x14"]]"""
        }

        test("an explicit serializer is used even where the static type has another") {
            val upper = object : SerializationStrategy<String> {
                override val descriptor = PrimitiveSerialDescriptor("Upper", PrimitiveKind.STRING)
                override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value.uppercase())
            }
            buildRpcParams { add("latest", upper) }.json() shouldBe """["LATEST"]"""
        }

        test("a serializer that throws fails at encode, not when the params are built") {
            val failing = object : SerializationStrategy<Int> {
                override val descriptor = PrimitiveSerialDescriptor("Failing", PrimitiveKind.INT)
                override fun serialize(encoder: Encoder, value: Int) = throw IllegalStateException("boom")
            }
            val params = buildRpcParams { add(1, failing) }
            shouldThrow<IllegalStateException> { params.encode() }
            params.toString() shouldContain "unencodable: boom"
        }
    }

    context("buildRpcParams") {
        test("any number of parameters, added conditionally") {
            val overrides: Filter? = null
            val params = buildRpcParams {
                add(addr)
                add("latest")
                if (overrides != null) add(overrides)
                add(Page(listOf(1), null))
                add(5)
                add(false)
            }
            params.size shouldBe 5
            params.json() shouldBe """["0x00000000000000000000000000000000000000aa","latest",{"items":[1],"cursor":null},5,false]"""
        }
    }

    // only the API Java can call: no inline or reified functions
    context("builder") {
        test("explicit serializers and the plain overloads") {
            val page = Page(listOf(Filter(addr, 1)), null)
            val params = RpcParams.builder()
                .add(page, Page.serializer(Filter.serializer()))
                .add("latest")
                .add(true)
                .add(1)
                .add(2L)
                .add(JsonPrimitive("x"))
                .add(byteArrayOf(1, 2), HexByteArraySerializer)
                .build()
            params.size shouldBe 7
            params[0] shouldBe page
            params.json() shouldBe
                """[{"items":[{"who":"0x00000000000000000000000000000000000000aa","limit":1}],"cursor":null},"latest",true,1,2,"x","0x0102"]"""
        }

        test("an empty builder gives EMPTY") {
            RpcParams.builder().build() shouldBeSameInstanceAs RpcParams.EMPTY
        }

        test("each builder is new") {
            RpcParams.builder().add(1).build()
            RpcParams.builder().build().isEmpty() shouldBe true
        }
    }

    context("ofJson") {
        test("parameters that are already JSON") {
            RpcParams.ofJson(listOf(JsonPrimitive(1), JsonPrimitive("x"), JsonNull)).json() shouldBe """[1,"x",null]"""
            RpcParams.ofJson(emptyList()) shouldBeSameInstanceAs RpcParams.EMPTY
        }
    }

    context("the encoding is kept") {
        test("encode twice encodes once and returns the same array") {
            val counter = Counter("x")
            val params = counter.params()
            val first = params.encode()
            first.toString() shouldBe """["a","x"]"""
            params.encode() shouldBeSameInstanceAs first
            counter.encodings shouldBe 1
        }

        test("a failed encode keeps nothing, and the next one tries again") {
            val counter = Counter("x", failOn = setOf(1))
            val params = counter.params()
            shouldThrow<IllegalStateException> { params.encode() }.message shouldBe "encoding 1 fails"
            val second = params.encode()
            second.toString() shouldBe """["a","x"]"""
            params.encode() shouldBeSameInstanceAs second
            counter.encodings shouldBe 2
        }

        test("toString before any encode fills the cache") {
            val counter = Counter("x")
            val params = counter.params()
            val printed = params.toString()
            printed shouldBe """["a","x"]"""
            counter.encodings shouldBe 1

            // encode uses what toString encoded, even after the value changed
            counter.value = "y"
            params.encode().toString() shouldBe printed
            counter.encodings shouldBe 1
        }

        test("toString of params that fail to encode doesn't throw, and keeps nothing") {
            val counter = Counter("x", failOn = setOf(1, 2))
            val params = counter.params()
            params.toString() shouldContain "unencodable: encoding 1 fails"
            shouldThrow<IllegalStateException> { params.encode() }.message shouldBe "encoding 2 fails"
            params.encode().toString() shouldBe """["a","x"]"""
            counter.encodings shouldBe 3
        }

        test("a mutable value changed after the first encode is not picked up") {
            val counter = Counter("x")
            val params = counter.params()
            params.encode().toString() shouldBe """["a","x"]"""
            counter.value = "y"
            params.encode().toString() shouldBe """["a","x"]"""
            params.toString() shouldBe """["a","x"]"""
            // new params pick up the change
            counter.params().encode().toString() shouldBe """["a","y"]"""
        }

        test("drop gives new params that encode on their own") {
            val counter = Counter("x")
            val params = counter.params()
            params.encode()
            val dropped = params.drop(1)
            dropped.encode().toString() shouldBe """["x"]"""
            dropped.encode() shouldBeSameInstanceAs dropped.encode()
            counter.encodings shouldBe 2
        }

        test("EMPTY and ofJson") {
            RpcParams.EMPTY.encode() shouldBeSameInstanceAs RpcParams.EMPTY.encode()
            RpcParams.EMPTY.encode().size shouldBe 0
            val json = RpcParams.ofJson(listOf(JsonPrimitive(1)))
            json.encode() shouldBeSameInstanceAs json.encode()
            json.toString() shouldBe "[1]"
        }
    }

    context("inspection") {
        test("size, get, toList and drop expose the values as passed in") {
            val params = rpcParams("signature", addr, 5)
            params.size shouldBe 3
            params.isEmpty() shouldBe false
            params[0] shouldBe "signature"
            params.toList() shouldBe listOf("signature", addr, 5)
            params.drop(0) shouldBeSameInstanceAs params
            params.drop(1).toList() shouldBe listOf(addr, 5)
            params.drop(1).json() shouldBe """["0x00000000000000000000000000000000000000aa",5]"""
            params.drop(3).isEmpty() shouldBe true
            RpcParams.EMPTY.isEmpty() shouldBe true
        }

        test("toString is the encoded JSON") {
            rpcParams("a", 1).toString() shouldBe """["a",1]"""
        }
    }
})
