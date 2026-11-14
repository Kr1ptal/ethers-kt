package io.ethers.solana.idl.reader

import io.ethers.crypto.Hashing
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
import io.ethers.solana.idl.IdlGenericArg
import io.ethers.solana.idl.IdlGenericParam
import io.ethers.solana.idl.IdlInstruction
import io.ethers.solana.idl.IdlPda
import io.ethers.solana.idl.IdlProgram
import io.ethers.solana.idl.IdlSeed
import io.ethers.solana.idl.IdlType
import io.ethers.solana.idl.IdlTypeBody
import io.ethers.solana.idl.IdlTypeDef
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.InputStream

/**
 * Reader for legacy Anchor IDLs, as produced before Anchor 0.30, converted as Anchor's own legacy converter does.
 *
 * Names of instructions, instruction accounts, fields and seed paths are converted to snake_case, as in later IDLs,
 * and type names are kept. Discriminators, which legacy IDLs do not record, are derived from the names: the first 8
 * bytes of the SHA-256 of `global:<instruction>`, `account:<Account>` or `event:<Event>`. The program address is
 * `metadata.address`, or null when the IDL has none. Legacy IDLs do not record zero-copy layouts, so every type is
 * read as Borsh.
 *
 * Format:
 * ```json
 * {
 *     "version": "...",
 *     "name": "...",
 *     "instructions": [ ... ],
 *     "accounts": [ ... ],
 *     "types": [ ... ],
 *     "events": [ ... ],
 *     "errors": [ ... ],
 *     "constants": [ ... ],
 *     "metadata": { "address": "..." }
 * }
 * ```
 */
object AnchorLegacyIdlReader : SolanaIdlReader {
    private val json = Json { ignoreUnknownKeys = true }

    override fun read(idl: InputStream): IdlProgram {
        val legacy = json.decodeFromString(AnchorLegacyIdl.serializer(), idl.bufferedReader().readText())
        return legacy.toProgram()
    }

    /**
     * [name] in snake_case, splitting words where a lowercase letter or digit is followed by an uppercase letter, and
     * before the last of a run of uppercase letters followed by a lowercase one, as Rust's `heck` crate does.
     */
    internal fun toSnakeCase(name: String): String {
        val words = ArrayList<String>()
        for (part in name.split(Regex("[^\\p{L}\\p{N}]+"))) {
            if (part.isEmpty()) continue
            var start = 0
            // whether the last cased letter was lowercase or uppercase; digits keep the previous case
            var lower: Boolean? = null
            for (i in part.indices) {
                val c = part[i]
                val current = when {
                    c.isLowerCase() -> true
                    c.isUpperCase() -> false
                    else -> lower
                }
                val next = part.getOrNull(i + 1) ?: break
                if (current == true && next.isUpperCase()) {
                    words.add(part.substring(start, i + 1))
                    start = i + 1
                    lower = null
                } else if (lower == false && c.isUpperCase() && next.isLowerCase()) {
                    words.add(part.substring(start, i))
                    start = i
                    lower = null
                } else {
                    lower = current
                }
            }
            words.add(part.substring(start))
        }
        return words.filter(String::isNotEmpty).joinToString("_") { it.lowercase() }
    }

    internal fun discriminator(namespace: String, name: String): IdlDiscriminator {
        val hash = Hashing.sha256("$namespace:$name".encodeToByteArray())
        return IdlDiscriminator(List(8) { hash[it].toInt() and 0xff })
    }
}

private fun AnchorLegacyIdl.toProgram(): IdlProgram {
    val typeDefs = types.map { it.toModel() } +
        accounts.map { it.toModel() } +
        events.map { event -> IdlTypeDef(event.name, IdlTypeBody.Struct(event.fields.namedFields { IdlField(snake(it.name), it.type.toModel()) })) }
    val forwarding = ConstForwarding(typeDefs)
    return IdlProgram(
        name = name,
        version = version,
        address = (metadata?.get("address") as? JsonPrimitive)?.takeIf { it.isString }?.content,
        docs = docs,
        instructions = instructions.map { ix ->
            val name = snake(ix.name)
            IdlInstruction(
                name = name,
                discriminator = AnchorLegacyIdlReader.discriminator("global", name),
                accounts = ix.accounts.map { it.toModel() },
                args = ix.args.map { forwarding.field(it.toModel()) },
                returns = ix.returns?.let { forwarding.type(it.toModel()) },
                docs = ix.docs,
            )
        },
        accounts = accounts.map { IdlAccount(it.name, AnchorLegacyIdlReader.discriminator("account", it.name)) },
        events = events.map { IdlEvent(it.name, AnchorLegacyIdlReader.discriminator("event", it.name)) },
        errors = errors.map { IdlErrorCode(it.code, it.name, it.msg) },
        types = forwarding.types.map(forwarding::typeDef),
        constants = constants.map { IdlConstant(it.name, forwarding.type(it.type.toModel()), it.value) },
    )
}

private fun snake(name: String) = AnchorLegacyIdlReader.toSnakeCase(name)

private fun snakePath(path: String) = path.split('.').joinToString(".", transform = ::snake)

private inline fun <T> List<T>.namedFields(field: (T) -> IdlField): IdlFields = if (isEmpty()) IdlFields.None else IdlFields.Named(map(field))

private fun AnchorLegacyIdl.AccountItem.toModel(): IdlAccountItem = when (this) {
    is AnchorLegacyIdl.Accounts -> IdlAccountItem.Group(snake(name), accounts.map { it.toModel() })
    is AnchorLegacyIdl.Account -> IdlAccountItem.Single(
        name = snake(name),
        writable = isMut,
        signer = isSigner,
        optional = isOptional,
        pda = pda?.let { pda -> IdlPda(pda.seeds.map { it.toModel() }, pda.programId?.toModel()) },
        relations = relations.map(::snakePath),
        docs = docs,
    )
}

private fun AnchorLegacyIdl.Seed.toModel(): IdlSeed = when (this) {
    is AnchorLegacyIdl.Seed.Const -> IdlSeed.Const(
        when (type) {
            AnchorLegacyIdl.Type.Primitive.STRING -> {
                val string = (value as? JsonPrimitive)?.takeIf { it.isString } ?: throw IllegalArgumentException("A string constant seed must be a JSON string, got $value")
                string.content.encodeToByteArray().map { it.toInt() and 0xff }
            }
            AnchorLegacyIdl.Type.Primitive.BYTES -> {
                val array = value as? JsonArray ?: throw IllegalArgumentException("A bytes constant seed must be a JSON array, got $value")
                array.map { byte -> (byte as? JsonPrimitive)?.intOrNull?.takeIf { it in 0..255 } ?: throw IllegalArgumentException("Constant seed bytes must be u8 values, got $byte") }
            }
            else -> throw IllegalArgumentException("Constant seeds of type $type are not supported")
        },
    )
    is AnchorLegacyIdl.Seed.Arg -> IdlSeed.Arg(snakePath(path))
    is AnchorLegacyIdl.Seed.Account -> IdlSeed.Account(snakePath(path), account)
}

private fun AnchorLegacyIdl.TypeDef.toModel(): IdlTypeDef {
    // a generic used as an array length is a const parameter, any other a type parameter
    val lengths = HashSet<String>()
    fun collect(type: AnchorLegacyIdl.Type) {
        when (type) {
            is AnchorLegacyIdl.Type.GenericLenArray -> lengths.add(type.length).also { collect(type.type) }
            is AnchorLegacyIdl.Type.Array -> collect(type.type)
            is AnchorLegacyIdl.Type.Option -> collect(type.option)
            is AnchorLegacyIdl.Type.Vec -> collect(type.vec)
            else -> Unit
        }
    }
    fun collect(fields: AnchorLegacyIdl.Fields?) = when (fields) {
        is AnchorLegacyIdl.Fields.Named -> fields.fields.forEach { collect(it.type) }
        is AnchorLegacyIdl.Fields.Tuple -> fields.types.forEach(::collect)
        null -> Unit
    }
    val body = when (val ty = type) {
        is AnchorLegacyIdl.TypeDefTy.Struct -> {
            ty.fields.forEach { collect(it.type) }
            IdlTypeBody.Struct(ty.fields.namedFields { it.toModel() })
        }
        is AnchorLegacyIdl.TypeDefTy.Enum -> {
            ty.variants.forEach { collect(it.fields) }
            IdlTypeBody.Enum(ty.variants.map { IdlEnumVariant(it.name, it.fields.toModel()) })
        }
        is AnchorLegacyIdl.TypeDefTy.Alias -> {
            collect(ty.value)
            IdlTypeBody.Alias(ty.value.toModel())
        }
    }
    val generics = generics.map { if (it in lengths) IdlGenericParam.Const(it, "usize") else IdlGenericParam.Type(it) }
    return IdlTypeDef(name, body, generics = generics, docs = docs)
}

private fun AnchorLegacyIdl.Fields?.toModel(): IdlFields = when (this) {
    null -> IdlFields.None
    is AnchorLegacyIdl.Fields.Named -> fields.namedFields { it.toModel() }
    is AnchorLegacyIdl.Fields.Tuple -> IdlFields.Tuple(types.map { it.toModel() })
}

private fun AnchorLegacyIdl.Field.toModel() = IdlField(snake(name), type.toModel(), docs)

private fun AnchorLegacyIdl.Type.toModel(): IdlType = when (this) {
    AnchorLegacyIdl.Type.Primitive.PUBLIC_KEY -> IdlType.Primitive.PUBKEY
    is AnchorLegacyIdl.Type.Primitive -> IdlType.Primitive.valueOf(name)
    is AnchorLegacyIdl.Type.Defined -> IdlType.Defined(defined)
    is AnchorLegacyIdl.Type.Option -> IdlType.Option(option.toModel())
    is AnchorLegacyIdl.Type.Vec -> IdlType.Vec(vec.toModel())
    is AnchorLegacyIdl.Type.Array -> IdlType.Array(type.toModel(), IdlArrayLength.Fixed(length))
    is AnchorLegacyIdl.Type.GenericLenArray -> IdlType.Array(type.toModel(), IdlArrayLength.Generic(length))
    is AnchorLegacyIdl.Type.Generic -> IdlType.Generic(generic)
    is AnchorLegacyIdl.Type.DefinedWithTypeArgs -> IdlType.Defined(
        definedWithTypeArgs.name,
        definedWithTypeArgs.args.map { arg ->
            when {
                arg.generic != null -> IdlGenericArg.Type(IdlType.Generic(arg.generic))
                arg.value != null -> IdlGenericArg.Const(arg.value)
                arg.type != null -> IdlGenericArg.Type(arg.type.toModel())
                else -> throw IllegalArgumentException("A type argument must be a generic, value or type")
            }
        },
    )
}
