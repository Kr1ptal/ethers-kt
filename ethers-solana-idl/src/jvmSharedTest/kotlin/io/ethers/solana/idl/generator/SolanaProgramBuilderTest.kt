package io.ethers.solana.idl.generator

import io.ethers.solana.idl.IdlAccount
import io.ethers.solana.idl.IdlConstant
import io.ethers.solana.idl.IdlDiscriminator
import io.ethers.solana.idl.IdlField
import io.ethers.solana.idl.IdlFields
import io.ethers.solana.idl.IdlInstruction
import io.ethers.solana.idl.IdlProgram
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

    test("generic type definitions and non-Borsh layouts fail generation") {
        shouldThrow<IllegalArgumentException> { generate(fixture("generics")) }.message shouldContain "generic type definitions are not supported yet"
        shouldThrow<IllegalArgumentException> { generate(fixture("amm_v3")) }.message shouldContain "BytemuckUnsafe serialization is not supported yet"
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

    test("constants must fit their type and have a supported type") {
        shouldThrow<IllegalArgumentException> { generate(program(constants = listOf(IdlConstant("C", IdlType.Primitive.U8, "256")))) }
            .message shouldBe "constant 'C': 256 is outside the range of U8"
        shouldThrow<IllegalArgumentException> { generate(program(constants = listOf(IdlConstant("C", IdlType.Vec(IdlType.Primitive.U8), "[]")))) }
            .message shouldContain "constants are not supported yet"
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
