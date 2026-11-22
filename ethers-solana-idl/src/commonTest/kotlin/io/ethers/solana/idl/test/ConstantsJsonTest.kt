package io.ethers.solana.idl.test

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json

// The constants program is generated with a JSON form, so its composite constants are values of serializable types.
class ConstantsJsonTest : FunSpec({
    test("struct and enum constants round-trip through JSON") {
        Json.encodeToString(Constants.ORIGIN) shouldBe """{"x":0,"y":-5}"""
        Json.encodeToString(Constants.PAIR) shouldBe """[255,"-9223372036854775808"]"""
        Json.encodeToString(Constants.MARKER) shouldBe "{}"
        Json.encodeToString<Constants.Kind>(Constants.KIND_PLAIN) shouldBe "\"Plain\""
        Json.encodeToString<Constants.Kind>(Constants.KIND_TUPLE) shouldBe """{"Tuple":[7,-300]}"""
        Json.encodeToString<Constants.Kind>(Constants.KIND_NAMED) shouldBe """{"Named":{"lo":"18446744073709551615","hi":null}}"""

        Json.decodeFromString<Constants.Point>(Json.encodeToString(Constants.ORIGIN)) shouldBe Constants.ORIGIN
        Json.decodeFromString<Constants.Duo>(Json.encodeToString(Constants.PAIR)) shouldBe Constants.PAIR
        Json.decodeFromString<Constants.Marker>("{}") shouldBe Constants.MARKER
        for (kind in listOf(Constants.KIND_PLAIN, Constants.KIND_TUPLE, Constants.KIND_NAMED)) {
            Json.decodeFromString<Constants.Kind>(Json.encodeToString(kind)) shouldBe kind
        }
        Json.decodeFromString<List<Constants.Kind>>(Json.encodeToString(Constants.KINDS)) shouldBe Constants.KINDS
    }

    test("a constant holding every kind of field, and a zero-copy one, round-trip exactly") {
        val limits = Json.encodeToString(Constants.LIMITS)
        limits shouldContain """"big":"-170141183460469231731687303715884105728""""
        limits shouldContain """"nested":[null]"""
        Json.decodeFromString<Constants.Limits>(limits) shouldBe Constants.LIMITS
        Json.decodeFromString<Constants.Reading>(Json.encodeToString(Constants.READING)) shouldBe Constants.READING
    }
})
