package io.ethers.solana.idl.generator

import io.ethers.solana.idl.IdlArrayLength
import io.ethers.solana.idl.IdlConstant
import io.ethers.solana.idl.IdlEnumVariant
import io.ethers.solana.idl.IdlField
import io.ethers.solana.idl.IdlFields
import io.ethers.solana.idl.IdlProgram
import io.ethers.solana.idl.IdlType
import io.ethers.solana.idl.IdlTypeBody
import io.ethers.solana.idl.IdlTypeDef
import io.ethers.solana.idl.reader.SolanaIdlReaderRegistry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith

private val POINT = IdlTypeDef(
    "geo::Point",
    IdlTypeBody.Struct(IdlFields.Named(listOf(IdlField("x", IdlType.Primitive.I32), IdlField("y_pos", IdlType.Primitive.I32)))),
)
private val EMPTY = IdlTypeDef("Empty", IdlTypeBody.Struct(IdlFields.None))
private val KIND = IdlTypeDef(
    "Kind",
    IdlTypeBody.Enum(
        listOf(
            IdlEnumVariant("Plain"),
            IdlEnumVariant("Tuple", IdlFields.Tuple(listOf(IdlType.Primitive.U8, IdlType.Primitive.I16))),
            IdlEnumVariant("Named", IdlFields.Named(listOf(IdlField("lo", IdlType.Primitive.U64)))),
        ),
    ),
)
private val ALIAS = IdlTypeDef("Points", IdlTypeBody.Alias(IdlType.Vec(IdlType.Defined("geo::Point"))))

private fun array(element: IdlType, size: Int) = IdlType.Array(element, IdlArrayLength.Fixed(size))

private fun defined(name: String) = IdlType.Defined(name)

private fun generate(type: IdlType, value: String): String = SolanaProgramBuilder(
    IdlProgram("test", "1.0.0", "11111111111111111111111111111111", types = listOf(POINT, EMPTY, KIND, ALIAS), constants = listOf(IdlConstant("C", type, value))),
    "test.pkg",
).build().toString()

private fun rejected(type: IdlType, value: String): String = shouldThrow<IllegalArgumentException> { generate(type, value) }.message!!

private const val NOT_LITERAL = "only literal values are supported, not expressions, casts or references to other constants"

class CompositeConstantsTest : FunSpec({
    context("values Anchor writes with Debug generate as vals") {
        test("arrays and vecs") {
            generate(array(IdlType.Primitive.U16, 3), "[1, 2, 65535]") shouldContain "public val C: List<Int> = listOf<Int>(1, 2, 65535)"
            generate(array(IdlType.Primitive.U8, 3), "[1, 2, 255]") shouldContain "public val C: SolanaBytes = SolanaBytes.fromHex(\"0102ff\")"
            generate(IdlType.Vec(IdlType.Primitive.U8), "[]") shouldContain "public val C: List<Int> = emptyList<Int>()"
            generate(IdlType.Vec(IdlType.Primitive.I64), "[-9223372036854775808]") shouldContain "listOf<Long>(Long.MIN_VALUE)"
            generate(IdlType.Vec(IdlType.Primitive.I32), "[-2147483648]") shouldContain "listOf<Int>(Int.MIN_VALUE)"
            generate(IdlType.Vec(IdlType.Primitive.U128), "[1]") shouldContain "listOf<BigInteger>(BigInteger(\"1\"))"
            generate(IdlType.Vec(IdlType.Primitive.BYTES), "[[1], []]") shouldContain "listOf<SolanaBytes>(SolanaBytes.fromHex(\"01\"), SolanaBytes.fromHex(\"\"))"
            generate(IdlType.Vec(IdlType.Primitive.STRING), """["a\"b\\", "\u{e9}\t"]""") shouldContain "listOf<String>(\"a\\\"b\\\\\", \"é\\t\")"
            generate(IdlType.Vec(IdlType.Primitive.F64), "[1e-7, -0.0, inf, NaN]") shouldContain
                "listOf<Double>(1.0E-7, -0.0, Double.POSITIVE_INFINITY, Double.NaN)"
            generate(IdlType.Vec(IdlType.Primitive.F32), "[0.1, -inf]") shouldContain "listOf<Float>(0.1f, Float.NEGATIVE_INFINITY)"
            generate(IdlType.Vec(IdlType.Primitive.PUBKEY), "[11111111111111111111111111111111]") shouldContain
                "listOf<SolanaAddress>(SolanaAddress(\"11111111111111111111111111111111\"))"
            generate(IdlType.Vec(IdlType.Primitive.BOOL), "[true, false]") shouldContain "listOf<Boolean>(true, false)"
        }

        test("options, nested ones explicit") {
            generate(IdlType.Option(IdlType.Primitive.U8), "Some(5)") shouldContain "public val C: Int? = 5"
            generate(IdlType.Option(IdlType.Primitive.U8), "None") shouldContain "public val C: Int? = null"
            val nested = IdlType.Option(IdlType.Option(IdlType.Primitive.BOOL))
            generate(nested, "Some(None)") shouldContain "public val C: SolanaOption<Boolean?> = SolanaOption.Some<Boolean?>(null)"
            generate(nested, "Some(Some(true))") shouldContain "SolanaOption.Some<Boolean?>(true)"
            generate(nested, "None") shouldContain "public val C: SolanaOption<Boolean?> = SolanaOption.None"
        }

        test("structs by named field, matched by their Rust name, and fieldless structs") {
            generate(defined("geo::Point"), "Point { x: 1, y_pos: -2 }") shouldContain "public val C: Point = Point(x = 1, yPos = -2)"
            generate(defined("Empty"), "Empty") shouldContain "public val C: Empty = Empty"
            generate(defined("Points"), "[Point { x: 1, y_pos: 2 }]") shouldContain "public val C: List<Point> = listOf<Point>(Point(x = 1, yPos = 2))"
        }

        test("enum variants of each form") {
            generate(defined("Kind"), "Plain") shouldContain "public val C: Kind = Kind.Plain"
            generate(defined("Kind"), "Tuple(1, -2)") shouldContain "public val C: Kind = Kind.Tuple(1, -2)"
            generate(defined("Kind"), "Named { lo: 18446744073709551615 }") shouldContain "Kind.Named(lo = BigInteger(\"18446744073709551615\"))"
        }

        test("legacy token text, with spaces between tokens, underscores and suffixes") {
            generate(IdlType.Option(IdlType.Vec(IdlType.Primitive.U32)), "Some ([1_000u32 , 2])") shouldContain "listOf<Long>(1000L, 2L)"
            generate(defined("geo::Point"), "Point { x : 1 , y_pos : 2 }") shouldContain "Point(x = 1, yPos = 2)"
        }

        test("a constant's docs and the @JvmField annotation are kept") {
            val source = SolanaProgramBuilder(
                IdlProgram("test", "1.0.0", "11111111111111111111111111111111", constants = listOf(IdlConstant("C", IdlType.Vec(IdlType.Primitive.U8), "[1]", listOf("Doc.")))),
                "test.pkg",
            ).build().toString()
            source shouldContain "/**\n     * Doc.\n     */\n    @JvmField\n    public val C: List<Int> = listOf<Int>(1)"
        }

        test("every constant of Anchor's own external test IDL, its types aside") {
            val program = SolanaIdlReaderRegistry.tryReadIdl(CompositeConstantsTest::class.java.getResource("/idl/external.json")!!).unwrap()
            val source = SolanaProgramBuilder(program.copy(instructions = emptyList(), accounts = emptyList(), events = emptyList(), types = emptyList()), "test.pkg").build().toString()
            source shouldContain "public val ARRAY: SolanaBytes = SolanaBytes.fromHex(\"01020304\")"
            source shouldContain "public val NESTED_OPTION_ARRAY: SolanaOption<SolanaBytes?> ="
            source shouldContain "SolanaOption.Some<SolanaBytes?>(SolanaBytes.fromHex(\"01020304\"))"
            source shouldContain "public val PUBKEY: SolanaAddress = SolanaAddress(\"SomeAdress111111111111111111111111111111111\")"
            source shouldContain "public val OPTION_STRING: String? = \"abc\""
            source shouldContain "public val NESTED_OPTION_BOOL: SolanaOption<Boolean?> = SolanaOption.Some<Boolean?>(false)"
        }
    }

    context("values that are not a literal of the declared type are rejected") {
        test("integers outside their type, and non-decimal integers") {
            rejected(IdlType.Vec(IdlType.Primitive.U16), "[1, 65536]") shouldStartWith "constant 'C': '65536' is outside the range of U16"
            rejected(IdlType.Vec(IdlType.Primitive.I8), "[-129]") shouldStartWith "constant 'C': '-129' is outside the range of I8"
            rejected(IdlType.Vec(IdlType.Primitive.U8), "[-1]") shouldStartWith "constant 'C': '-1' is outside the range of U8"
            rejected(array(IdlType.Primitive.U8, 1), "[256]") shouldStartWith "constant 'C': '256' is outside the range of U8"
            rejected(IdlType.Vec(IdlType.Primitive.U64), "[0x10]") shouldStartWith "constant 'C': '0x10' is not a decimal U64 integer literal"
            rejected(IdlType.Vec(IdlType.Primitive.U64), "[1.5]") shouldStartWith "constant 'C': '1.5' is not a decimal U64 integer literal"
        }

        test("arrays of the wrong length") {
            rejected(array(IdlType.Primitive.U16, 3), "[1, 2]") shouldStartWith "constant 'C': expected 3 elements for an array of length 3, got 2"
            rejected(array(IdlType.Primitive.U8, 4), "[1, 2, 3, 4, 5]") shouldStartWith "constant 'C': expected 4 elements for [u8; 4], got 5"
        }

        test("floats that are not decimal literals or do not fit") {
            rejected(IdlType.Vec(IdlType.Primitive.F32), "[1e39]") shouldStartWith "constant 'C': '1e39' is outside the range of F32"
            rejected(IdlType.Vec(IdlType.Primitive.F64), "[1.5.2]") shouldStartWith "constant 'C': '1.5.2' is not a decimal F64 literal"
            rejected(IdlType.Vec(IdlType.Primitive.F64), "[Infinity]") shouldStartWith "constant 'C': 'Infinity' is not a decimal F64 literal"
        }

        test("invalid addresses, bools and strings") {
            rejected(IdlType.Vec(IdlType.Primitive.PUBKEY), "[0OIl]") shouldStartWith "constant 'C': '0OIl' is not a base58 address"
            rejected(IdlType.Vec(IdlType.Primitive.PUBKEY), "[1111]") shouldStartWith "constant 'C': '1111' decodes to 4 bytes, but an address has 32"
            rejected(IdlType.Vec(IdlType.Primitive.BOOL), "[yes]") shouldStartWith "constant 'C': 'yes' is not 'true' or 'false'"
            rejected(IdlType.Vec(IdlType.Primitive.STRING), "[abc]") shouldStartWith "constant 'C': expected a string literal but got 'abc'"
            rejected(IdlType.Vec(IdlType.Primitive.STRING), "[\"abc]") shouldStartWith "constant 'C': unterminated string literal at offset 1"
            rejected(IdlType.Vec(IdlType.Primitive.STRING), "[\"a\\qb\"]") shouldStartWith "constant 'C': unsupported escape at offset 3"
            rejected(IdlType.Vec(IdlType.Primitive.STRING), "[\"\\u{d800}\"]") shouldStartWith "constant 'C': unsupported escape"
        }

        test("expressions, casts, paths, byte strings and references to other constants") {
            rejected(IdlType.Vec(IdlType.Primitive.U8), "[1 + 2]") shouldBe
                "constant 'C': expected ']' but got '+' at offset 3 of value `[1 + 2]`; $NOT_LITERAL"
            rejected(IdlType.Option(IdlType.Primitive.U64), "Some(5 as u64)") shouldContain "expected ')' but got 'as'"
            rejected(IdlType.Option(IdlType.Primitive.U64), "Some(MAX_FEE)") shouldStartWith "constant 'C': 'MAX_FEE' is not a decimal U64 integer literal"
            rejected(IdlType.Vec(IdlType.Primitive.U64), "vec![1]") shouldContain "expected '[' but got 'vec!'"
            rejected(array(IdlType.Primitive.U8, 3), "*b\"abc\"") shouldContain "expected '[' but got '*b'"
            rejected(defined("Kind"), "Kind::Plain") shouldStartWith "constant 'C': 'Kind' is not a variant of enum 'Kind'"
            rejected(defined("geo::Point"), "Point::new(1, 2)") shouldContain "expected '{' but got ':'"
            rejected(IdlType.Vec(IdlType.Primitive.U8), "[1] [2]") shouldContain "expected the end of the value but got '['"
            rejected(IdlType.Vec(IdlType.Primitive.U8), "[1, 2,]") shouldContain "expected a U8 integer literal but got ']'"
            rejected(IdlType.Vec(IdlType.Primitive.U8), "[1, 2") shouldContain "expected ']' but got the end of the value"
        }

        test("options that are not Some or None") {
            rejected(IdlType.Option(IdlType.Primitive.U8), "Maybe(1)") shouldStartWith "constant 'C': 'Maybe' is not 'Some(…)' or 'None'"
            rejected(IdlType.Option(IdlType.Primitive.U8), "5") shouldStartWith "constant 'C': '5' is not 'Some(…)' or 'None'"
        }

        test("structs with missing, extra, misordered or misnamed fields, or of another type") {
            rejected(defined("geo::Point"), "Point { x: 1 }") shouldStartWith "constant 'C': struct 'geo::Point' has 2 fields, but the value gives 1"
            rejected(defined("geo::Point"), "Point { y_pos: 1, x: 2 }") shouldStartWith
                "constant 'C': expected field 'x' of struct 'geo::Point', got 'y_pos'; every field must be given, in declaration order"
            rejected(defined("geo::Point"), "Point { x: 1, y_pos: 2, z: 3 }") shouldStartWith "constant 'C': the value gives more than the 2 fields of struct 'geo::Point'"
            rejected(defined("geo::Point"), "Point { x: 1, yPos: 2 }") shouldStartWith "constant 'C': expected field 'y_pos'"
            rejected(defined("geo::Point"), "Point(1, 2)") shouldContain "expected '{' but got '('"
            rejected(defined("geo::Point"), "Line { x: 1, y_pos: 2 }") shouldStartWith
                "constant 'C': 'Line' is not a value of struct 'geo::Point', which Debug writes as 'Point'"
            rejected(defined("Empty"), "Empty {}") shouldStartWith "constant 'C': struct 'Empty' has no fields, so Debug writes only its name"
        }

        test("enums with unknown variants or fields of the wrong shape") {
            rejected(defined("Kind"), "Other") shouldStartWith "constant 'C': 'Other' is not a variant of enum 'Kind', which has 'Plain', 'Tuple', 'Named'"
            rejected(defined("Kind"), "Tuple(1)") shouldStartWith "constant 'C': variant 'Tuple' of enum 'Kind' has 2 fields, but the value gives 1"
            rejected(defined("Kind"), "Tuple(1, 2, 3)") shouldStartWith "constant 'C': the value gives more than the 2 fields of variant 'Tuple' of enum 'Kind'"
            rejected(defined("Kind"), "Plain(1)") shouldStartWith "constant 'C': variant 'Plain' of enum 'Kind' has no fields"
            rejected(defined("Kind"), "Named(1)") shouldContain "expected '{' but got '('"
        }
    }
})
