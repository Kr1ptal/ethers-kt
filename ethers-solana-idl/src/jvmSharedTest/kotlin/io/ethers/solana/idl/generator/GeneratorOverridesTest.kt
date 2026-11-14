package io.ethers.solana.idl.generator

import io.ethers.solana.idl.IdlAccount
import io.ethers.solana.idl.IdlAccountItem
import io.ethers.solana.idl.IdlArrayLength
import io.ethers.solana.idl.IdlConstant
import io.ethers.solana.idl.IdlDiscriminator
import io.ethers.solana.idl.IdlEnumVariant
import io.ethers.solana.idl.IdlErrorCode
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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream

private const val CODEC = "com.example.Codec"
private const val TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"

private fun named(vararg fields: Pair<String, IdlType>) = IdlFields.Named(fields.map { (name, type) -> IdlField(name, type) })

private fun struct(name: String, vararg fields: Pair<String, IdlType>, serialization: IdlSerialization = IdlSerialization.Borsh, repr: IdlRepr? = null) = IdlTypeDef(name, IdlTypeBody.Struct(named(*fields)), serialization = serialization, repr = repr)

private val U8 = IdlType.Primitive.U8
private val U64 = IdlType.Primitive.U64
private val STRING = IdlType.Primitive.STRING
private fun defined(name: String) = IdlType.Defined(name)

/**
 * A program exercising every override target: types `Amount` (an alias), `Price`, `Compressed` (custom serialization),
 * `Side` (an enum), `Vault` (an account), `Swapped` (an event), instruction `deposit` with an account group, an error
 * and a constant.
 */
private fun program(
    types: List<IdlTypeDef> = emptyList(),
    accounts: List<IdlAccount> = listOf(IdlAccount("Vault", IdlDiscriminator(listOf(9)))),
) = IdlProgram(
    "test",
    "1.0.0",
    "11111111111111111111111111111111",
    instructions = listOf(
        IdlInstruction(
            "deposit",
            IdlDiscriminator(listOf(1)),
            accounts = listOf(
                IdlAccountItem.Single("vault", writable = true),
                IdlAccountItem.Single("token_program"),
                IdlAccountItem.Single("system_program", address = "11111111111111111111111111111111"),
                IdlAccountItem.Group("pair", listOf(IdlAccountItem.Single("base"), IdlAccountItem.Single("quote", pda = IdlPda(listOf(IdlSeed.Const(listOf(1))))))),
            ),
            args = listOf(IdlField("amount", defined("Amount")), IdlField("note", STRING)),
        ),
        IdlInstruction("withdraw", IdlDiscriminator(listOf(2))),
    ),
    accounts = accounts,
    events = listOf(IdlEvent("Swapped", IdlDiscriminator(listOf(3)))),
    errors = listOf(IdlErrorCode(6000, "Overflow"), IdlErrorCode(6001, "Underflow")),
    constants = listOf(IdlConstant("MAX", U8, "1")),
    types = listOf(
        IdlTypeDef("Amount", IdlTypeBody.Alias(U64)),
        struct("Price", "mantissa" to IdlType.Primitive.I64, "expo" to IdlType.Primitive.I32),
        struct("Compressed", "value" to IdlType.Primitive.U32, serialization = IdlSerialization.Custom("varint")),
        IdlTypeDef("Side", IdlTypeBody.Enum(listOf(IdlEnumVariant("Buy"), IdlEnumVariant("Sell", named("limit" to U8))))),
        struct("Vault", "owner" to IdlType.Primitive.PUBKEY, "packed" to defined("Compressed"), "price" to defined("Price")),
        struct("Swapped", "amount" to defined("Amount"), "side" to defined("Side")),
    ) + types,
)

private fun generate(overrides: GeneratorOverrides, program: IdlProgram = program()): String = SolanaProgramBuilder(program, "test.pkg", overrides = overrides).build().toString()

private fun rejected(overrides: GeneratorOverrides, program: IdlProgram = program()): String? = shouldThrow<IllegalArgumentException> { generate(overrides, program) }.message

private fun codecs(vararg codecs: Pair<String, GeneratorOverrides.CustomCodec>) = GeneratorOverrides(customCodecs = codecs.toMap())

private fun substitutions(vararg substitutions: Pair<String, GeneratorOverrides.TypeSubstitution>) = GeneratorOverrides(typeSubstitutions = substitutions.toMap())

private fun renames(vararg renames: Pair<String, String>) = GeneratorOverrides(renames = renames.toMap())

private fun defaults(vararg defaults: Pair<String, String>) = GeneratorOverrides(accountDefaults = defaults.toMap())

private val COMPRESSED = "Compressed" to GeneratorOverrides.CustomCodec(CODEC)

private fun String.flat() = replace(Regex("\\s+"), " ")

class GeneratorOverridesTest : FunSpec({
    test("a type with custom serialization fails generation unless an override covers it") {
        shouldThrow<IllegalArgumentException> { SolanaProgramBuilder(program(), "test.pkg") }.message shouldBe
            "type 'Compressed': custom serialization 'varint' cannot be generated from the IDL; configure a custom codec or a type substitution for it"
    }

    context("custom codecs") {
        test("the generated class delegates its codec, and every use reads through it") {
            val source = generate(codecs(COMPRESSED)).flat()
            source shouldContain "public data class Compressed( public val `value`: Long, ) { public companion object : SolanaCodec<Compressed> by Codec }"
            source shouldContain "Read and written by the handwritten [Codec]."
            source shouldContain "import com.example.Codec"
            source shouldContain "packed = Compressed.read(this),"
        }

        test("a type without fields becomes an object delegating to the codec, and an enum keeps its variants") {
            val marker = IdlTypeDef("Marker", IdlTypeBody.Struct(IdlFields.None), serialization = IdlSerialization.Custom("raw"))
            val source = generate(codecs(COMPRESSED, "Marker" to GeneratorOverrides.CustomCodec(CODEC), "Side" to GeneratorOverrides.CustomCodec(CODEC)), program(listOf(marker))).flat()
            source shouldContain "public data object Marker : SolanaCodec<Marker> by Codec"
            source shouldContain "public data class Sell( public val limit: Int, ) : Side() public companion object : SolanaCodec<Side> by Codec"
            source shouldNotContain "invalidVariant(\"Side\""
        }

        test("an account with a custom codec gets its factory and accessors, but no field filters or slices") {
            val source = generate(codecs(COMPRESSED, "Vault" to GeneratorOverrides.CustomCodec(CODEC, size = 100))).flat()
            source shouldContain "public companion object : SolanaCodec<Vault> by Codec { @JvmField public val ACCOUNT: SolanaAccountFactory<Vault>"
            source shouldContain "public fun fetch("
            source shouldNotContain "public fun owner("
            source shouldNotContain "object Fields"
        }

        test("nested in a zero-copy type, an opaque type needs a declared size, and in an unpacked C layout an align") {
            fun zeroCopy(repr: IdlRepr) = struct("Z", "a" to U8, "b" to defined("Compressed"), serialization = IdlSerialization.Bytemuck, repr = repr)
            rejected(codecs(COMPRESSED), program(listOf(zeroCopy(IdlRepr.C(packed = true))))) shouldBe
                "type 'Z' field 'b': 'Compressed' has a custom codec without a declared size, so its size in a zero-copy layout is unknown"
            rejected(codecs("Compressed" to GeneratorOverrides.CustomCodec(CODEC, size = 4)), program(listOf(zeroCopy(IdlRepr.C())))) shouldBe
                "type 'Z' field 'b': 'Compressed' has a custom codec without a declared align, which an unpacked C layout needs"
            val packed = generate(codecs("Compressed" to GeneratorOverrides.CustomCodec(CODEC, size = 4)), program(listOf(zeroCopy(IdlRepr.C(packed = true))))).flat()
            packed shouldContain "a = readU8(), b = Compressed.read(this),"
            val aligned = generate(codecs("Compressed" to GeneratorOverrides.CustomCodec(CODEC, size = 4, align = 4)), program(listOf(zeroCopy(IdlRepr.C())))).flat()
            aligned shouldContain "b = run { skipPadding(3) Compressed.read(this) },"
        }

        test("a custom codec replaces an unsupported zero-copy layout of its own type") {
            val rust = struct("R", "a" to U8, serialization = IdlSerialization.Bytemuck, repr = IdlRepr.Rust())
            rejected(codecs(COMPRESSED), program(listOf(rust))) shouldContain "zero-copy types are only supported with a C or transparent layout"
            generate(codecs(COMPRESSED, "R" to GeneratorOverrides.CustomCodec(CODEC, size = 1)), program(listOf(rust))) shouldContain
                "public companion object : SolanaCodec<R> by Codec"
        }
    }

    context("type substitutions") {
        val amount = "Amount" to GeneratorOverrides.TypeSubstitution("com.example.TokenAmount", "com.example.TokenAmount.Codec", size = 8, align = 8)

        test("a substituted alias or struct takes the existing type and codec, and gets no class") {
            val price = "Price" to GeneratorOverrides.TypeSubstitution("com.example.Price", "com.example.PriceCodec")
            val source = generate(codecs(COMPRESSED).merge(substitutions(amount, price))).flat()
            source shouldContain "public val amount: TokenAmount,"
            source shouldContain "amount = TokenAmount.Codec.read(this),"
            source shouldContain "TokenAmount.Codec.write(this, value.amount)"
            source shouldContain "public val price: Price,"
            source shouldContain "price = PriceCodec.read(this),"
            source shouldNotContain "data class Price("
        }

        test("an account or event cannot be substituted, since they keep their generated class") {
            val vault = "Vault" to GeneratorOverrides.TypeSubstitution("com.example.Vault", CODEC)
            rejected(codecs(COMPRESSED).merge(substitutions(vault))) shouldBe
                "type substitution for type 'Vault': an account keeps its generated class, which holds its account factory and filter; give it a custom codec instead"
            val swapped = "Swapped" to GeneratorOverrides.TypeSubstitution("com.example.Swapped", CODEC)
            rejected(codecs(COMPRESSED).merge(substitutions(swapped))) shouldContain "events cannot be substituted"
        }

        test("a substituted seed type takes the seed's bytes, since the generator cannot encode it") {
            val pda = IdlAccountItem.Single("vault", pda = IdlPda(listOf(IdlSeed.Arg("amount"))))
            val ix = IdlInstruction("open", IdlDiscriminator(listOf(5)), accounts = listOf(pda), args = listOf(IdlField("amount", defined("Amount"))))
            val base = program()
            val source = generate(codecs(COMPRESSED).merge(substitutions(amount)), base.copy(instructions = base.instructions + ix))
            source shouldContain "amountSeed: ByteArray"
        }
    }

    context("wire compatibility") {
        test("a declared size must match the size the IDL lays the type out in, where it does") {
            val price = GeneratorOverrides.TypeSubstitution("com.example.Price", CODEC, size = 16)
            rejected(codecs(COMPRESSED).merge(substitutions("Price" to price))) shouldBe
                "type substitution for type 'Price': declares size 16, but the IDL lays the type out in 12 bytes"
            generate(codecs(COMPRESSED).merge(substitutions("Price" to price.copy(size = 12)))) shouldContain "price = Codec.read(this),"
            val note = struct("Note", "text" to STRING)
            rejected(codecs(COMPRESSED, "Note" to GeneratorOverrides.CustomCodec(CODEC, size = 4)), program(listOf(note))) shouldBe
                "custom codec for type 'Note': declares size 4, but the IDL lays the type out with a variable size"
        }

        test("a declared align must match a zero-copy or primitive alias alignment") {
            val amount = GeneratorOverrides.TypeSubstitution("com.example.TokenAmount", CODEC, size = 8, align = 4)
            rejected(codecs(COMPRESSED).merge(substitutions("Amount" to amount))) shouldBe
                "type substitution for type 'Amount': declares align 4, but the IDL aligns the type to 8"
            val zeroCopy = struct("Z", "a" to U8, "b" to U64, serialization = IdlSerialization.Bytemuck, repr = IdlRepr.C())
            rejected(codecs(COMPRESSED, "Z" to GeneratorOverrides.CustomCodec(CODEC, size = 9)), program(listOf(zeroCopy))) shouldBe
                "custom codec for type 'Z': declares size 9, but the IDL lays the type out in 16 bytes"
        }

        test("custom serialization leaves the layout to the caller") {
            generate(codecs("Compressed" to GeneratorOverrides.CustomCodec(CODEC, size = 3, align = 2))) shouldContain "by Codec"
        }

        test("account offsets continue past an opaque type of declared size, and stop after one without") {
            val variable = generate(codecs(COMPRESSED)).flat()
            variable shouldContain "public fun packed(`value`: Compressed): Filter = field(32) { Compressed.write(this, value) }"
            variable shouldNotContain "public fun price("
            variable shouldNotContain "public val packed: SolanaAccountField"
            val fixed = generate(codecs("Compressed" to GeneratorOverrides.CustomCodec(CODEC, size = 5))).flat()
            fixed shouldContain "SolanaAccountField<Vault, Compressed>(\"packed\", 32, 5,"
            fixed shouldContain "public fun price(`value`: Price): Filter = field(37)"
            fixed shouldContain "Price.Path<Vault>(\"price\", 37)"
        }
    }

    context("renames") {
        val all = renames(
            "type:Price" to "Quote",
            "account:Vault" to "VaultState",
            "field:Vault.owner" to "authority",
            "field:Side.Sell.limit" to "maxPrice",
            "field:Swapped.amount" to "quantity",
            "variant:Side.Buy" to "Bid",
            "instruction:deposit" to "AddFunds",
            "argument:deposit.amount" to "quantity",
            "instructionAccount:deposit.vault" to "vaultState",
            "instructionAccount:deposit.pair" to "market",
            "instructionAccount:deposit.pair.quote" to "quoteVault",
            "event:Swapped" to "SwapDone",
            "error:Overflow" to "TooLarge",
            "constant:MAX" to "LIMIT",
        )

        test("every kind of declaration takes its new name, while the IDL names stay on the wire") {
            val source = generate(codecs(COMPRESSED).merge(all)).flat()
            source shouldContain "public data class Quote("
            source shouldContain "public data class VaultState( public val authority: SolanaAddress,"
            source shouldContain "SolanaAccountFactory(\"Vault\""
            source shouldContain "public data object Bid : Side()"
            source shouldContain "public val maxPrice: Int,"
            source shouldContain "public data class SwapDone( public val quantity: BigInteger,"
            source shouldContain "override val name: String = \"Swapped\""
            source shouldContain "public data object TooLarge : Error(6_000L, \"Overflow\", null)"
            source shouldContain "public const val LIMIT: Int = 1"
            source shouldContain "public val ADD_FUNDS: SolanaInstructionDescriptor<AddFundsArgs> = SolanaInstructionDescriptor(\"deposit\""
            source shouldContain "public fun addFunds( accounts: AddFundsAccounts, args: AddFundsArgs,"
            source shouldContain "public data class AddFunds( public val accounts: AddFundsAccounts,"
            source shouldContain "public data class AddFundsArgs( public val quantity: BigInteger,"
            source shouldContain "public val vaultState: SolanaAddress,"
            source shouldContain "public val market: Market,"
            source shouldContain "public data class Market( public val base: SolanaAddress,"
            source shouldContain "public val quoteVault: SolanaAddress,"
            source shouldContain "public fun findQuoteVault("
            source shouldContain "vaultState = reader.required(\"vault\", false, true),"
        }

        test("a tuple field is renamed by its index") {
            val tuple = IdlTypeDef("Pair", IdlTypeBody.Struct(IdlFields.Tuple(listOf(U8, U8))))
            generate(codecs(COMPRESSED).merge(renames("field:Pair.1" to "second")), program(listOf(tuple))).flat() shouldContain
                "public val field0: Int, public val second: Int,"
            rejected(codecs(COMPRESSED).merge(renames("field:Pair.2" to "third")), program(listOf(tuple))) shouldBe
                "rename 'field:Pair.2': type 'Pair' has no tuple field 2"
            rejected(codecs(COMPRESSED).merge(renames("field:Pair.1" to "field0")), program(listOf(tuple))) shouldBe
                "rename 'field:Pair.1': 'field0' is also the name of field 0"
        }

        test("targets the program does not declare are rejected") {
            fun unknown(target: String) = rejected(codecs(COMPRESSED).merge(renames(target to "Name")))
            unknown("struct:Price") shouldContain "rename 'struct:Price': unknown kind 'struct', expected <kind>:<path> with a kind of [type, account,"
            unknown("Price") shouldContain "unknown kind ''"
            unknown("type:Missing") shouldBe "rename 'type:Missing': the program declares no type 'Missing'"
            unknown("type:Amount") shouldBe "rename 'type:Amount': 'Amount' is an alias, which has no class of its own"
            unknown("account:Price") shouldBe "rename 'account:Price': the program declares no account 'Price'"
            unknown("field:Price.missing") shouldBe "rename 'field:Price.missing': type 'Price' has no field 'missing'"
            unknown("field:Side.limit") shouldBe "rename 'field:Side.limit': type 'Side' is an enum; name a variant's field as field:Side.<Variant>.<field>"
            unknown("field:Side.Hold.limit") shouldBe "rename 'field:Side.Hold.limit': type 'Side' has no variant 'Hold'"
            unknown("field:Side.Buy.limit") shouldBe "rename 'field:Side.Buy.limit': type 'Side' variant 'Buy' has no fields"
            unknown("variant:Side.Hold") shouldBe "rename 'variant:Side.Hold': type 'Side' has no variant 'Hold'"
            unknown("instruction:swap") shouldBe "rename 'instruction:swap': the program declares no instruction 'swap'"
            unknown("argument:deposit.fee") shouldBe "rename 'argument:deposit.fee': instruction 'deposit' has no argument 'fee'"
            unknown("instructionAccount:deposit.pair.missing") shouldBe "rename 'instructionAccount:deposit.pair.missing': instruction 'deposit' has no account at 'pair.missing'"
            unknown("instructionAccount:deposit") shouldBe "rename 'instructionAccount:deposit': expected instructionAccount:<instruction>.<account path>"
            unknown("event:Missing") shouldBe "rename 'event:Missing': the program declares no event 'Missing'"
            unknown("error:Missing") shouldBe "rename 'error:Missing': the program declares no error 'Missing'"
            unknown("constant:MIN") shouldBe "rename 'constant:MIN': the program declares no constant 'MIN'"
        }

        test("a field named more than once in the IDL cannot be renamed, being ambiguous") {
            val repeated = struct("Padded", "padding" to U8, "padding" to U8)
            rejected(codecs(COMPRESSED).merge(renames("field:Padded.padding" to "pad")), program(listOf(repeated))) shouldBe
                "rename 'field:Padded.padding': type 'Padded' has more than one field named 'padding'"
        }

        test("names that are not valid Kotlin identifiers are rejected") {
            for (name in listOf("1abc", "my-name", "class", "", "_", "a b")) {
                rejected(codecs(COMPRESSED).merge(renames("type:Price" to name))) shouldBe "rename 'type:Price': '$name' is not a valid Kotlin identifier"
            }
        }

        test("renames that collide with another declaration are rejected") {
            fun collision(vararg renames: Pair<String, String>) = rejected(codecs(COMPRESSED).merge(renames(*renames)))
            collision("type:Price" to "Side") shouldBe "generated type 'Side' is produced by both type 'Price' and type 'Side'"
            collision("type:Price" to "Event") shouldBe "generated type 'Event' is produced by both type 'Price' and the event hierarchy"
            collision("instruction:deposit" to "Withdraw") shouldBe "generated type 'WithdrawArgs' is produced by both instruction 'deposit' and instruction 'withdraw'"
            collision("field:Vault.owner" to "price") shouldBe "rename 'field:Vault.owner': 'price' is also the name of field 'price'"
            collision("argument:deposit.amount" to "note") shouldBe "rename 'argument:deposit.amount': 'note' is also the name of field 'note'"
            collision("field:Swapped.amount" to "context") shouldBe "rename 'field:Swapped.amount': 'context' is reserved in this declaration"
            collision("variant:Side.Buy" to "Sell") shouldBe "generated variant 'Side.Sell' is produced by both type 'Side' variant 'Buy' and type 'Side' variant 'Sell'"
            collision("error:Overflow" to "Underflow") shouldBe "generated error 'Error.Underflow' is produced by both error 'Overflow' and error 'Underflow'"
            collision("constant:MAX" to "PROGRAM_ID") shouldBe "generated member 'PROGRAM_ID' is produced by both the program and constant 'MAX'"
            collision("instructionAccount:deposit.vault" to "tokenProgram") shouldBe
                "instruction 'deposit': accounts 'vault' and 'token_program' both take the property name 'tokenProgram'"
            collision("type:Price" to "Quote", "account:Price" to "Other") shouldContain "the program declares no account 'Price'"
            collision("type:Vault" to "A", "account:Vault" to "B") shouldBe "rename 'account:Vault': conflicts with rename 'type:Vault'"
        }

        test("a substituted type has no class to rename") {
            val price = "Price" to GeneratorOverrides.TypeSubstitution("com.example.Price", CODEC)
            rejected(codecs(COMPRESSED).merge(substitutions(price)).merge(renames("type:Price" to "Quote"))) shouldBe
                "rename 'type:Price': 'Price' is substituted, so has no generated class"
        }
    }

    context("account defaults") {
        test("an instruction account parameter defaults to the configured address") {
            val source = generate(codecs(COMPRESSED).merge(defaults("deposit.token_program" to TOKEN_PROGRAM, "deposit.pair.base" to TOKEN_PROGRAM))).flat()
            source shouldContain "public val tokenProgram: SolanaAddress = SolanaAddress(\"$TOKEN_PROGRAM\"),"
            source shouldContain "public val base: SolanaAddress = SolanaAddress(\"$TOKEN_PROGRAM\"),"
        }

        test("defaults must name an account that takes an address, and be a valid address") {
            fun default(target: String, address: String = TOKEN_PROGRAM) = rejected(codecs(COMPRESSED).merge(defaults(target to address)))
            default("swap.vault") shouldBe "account default for 'swap.vault': the program declares no instruction 'swap'"
            default("deposit") shouldBe "account default for 'deposit': expected <instruction>.<account path>"
            default("deposit.missing") shouldBe "account default for 'deposit.missing': instruction 'deposit' has no account at 'missing'"
            default("deposit.pair") shouldBe "account default for 'deposit.pair': 'pair' is an account group, which has no address of its own"
            default("deposit.system_program") shouldBe
                "account default for 'deposit.system_program': the IDL fixes its address to 11111111111111111111111111111111"
            default("deposit.vault", "not-base58!") shouldBe "account default for 'deposit.vault': 'not-base58!' is not a base58 address"
            default("deposit.vault", "abc") shouldBe "account default for 'deposit.vault': 'abc' decodes to 3 bytes, but an address has 32"
        }
    }

    context("conflicts and unknown types") {
        test("a custom codec and a type substitution for one type are rejected") {
            val substitution = substitutions("Compressed" to GeneratorOverrides.TypeSubstitution("com.example.C", CODEC))
            rejected(codecs(COMPRESSED).merge(substitution)) shouldBe "type 'Compressed': has both a custom codec and a type substitution, configure one"
        }

        test("overrides naming types the program does not declare, or that cannot take them, are rejected") {
            rejected(codecs(COMPRESSED, "Missing" to GeneratorOverrides.CustomCodec(CODEC))) shouldBe "custom codec for type 'Missing': the program declares no such type"
            rejected(codecs(COMPRESSED).merge(substitutions("Missing" to GeneratorOverrides.TypeSubstitution("com.example.M", CODEC)))) shouldBe
                "type substitution for type 'Missing': the program declares no such type"
            rejected(codecs(COMPRESSED, "Amount" to GeneratorOverrides.CustomCodec(CODEC))) shouldBe
                "custom codec for type 'Amount': an alias has no class of its own for the codec to read; substitute it instead"
            rejected(codecs(COMPRESSED, "Swapped" to GeneratorOverrides.CustomCodec(CODEC))) shouldBe
                "custom codec for type 'Swapped': custom codecs for event types are not supported yet"
        }

        test("a generic type definition fails generation even with an override") {
            val generic = IdlTypeDef("G", IdlTypeBody.Struct(named("x" to IdlType.Generic("T"))), generics = listOf(IdlGenericParam.Type("T")))
            rejected(codecs(COMPRESSED, "G" to GeneratorOverrides.CustomCodec(CODEC)), program(listOf(generic))) shouldBe
                "type 'G': generic type definitions are not supported yet"
        }

        test("merging overrides accepts repeated identical entries and rejects differing ones") {
            val a = GeneratorOverrides().withRename("type:Price", "Quote").withCustomCodec("Compressed", GeneratorOverrides.CustomCodec(CODEC))
            a.withRename("type:Price", "Quote") shouldBe a
            shouldThrow<IllegalArgumentException> { a.withRename("type:Price", "Other") }.message shouldBe
                "conflicting rename for 'type:Price': Quote and Other"
            shouldThrow<IllegalArgumentException> { a.withCustomCodec("Compressed", GeneratorOverrides.CustomCodec(CODEC, size = 1)) }.message shouldContain
                "conflicting custom codec for 'Compressed'"
            shouldThrow<IllegalArgumentException> {
                GeneratorOverrides.mergeAll(mapOf("p" to a), mapOf("p" to GeneratorOverrides(accountDefaults = mapOf("x.y" to "1")).withRename("type:Price", "B")))
            }.message shouldBe "program 'p': conflicting rename for 'type:Price': Quote and B"
        }

        test("codec and type names must be fully qualified Kotlin class names, and layouts valid") {
            for (name in listOf("Codec", "com.example.codec", "com.example.", "com..Codec", "com.example.My-Codec")) {
                shouldThrow<IllegalArgumentException> { GeneratorOverrides.CustomCodec(name) }.message shouldBe
                    "codec '$name' is not a fully qualified Kotlin class name, e.g. com.example.Name"
            }
            shouldThrow<IllegalArgumentException> { GeneratorOverrides.TypeSubstitution("Amount", CODEC) }.message shouldContain "type 'Amount'"
            shouldThrow<IllegalArgumentException> { GeneratorOverrides.CustomCodec(CODEC, size = -1) }.message shouldBe "size -1 must not be negative"
            shouldThrow<IllegalArgumentException> { GeneratorOverrides.CustomCodec(CODEC, align = 4) }.message shouldBe "align 4 requires a size"
            shouldThrow<IllegalArgumentException> { GeneratorOverrides.CustomCodec(CODEC, size = 4, align = 3) }.message shouldBe "align 3 must be a positive power of two"
        }
    }

    context("determinism") {
        test("configuration order does not change the overrides, their serialized form or the generated output") {
            val entries = listOf(
                "type:Price" to "Quote",
                "field:Vault.owner" to "authority",
                "variant:Side.Buy" to "Bid",
                "constant:MAX" to "LIMIT",
            )
            val forward = entries.fold(GeneratorOverrides().withCustomCodec("Compressed", GeneratorOverrides.CustomCodec(CODEC))) { acc, (t, n) -> acc.withRename(t, n) }
                .withAccountDefault("deposit.token_program", TOKEN_PROGRAM)
            val backward = entries.reversed().fold(GeneratorOverrides().withAccountDefault("deposit.token_program", TOKEN_PROGRAM)) { acc, (t, n) -> acc.withRename(t, n) }
                .withCustomCodec("Compressed", GeneratorOverrides.CustomCodec(CODEC))
            forward shouldBe backward
            forward.hashCode() shouldBe backward.hashCode()
            fun serialized(value: Any) = ByteArrayOutputStream().also { ObjectOutputStream(it).use { out -> out.writeObject(value) } }.toByteArray().toList()
            serialized(forward) shouldBe serialized(backward)
            generate(forward) shouldBe generate(backward)
        }

        test("an array of a substituted type keeps the substitution") {
            val holder = struct("Holder", "amounts" to IdlType.Array(defined("Amount"), IdlArrayLength.Fixed(2)))
            val amount = GeneratorOverrides.TypeSubstitution("com.example.TokenAmount", CODEC, size = 8)
            generate(codecs(COMPRESSED).merge(substitutions("Amount" to amount)), program(listOf(holder))).flat() shouldContain
                "amounts = readArray(2) { Codec.read(this) },"
        }
    }

    context("account field paths") {
        val inner = struct("Inner", "a" to U8, "b" to IdlType.Primitive.U16)
        fun holder(vararg fields: Pair<String, IdlType>, types: List<IdlTypeDef> = listOf(inner)) = program(
            types + struct("Holder", *fields),
            accounts = listOf(IdlAccount("Vault", IdlDiscriminator(listOf(9))), IdlAccount("Holder", IdlDiscriminator(listOf(8)))),
        )
        val sized = "Compressed" to GeneratorOverrides.CustomCodec(CODEC, size = 5)

        test("field renames apply to path descriptors, including the suffix for a name a descriptor member takes") {
            val source = generate(codecs(COMPRESSED).merge(renames("field:Inner.a" to "offset", "field:Inner.b" to "second")), holder("inner" to defined("Inner"))).flat()
            source shouldContain "public val offsetField: SolanaAccountField<T, Int> = SolanaAccountField<T, Int>(name + \".offset\", offset, 1,"
            source shouldContain "public val second: SolanaAccountField<T, Int> = SolanaAccountField<T, Int>(name + \".second\", offset + 1, 2,"
        }

        test("a rename whose suffixed descriptor name collides with another field's is rejected") {
            val clash = struct("Inner", "a" to U8, "offset_field" to U8)
            rejected(codecs(COMPRESSED).merge(renames("field:Inner.a" to "offset")), holder("inner" to defined("Inner"), types = listOf(clash))) shouldBe
                "type 'Inner': fields 'offset' and 'offsetField' both take the path descriptor name 'offsetField', as a descriptor member takes the name"
        }

        test("a path does not descend into a type with a custom codec or substitution, which a sized override describes whole") {
            val plain = generate(codecs(COMPRESSED), holder("inner" to defined("Inner"), "after" to U8)).flat()
            plain shouldContain "public class Path<T>("
            val overridden = codecs(COMPRESSED, "Inner" to GeneratorOverrides.CustomCodec(CODEC, size = 3))
            val opaque = generate(overridden, holder("inner" to defined("Inner"), "after" to U8)).flat()
            opaque shouldNotContain "class Path"
            opaque shouldContain "public val `inner`: SolanaAccountField<Holder, Inner> = SolanaAccountField<Holder, Inner>(\"inner\", 0, 3,"
            opaque shouldContain "public val after: SolanaAccountField<Holder, Int> = SolanaAccountField<Holder, Int>(\"after\", 3, 1,"

            val price = "Price" to GeneratorOverrides.TypeSubstitution("com.example.Quote", "com.example.QuoteCodec", size = 12)
            val substituted = generate(codecs(sized).merge(substitutions(price)), holder("prices" to IdlType.Array(defined("Price"), IdlArrayLength.Fixed(2)), "after" to U8)).flat()
            substituted shouldContain "SolanaAccountArrayField<Holder, List<Quote>, SolanaAccountField<Holder, Quote>>"
            substituted shouldContain "SolanaAccountField<Holder, Int>(\"after\", 24, 1,"
        }

        test("an opaque type without a declared size ends every following path and array descriptor") {
            val source = generate(codecs(COMPRESSED), holder("first" to U8, "packed" to defined("Compressed"), "inner" to defined("Inner"), "list" to IdlType.Array(IdlType.Primitive.U16, IdlArrayLength.Fixed(2)))).flat()
            source shouldContain "SolanaAccountField<Holder, Int>(\"first\", 0, 1,"
            source shouldContain "- `packed` has a variable size"
            source shouldContain "- `inner` follows a field of variable size, so has no fixed offset"
            source shouldContain "- `list` follows a field of variable size, so has no fixed offset"
            source shouldNotContain "class Path"
            source shouldNotContain "SolanaAccountArrayField"
        }
    }

    context("constants") {
        fun withConstant(type: IdlType, value: String, overrides: GeneratorOverrides, name: String = "C") = generate(codecs(COMPRESSED).merge(overrides), program().let { it.copy(constants = it.constants + IdlConstant(name, type, value)) })
        fun rejectedConstant(type: IdlType, value: String, overrides: GeneratorOverrides) = shouldThrow<IllegalArgumentException> {
            withConstant(type, value, overrides)
        }.message

        test("a renamed composite constant takes its new name, and the renamed fields of its type") {
            val source = withConstant(defined("Price"), "Price { mantissa: 5, expo: -2 }", renames("constant:C" to "QUOTE", "field:Price.expo" to "exponent")).flat()
            source shouldContain "public val QUOTE: Price = Price(mantissa = 5L, exponent = -2)"
        }

        test("a constant of a type with a custom codec is constructed from the IDL value, as its generated class") {
            withConstant(defined("Compressed"), "Compressed { value: 7 }", GeneratorOverrides()).flat() shouldContain
                "public val C: Compressed = Compressed(`value` = 7L)"
        }

        test("a constant of, or containing, a substituted type fails generation") {
            val amount = substitutions("Amount" to GeneratorOverrides.TypeSubstitution("com.example.TokenAmount", CODEC, size = 8))
            rejectedConstant(defined("Amount"), "5", amount) shouldBe
                "constant 'C': type 'Amount' is substituted by com.example.TokenAmount, which cannot be constructed from the IDL's value; " +
                "constants of substituted types are not supported, in value `5`"
            rejectedConstant(IdlType.Vec(defined("Amount")), "[5]", amount) shouldContain "type 'Amount' is substituted by com.example.TokenAmount"
            val price = substitutions("Price" to GeneratorOverrides.TypeSubstitution("com.example.Quote", CODEC))
            rejectedConstant(IdlType.Option(defined("Price")), "Some(Price { mantissa: 1, expo: 0 })", price) shouldContain
                "type 'Price' is substituted by com.example.Quote"
        }

        test("a primitive constant renamed reports errors under its IDL name") {
            rejectedConstant(U8, "300", renames("constant:C" to "LIMIT")) shouldBe "constant 'C': 300 is outside the range of U8"
        }
    }

    context("JSON configuration") {
        test("parses every section, keyed by program") {
            val json = """
                {
                  "test": {
                    "customCodecs": {"Compressed": {"codec": "$CODEC", "size": 4, "align": 4}},
                    "typeSubstitutions": {"Amount": {"type": "com.example.TokenAmount", "codec": "$CODEC", "size": 8}},
                    "renames": {"type:Price": "Quote"},
                    "accountDefaults": {"deposit.token_program": "$TOKEN_PROGRAM"}
                  },
                  "other": {}
                }
            """.trimIndent()
            val parsed = GeneratorOverrides.parse(json)
            parsed.keys.toList() shouldBe listOf("other", "test")
            parsed.getValue("other").isEmpty() shouldBe true
            parsed.getValue("test") shouldBe GeneratorOverrides(
                customCodecs = mapOf("Compressed" to GeneratorOverrides.CustomCodec(CODEC, 4, 4)),
                typeSubstitutions = mapOf("Amount" to GeneratorOverrides.TypeSubstitution("com.example.TokenAmount", CODEC, 8)),
                renames = mapOf("type:Price" to "Quote"),
                accountDefaults = mapOf("deposit.token_program" to TOKEN_PROGRAM),
            )
        }

        test("rejects unknown keys and invalid entries") {
            shouldThrow<IllegalArgumentException> { GeneratorOverrides.parse("""{"test": {"rename": {}}}""") }.message shouldContain
                "Invalid generator overrides:"
            shouldThrow<IllegalArgumentException> { GeneratorOverrides.parse("""{"test": {"customCodecs": {"C": {"codec": "$CODEC", "sizes": 1}}}}""") }
            shouldThrow<IllegalArgumentException> { GeneratorOverrides.parse("""{"test": {"customCodecs": {"C": {"codec": "codec"}}}}""") }.message shouldBe
                "Invalid generator overrides for program 'test', custom codec 'C': codec 'codec' is not a fully qualified Kotlin class name, e.g. com.example.Name"
            shouldThrow<IllegalArgumentException> { GeneratorOverrides.parse("[]") }
        }
    }
})
