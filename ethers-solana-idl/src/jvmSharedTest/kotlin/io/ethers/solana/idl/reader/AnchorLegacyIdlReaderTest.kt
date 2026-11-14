package io.ethers.solana.idl.reader

import io.ethers.solana.idl.IdlAccountItem
import io.ethers.solana.idl.IdlArrayLength
import io.ethers.solana.idl.IdlEnumVariant
import io.ethers.solana.idl.IdlField
import io.ethers.solana.idl.IdlFields
import io.ethers.solana.idl.IdlGenericArg
import io.ethers.solana.idl.IdlGenericParam
import io.ethers.solana.idl.IdlPda
import io.ethers.solana.idl.IdlProgram
import io.ethers.solana.idl.IdlSeed
import io.ethers.solana.idl.IdlType
import io.ethers.solana.idl.IdlTypeBody
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private fun fixture(name: String): String = AnchorLegacyIdlReaderTest::class.java.getResource("/idl/$name.json")!!.readText()

private fun legacy(json: String): IdlProgram = AnchorLegacyIdlReader.read(json)

/** A legacy IDL with [instructions] and [types], and an address unless [address] is null. */
private fun idl(instructions: String = "[]", types: String = "[]", accounts: String = "[]", events: String = "[]", address: String? = "11111111111111111111111111111111") = """
    {
      "version": "0.1.0",
      "name": "test",
      "instructions": $instructions,
      "accounts": $accounts,
      "types": $types,
      "events": $events
      ${if (address != null) """, "metadata": {"address": "$address"}""" else ""}
    }
"""

/** The roles of [items], as names and flags. */
private fun roles(items: List<IdlAccountItem>): List<Any> = items.map {
    when (it) {
        is IdlAccountItem.Single -> listOf(it.name, it.writable, it.signer, it.optional)
        is IdlAccountItem.Group -> listOf(it.name, roles(it.accounts))
    }
}

class AnchorLegacyIdlReaderTest : FunSpec({
    test("names convert to snake_case as Rust's heck crate does") {
        mapOf(
            "initializeUser" to "initialize_user",
            "setFeeV2" to "set_fee_v2",
            "fooBar2Baz" to "foo_bar2_baz",
            "testV1Abc" to "test_v1_abc",
            "HTTPServer" to "http_server",
            "already_snake" to "already_snake",
            "_padding" to "padding",
            "x" to "x",
        ).forEach { (name, snake) -> AnchorLegacyIdlReader.toSnakeCase(name) shouldBe snake }
    }

    test("a legacy IDL converts as Anchor converts it, matching the same program's Anchor 0.30 IDL") {
        val converted = legacy(fixture("external_legacy"))
        val current = AnchorIdlReader.read(fixture("external"))
        converted.name shouldBe "external"
        converted.address shouldBe current.address

        val shared = listOf("init", "update", "update_composite", "test_compilation_defined_type_param", "test_compilation_return_type")
        for (name in shared) {
            val ix = converted.instructions.single { it.name == name }
            val expected = current.instructions.single { it.name == name }
            ix.discriminator shouldBe expected.discriminator
            roles(ix.accounts) shouldBe roles(expected.accounts)
            ix.args.map { it.type } shouldBe expected.args.map { it.type }
            ix.returns shouldBe expected.returns
        }
        // the legacy IDL's camelCase name has lost the leading underscore of the Rust name
        converted.instructions.single { it.name == "test_compilation_defined_type_param" }.args.single().name shouldBe "my_account"
        current.instructions.single { it.name == "test_compilation_defined_type_param" }.args.single().name shouldBe "_my_account"

        converted.accounts shouldBe current.accounts.filter { it.name == "MyAccount" }
        converted.events shouldBe current.events
        converted.type("MyAccount") shouldBe current.type("MyAccount")
        converted.type("MyEvent") shouldBe current.type("MyEvent")
    }

    test("Drift's IDL reads, with discriminators matching its mainnet accounts and no address") {
        val drift = legacy(fixture("drift"))
        drift.name shouldBe "drift"
        drift.address shouldBe null
        drift.instructions.size shouldBe 249
        // the first 8 bytes of Drift's State account on mainnet
        drift.accounts.single { it.name == "State" }.discriminator.bytes shouldBe listOf(0xd8, 0x92, 0x6b, 0x5e, 0x68, 0x4b, 0xb6, 0xb1)
        // Rust's `_padding` and `padding` are both `padding` in a legacy IDL
        val fields = (drift.type("LPPool")!!.body as IdlTypeBody.Struct).fields as IdlFields.Named
        fields.fields.count { it.name == "padding" } shouldBe 2
    }

    test("instruction accounts, PDA seeds and relations convert, with paths in snake_case") {
        val program = legacy(
            idl(
                instructions = """[{
                    "name": "openVault",
                    "accounts": [
                      {"name": "ownerAccount", "isMut": false, "isSigner": true},
                      {"name": "vaultState", "isMut": true, "isSigner": false, "isOptional": true, "relations": ["ownerAccount"], "docs": ["The vault."],
                       "pda": {
                         "seeds": [
                           {"kind": "const", "type": "string", "value": "vault"},
                           {"kind": "const", "type": "bytes", "value": [1, 255]},
                           {"kind": "account", "type": "publicKey", "path": "ownerAccount"},
                           {"kind": "account", "type": "u64", "account": "Config", "path": "ownerAccount.lastNonce"},
                           {"kind": "arg", "type": "u16", "path": "vaultIndex"}
                         ],
                         "programId": {"kind": "const", "type": "bytes", "value": [${List(32) { "7" }.joinToString()}]}
                       }},
                      {"name": "extraGroup", "accounts": [{"name": "innerOne", "isMut": true, "isSigner": false}]}
                    ],
                    "args": [{"name": "vaultIndex", "type": "u16"}]
                }]""",
            ),
        )
        val ix = program.instructions.single()
        ix.name shouldBe "open_vault"
        ix.discriminator shouldBe AnchorLegacyIdlReader.discriminator("global", "open_vault")
        ix.args shouldBe listOf(IdlField("vault_index", IdlType.Primitive.U16))
        ix.accounts[0] shouldBe IdlAccountItem.Single("owner_account", signer = true)
        ix.accounts[1] shouldBe IdlAccountItem.Single(
            name = "vault_state",
            writable = true,
            optional = true,
            pda = IdlPda(
                listOf(
                    IdlSeed.Const("vault".map { it.code }),
                    IdlSeed.Const(listOf(1, 255)),
                    IdlSeed.Account("owner_account"),
                    IdlSeed.Account("owner_account.last_nonce", "Config"),
                    IdlSeed.Arg("vault_index"),
                ),
                IdlSeed.Const(List(32) { 7 }),
            ),
            relations = listOf("owner_account"),
            docs = listOf("The vault."),
        )
        ix.accounts[2] shouldBe IdlAccountItem.Group("extra_group", listOf(IdlAccountItem.Single("inner_one", writable = true)))
    }

    test("constant seeds of other types are rejected, as Anchor rejects them") {
        val seeded = idl(
            instructions = """[{"name": "ix", "accounts": [{"name": "a", "isMut": false, "isSigner": false,
                "pda": {"seeds": [{"kind": "const", "type": "u8", "value": 1}]}}], "args": []}]""",
        )
        shouldThrow<IllegalArgumentException> { legacy(seeded) }.message shouldContain "Constant seeds of type U8 are not supported"
    }

    test("types, accounts and events convert, with fields in snake_case and type names kept") {
        val program = legacy(
            idl(
                types = """[
                  {"name": "Holder", "generics": ["T", "N"], "type": {"kind": "struct", "fields": [
                    {"name": "someValue", "type": {"generic": "T"}},
                    {"name": "items", "type": {"genericLenArray": ["u8", "N"]}}
                  ]}},
                  {"name": "Kind", "type": {"kind": "enum", "variants": [
                    {"name": "Plain"},
                    {"name": "Tuple", "fields": ["publicKey", {"option": "u8"}]},
                    {"name": "Named", "fields": [{"name": "innerValue", "type": {"vec": "string"}}]}
                  ]}},
                  {"name": "Short", "type": {"kind": "alias", "value": {"array": ["u8", 4]}}},
                  {"name": "Empty", "type": {"kind": "struct", "fields": []}},
                  {"name": "User", "type": {"kind": "struct", "fields": [
                    {"name": "held", "type": {"definedWithTypeArgs": {"name": "Holder", "args": [{"type": "u64"}, {"value": "3"}]}}}
                  ]}}
                ]""",
                accounts = """[{"name": "Config", "type": {"kind": "struct", "fields": [{"name": "lastNonce", "type": "u64"}]}}]""",
                events = """[{"name": "VaultOpened", "fields": [{"name": "vaultKey", "type": "publicKey", "index": false}]}]""",
            ),
        )
        program.type("Holder")!!.generics shouldBe listOf(IdlGenericParam.Type("T"), IdlGenericParam.Const("N", "usize"))
        program.type("Holder")!!.body shouldBe IdlTypeBody.Struct(
            IdlFields.Named(
                listOf(
                    IdlField("some_value", IdlType.Generic("T")),
                    IdlField("items", IdlType.Array(IdlType.Primitive.U8, IdlArrayLength.Generic("N"))),
                ),
            ),
        )
        program.type("Kind")!!.body shouldBe IdlTypeBody.Enum(
            listOf(
                IdlEnumVariant("Plain"),
                IdlEnumVariant("Tuple", IdlFields.Tuple(listOf(IdlType.Primitive.PUBKEY, IdlType.Option(IdlType.Primitive.U8)))),
                IdlEnumVariant("Named", IdlFields.Named(listOf(IdlField("inner_value", IdlType.Vec(IdlType.Primitive.STRING))))),
            ),
        )
        program.type("Short")!!.body shouldBe IdlTypeBody.Alias(IdlType.Array(IdlType.Primitive.U8, IdlArrayLength.Fixed(4)))
        program.type("Empty")!!.body shouldBe IdlTypeBody.Struct(IdlFields.None)
        val held = ((program.type("User")!!.body as IdlTypeBody.Struct).fields as IdlFields.Named).fields.single().type
        held shouldBe IdlType.Defined("Holder", listOf(IdlGenericArg.Type(IdlType.Primitive.U64), IdlGenericArg.Const("3")))

        program.accounts.single().discriminator shouldBe AnchorLegacyIdlReader.discriminator("account", "Config")
        program.type("Config")!!.body shouldBe IdlTypeBody.Struct(IdlFields.Named(listOf(IdlField("last_nonce", IdlType.Primitive.U64))))
        program.events.single().discriminator shouldBe AnchorLegacyIdlReader.discriminator("event", "VaultOpened")
        program.type("VaultOpened")!!.body shouldBe IdlTypeBody.Struct(IdlFields.Named(listOf(IdlField("vault_key", IdlType.Primitive.PUBKEY))))
    }

    test("the address is metadata.address, or null when the IDL has none") {
        legacy(idl(address = "Externa111111111111111111111111111111111111")).address shouldBe "Externa111111111111111111111111111111111111"
        legacy(idl(address = null)).address shouldBe null
    }

    test("an IDL of the current format is not read") {
        shouldThrow<Exception> { legacy(fixture("relations")) }
    }
})
