package io.ethers.solana.idl.generator

import io.ethers.solana.idl.IdlAccount
import io.ethers.solana.idl.IdlAccountItem
import io.ethers.solana.idl.IdlArrayLength
import io.ethers.solana.idl.IdlConstant
import io.ethers.solana.idl.IdlDiscriminator
import io.ethers.solana.idl.IdlEnumVariant
import io.ethers.solana.idl.IdlEvent
import io.ethers.solana.idl.IdlField
import io.ethers.solana.idl.IdlFields
import io.ethers.solana.idl.IdlGenericArg
import io.ethers.solana.idl.IdlGenericParam
import io.ethers.solana.idl.IdlInstruction
import io.ethers.solana.idl.IdlPda
import io.ethers.solana.idl.IdlProgram
import io.ethers.solana.idl.IdlRepr
import io.ethers.solana.idl.IdlSeed
import io.ethers.solana.idl.IdlSerialization
import io.ethers.solana.idl.IdlType
import io.ethers.solana.idl.IdlTypeBody
import io.ethers.solana.idl.IdlTypeDef
import io.ethers.solana.idl.reader.SolanaIdlReaderRegistry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private fun fixture(name: String): IdlProgram = SolanaIdlReaderRegistry.tryReadIdl(SolanaProgramBuilderTest::class.java.getResource("/idl/$name.json")!!).unwrap()

private fun program(
    instructions: List<IdlInstruction> = listOf(IdlInstruction("ix", IdlDiscriminator(listOf(1)))),
    types: List<IdlTypeDef> = emptyList(),
    constants: List<IdlConstant> = emptyList(),
) = IdlProgram("test", "1.0.0", "11111111111111111111111111111111", instructions = instructions, types = types, constants = constants)

private fun struct(name: String) = IdlTypeDef(name, IdlTypeBody.Struct(IdlFields.Named(listOf(IdlField("x", IdlType.Primitive.U8)))))

private fun generate(program: IdlProgram): String = SolanaProgramBuilder(program, "test.pkg").build().toString()

class SolanaProgramBuilderTest : FunSpec({
    test("generates an Anchor IDL whose layouts are all Borsh") {
        val source = generate(fixture("relations"))
        source shouldContain "public object Relations"
        source shouldContain "public data class MyAccount("
        source shouldContain "public fun testRelation("
    }

    context("generic type definitions") {
        val t = IdlType.Generic("T")
        val typeParam = IdlGenericParam.Type("T")
        fun constParam(type: String = "usize") = IdlGenericParam.Const("N", type)
        fun arg(type: IdlType) = IdlGenericArg.Type(type)
        fun defined(name: String, vararg args: IdlGenericArg) = IdlType.Defined(name, args.toList())
        fun generic(name: String, params: List<IdlGenericParam>, vararg fields: Pair<String, IdlType>) = IdlTypeDef(name, IdlTypeBody.Struct(IdlFields.Named(fields.map { (field, type) -> IdlField(field, type) })), generics = params)
        fun user(vararg fields: IdlType) = IdlTypeDef("User", IdlTypeBody.Struct(IdlFields.Named(fields.mapIndexed { i, type -> IdlField("f$i", type) })))
        val pair = generic("Pair", listOf(IdlGenericParam.Type("A"), IdlGenericParam.Type("B")), "first" to IdlType.Generic("A"), "second" to IdlType.Generic("B"))
        val buffer = generic("Buffer", listOf(typeParam, constParam()), "items" to IdlType.Array(t, IdlArrayLength.Generic("N")))
        fun rejected(vararg types: IdlTypeDef) = shouldThrow<IllegalArgumentException> { generate(program(types = types.toList())) }.message
        fun flat(source: String) = source.replace(Regex("\\s+"), " ")
        fun flat(vararg types: IdlTypeDef) = flat(generate(program(types = types.toList())))

        test("Anchor's generics IDL generates a class per instantiation, nested and with const arguments passed on") {
            val source = flat(generate(fixture("generics")))
            source shouldContain "public data class GenericAccount( public val `data`: GenericTypeU32U64N10,"
            source shouldContain "* Instantiation `GenericType<u32, u64, 10>` of the generic `GenericType`."
            // a generic within a generic within a generic
            source shouldContain "gen7 = GenericNestedU32GenericNestedU32U64.read(this),"
            source shouldContain "arr = readFixedBytes(10),"
            // the const parameter passed on, and a const argument of the same value, are one instantiation
            source shouldContain "warr = WrappedU8ArrayN10.read(this), warrval = WrappedU8ArrayN10.read(this),"
            source shouldContain "enm2 = GenericEnumGenericNestedU32U64U32N30.read(this),"
            source shouldContain "field0 = readArray(30) { GenericNestedU32U64.read(this) },"
            source shouldContain "public val genericField: GenericTypeU32U64N10,"
            // the generic definitions themselves have no layout, so are not generated
            (source.contains("class GenericType(") || source.contains("class GenericNested(")) shouldBe false
        }

        test("instantiations are listed in place of their definition, in order of first use, and generation is deterministic") {
            val source = generate(fixture("generics"))
            // classes nested directly in the program object
            val classes = Regex("\n {4}public (?:data|sealed) class (\\w+)").findAll(source).map { it.groupValues[1] }
            classes.takeWhile { it != "GenericAccounts" }.toList() shouldBe listOf(
                "GenericAccount",
                "GenericEnumU32U64N10",
                "GenericEnumGenericNestedU32U64U32N30",
                "GenericNestedU32U64",
                "GenericNestedU32MyStruct",
                "GenericNestedU32GenericNestedU32U64",
                "GenericNestedGenericNestedU32U64U32",
                "GenericTypeU32U64N10",
                "MyStruct",
                "WrappedU8ArrayN10",
                "GenericArgs",
            )
            source shouldBe generate(fixture("generics"))
        }

        test("type arguments are named by their kind, and arguments the same once aliases are resolved are one instantiation") {
            val alias = IdlTypeDef("Amount", IdlTypeBody.Alias(IdlType.Primitive.U64))
            val source = flat(
                pair,
                alias,
                user(
                    defined("Pair", arg(IdlType.Defined("Amount")), arg(IdlType.Primitive.PUBKEY)),
                    defined("Pair", arg(IdlType.Primitive.U64), arg(IdlType.Primitive.PUBKEY)),
                    defined("Pair", arg(IdlType.Option(IdlType.Vec(IdlType.Primitive.STRING))), arg(IdlType.Array(IdlType.Primitive.U8, IdlArrayLength.Fixed(4)))),
                    defined("Pair", arg(IdlType.Primitive.BYTES), arg(IdlType.Primitive.BOOL)),
                ),
            )
            source shouldContain "public val f0: PairU64Pubkey, public val f1: PairU64Pubkey, public val f2: PairOptionVecStringArray4U8, public val f3: PairBytesBool,"
            Regex("data class PairU64Pubkey\\(").findAll(source).count() shouldBe 1
            source shouldContain "* Instantiation `Pair<u64, pubkey>` of the generic `Pair`."
            source shouldContain "* Instantiation `Pair<Option<Vec<string>>, [u8; 4]>` of the generic `Pair`."
        }

        test("a const argument fixes an array length, and the fixed size and offsets that follow it") {
            val account = user(
                defined("Buffer", arg(IdlType.Primitive.U16), IdlGenericArg.Const("003")),
                IdlType.Primitive.U8,
                defined("Buffer", arg(IdlType.Primitive.U16), IdlGenericArg.Const("3")),
            )
            val source = flat(generate(program(types = listOf(buffer, account)).copy(accounts = listOf(IdlAccount("User", IdlDiscriminator(listOf(1)))))))
            source shouldContain "public val f0: BufferU16N3, public val f1: Int, public val f2: BufferU16N3,"
            source shouldContain "items = readArray(3) { readU16() },"
            source shouldContain "SolanaAccountField<User, Int>(\"f1\", 6, 1,"
            source shouldContain "BufferU16N3.Path<User>(\"f2\", 7)"
            // the path into the instantiation describes its three elements
            source shouldContain "(name + \".items\", offset, 3, 2,"
        }

        test("a self-referential instantiation that does not grow is generated once") {
            val node = generic("Node", listOf(typeParam), "value" to t, "next" to IdlType.Option(defined("Node", arg(t))))
            flat(node, user(defined("Node", arg(IdlType.Primitive.U8)))) shouldContain "public val `value`: Int, public val next: NodeU8?,"
        }

        test("instantiations that would expand without end are rejected, unless the program never uses them") {
            val growing = generic("List", listOf(typeParam), "next" to IdlType.Option(defined("List", arg(IdlType.Vec(t)))))
            rejected(growing, user(defined("List", arg(IdlType.Primitive.U8)))) shouldBe
                "type 'List': instantiating it never ends, as generic parameter List.T flows back into itself through List.T -> List.T, growing at 'Vec<T>'"
            // through another definition
            val a = generic("A", listOf(typeParam), "b" to IdlType.Vec(defined("B", arg(IdlType.Option(t)))))
            val b = generic("B", listOf(IdlGenericParam.Type("U")), "a" to IdlType.Vec(defined("A", arg(IdlType.Generic("U")))))
            rejected(a, b, user(defined("A", arg(IdlType.Primitive.U8)))) shouldBe
                "type 'A': instantiating it never ends, as generic parameter A.T flows back into itself through A.T -> B.U -> A.T, growing at 'Option<T>'"
            generate(program(types = listOf(growing, a, b))) shouldContain "public object Test"
        }

        test("a generic alias is inlined at each use, and one that refers to itself is rejected") {
            val bounded = IdlTypeDef("Bounded", IdlTypeBody.Alias(defined("Buffer", arg(t), IdlGenericArg.Const("2"))), generics = listOf(typeParam))
            flat(buffer, bounded, user(defined("Bounded", arg(IdlType.Primitive.U8)))) shouldContain "public val f0: BufferU8N2,"
            val looping = IdlTypeDef("Loop", IdlTypeBody.Alias(IdlType.Vec(defined("Loop", arg(t)))), generics = listOf(typeParam))
            rejected(looping, user(defined("Loop", arg(IdlType.Primitive.U8)))) shouldBe
                "type 'User' field 'f0': generic alias 'Loop' refers to itself through Loop -> Loop"
        }

        test("an instantiation named like a declared type is rejected") {
            val pairOfBytes = user(defined("Pair", arg(IdlType.Primitive.U8), arg(IdlType.Primitive.U8)))
            rejected(pair, struct("PairU8U8"), pairOfBytes) shouldBe "generated type 'PairU8U8' is produced by both type 'PairU8U8' and type 'Pair<u8, u8>'"
            rejected(pair, struct("Pair<u8, u8>"), pairOfBytes) shouldBe "type 'User' field 'f0': instantiation 'Pair<u8, u8>' has the name of a declared type definition"
        }

        test("arguments that do not match the parameters in number or kind are rejected") {
            rejected(pair, user(defined("Pair", arg(IdlType.Primitive.U8)))) shouldBe "type 'User' field 'f0': 'Pair' takes 2 generic arguments, got 1"
            rejected(buffer, user(defined("Buffer", arg(IdlType.Primitive.U8), arg(IdlType.Primitive.U8)))) shouldBe
                "type 'User' field 'f0': generic parameter 'N' of 'Buffer' takes a const value"
            rejected(buffer, user(defined("Buffer", IdlGenericArg.Const("1"), IdlGenericArg.Const("1")))) shouldBe
                "type 'User' field 'f0': generic parameter 'T' of 'Buffer' takes a type"
        }

        test("const arguments must fit their parameter's type, and an array length an Int") {
            val small = generic("Small", listOf(constParam("u8")), "items" to IdlType.Array(IdlType.Primitive.U8, IdlArrayLength.Generic("N")))
            flat(small, user(defined("Small", IdlGenericArg.Const("255")))) shouldContain "readFixedBytes(255)"
            rejected(small, user(defined("Small", IdlGenericArg.Const("256")))) shouldBe
                "type 'User' field 'f0': const generic argument 256 is outside the range of 'N: u8' of 'Small'"
            rejected(buffer, user(defined("Buffer", arg(IdlType.Primitive.U8), IdlGenericArg.Const("2147483648")))) shouldBe
                "type 'Buffer<u8, 2147483648>' field 'items': array length 2147483648, from const generic parameter 'N', is too large"
            val flagged = generic("Flagged", listOf(constParam("bool")), "x" to IdlType.Primitive.U8)
            rejected(flagged, user(defined("Flagged", IdlGenericArg.Const("1")))) shouldBe
                "type 'User' field 'f0': const generic parameter 'N' of 'Flagged' has type 'bool', which is not supported"
        }

        test("an account or event laid out as a generic definition is rejected") {
            val node = generic("Node", listOf(typeParam), "value" to t)
            shouldThrow<IllegalArgumentException> {
                generate(program(types = listOf(node)).copy(accounts = listOf(IdlAccount("Node", IdlDiscriminator(listOf(1))))))
            }.message shouldBe "account 'Node': its type definition is generic, and the IDL does not say which instantiation the account holds"
            shouldThrow<IllegalArgumentException> {
                generate(program(types = listOf(node)).copy(events = listOf(IdlEvent("Node", IdlDiscriminator(listOf(1))))))
            }.message shouldBe "event 'Node': its type definition is generic, and the IDL does not say which instantiation the event holds"
        }

        test("instantiations are checked like any type, naming the instantiation") {
            val cell = IdlTypeDef(
                "Cell",
                IdlTypeBody.Struct(IdlFields.Named(listOf(IdlField("value", t)))),
                serialization = IdlSerialization.Bytemuck,
                repr = IdlRepr.C(packed = true),
                generics = listOf(typeParam),
            )
            flat(cell, user(defined("Cell", arg(IdlType.Primitive.U32)))) shouldContain "public data class CellU32("
            rejected(cell, user(defined("Cell", arg(IdlType.Primitive.STRING)))) shouldBe
                "type 'Cell<string>' field 'value': a zero-copy type cannot contain STRING, which varies in size"
        }

        test("a constant of an instantiation is read under the definition's own name, as Debug writes it") {
            val type = defined("Pair", arg(IdlType.Primitive.U8), arg(IdlType.Array(IdlType.Primitive.I8, IdlArrayLength.Fixed(2))))
            fun constant(value: String) = program(types = listOf(pair), constants = listOf(IdlConstant("C", type, value)))
            flat(generate(constant("Pair { first: 1, second: [-1, 2] }"))) shouldContain
                "public val C: PairU8Array2I8 = PairU8Array2I8(first = 1, second = listOf<Byte>(-1, 2))"
            shouldThrow<IllegalArgumentException> { generate(constant("PairU8Array2I8 { first: 1, second: [-1, 2] }")) }.message shouldContain
                "'PairU8Array2I8' is not a value of struct 'Pair<u8, [i8; 2]>', which Debug writes as 'Pair'"
            // the const argument fixes the array length the value must have
            shouldThrow<IllegalArgumentException> {
                generate(program(types = listOf(buffer), constants = listOf(IdlConstant("C", defined("Buffer", arg(IdlType.Primitive.U8), IdlGenericArg.Const("2")), "Buffer { items: [1] }"))))
            }.message shouldContain "constant 'C'"
        }

        test("instruction arguments, return types and seeds use instantiations") {
            val args = listOf(IdlField("amount", defined("Pair", arg(IdlType.Primitive.U64), arg(IdlType.Primitive.I32))))
            val accounts = listOf(IdlAccountItem.Single("vault", pda = IdlPda(listOf(IdlSeed.Arg("amount.first")))))
            val returns = defined("Pair", arg(IdlType.Primitive.U8), arg(IdlType.Primitive.U8))
            val ix = IdlInstruction("ix", IdlDiscriminator(listOf(1)), accounts = accounts, args = args, returns = returns)
            val source = flat(generate(program(instructions = listOf(ix), types = listOf(pair))))
            source shouldContain "public val amount: PairU64I32,"
            source shouldContain "SolanaInstructionDescriptor.Returning<IxArgs, PairU8U8>"
            source shouldContain "public fun findVault(amountFirst: BigInteger,"
        }
    }

    context("zero-copy types") {
        val packed = IdlRepr.C(packed = true)
        fun zeroCopy(name: String, vararg fields: IdlType, repr: IdlRepr? = packed, serialization: IdlSerialization = IdlSerialization.BytemuckUnsafe) = IdlTypeDef(name, IdlTypeBody.Struct(IdlFields.Named(fields.mapIndexed { i, type -> IdlField("f$i", type) })), serialization = serialization, repr = repr)
        fun rejected(vararg types: IdlTypeDef) = shouldThrow<IllegalArgumentException> { generate(program(types = types.toList())) }.message

        test("packed and transparent layouts with fixed-size fields generate with the Borsh codecs") {
            generate(fixture("amm_v3")) shouldContain "public data class PoolState("
            val point = zeroCopy("Point", IdlType.Primitive.I32, IdlType.Primitive.U128)
            val source = generate(
                program(
                    types = listOf(
                        point,
                        zeroCopy("Wrapper", IdlType.Defined("Point"), repr = IdlRepr.Transparent, serialization = IdlSerialization.Bytemuck),
                        zeroCopy("Grid", IdlType.Array(IdlType.Defined("Point"), IdlArrayLength.Fixed(2)), IdlType.Primitive.BOOL, IdlType.Primitive.PUBKEY),
                    ),
                ),
            )
            source shouldContain "f1 = readU128()"
            source shouldContain "f0 = readArray(2) { Point.read(this) }"
        }

        test("an unpacked C layout pads each field to its alignment, with 128-bit integers 8-byte aligned") {
            val source = generate(
                program(
                    types = listOf(
                        zeroCopy("Wide", IdlType.Primitive.U8, IdlType.Primitive.U128, IdlType.Primitive.U16, repr = IdlRepr.C()),
                        zeroCopy("Holder", IdlType.Primitive.U8, IdlType.Defined("Wide"), repr = IdlRepr.C(align = 16)),
                    ),
                ),
            )
            val flat = source.replace(Regex("\\s+"), " ")
            // Wide: u8, 7 bytes to the u128 at 8, the u16 at 24, then 6 bytes to its size of 32
            flat shouldContain "f1 = run { skipPadding(7) readU128() }, f2 = readU16(), ).also { skipPadding(6) }"
            flat shouldContain "writeU8(value.f0) writePadding(7) writeU128(value.f1) writeU16(value.f2) writePadding(6)"
            // Holder: Wide is 8-byte aligned, and align(16) pads the size from 40 to 48
            flat shouldContain "f1 = run { skipPadding(7) Wide.read(this) }, ).also { skipPadding(8) }"
            flat shouldContain "writeU8(value.f0) writePadding(7) Wide.write(this, value.f1) writePadding(8)"
        }

        test("an enum without fields is a byte in a zero-copy layout") {
            val status = IdlTypeDef("Status", IdlTypeBody.Enum(listOf(IdlEnumVariant("On"), IdlEnumVariant("Off"))))
            val source = generate(program(types = listOf(status, zeroCopy("S", IdlType.Defined("Status"), IdlType.Primitive.U16, repr = IdlRepr.C()))))
            val flat = source.replace(Regex("\\s+"), " ")
            // the u16 is 2-byte aligned after the 1-byte enum
            flat shouldContain "f0 = Status.read(this), f1 = run { skipPadding(1) readU16() }"
        }

        test("packed and transparent layouts have no padding, even nested in an unpacked one") {
            val source = generate(
                program(
                    types = listOf(
                        zeroCopy("Packed", IdlType.Primitive.U8, IdlType.Primitive.U64),
                        zeroCopy("Outer", IdlType.Primitive.U8, IdlType.Defined("Packed"), IdlType.Primitive.U8, repr = IdlRepr.C()),
                    ),
                ),
            )
            (source.contains("Padding")) shouldBe false
        }

        test("a Rust layout, packed with align, and 256-bit integers in an unpacked layout are rejected") {
            rejected(zeroCopy("S", IdlType.Primitive.U8, repr = null)) shouldBe
                "type 'S': zero-copy types are only supported with a C or transparent layout, without both packed and align; got the default Rust layout"
            rejected(zeroCopy("S", IdlType.Primitive.U8, repr = IdlRepr.Rust(packed = true))) shouldContain "got Rust(packed=true, align=null)"
            rejected(zeroCopy("S", IdlType.Primitive.U8, repr = IdlRepr.C(packed = true, align = 8))) shouldContain "got C(packed=true, align=8)"
            rejected(zeroCopy("S", IdlType.Primitive.U256, repr = IdlRepr.C())) shouldBe
                "type 'S' field 'f0': U256 has no alignment, so is only supported in packed zero-copy types"
            generate(program(types = listOf(zeroCopy("S", IdlType.Primitive.U256)))) shouldContain "readU256()"
        }

        test("a padded zero-copy type cannot be Borsh-encoded, while one without padding can") {
            val padded = zeroCopy("Padded", IdlType.Primitive.U8, IdlType.Primitive.U64, repr = IdlRepr.C())
            val tight = zeroCopy("Tight", IdlType.Primitive.U64, IdlType.Primitive.U64, repr = IdlRepr.C())
            fun withArg(type: String) = program(
                instructions = listOf(IdlInstruction("ix", IdlDiscriminator(listOf(1)), args = listOf(IdlField("arg", IdlType.Vec(IdlType.Defined(type)))))),
                types = listOf(padded, tight),
            )
            shouldThrow<IllegalArgumentException> { generate(withArg("Padded")) }.message shouldBe
                "instruction 'ix' argument 'arg': 'Padded' has padding in its zero-copy layout, so cannot be Borsh-encoded"
            generate(withArg("Tight")) shouldContain "public data class Tight("
            val borsh = IdlTypeDef("Borsh", IdlTypeBody.Struct(IdlFields.Named(listOf(IdlField("inner", IdlType.Defined("Padded"))))))
            rejected(padded, borsh) shouldBe "type 'Borsh' field 'inner': 'Padded' has padding in its zero-copy layout, so cannot be Borsh-encoded"
            // padding in a nested zero-copy type counts too
            val wrapper = zeroCopy("Wrapper", IdlType.Defined("Padded"), repr = IdlRepr.C())
            val holder = IdlTypeDef("Holder", IdlTypeBody.Struct(IdlFields.Named(listOf(IdlField("inner", IdlType.Defined("Wrapper"))))))
            rejected(padded, wrapper, holder) shouldContain "'Wrapper' has padding in its zero-copy layout"
        }

        test("fields that vary in size, and nested types that are not zero-copy, are rejected") {
            rejected(zeroCopy("S", IdlType.Primitive.STRING)) shouldBe "type 'S' field 'f0': a zero-copy type cannot contain STRING, which varies in size"
            rejected(zeroCopy("S", IdlType.Vec(IdlType.Primitive.U8))) shouldBe "type 'S' field 'f0': a zero-copy type cannot contain a vec, which varies in size"
            rejected(zeroCopy("S", IdlType.Option(IdlType.Primitive.U8))) shouldBe "type 'S' field 'f0': a zero-copy type cannot contain an option"
            rejected(zeroCopy("S", IdlType.Array(IdlType.Primitive.BYTES, IdlArrayLength.Fixed(2)))) shouldContain "cannot contain BYTES"
            rejected(zeroCopy("S", IdlType.Defined("Plain")), struct("Plain")) shouldBe
                "type 'S' field 'f0': a zero-copy type can only contain zero-copy types and enums without fields, but 'Plain' is Borsh"
            // an enum with a field is no single byte
            val dataEnum = IdlTypeDef("D", IdlTypeBody.Enum(listOf(IdlEnumVariant("A", IdlFields.Tuple(listOf(IdlType.Primitive.U8))))))
            rejected(zeroCopy("S", IdlType.Defined("D")), dataEnum) shouldContain "but 'D' is Borsh"
            val enum = IdlTypeDef("E", IdlTypeBody.Enum(listOf(IdlEnumVariant("A"))), serialization = IdlSerialization.Bytemuck, repr = packed)
            rejected(enum) shouldBe "type 'E': zero-copy enums are not supported"
            rejected(zeroCopy("S", IdlType.Primitive.U8, serialization = IdlSerialization.Custom("Compressed"))) shouldBe
                "type 'S': Custom(name=Compressed) serialization is not supported"
        }
    }

    test("an invalid program fails before generating") {
        val missing = program(types = listOf(IdlTypeDef("S", IdlTypeBody.Struct(IdlFields.Named(listOf(IdlField("x", IdlType.Defined("Missing"))))))))
        shouldThrow<IllegalArgumentException> { generate(missing) }.message shouldBe "type 'S' field 'x': no type definition named 'Missing'"
    }

    test("names that collide once converted to Kotlin are rejected") {
        shouldThrow<IllegalArgumentException> { generate(program(types = listOf(struct("Event")))) }.message shouldBe
            "generated type 'Event' is produced by both type 'Event' and the event hierarchy"
        shouldThrow<IllegalArgumentException> { generate(program(types = listOf(struct("IxArgs")))) }.message shouldBe
            "generated type 'IxArgs' is produced by both type 'IxArgs' and instruction 'ix'"
        shouldThrow<IllegalArgumentException> { generate(program(types = listOf(struct("my_value"), struct("MyValue")))) }.message shouldBe
            "generated type 'MyValue' is produced by both type 'my_value' and type 'MyValue'"
        shouldThrow<IllegalArgumentException> {
            generate(program(constants = listOf(IdlConstant("IX", IdlType.Primitive.U8, "1"))))
        }.message shouldBe "generated member 'IX' is produced by both constant 'IX' and instruction 'ix'"
    }

    test("types that share a last path segment are named by their whole path") {
        val source = generate(program(types = listOf(struct("a::Point"), struct("b::Point"), struct("c::Line"))))
        source shouldContain "data class APoint("
        source shouldContain "data class BPoint("
        source shouldContain "data class Line("
    }

    test("constants must fit their type") {
        shouldThrow<IllegalArgumentException> { generate(program(constants = listOf(IdlConstant("C", IdlType.Primitive.U8, "256")))) }
            .message shouldBe "constant 'C': 256 is outside the range of U8"
        shouldThrow<IllegalArgumentException> { generate(program(constants = listOf(IdlConstant("C", IdlType.Primitive.PUBKEY, "1111")))) }
            .message shouldBe "constant 'C': '1111' decodes to 4 bytes, but an address has 32"
        generate(program(constants = listOf(IdlConstant("C", IdlType.Primitive.I64, "-9_223_372_036_854_775_808i64")))) shouldContain
            "const val C: Long = Long.MIN_VALUE"
    }

    test("account filter methods stop at the first field without a fixed size") {
        val source = generate(fixture("relations"))
        val account = IdlTypeDef(
            "Acct",
            IdlTypeBody.Struct(
                IdlFields.Named(
                    listOf(
                        IdlField("a", IdlType.Primitive.U8),
                        IdlField("name", IdlType.Primitive.STRING),
                        IdlField("b", IdlType.Primitive.U8),
                    ),
                ),
            ),
        )
        val generated = generate(
            program(types = listOf(account)).copy(accounts = listOf(IdlAccount("Acct", IdlDiscriminator(listOf(2))))),
        )
        generated shouldContain "public fun a(`value`: Int): Filter"
        generated shouldContain "public fun name(`value`: String): Filter"
        (generated.contains("public fun b(")) shouldBe false
        source shouldContain "public class Filter("
    }

    context("program derived addresses") {
        /** A program whose instruction [ix] has a PDA account `vault` with [seeds], and arguments [args]. */
        fun pdaProgram(seeds: List<IdlSeed>, args: List<IdlField> = emptyList(), ix: String = "ix", constants: List<IdlConstant> = emptyList()): IdlProgram {
            val accounts = listOf(IdlAccountItem.Single("owner"), IdlAccountItem.Single("vault", pda = IdlPda(seeds)))
            return program(instructions = listOf(IdlInstruction(ix, IdlDiscriminator(listOf(1)), accounts = accounts, args = args)), constants = constants)
        }
        val index = listOf(IdlField("index", IdlType.Primitive.U32))
        val seeds = listOf(IdlSeed.Const("vault".map { it.code }), IdlSeed.Account("owner"), IdlSeed.Arg("index"))
        fun build(program: IdlProgram, orders: SeedByteOrders) = SolanaProgramBuilder(program, "test.pkg", seedByteOrders = orders).build().toString()

        test("integer seeds are little-endian by default, and big-endian for the whole program or one seed when configured") {
            val default = generate(pdaProgram(seeds, index))
            default shouldContain "public fun findVault(\n"
            default shouldContain "BorshWriter(4).apply { writeU32(index) }.toByteArray())"
            default shouldContain "Encodes `index` as a little-endian integer."
            (default.contains("reverse()")) shouldBe false

            val bigEndian = "BorshWriter(4).apply { writeU32(index) }.toByteArray().also { it.reverse() }"
            build(pdaProgram(seeds, index), SeedByteOrders(SeedByteOrder.BIG_ENDIAN)) shouldContain bigEndian
            val seed = SeedByteOrders(seeds = mapOf(SeedByteOrders.Seed("vault", "index") to SeedByteOrder.BIG_ENDIAN))
            build(pdaProgram(seeds, index), seed) shouldContain bigEndian
            build(pdaProgram(seeds, index), seed) shouldContain "Encodes `index` as a big-endian integer."
        }

        test("a byte order for a seed that is not a multi-byte integer seed is rejected") {
            fun rejected(account: String, path: String, program: IdlProgram = pdaProgram(seeds, index)) = shouldThrow<IllegalArgumentException> {
                build(program, SeedByteOrders(seeds = mapOf(SeedByteOrders.Seed(account, path) to SeedByteOrder.BIG_ENDIAN)))
            }.message shouldBe "seed byte order for account '$account' seed '$path': no PDA account has an integer seed at that path"
            rejected("vault", "missing")
            rejected("owner", "index")
            // an account address and a single byte have no byte order
            rejected("vault", "owner")
            rejected("vault", "index", pdaProgram(seeds, listOf(IdlField("index", IdlType.Primitive.U8))))
        }

        test("constant seeds are shared across instructions") {
            val twice = pdaProgram(seeds, index).let { it.copy(instructions = it.instructions + it.instructions.single().copy(name = "other", discriminator = IdlDiscriminator(listOf(2)))) }
            val source = generate(twice)
            source shouldContain "private val PDA_SEED_0: ByteArray = \"vault\".encodeToByteArray()"
            (source.contains("PDA_SEED_1")) shouldBe false
        }

        test("a seed that takes the program id's parameter name, or an IDL constant named like a seed constant, is rejected") {
            val programIdArg = listOf(IdlField("program_id", IdlType.Primitive.PUBKEY))
            shouldThrow<IllegalArgumentException> { generate(pdaProgram(listOf(IdlSeed.Arg("program_id")), programIdArg)) }.message shouldBe
                "instruction 'ix' account 'vault': PDA seed argument 'program_id' takes a parameter named 'programId', which the program it derives under takes"
            val constant = IdlConstant("PDA_SEED_0", IdlType.Primitive.U8, "1")
            shouldThrow<IllegalArgumentException> { generate(pdaProgram(seeds, index, constants = listOf(constant))) }.message shouldBe
                "generated member 'PDA_SEED_0' is produced by both constant 'PDA_SEED_0' and the PDA seeds"
        }

        test("a seed path shared by an argument and an account is rejected, since both would take the same parameter") {
            val ownerArg = listOf(IdlField("owner", IdlType.Primitive.PUBKEY))
            shouldThrow<IllegalArgumentException> { generate(pdaProgram(listOf(IdlSeed.Account("owner"), IdlSeed.Arg("owner")), ownerArg)) }.message shouldBe
                "instruction 'ix' account 'vault': PDA seeds account 'owner' and argument 'owner' both take a parameter named 'owner'"
        }
    }

    test("slice properties have their own JVM names, and fields named like slice members a suffix") {
        val first = IdlTypeDef("First", IdlTypeBody.Struct(IdlFields.Named(listOf(IdlField("address", IdlType.Primitive.PUBKEY), IdlField("bump", IdlType.Primitive.U8)))))
        val second = IdlTypeDef("Second", IdlTypeBody.Struct(IdlFields.Named(listOf(IdlField("bump", IdlType.Primitive.U8)))))
        val accounts = listOf(IdlAccount("First", IdlDiscriminator(listOf(1))), IdlAccount("Second", IdlDiscriminator(listOf(2))))
        val source = generate(program(types = listOf(first, second)).copy(accounts = accounts)).replace(Regex("\\s+"), " ")
        source shouldContain "@get:JvmName(\"getFirstAddress\") public val SolanaAccountSlice<Test.First>.addressField: SolanaAddress get() = this[Test.First.Fields.address]"
        source shouldContain "@get:JvmName(\"getFirstBump\") public val SolanaAccountSlice<Test.First>.bump: Int"
        source shouldContain "@get:JvmName(\"getSecondBump\") public val SolanaAccountSlice<Test.Second>.bump: Int"
        source shouldContain "public inline fun <R> select(fields: Fields.() -> SolanaAccountProjection<First, R>): SolanaAccountSelection<First, R>"
    }

    context("nested field paths") {
        fun named(vararg fields: Pair<String, IdlType>) = IdlTypeBody.Struct(IdlFields.Named(fields.map { (name, type) -> IdlField(name, type) }))
        fun array(element: IdlType, length: Int) = IdlType.Array(element, IdlArrayLength.Fixed(length))
        fun generateAccount(account: IdlTypeDef, vararg types: IdlTypeDef): String {
            val accounts = listOf(IdlAccount(account.name, IdlDiscriminator(listOf(1))))
            return generate(program(types = listOf(account) + types).copy(accounts = accounts)).replace(Regex("\\s+"), " ")
        }

        test("only fields at a fixed offset with a fixed size get descriptors, and the others are listed with why") {
            val account = IdlTypeDef(
                "A",
                named(
                    "head" to IdlType.Defined("Inner"),
                    "nothing" to IdlType.Defined("Empty"),
                    "none" to array(IdlType.Primitive.U64, 0),
                    "variable" to IdlType.Defined("Variable"),
                    "list" to IdlType.Vec(IdlType.Defined("Listed")),
                    "tail" to IdlType.Defined("Trailing"),
                ),
            )
            val source = generateAccount(
                account,
                IdlTypeDef("Inner", named("x" to IdlType.Primitive.U8, "y" to IdlType.Primitive.U16)),
                IdlTypeDef("Empty", IdlTypeBody.Struct(IdlFields.None)),
                IdlTypeDef("Variable", named("x" to IdlType.Primitive.U8, "s" to IdlType.Primitive.STRING)),
                IdlTypeDef("Listed", named("x" to IdlType.Primitive.U8)),
                IdlTypeDef("Trailing", named("x" to IdlType.Primitive.U8)),
            )
            source shouldContain "public val head: Inner.Path<A> = Inner.Path<A>(\"head\", 0)"
            source shouldContain "public val y: SolanaAccountField<T, Int> = SolanaAccountField<T, Int>(name + \".y\", offset + 1, 2,"
            source shouldContain "* - `nothing` takes no bytes * - `none` takes no bytes * - `variable` has a variable size " +
                "* - `list` follows a field of variable size, so has no fixed offset * - `tail` follows a field of variable size, so has no fixed offset"
            // structs are described only where a fixed-size field of an account reaches them
            source.split("public class Path<T>").size shouldBe 2
            listOf("nothing", "none", "variable", "list", "tail").forEach { (source.contains("public val $it: SolanaAccount") || source.contains("public val $it: Inner")) shouldBe false }
        }

        test("nested offsets follow packed and unpacked zero-copy layouts") {
            fun zeroCopy(name: String, body: IdlTypeBody.Struct, packed: Boolean) = IdlTypeDef(name, body, serialization = IdlSerialization.BytemuckUnsafe, repr = IdlRepr.C(packed = packed))
            val account = zeroCopy("Z", named("a" to IdlType.Primitive.U8, "loose" to IdlType.Defined("Loose"), "tight" to array(IdlType.Defined("Tight"), 2)), packed = false)
            val source = generateAccount(
                account,
                zeroCopy("Loose", named("x" to IdlType.Primitive.U8, "y" to IdlType.Primitive.U128), packed = false),
                zeroCopy("Tight", named("x" to IdlType.Primitive.U8, "y" to IdlType.Primitive.U64), packed = true),
            )
            // Loose is 8-byte aligned, with 128-bit integers 8-byte aligned as on-chain, and 24 bytes
            source shouldContain "Loose.Path<Z>(\"loose\", 8)"
            source shouldContain "SolanaAccountField<T, BigInteger>(name + \".y\", offset + 8, 16,"
            // Tight is packed: 9 bytes, 1-byte aligned, so follows Loose directly
            source shouldContain "SolanaAccountArrayField<Z, List<Tight>, Tight.Path<Z>>(\"tight\", 32, 2, 9,"
            source shouldContain "SolanaAccountField<T, BigInteger>(name + \".y\", offset + 1, 8,"
        }

        test("nested fields named like descriptor members take a suffix, and arrays of arrays nest their elements") {
            val account = IdlTypeDef("A", named("slot" to IdlType.Defined("Slot"), "grid" to array(array(IdlType.Primitive.U16, 3), 2)))
            val source = generateAccount(
                account,
                IdlTypeDef("Slot", named("name" to IdlType.Primitive.U8, "offset" to IdlType.Primitive.U8, "size" to IdlType.Primitive.U8, "read" to IdlType.Primitive.U8, "write" to IdlType.Primitive.U8)),
            )
            listOf("name", "offset", "size", "read", "write").forEachIndexed { i, name ->
                val offset = if (i == 0) "offset" else "offset + $i"
                source shouldContain "public val ${name}Field: SolanaAccountField<T, Int> = SolanaAccountField<T, Int>(name + \".$name\", $offset, 1,"
            }
            source shouldContain "SolanaAccountArrayField<A, List<List<Int>>, SolanaAccountArrayField<A, List<Int>, SolanaAccountField<A, Int>>>(\"grid\", 5, 2, 6,"
            source shouldContain "{ elementName0, elementOffset0 -> SolanaAccountArrayField<A, List<Int>, SolanaAccountField<A, Int>>(elementName0, elementOffset0, 3, 2,"
            source shouldContain "{ elementName1, elementOffset1 -> SolanaAccountField<A, Int>(elementName1, elementOffset1, 2,"
        }
    }

    context("legacy IDLs") {
        test("a program without an address fails generation, naming the way to give one") {
            shouldThrow<IllegalArgumentException> { generate(program().copy(address = null)) }.message shouldBe
                "program 'test': the IDL declares no address, so one must be given, such as with the Gradle plugin's programAddress"
        }

        test("repeated field names are numbered from the first, and the last keeps the name") {
            val fields = listOf("padding" to IdlType.Primitive.U64, "padding1" to IdlType.Primitive.U8, "padding" to IdlType.Primitive.U16, "padding" to IdlType.Primitive.U32)
            val def = IdlTypeDef("S", IdlTypeBody.Struct(IdlFields.Named(fields.map { (name, type) -> IdlField(name, type) })))
            val source = generate(program(types = listOf(def))).replace(Regex("\\s+"), " ")
            // padding1 is taken, so the repeated names take 2 and 3
            source shouldContain "public val padding2: BigInteger, public val padding1: Int, /** * Named `padding` in the IDL, like a later field, which keeps the name. */ public val padding3: Int, public val padding: Long,"
            source shouldContain "padding2 = readU64(), padding1 = readU8(), padding3 = readU16(), padding = readU32(),"
        }

        test("Drift's legacy IDL generates, with its repeated padding field numbered") {
            val drift = fixture("drift").copy(address = "dRiftyHA39MWEi3m9aunc5MzRF1JYuBsbn6VPcn33UH")
            val source = generate(drift).replace(Regex("\\s+"), " ")
            source shouldContain "public val padding1: BigInteger,"
            source shouldContain "padding1 = readU64(),"
            source shouldContain "padding = readFixedBytes(174),"
        }

        // Drift's zero-copy State, PerpMarket and SpotMarket are 992, 1216 and 776 bytes on mainnet. With 128-bit
        // integers 8-byte aligned, as on-chain, their C layouts have no padding and match those sizes; 16-byte
        // alignment would pad them to 1016, 1240 and 808 bytes.
        test("Drift's zero-copy markets have no padding with on-chain 128-bit alignment") {
            val drift = fixture("drift")
            val zeroCopy = HashSet<String>()
            fun mark(name: String) {
                if (!zeroCopy.add(name)) return
                val def = drift.type(name)!!
                val body = def.body as? IdlTypeBody.Struct ?: return
                fun visit(type: IdlType) {
                    when (type) {
                        is IdlType.Array -> visit(type.element)
                        is IdlType.Defined -> if (drift.type(type.name)!!.body is IdlTypeBody.Struct) mark(type.name)
                        else -> Unit
                    }
                }
                (body.fields as IdlFields.Named).fields.forEach { visit(it.type) }
            }
            listOf("State", "PerpMarket", "SpotMarket").forEach(::mark)
            val marked = drift.copy(
                address = "dRiftyHA39MWEi3m9aunc5MzRF1JYuBsbn6VPcn33UH",
                types = drift.types.map { if (it.name in zeroCopy) it.copy(serialization = IdlSerialization.BytemuckUnsafe, repr = IdlRepr.C()) else it },
            )
            (generate(marked).contains("Padding(")) shouldBe false
        }
    }

    test("generation is deterministic") {
        generate(fixture("relations")) shouldBe generate(fixture("relations"))
    }

    test("naming") {
        "system_program".toCamelCase() shouldBe "systemProgram"
        "idl::some_module::NamedStruct".toPascalCase() shouldBe "IdlSomeModuleNamedStruct"
        "initializeConfig".toUpperSnakeCase() shouldBe "INITIALIZE_CONFIG"
        "set_fee_v2".toUpperSnakeCase() shouldBe "SET_FEE_V2"
    }
})
