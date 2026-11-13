package io.ethers.solana.idl.reader

import io.ethers.solana.idl.IdlAccountItem
import io.ethers.solana.idl.IdlArrayLength
import io.ethers.solana.idl.IdlDiscriminator
import io.ethers.solana.idl.IdlEnumVariant
import io.ethers.solana.idl.IdlErrorCode
import io.ethers.solana.idl.IdlEvent
import io.ethers.solana.idl.IdlField
import io.ethers.solana.idl.IdlFields
import io.ethers.solana.idl.IdlGenericArg
import io.ethers.solana.idl.IdlGenericParam
import io.ethers.solana.idl.IdlProgram
import io.ethers.solana.idl.IdlRepr
import io.ethers.solana.idl.IdlSeed
import io.ethers.solana.idl.IdlSerialization
import io.ethers.solana.idl.IdlType
import io.ethers.solana.idl.IdlTypeBody
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

private fun fixture(name: String): String = AnchorIdlReaderTest::class.java.getResource("/idl/$name.json")!!.readText()

private fun read(name: String): IdlProgram = SolanaIdlReaderRegistry.tryReadIdl(fixture(name)).unwrap()

class AnchorIdlReaderTest : FunSpec({
    context("Anchor test IDLs") {
        test("idl.json reads every declaration") {
            val program = read("idl")
            program.name shouldBe "idl"
            program.version shouldBe "0.1.0"
            program.address shouldBe "id11111111111111111111111111111111111111111"
            program.instructions shouldHaveSize 16
            program.accounts shouldHaveSize 15
            program.events shouldHaveSize 1
            program.errors shouldHaveSize 4
            program.types shouldHaveSize 27
            program.constants shouldHaveSize 5

            program.events.single() shouldBe IdlEvent("SimpleEvent", IdlDiscriminator(listOf(72, 103, 116, 133, 133, 111, 132, 229)))
            program.errors.first() shouldBe IdlErrorCode(500000, "SomeError", "Example error.")
        }

        test("instruction accounts, groups, fixed addresses, PDAs and returns") {
            val program = read("idl")
            val returnType = program.instructions.single { it.name == "return_type" }
            returnType.returns shouldBe IdlType.Defined("idl::NamedStruct")

            val compilation = program.instructions.single { it.name == "test_compilation" }
            compilation.accounts shouldContain IdlAccountItem.Group("composite", listOf(IdlAccountItem.Single("account")))
            compilation.accounts shouldContain IdlAccountItem.Single("token_program", address = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")

            val generic = program.instructions.single { it.name == "generic" }
            val pda = generic.accounts.filterIsInstance<IdlAccountItem.Single>().single { it.name == "my_account" }.pda!!
            pda.seeds shouldBe listOf(IdlSeed.Const("generic".encodeToByteArray().map { it.toInt() }), IdlSeed.Account("signer"))
        }

        test("enums with unit, named and tuple variants") {
            val body = read("idl").type("FullEnum")!!.body.shouldBeInstanceOf<IdlTypeBody.Enum>()
            body.variants shouldBe listOf(
                IdlEnumVariant("Unit"),
                IdlEnumVariant(
                    "Named",
                    IdlFields.Named(listOf(IdlField("point_x", IdlType.Primitive.U64), IdlField("point_y", IdlType.Primitive.U64))),
                ),
                IdlEnumVariant(
                    "Unnamed",
                    IdlFields.Tuple(listOf(IdlType.Primitive.U8, IdlType.Primitive.U8, IdlType.Primitive.U16, IdlType.Primitive.U16)),
                ),
                IdlEnumVariant("UnnamedStruct", IdlFields.Tuple(listOf(IdlType.Defined("idl::NamedStruct")))),
            )
        }

        test("serialization and repr metadata") {
            val program = read("idl")
            program.type("ZcAccount")!!.serialization shouldBe IdlSerialization.Bytemuck
            program.type("ZcAccount")!!.repr shouldBe IdlRepr.C()
            program.type("ZcUnsafeAccount")!!.serialization shouldBe IdlSerialization.BytemuckUnsafe
            program.type("ZcUnsafeAccount")!!.repr shouldBe IdlRepr.Rust(packed = true)
            program.type("SimpleAccount")!!.serialization shouldBe IdlSerialization.Borsh
        }

        test("generic parameters and arguments") {
            val def = read("idl").type("GenericStruct")!!
            def.generics shouldBe listOf(IdlGenericParam.Type("T"), IdlGenericParam.Const("N", "usize"))
            val fields = def.body.shouldBeInstanceOf<IdlTypeBody.Struct>().fields.shouldBeInstanceOf<IdlFields.Named>().fields
            fields[0].type shouldBe IdlType.Array(IdlType.Generic("T"), IdlArrayLength.Generic("N"))
            fields[1].type shouldBe IdlType.Defined(
                "SubGenericStruct",
                listOf(
                    IdlGenericArg.Const("8"),
                    IdlGenericArg.Type(IdlType.Generic("T")),
                    IdlGenericArg.Type(IdlType.Vec(IdlType.Option(IdlType.Generic("T")))),
                ),
            )
        }

        test("generics.json and relations.json") {
            val generics = read("generics")
            generics.types.map { it.name } shouldBe listOf("GenericAccount", "GenericEnum", "GenericNested", "GenericType", "MyStruct", "WrappedU8Array")
            // Anchor writes a forwarded const parameter as a type argument; it reads back as a const
            val fields = generics.type("GenericType")!!.body.shouldBeInstanceOf<IdlTypeBody.Struct>().fields.shouldBeInstanceOf<IdlFields.Named>().fields
            fields.single { it.name == "warr" }.type shouldBe IdlType.Defined("WrappedU8Array", listOf(IdlGenericArg.ConstParam("N")))
            fields.single { it.name == "enm1" }.type shouldBe IdlType.Defined(
                "GenericEnum",
                listOf(IdlGenericArg.Type(IdlType.Generic("T")), IdlGenericArg.Type(IdlType.Generic("U")), IdlGenericArg.ConstParam("N")),
            )

            val relation = read("relations").instructions.single { it.name == "test_relation" }
            relation.accounts[0] shouldBe IdlAccountItem.Single("my_account", relations = listOf("account"))
            relation.accounts[2].shouldBeInstanceOf<IdlAccountItem.Group>().accounts shouldHaveSize 2
        }

        test("a production IDL: Raydium CLMM") {
            val program = read("amm_v3")
            program.address shouldBe "CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK"
            program.instructions shouldHaveSize 25
            program.errors shouldHaveSize 45
            program.type("PoolState")!!.serialization shouldBe IdlSerialization.BytemuckUnsafe
            program.type("PoolState")!!.repr shouldBe IdlRepr.C(packed = true)
            program.type("TickArrayBitmap")!!.body shouldBe IdlTypeBody.Alias(IdlType.Array(IdlType.Primitive.U64, IdlArrayLength.Fixed(8)))

            val open = program.instructions.single { it.name == "open_position" }
            val nft = open.accounts.filterIsInstance<IdlAccountItem.Single>().single { it.name == "position_nft_account" }
            nft.pda!!.program.shouldBeInstanceOf<IdlSeed.Const>().bytes shouldHaveSize 32
        }
    }

    test("legacy IDLs are rejected with an explanation, and read by the legacy reader in the registry") {
        shouldThrow<IllegalArgumentException> { AnchorIdlReader.read(fixture("external_legacy")) }.message shouldContain "AnchorLegacyIdlReader"
        SolanaIdlReaderRegistry.tryReadIdl(fixture("external_legacy")).unwrap().name shouldBe "external"
    }

    test("the reader parses without validating") {
        val json = """
            {
              "address": "11111111111111111111111111111111",
              "metadata": {"name": "test", "version": "0.1.0", "spec": "0.1.0"},
              "instructions": [{"name": "ix", "discriminator": [1], "accounts": [], "args": [{"name": "x", "type": {"defined": {"name": "Missing"}}}]}]
            }
        """
        // an unresolved type is structurally valid Anchor JSON; only the registry's validation rejects it
        AnchorIdlReader.read(json).instructions.single().args.single().type shouldBe IdlType.Defined("Missing")
        val error = SolanaIdlReaderRegistry.tryReadIdl(json).unwrapError().shouldBeInstanceOf<SolanaIdlReaderRegistry.IdlReadError.Invalid>()
        error.cause.message shouldBe "instruction 'ix' argument 'x': no type definition named 'Missing'"
    }

    test("reading is deterministic") {
        AnchorIdlReader.read(fixture("amm_v3")) shouldBe AnchorIdlReader.read(fixture("amm_v3"))
    }
})
