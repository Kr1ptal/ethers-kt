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
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf

private fun struct(name: String, vararg fields: Pair<String, IdlType>, serialization: IdlSerialization = IdlSerialization.Borsh) = IdlTypeDef(name, IdlTypeBody.Struct(IdlFields.Named(fields.map { (n, t) -> IdlField(n, t) })), serialization = serialization)

private fun defined(name: String) = IdlType.Defined(name)

private fun discriminator(vararg bytes: Int) = IdlDiscriminator(bytes.toList())

private val GOOD = struct("Good", "x" to IdlType.Primitive.U8)
private val GOOD_ACCOUNT = struct("GoodAccount", "owner" to IdlType.Primitive.PUBKEY, "value" to IdlType.Primitive.U64, "nested" to defined("Good"))
private val GOOD_EVENT = struct("GoodEvent", "v" to IdlType.Primitive.U8)

/** A program whose every declaration can be generated. */
private val CLEAN = IdlProgram(
    "test",
    "1.0.0",
    "11111111111111111111111111111111",
    instructions = listOf(
        IdlInstruction(
            "good_ix",
            discriminator(10),
            accounts = listOf(IdlAccountItem.Single("payer", writable = true, signer = true)),
            args = listOf(IdlField("amount", IdlType.Primitive.U64)),
        ),
        IdlInstruction(
            "seeded_ok",
            discriminator(14),
            accounts = listOf(
                IdlAccountItem.Single("owner_acc"),
                IdlAccountItem.Single("vault", pda = IdlPda(listOf(IdlSeed.Const(listOf(118)), IdlSeed.Account("owner_acc.owner", "GoodAccount")))),
            ),
        ),
    ),
    accounts = listOf(IdlAccount("GoodAccount", discriminator(1))),
    events = listOf(IdlEvent("GoodEvent", discriminator(3))),
    types = listOf(GOOD, GOOD_ACCOUNT, GOOD_EVENT),
    constants = listOf(IdlConstant("GOOD", IdlType.Primitive.U8, "1")),
)

/** [CLEAN], plus declarations that cannot be generated, and declarations that depend on one of them. */
private val MIXED = CLEAN.copy(
    instructions = CLEAN.instructions + listOf(
        IdlInstruction("bad_arg", discriminator(11), args = listOf(IdlField("h", defined("Holder")))),
        IdlInstruction("bad_return", discriminator(12), returns = defined("Wrapper")),
        IdlInstruction(
            "seeded",
            discriminator(13),
            accounts = listOf(
                IdlAccountItem.Single("holder"),
                // a seed reading the data of a Holder account, whose type is not generated
                IdlAccountItem.Single("vault", pda = IdlPda(listOf(IdlSeed.Const(listOf(118)), IdlSeed.Account("holder.good", "Holder")))),
            ),
        ),
    ),
    accounts = CLEAN.accounts + IdlAccount("Holder", discriminator(2)),
    events = CLEAN.events + IdlEvent("HolderEvent", discriminator(4)),
    types = CLEAN.types + listOf(
        // custom serialization: unsupported by design
        struct("Bad", "x" to IdlType.Primitive.U8, serialization = IdlSerialization.Custom("Compressed")),
        // a generic definition nothing uses: not supported yet
        IdlTypeDef("Generic", IdlTypeBody.Struct(IdlFields.Named(listOf(IdlField("t", IdlType.Generic("T"))))), generics = listOf(IdlGenericParam.Type("T"))),
        // Bad nested within an option, a vec and an array
        struct("Holder", "good" to defined("Good"), "bad" to IdlType.Option(IdlType.Vec(IdlType.Array(defined("Bad"), IdlArrayLength.Fixed(2))))),
        IdlTypeDef("HolderAlias", IdlTypeBody.Alias(defined("Holder"))),
        struct("Wrapper", "h" to defined("HolderAlias")),
        struct("HolderEvent", "h" to defined("Holder")),
    ),
    constants = CLEAN.constants + listOf(
        IdlConstant("HOLDER_C", defined("HolderAlias"), "Holder { good: Good { x: 1 }, bad: None }"),
        IdlConstant("EXPR", IdlType.Primitive.U64, "10 * 1000"),
    ),
)

private fun diagnostic(kind: SolanaIdlDiagnostic.Kind, name: String, support: SolanaIdlDiagnostic.Support, message: String, dependsOn: String? = null) = SolanaIdlDiagnostic(kind, name, support, message, dependsOn)

private fun dependent(kind: SolanaIdlDiagnostic.Kind, name: String, type: String) = diagnostic(
    kind,
    name,
    SolanaIdlDiagnostic.Support.INTENTIONALLY_UNSUPPORTED,
    "${kind.label} '$name': depends on type '$type', which is not generated",
    "type '$type'",
)

private val MIXED_DIAGNOSTICS = listOf(
    diagnostic(
        SolanaIdlDiagnostic.Kind.TYPE,
        "Bad",
        SolanaIdlDiagnostic.Support.INTENTIONALLY_UNSUPPORTED,
        "type 'Bad': Custom(name=Compressed) serialization is intentionally unsupported, as the program's own code defines that layout; " +
            "bind the type with a handwritten codec instead",
    ),
    diagnostic(SolanaIdlDiagnostic.Kind.TYPE, "Generic", SolanaIdlDiagnostic.Support.NOT_SUPPORTED_YET, "type 'Generic': generic type definitions are not supported yet"),
    dependent(SolanaIdlDiagnostic.Kind.TYPE, "Holder", "Bad"),
    dependent(SolanaIdlDiagnostic.Kind.TYPE, "HolderAlias", "Holder"),
    dependent(SolanaIdlDiagnostic.Kind.TYPE, "Wrapper", "HolderAlias"),
    dependent(SolanaIdlDiagnostic.Kind.TYPE, "HolderEvent", "Holder"),
    dependent(SolanaIdlDiagnostic.Kind.CONSTANT, "HOLDER_C", "HolderAlias"),
    diagnostic(
        SolanaIdlDiagnostic.Kind.CONSTANT,
        "EXPR",
        SolanaIdlDiagnostic.Support.NOT_SUPPORTED_YET,
        "constant 'EXPR': `10 * 1000` is not a literal of type U64; only literal values are supported, not expressions, casts or references to other constants",
    ),
    dependent(SolanaIdlDiagnostic.Kind.ACCOUNT, "Holder", "Holder"),
    dependent(SolanaIdlDiagnostic.Kind.EVENT, "HolderEvent", "HolderEvent"),
    dependent(SolanaIdlDiagnostic.Kind.INSTRUCTION, "bad_arg", "Holder"),
    dependent(SolanaIdlDiagnostic.Kind.INSTRUCTION, "bad_return", "Wrapper"),
    dependent(SolanaIdlDiagnostic.Kind.INSTRUCTION, "seeded", "Holder"),
)

/** [source] without the documentation listing what was not generated. */
private fun withoutReport(source: String): String {
    val lines = source.lines().toMutableList()
    val start = lines.indexOfFirst { it.contains("Not generated, as these bindings cannot represent them:") }
    if (start < 0) return source
    var end = start + 1
    while (lines[end].trimStart().startsWith("* - ")) end++
    // the blank documentation line before the list goes with it
    lines.subList(start - 1, end).clear()
    return lines.joinToString("\n")
}

class LenientGenerationTest : FunSpec({
    test("an unsupported type nothing uses is left out, and the rest generates as without it") {
        val bad = struct("Bad", "x" to IdlType.Primitive.U8, serialization = IdlSerialization.Custom("Compressed"))
        val builder = SolanaProgramBuilder(CLEAN.copy(types = CLEAN.types + bad), "test.pkg")
        builder.diagnostics shouldContainExactly MIXED_DIAGNOSTICS.take(1)
        val source = builder.build().toString()
        source shouldNotContain "class Bad"
        withoutReport(source) shouldBe SolanaProgramBuilder(CLEAN, "test.pkg", strict = true).build().toString()
    }

    test("everything that depends on an unsupported type is left out with it, and reported") {
        val builder = SolanaProgramBuilder(MIXED, "test.pkg")
        builder.diagnostics shouldContainExactly MIXED_DIAGNOSTICS

        val source = builder.build().toString()
        for (name in listOf("class Bad", "class Holder", "class Wrapper", "HOLDER_C", "EXPR", "BadArg", "badReturn", "fun seeded(", "class HolderEvent")) {
            withoutReport(source) shouldNotContain name
        }
        // what is left generates exactly as in a program without what was left out
        withoutReport(source) shouldBe SolanaProgramBuilder(CLEAN, "test.pkg", strict = true).build().toString()
        // the program object documents what is missing
        source shouldContain " * Not generated, as these bindings cannot represent them:\n"
        for (diagnostic in MIXED_DIAGNOSTICS) source shouldContain " * - $diagnostic\n"
    }

    test("a strict builder fails with every diagnostic") {
        val error = shouldThrow<SolanaIdlUnsupportedException> { SolanaProgramBuilder(MIXED, "test.pkg", strict = true) }
        error.program shouldBe "test"
        error.diagnostics shouldBe MIXED_DIAGNOSTICS
        error.message shouldBe "program 'test': ${MIXED_DIAGNOSTICS.size} declaration(s) cannot be generated:\n" +
            MIXED_DIAGNOSTICS.joinToString("\n") { "- $it" }
        SolanaProgramBuilder(CLEAN, "test.pkg", strict = true).diagnostics shouldBe emptyList()
    }

    test("a zero-copy layout failing leaves out the types laid out with it") {
        val zeroCopy = IdlSerialization.BytemuckUnsafe
        val status = IdlTypeDef("Status", IdlTypeBody.Enum(listOf(IdlEnumVariant("On"))))
        val types = listOf(
            status,
            struct("Leaf", "s" to defined("Status"), serialization = zeroCopy).copy(repr = IdlRepr.C()),
            struct("Branch", "leaf" to defined("Leaf"), serialization = zeroCopy).copy(repr = IdlRepr.C()),
            // a layout failing on its own: Pod with padding
            struct("Padded", "a" to IdlType.Primitive.U8, "b" to IdlType.Primitive.U64, serialization = IdlSerialization.Bytemuck).copy(repr = IdlRepr.C()),
            struct("UsesPadded", "p" to defined("Padded"), serialization = zeroCopy).copy(repr = IdlRepr.C()),
            struct("Fine", "a" to IdlType.Primitive.U64, serialization = zeroCopy).copy(repr = IdlRepr.C()),
        )
        val builder = SolanaProgramBuilder(CLEAN.copy(types = CLEAN.types + types), "test.pkg")
        builder.diagnostics.map { it.kind to it.name } shouldBe listOf("Leaf", "Branch", "Padded", "UsesPadded").map { SolanaIdlDiagnostic.Kind.TYPE to it }
        builder.diagnostics.map { it.support }.distinct() shouldBe listOf(SolanaIdlDiagnostic.Support.INTENTIONALLY_UNSUPPORTED, SolanaIdlDiagnostic.Support.INVALID)
        builder.diagnostics[3].dependsOn shouldBe "type 'Padded'"
        val source = builder.build().toString()
        source shouldContain "public data class Fine("
        source shouldContain "public sealed class Status"
    }

    test("an invalid program, colliding names and invalid configuration fail whatever the mode") {
        val bad = struct("Bad", "x" to IdlType.Primitive.U8, serialization = IdlSerialization.Custom("Compressed"))
        val missing = CLEAN.copy(types = CLEAN.types + bad + struct("S", "x" to defined("Missing")))
        shouldThrow<IllegalArgumentException> { SolanaProgramBuilder(missing, "test.pkg") }.message shouldBe
            "type 'S' field 'x': no type definition named 'Missing'"
        // names collide even when one of them is not generated
        val colliding = CLEAN.copy(types = CLEAN.types + struct("my_bad", "x" to IdlType.Primitive.U8, serialization = IdlSerialization.Custom("C")) + struct("MyBad"))
        shouldThrow<IllegalArgumentException> { SolanaProgramBuilder(colliding, "test.pkg") }.message shouldContain "is produced by both"
        // a byte order for a seed of an instruction that is not generated is still checked against it
        val orders = SeedByteOrders(seeds = mapOf(SeedByteOrders.Seed("vault", "nonexistent") to SeedByteOrder.BIG_ENDIAN))
        shouldThrow<IllegalArgumentException> { SolanaProgramBuilder(MIXED, "test.pkg", seedByteOrders = orders) }
            .shouldBeInstanceOf<IllegalArgumentException>().message shouldContain "no PDA account has an integer seed at that path"
        shouldThrow<IllegalArgumentException> { SolanaProgramBuilder(MIXED.copy(address = null), "test.pkg") }.message shouldContain "declares no address"
    }

    test("Drift's zero-copy markets, whose enum fields the IDL cannot lay out, are left out with what uses them") {
        val drift = SolanaIdlReaderRegistry.tryReadIdl(LenientGenerationTest::class.java.getResource("/idl/drift.json")!!).unwrap()
        val markets = setOf("State", "PerpMarket", "SpotMarket")
        // the markets and the structs they contain are zero-copy, as on-chain; their enums have Rust's default layout
        val zeroCopy = HashSet<String>()
        fun mark(name: String) {
            val body = drift.type(name)!!.body as? IdlTypeBody.Struct ?: return
            if (!zeroCopy.add(name)) return
            fun visit(type: IdlType): Unit = when (type) {
                is IdlType.Array -> visit(type.element)
                is IdlType.Defined -> mark(type.name)
                else -> Unit
            }
            (body.fields as IdlFields.Named).fields.forEach { visit(it.type) }
        }
        markets.forEach(::mark)
        val marked = drift.copy(
            address = "dRiftyHA39MWEi3m9aunc5MzRF1JYuBsbn6VPcn33UH",
            types = drift.types.map { if (it.name in zeroCopy) it.copy(serialization = IdlSerialization.BytemuckUnsafe, repr = IdlRepr.C()) else it },
        )
        val builder = SolanaProgramBuilder(marked, "test.pkg")
        val skipped = builder.diagnostics.map { it.kind to it.name }.toSet()
        // State has no enum field, so its C layout is generated
        skipped.contains(SolanaIdlDiagnostic.Kind.ACCOUNT to "State") shouldBe false
        for (market in listOf("PerpMarket", "SpotMarket")) {
            skipped.contains(SolanaIdlDiagnostic.Kind.TYPE to market) shouldBe true
            skipped.contains(SolanaIdlDiagnostic.Kind.ACCOUNT to market) shouldBe true
        }
        builder.diagnostics.first { it.name == "PerpMarket" && it.kind == SolanaIdlDiagnostic.Kind.TYPE }.message shouldContain
            "in a zero-copy type is intentionally unsupported, as the IDL does not record the width of its discriminant"
        builder.diagnostics.all { it.support == SolanaIdlDiagnostic.Support.INTENTIONALLY_UNSUPPORTED } shouldBe true
        val source = builder.build().toString()
        source shouldNotContain "public data class PerpMarket("
        // accounts that do not use the markets still generate
        source shouldContain "public data class User("
        // strict mode lists the same declarations
        shouldThrow<SolanaIdlUnsupportedException> { SolanaProgramBuilder(marked, "test.pkg", strict = true) }.diagnostics shouldBe builder.diagnostics
    }
})
