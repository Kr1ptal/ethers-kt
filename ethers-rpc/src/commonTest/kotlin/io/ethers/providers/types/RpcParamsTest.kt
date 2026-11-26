package io.ethers.providers.types

import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigDecimalOf
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
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

// T is not reified here, so the params are built from Page<T> with T unknown
private fun <T> pageParams(page: Page<T>): RpcParams = rpcParams(page)

class RpcParamsTest : FunSpec({
    val addr = Address("0x00000000000000000000000000000000000000aa")

    fun RpcParams.json(): String = encode().toString()

    context("rpcParams captures static types") {
        test("basic values") {
            rpcParams(addr, "latest", 5, true).json() shouldBe """["0x00000000000000000000000000000000000000aa","latest",5,true]"""
            rpcParams(1L, 1.5, null as String?).json() shouldBe """[1,1.5,null]"""
            rpcParams().json() shouldBe "[]"
        }

        test("byte arrays are hex strings, not number arrays") {
            rpcParams(byteArrayOf(1, 2)).json() shouldBe """["0x0102"]"""
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

        test("collections of @Serializable values") {
            rpcParams(listOf(Filter(addr, 1)), mapOf("a" to Mode.FAST)).json() shouldBe
                """[[{"who":"0x00000000000000000000000000000000000000aa","limit":1}],{"a":"FAST"}]"""
        }

        test("sealed types follow their static type, as kotlinx does") {
            rpcParams<Shape>(Circle(2)).json() shouldBe """[{"type":"io.ethers.providers.types.Circle","r":2}]"""
            rpcParams(Circle(2)).json() shouldBe """[{"r":2}]"""
        }

        test("big number fields use the serializer declared on the field") {
            rpcParams(Amounts(bigIntegerOf(20L), 3)).json() shouldBe """[{"wei":"0x14","count":3}]"""
        }
    }

    context("byte arrays, big numbers and Any are handled wherever they appear in the type") {
        test("byte arrays nested in collections and generic classes are hex strings") {
            rpcParams(listOf(byteArrayOf(1), byteArrayOf(2, 3))).json() shouldBe """[["0x01","0x0203"]]"""
            rpcParams(mapOf("data" to byteArrayOf(1))).json() shouldBe """[{"data":"0x01"}]"""
            rpcParams(Page(listOf(byteArrayOf(1)), null)).json() shouldBe """[{"items":["0x01"],"cursor":null}]"""
        }

        test("big numbers as type arguments are JSON numbers") {
            rpcParams(Page(listOf(bigIntegerOf(7L)), bigIntegerOf(8L))).json() shouldBe """[{"items":[7],"cursor":8}]"""
            rpcParams(listOf(bigIntegerOf(25L), null)).json() shouldBe "[[25,null]]"
            rpcParams(Pair2(bigDecimalOf("1.50"), listOf(bigIntegerOf(1L)))).json() shouldBe """[{"first":1.50,"second":[1]}]"""
        }

        test("Any as a type argument is converted by the runtime value") {
            rpcParams(Page(listOf<Any>(1, "a", addr, Filter(addr, 2)), null)).json() shouldBe
                """[{"items":[1,"a","0x00000000000000000000000000000000000000aa",{"who":"0x00000000000000000000000000000000000000aa","limit":2}],"cursor":null}]"""
            rpcParams(mapOf<String, Any?>("page" to listOf(Mode.FAST), "none" to null)).json() shouldBe
                """[{"page":["FAST"],"none":null}]"""
        }

        test("star projections and type parameters of a generic function") {
            val star: Page<*> = Page(listOf(1, 2), 3)
            rpcParams(star).json() shouldBe """[{"items":[1,2],"cursor":3}]"""
            pageParams(Page(listOf(Mode.SLOW), null)).json() shouldBe """[{"items":["SLOW"],"cursor":null}]"""
        }
    }

    context("values without a static serializer fall back to their runtime type") {
        test("maps and lists of mixed values") {
            rpcParams(mapOf("from" to addr, "data" to byteArrayOf(1), "n" to 3, "nested" to listOf(1, "a"))).json() shouldBe
                """[{"from":"0x00000000000000000000000000000000000000aa","data":"0x01","n":3,"nested":[1,"a"]}]"""
        }

        test("bare big numbers are JSON numbers") {
            rpcParams(bigIntegerOf("123456789012345678901234567890"), bigDecimalOf("12.50")).json() shouldBe
                "[123456789012345678901234567890,12.50]"
        }

        test("JSON elements pass through") {
            rpcParams(buildJsonObject { put("a", 1) }, JsonPrimitive("x")).json() shouldBe """[{"a":1},"x"]"""
        }

        test("a class without a serializer fails with a message naming it and pointing at the plugin") {
            val error = shouldThrow<IllegalArgumentException> { rpcParams(NotAnnotated(1)).encode() }
            error.message shouldContain "NotAnnotated"
            error.message shouldContain "applies the kotlinx serialization plugin"
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

        test("explicit serializer") {
            buildRpcParams { add(listOf(bigIntegerOf(20L)), ListSerializer(HexBigIntegerSerializer)) }.json() shouldBe """[["0x14"]]"""
        }
    }

    // only the API Java can call: no inline or reified functions
    context("builder") {
        test("explicit serializer for a generic class, and addDynamic") {
            val page = Page(listOf(Filter(addr, 1)), null)
            val params = RpcParams.builder()
                .add(page, Page.serializer(Filter.serializer()))
                .addDynamic("latest")
                .addDynamic(byteArrayOf(1, 2))
                .build()
            params.size shouldBe 3
            params[0] shouldBe page
            params.json() shouldBe
                """[{"items":[{"who":"0x00000000000000000000000000000000000000aa","limit":1}],"cursor":null},"latest","0x0102"]"""
        }

        test("an explicit serializer is used even where the static type would pick another") {
            RpcParams.builder().add(bigIntegerOf(20L), HexBigIntegerSerializer).build().json() shouldBe """["0x14"]"""
        }

        test("an empty builder gives EMPTY") {
            RpcParams.builder().build() shouldBeSameInstanceAs RpcParams.EMPTY
        }

        test("each builder is new") {
            RpcParams.builder().addDynamic(1).build()
            RpcParams.builder().build().isEmpty() shouldBe true
        }
    }

    context("ofDynamic and ofJson") {
        test("basic values and non-generic @Serializable classes") {
            RpcParams.ofDynamic(addr, "latest", Filter(addr, 1), null).json() shouldBe
                """["0x00000000000000000000000000000000000000aa","latest",{"who":"0x00000000000000000000000000000000000000aa","limit":1},null]"""
        }

        test("a generic class can't be encoded from its runtime value") {
            val error = shouldThrow<IllegalArgumentException> { RpcParams.ofDynamic(Page(listOf(1), null)).encode() }
            error.message shouldContain "Page"
            error.message shouldContain "generic"
        }

        test("the caller's array is copied") {
            val values = arrayOf<Any?>("a", "b")
            val params = RpcParams.ofDynamic(*values)
            values[1] = "changed"
            params.json() shouldBe """["a","b"]"""
        }

        test("ofJson") {
            RpcParams.ofJson(listOf(JsonPrimitive(1), JsonPrimitive("x"))).json() shouldBe """[1,"x"]"""
        }
    }

    context("inspection") {
        test("size, get, toList and drop expose the values as passed in") {
            val params = rpcParams("signature", addr, 5)
            params.size shouldBe 3
            params.isEmpty() shouldBe false
            params[0] shouldBe "signature"
            params.toList() shouldBe listOf("signature", addr, 5)
            params.drop(1).json() shouldBe """["0x00000000000000000000000000000000000000aa",5]"""
            params.drop(3).isEmpty() shouldBe true
            RpcParams.EMPTY.isEmpty() shouldBe true
        }

        test("toString is the encoded JSON, and never throws") {
            rpcParams("a", 1).toString() shouldBe """["a",1]"""
            rpcParams(NotAnnotated(1)).toString() shouldContain "unencodable"
        }
    }
})
