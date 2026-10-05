package io.ethers.solana.idl

import io.ethers.solana.idl.reader.AnchorIdlReader
import io.ethers.solana.idl.reader.SolanaIdlReaderRegistry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

private const val ADDRESS = "11111111111111111111111111111111"

private fun idl(
    instructions: String = "[]",
    accounts: String = "[]",
    events: String = "[]",
    errors: String = "[]",
    types: String = "[]",
    constants: String = "[]",
    address: String = ADDRESS,
    spec: String = "0.1.0",
): String = """
    {
      "address": "$address",
      "metadata": {"name": "test", "version": "0.1.0", "spec": "$spec"},
      "instructions": $instructions,
      "accounts": $accounts,
      "events": $events,
      "errors": $errors,
      "types": $types,
      "constants": $constants
    }
"""

/** An instruction named [name] with one argument of [argType] and the given accounts. */
private fun instruction(name: String = "ix", discriminator: String = "[1]", argType: String = "\"u8\"", accounts: String = "[]") = """{"name": "$name", "discriminator": $discriminator, "accounts": $accounts, "args": [{"name": "value", "type": $argType}]}"""

private fun struct(name: String, fieldType: String, generics: String = "[]") = """{"name": "$name", "generics": $generics, "type": {"kind": "struct", "fields": [{"name": "field", "type": $fieldType}]}}"""

private fun alias(name: String, target: String) = """{"name": "$name", "type": {"kind": "type", "alias": {"defined": {"name": "$target"}}}}"""

/** The validation failure [json] is rejected with. */
private fun problem(json: String): String? {
    return SolanaIdlReaderRegistry.tryReadIdl(json).unwrapError().shouldBeInstanceOf<SolanaIdlReaderRegistry.IdlReadError.Invalid>().cause.message
}

private fun valid(json: String): IdlProgram = SolanaIdlReaderRegistry.tryReadIdl(json).unwrap()

class IdlValidatorTest : FunSpec({
    test("a minimal IDL is valid") {
        valid(idl(instructions = "[${instruction()}]")).instructions.single().args.single().type shouldBe IdlType.Primitive.U8
    }

    context("structure") {
        test("input that does not decode as an Anchor IDL is rejected by the reader") {
            shouldThrow<IllegalArgumentException> { AnchorIdlReader.read("{") }
            shouldThrow<IllegalArgumentException> { AnchorIdlReader.read(idl(instructions = """[{"name": "ix", "accounts": [], "args": []}]""")) }
            shouldThrow<IllegalArgumentException> { AnchorIdlReader.read("""{"address": "$ADDRESS", "instructions": []}""") }
            shouldThrow<IllegalArgumentException> { AnchorIdlReader.read(idl(types = "[${struct("S", """{"defined": "Other"}""")}]")) }
            SolanaIdlReaderRegistry.tryReadIdl("{").unwrapError().shouldBeInstanceOf<SolanaIdlReaderRegistry.IdlReadError.Unreadable>()
        }

        test("unsupported spec versions") {
            shouldThrow<IllegalArgumentException> { AnchorIdlReader.read(idl(spec = "0.2.0")) }.message shouldContain "0.1.x"
            valid(idl(spec = "0.1.7"))
        }

        test("unsupported and legacy types") {
            shouldThrow<IllegalArgumentException> { AnchorIdlReader.read(idl(instructions = "[${instruction(argType = """{"coption": "u8"}""")}]")) }
                .message shouldContain "coption"
            shouldThrow<IllegalArgumentException> { AnchorIdlReader.read(idl(instructions = "[${instruction(argType = "\"publicKey\"")}]")) }
        }

        test("values that are invalid on their own are rejected while reading") {
            fun rejected(json: String): String? {
                SolanaIdlReaderRegistry.tryReadIdl(json).unwrapError().shouldBeInstanceOf<SolanaIdlReaderRegistry.IdlReadError.Unreadable>()
                return shouldThrow<IllegalArgumentException> { AnchorIdlReader.read(json) }.message
            }
            rejected(idl(instructions = "[${instruction(discriminator = "[1, 256]")}]")) shouldBe "Byte 256 is outside 0..255"
            rejected(idl(instructions = "[${instruction(discriminator = "[]")}]")) shouldBe "Discriminator must not be empty"
            rejected(idl(errors = """[{"code": -1, "name": "E"}]""")) shouldBe "Error 'E' code -1 is outside the u32 range"
            rejected(idl(address = "not-base58!")) shouldBe "Invalid base58 address 'not-base58!'"
            rejected(idl(address = "1111")) shouldBe "Address '1111' decodes to 4 bytes, expected 32"
            rejected(idl(instructions = "[${instruction(accounts = """[{"name": "a", "address": "0OIl"}]""")}]")) shouldBe "Invalid base58 address '0OIl'"
            rejected(idl(instructions = "[${instruction(argType = """{"array": ["u8", -1]}""")}]")) shouldBe "Array length -1 must not be negative"
            rejected(idl(types = "[${struct("S", """{"defined": {"name": "S", "generics": [{"kind": "const", "value": "-4"}]}}""")}]")) shouldBe
                "Const generic argument '-4' is not a non-negative integer"
        }

        test("model constructors reject invalid values from any reader") {
            shouldThrow<IllegalArgumentException> { IdlDiscriminator(emptyList()) }
            shouldThrow<IllegalArgumentException> { IdlDiscriminator(listOf(-1)) }
            shouldThrow<IllegalArgumentException> { IdlErrorCode(4294967296L, "E") }
            shouldThrow<IllegalArgumentException> { IdlSeed.Const(listOf(256)) }
            shouldThrow<IllegalArgumentException> { IdlArrayLength.Fixed(-1) }
            shouldThrow<IllegalArgumentException> { IdlGenericArg.Const("x") }
            shouldThrow<IllegalArgumentException> { IdlAccountItem.Single("a", address = "1111") }
            shouldThrow<IllegalArgumentException> { IdlProgram("p", "1.0.0", "1111") }
            IdlErrorCode(4294967295L, "E").code shouldBe 4294967295L
            IdlSeed.Const(emptyList()).bytes shouldBe emptyList()
        }
    }

    context("type resolution") {
        test("unresolved defined types name the element that uses them") {
            problem(idl(types = "[${struct("S", """{"option": {"defined": {"name": "Missing"}}}""")}]")) shouldBe
                "type 'S' field 'field': no type definition named 'Missing'"
            problem(idl(instructions = "[${instruction(argType = """{"vec": {"defined": {"name": "Missing"}}}""")}]")) shouldBe
                "instruction 'ix' argument 'value': no type definition named 'Missing'"
        }

        test("generic arguments must match the parameters in number and kind") {
            val generic = struct("G", """{"array": [{"generic": "T"}, {"generic": "N"}]}""", """[{"kind": "type", "name": "T"}, {"kind": "const", "name": "N", "type": "usize"}]""")
            fun use(args: String) = idl(types = "[$generic, ${struct("User", """{"defined": {"name": "G", "generics": $args}}""")}]")

            valid(use("""[{"kind": "type", "type": "u8"}, {"kind": "const", "value": "4"}]"""))
            problem(use("""[{"kind": "type", "type": "u8"}]""")) shouldBe "type 'User' field 'field': 'G' takes 2 generic arguments, got 1"
            problem(use("""[{"kind": "const", "value": "4"}, {"kind": "type", "type": "u8"}]""")) shouldBe
                "type 'User' field 'field': generic parameter 'T' of 'G' takes a type"
            problem(use("""[{"kind": "type", "type": "u8"}, {"kind": "type", "type": "u8"}]""")) shouldBe
                "type 'User' field 'field': generic parameter 'N' of 'G' takes a const value"
        }

        test("a const parameter may be forwarded as Anchor writes it, but only from a const parameter in scope") {
            val wrapped = """{"name": "W", "generics": [{"kind": "const", "name": "N", "type": "usize"}], "type": {"kind": "struct", "fields": ["u8"]}}"""
            fun forwarding(params: String) = idl(types = "[$wrapped, ${struct("F", """{"defined": {"name": "W", "generics": [{"kind": "type", "type": {"generic": "N"}}]}}""", params)}]")

            val field = valid(forwarding("""[{"kind": "const", "name": "N", "type": "usize"}]""")).type("F")!!
                .body.shouldBeInstanceOf<IdlTypeBody.Struct>().fields.shouldBeInstanceOf<IdlFields.Named>().fields.single()
            field.type shouldBe IdlType.Defined("W", listOf(IdlGenericArg.ConstParam("N")))

            problem(forwarding("""[{"kind": "type", "name": "N"}]""")) shouldBe "type 'F' field 'field': no const generic parameter named 'N' in scope"
            problem(forwarding("[]")) shouldBe "type 'F' field 'field': no const generic parameter named 'N' in scope"
        }

        test("generic references must be bound by the enclosing type definition") {
            problem(idl(instructions = "[${instruction(argType = """{"generic": "T"}""")}]")) shouldBe
                "instruction 'ix' argument 'value': no type generic parameter named 'T' in scope"
            problem(idl(types = "[${struct("S", """{"array": ["u8", {"generic": "N"}]}""")}]")) shouldBe
                "type 'S' field 'field': no const generic parameter named 'N' in scope"
            // a type parameter cannot stand in for an array length
            problem(idl(types = "[${struct("S", """{"array": ["u8", {"generic": "T"}]}""", """[{"kind": "type", "name": "T"}]""")}]")) shouldBe
                "type 'S' field 'field': no const generic parameter named 'T' in scope"
        }

        test("alias cycles") {
            problem(idl(types = "[${alias("A", "B")}, ${alias("B", "A")}]")) shouldBe "type 'A': alias cycle A -> B -> A"
            problem(idl(types = "[${alias("A", "B")}, ${alias("B", "B")}]")) shouldBe "type 'B': alias cycle B -> B"
        }

        test("an alias chain that ends in a concrete type is valid") {
            valid(idl(types = "[${alias("A", "B")}, ${struct("B", "\"u8\"")}]"))
        }
    }

    context("dispatch") {
        test("identical and prefix-overlapping discriminators are ambiguous") {
            problem(idl(instructions = "[${instruction(name = "a")}, ${instruction(name = "b")}]")) shouldBe
                "instruction 'b': discriminator [1] cannot be told apart from instruction 'a' [1]"
            problem(idl(instructions = "[${instruction(name = "a", discriminator = "[1, 2]")}, ${instruction(name = "b", discriminator = "[1]")}]")) shouldBe
                "instruction 'b': discriminator [1] cannot be told apart from instruction 'a' [1, 2]"
            valid(idl(instructions = "[${instruction(name = "a", discriminator = "[1, 2]")}, ${instruction(name = "b", discriminator = "[1, 3]")}]"))
        }

        test("accounts and events are dispatched separately and may share a discriminator") {
            valid(
                idl(
                    accounts = """[{"name": "S", "discriminator": [1]}]""",
                    events = """[{"name": "S", "discriminator": [1]}]""",
                    types = "[${struct("S", "\"u8\"")}]",
                ),
            )
        }

        test("accounts and events need a layout") {
            problem(idl(accounts = """[{"name": "Missing", "discriminator": [1]}]""")) shouldBe "account 'Missing': no type definition with its name"
            problem(idl(events = """[{"name": "Missing", "discriminator": [1]}]""")) shouldBe "event 'Missing': no type definition with its name"
        }

        test("duplicate names and error codes") {
            problem(idl(types = "[${struct("S", "\"u8\"")}, ${struct("S", "\"u16\"")}]")) shouldBe "duplicate type 'S'"
            problem(idl(errors = """[{"code": 6000, "name": "A"}, {"code": 6000, "name": "B"}]""")) shouldBe "error 'B': duplicate code 6000"
            problem(idl(instructions = """[{"name": "ix", "discriminator": [1], "accounts": [], "args": [{"name": "a", "type": "u8"}, {"name": "a", "type": "u8"}]}]""")) shouldBe
                "duplicate instruction 'ix' argument 'a'"
        }
    }

    context("addresses and account references") {
        test("relations must name an account of the same instruction, including nested groups") {
            valid(idl(instructions = "[${instruction(accounts = """[{"name": "a", "relations": ["b"]}, {"name": "group", "accounts": [{"name": "b"}]}]""")}]"))
            problem(idl(instructions = "[${instruction(accounts = """[{"name": "a", "relations": ["missing"]}]""")}]")) shouldBe
                "instruction 'ix' account 'a': relation to unknown account 'missing'"
        }

        test("argument seeds must name an instruction argument, or a field within one") {
            fun seeded(path: String) = instruction(
                argType = """{"defined": {"name": "Outer"}}""",
                accounts = """[{"name": "a", "pda": {"seeds": [{"kind": "arg", "path": "$path"}]}}]""",
            )
            // fields are reached through struct fields and aliases
            val types = "[${struct("Outer", """{"defined": {"name": "Alias"}}""")}, ${alias("Alias", "Inner")}, ${struct("Inner", "\"u16\"")}]"
            valid(idl(instructions = "[${seeded("value")}]", types = types))
            valid(idl(instructions = "[${seeded("value.field.field")}]", types = types))
            problem(idl(instructions = "[${seeded("missing")}]", types = types)) shouldBe "instruction 'ix' account 'a' PDA seed 0: no argument named 'missing'"
            problem(idl(instructions = "[${seeded("value.missing")}]", types = types)) shouldBe
                "instruction 'ix' account 'a' PDA seed 0: argument path 'value.missing' does not resolve to a single field"
            // a primitive has no fields
            problem(idl(instructions = "[${seeded("value.field.field.more")}]", types = types)) shouldBe
                "instruction 'ix' account 'a' PDA seed 0: argument path 'value.field.field.more' does not resolve to a single field"
        }

        test("account seeds must name an instruction account, and a field of its data when its type is declared") {
            fun seeded(seed: String) = instruction(
                accounts = """[{"name": "owner"}, {"name": "group", "accounts": [{"name": "member"}]}, {"name": "a", "pda": {"seeds": [$seed]}}]""",
            )
            val data = """[{"name": "Data", "discriminator": [7]}]"""
            val types = "[${struct("Data", "\"u64\"")}]"
            valid(idl(instructions = "[${seeded("""{"kind": "account", "path": "owner"}""")}]"))
            valid(idl(instructions = "[${seeded("""{"kind": "account", "path": "group.member"}""")}]"))
            valid(idl(instructions = "[${seeded("""{"kind": "account", "path": "owner.field", "account": "Data"}""")}]", accounts = data, types = types))
            // an account type another program declares cannot be checked
            valid(idl(instructions = "[${seeded("""{"kind": "account", "path": "owner.supply", "account": "Mint"}""")}]"))
            problem(idl(instructions = "[${seeded("""{"kind": "account", "path": "missing"}""")}]")) shouldBe
                "instruction 'ix' account 'a' PDA seed 0: no account named by 'missing'"
            problem(idl(instructions = "[${seeded("""{"kind": "account", "path": "group"}""")}]")) shouldBe
                "instruction 'ix' account 'a' PDA seed 0: no account named by 'group'"
            problem(idl(instructions = "[${seeded("""{"kind": "account", "path": "owner.missing", "account": "Data"}""")}]", accounts = data, types = types)) shouldBe
                "instruction 'ix' account 'a' PDA seed 0: account path 'owner.missing' does not resolve to a single field of 'Data'"
        }

        test("a struct's field names may repeat, but a seed path through a repeated name is ambiguous") {
            val repeated = """{"name": "Outer", "type": {"kind": "struct", "fields": [{"name": "padding", "type": "u64"}, {"name": "padding", "type": "u8"}]}}"""
            fun seeded(path: String) = instruction(
                argType = """{"defined": {"name": "Outer"}}""",
                accounts = """[{"name": "a", "pda": {"seeds": [{"kind": "arg", "path": "$path"}]}}]""",
            )
            valid(idl(instructions = "[${instruction()}]", types = "[$repeated]"))
            problem(idl(instructions = "[${seeded("value.padding")}]", types = "[$repeated]")) shouldBe
                "instruction 'ix' account 'a' PDA seed 0: argument path 'value.padding' does not resolve to a single field"
        }

        test("a PDA in an account group resolves its account seeds within the group first") {
            val grouped = instruction(
                accounts = """[{"name": "group", "accounts": [{"name": "member"}, {"name": "a", "pda": {"seeds": [{"kind": "account", "path": "member"}]}}]}]""",
            )
            valid(idl(instructions = "[$grouped]"))
        }

        test("seeds must fit a derivation") {
            fun pda(pda: String) = instruction(accounts = """[{"name": "a", "pda": $pda}]""")
            fun const(size: Int) = """{"kind": "const", "value": [${List(size) { "1" }.joinToString()}]}"""
            valid(idl(instructions = "[${pda("""{"seeds": [${List(15) { const(32) }.joinToString()}], "program": ${const(32)}}""")}]"))
            problem(idl(instructions = "[${pda("""{"seeds": [${List(16) { const(1) }.joinToString()}]}""")}]")) shouldBe
                "instruction 'ix' account 'a': 16 PDA seeds, but at most 15 fit beside the bump"
            problem(idl(instructions = "[${pda("""{"seeds": [${const(33)}]}""")}]")) shouldBe
                "instruction 'ix' account 'a' PDA seed 0: 33 bytes, but a seed has at most 32"
            problem(idl(instructions = "[${pda("""{"seeds": [], "program": ${const(31)}}""")}]")) shouldBe
                "instruction 'ix' account 'a' PDA program: a program id has 32 bytes, got 31"
            problem(idl(instructions = "[${pda("""{"seeds": [], "program": {"kind": "account", "path": "missing"}}""")}]")) shouldBe
                "instruction 'ix' account 'a' PDA program: no account named by 'missing'"
        }

        test("account names within one group must be unique") {
            problem(idl(instructions = "[${instruction(accounts = """[{"name": "a"}, {"name": "a"}]""")}]")) shouldBe
                "duplicate instruction 'ix' account 'a'"
        }
    }

    test("the validator runs on a model from any source") {
        val program = IdlProgram(
            name = "handwritten",
            version = "1.0.0",
            address = ADDRESS,
            instructions = listOf(IdlInstruction("ix", IdlDiscriminator(listOf(1)), args = listOf(IdlField("x", IdlType.Defined("Missing"))))),
        )
        shouldThrow<IllegalArgumentException> { IdlValidator.validate(program) }.message shouldBe
            "instruction 'ix' argument 'x': no type definition named 'Missing'"
        IdlValidator.validate(program.copy(instructions = emptyList()))
    }
})
