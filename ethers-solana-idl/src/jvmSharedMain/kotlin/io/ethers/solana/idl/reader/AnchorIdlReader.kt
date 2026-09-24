package io.ethers.solana.idl.reader

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
import io.ethers.solana.idl.IdlRepr
import io.ethers.solana.idl.IdlSeed
import io.ethers.solana.idl.IdlSerialization
import io.ethers.solana.idl.IdlType
import io.ethers.solana.idl.IdlTypeBody
import io.ethers.solana.idl.IdlTypeDef
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.InputStream

/**
 * Reader for Anchor IDLs of spec version 0.1.x, as produced by Anchor 0.30 and later.
 *
 * Unknown fields are ignored. Legacy IDLs, other spec versions, and input that does not decode as an
 * Anchor IDL are rejected by throwing.
 *
 * Format:
 * ```json
 * {
 *     "address": "...",
 *     "metadata": { "name": "...", "version": "...", "spec": "0.1.0" },
 *     "instructions": [ ... ],
 *     "accounts": [ ... ],
 *     "events": [ ... ],
 *     "errors": [ ... ],
 *     "types": [ ... ],
 *     "constants": [ ... ]
 * }
 * ```
 */
object AnchorIdlReader : SolanaIdlReader {
    /** The major and minor spec version read by this reader; any patch version is accepted. */
    const val SUPPORTED_SPEC = "0.1"

    private val json = Json { ignoreUnknownKeys = true }

    override fun read(idl: InputStream): IdlProgram {
        val text = idl.bufferedReader().readText()
        val anchor = try {
            json.decodeFromString(AnchorIdl.serializer(), text)
        } catch (e: SerializationException) {
            if (isLegacy(text)) throw IllegalArgumentException("Legacy Anchor IDL (before Anchor 0.30) is not supported; regenerate it with Anchor 0.30 or later", e)
            throw e
        }
        val spec = anchor.metadata.spec
        require(spec.split('.').take(2).joinToString(".") == SUPPORTED_SPEC) { "Unsupported Anchor IDL spec $spec; supported: $SUPPORTED_SPEC.x" }
        return anchor.toProgram()
    }

    /** Legacy IDLs have no top-level `address` and no `metadata.spec`. */
    private fun isLegacy(text: String): Boolean {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return false
        return "address" !in root && (root["metadata"] as? JsonObject)?.get("spec") == null && ("version" in root || "name" in root)
    }
}

private fun AnchorIdl.toProgram(): IdlProgram {
    val forwarding = ConstForwarding(types.map { it.toModel() })
    return IdlProgram(
        name = metadata.name,
        version = metadata.version,
        address = address,
        docs = docs,
        instructions = instructions.map { ix ->
            IdlInstruction(
                name = ix.name,
                discriminator = IdlDiscriminator(ix.discriminator),
                accounts = ix.accounts.map { it.toModel() },
                args = ix.args.map { forwarding.field(it.toModel()) },
                returns = ix.returns?.let { forwarding.type(it.toModel()) },
                docs = ix.docs,
            )
        },
        accounts = accounts.map { IdlAccount(it.name, IdlDiscriminator(it.discriminator)) },
        events = events.map { IdlEvent(it.name, IdlDiscriminator(it.discriminator)) },
        errors = errors.map { IdlErrorCode(it.code, it.name, it.msg) },
        types = forwarding.types.map(forwarding::typeDef),
        constants = constants.map { IdlConstant(it.name, forwarding.type(it.type.toModel()), it.value, it.docs) },
    )
}

private fun AnchorIdl.AccountItem.toModel(): IdlAccountItem = when (this) {
    is AnchorIdl.Accounts -> IdlAccountItem.Group(name, accounts.map { it.toModel() })
    is AnchorIdl.Account -> IdlAccountItem.Single(
        name = name,
        writable = writable,
        signer = signer,
        optional = optional,
        address = address,
        pda = pda?.let { pda -> IdlPda(pda.seeds.map { it.toModel() }, pda.program?.toModel()) },
        relations = relations,
        docs = docs,
    )
}

private fun AnchorIdl.Seed.toModel(): IdlSeed = when (this) {
    is AnchorIdl.Seed.Const -> IdlSeed.Const(value)
    is AnchorIdl.Seed.Arg -> IdlSeed.Arg(path)
    is AnchorIdl.Seed.Account -> IdlSeed.Account(path, account)
}

private fun AnchorIdl.TypeDef.toModel() = IdlTypeDef(
    name = name,
    body = when (val ty = type) {
        is AnchorIdl.TypeDefTy.Struct -> IdlTypeBody.Struct(ty.fields.toModel())
        is AnchorIdl.TypeDefTy.Enum -> IdlTypeBody.Enum(ty.variants.map { IdlEnumVariant(it.name, it.fields.toModel()) })
        is AnchorIdl.TypeDefTy.Alias -> IdlTypeBody.Alias(ty.alias.toModel())
    },
    serialization = when (val s = serialization) {
        AnchorIdl.Serialization.Builtin.BORSH -> IdlSerialization.Borsh
        AnchorIdl.Serialization.Builtin.BYTEMUCK -> IdlSerialization.Bytemuck
        AnchorIdl.Serialization.Builtin.BYTEMUCK_UNSAFE -> IdlSerialization.BytemuckUnsafe
        is AnchorIdl.Serialization.Custom -> IdlSerialization.Custom(s.custom)
    },
    repr = when (val r = repr) {
        null -> null
        is AnchorIdl.Repr.Rust -> IdlRepr.Rust(r.packed, r.align)
        is AnchorIdl.Repr.C -> IdlRepr.C(r.packed, r.align)
        AnchorIdl.Repr.Transparent -> IdlRepr.Transparent
    },
    generics = generics.map {
        when (it) {
            is AnchorIdl.GenericParam.Type -> IdlGenericParam.Type(it.name)
            is AnchorIdl.GenericParam.Const -> IdlGenericParam.Const(it.name, it.type)
        }
    },
    docs = docs,
)

private fun AnchorIdl.Fields?.toModel(): IdlFields = when (this) {
    null -> IdlFields.None
    is AnchorIdl.Fields.Named -> IdlFields.Named(fields.map { it.toModel() })
    is AnchorIdl.Fields.Tuple -> IdlFields.Tuple(types.map { it.toModel() })
}

private fun AnchorIdl.Field.toModel() = IdlField(name, type.toModel(), docs)

private fun AnchorIdl.Type.toModel(): IdlType = when (this) {
    is AnchorIdl.Type.Primitive -> IdlType.Primitive.valueOf(name)
    is AnchorIdl.Type.Option -> IdlType.Option(option.toModel())
    is AnchorIdl.Type.Vec -> IdlType.Vec(vec.toModel())
    is AnchorIdl.Type.Array -> IdlType.Array(
        array.type.toModel(),
        array.generic?.let(IdlArrayLength::Generic) ?: IdlArrayLength.Fixed(array.length!!),
    )
    is AnchorIdl.Type.Defined -> IdlType.Defined(
        defined.name,
        defined.generics.map {
            when (it) {
                is AnchorIdl.GenericArg.Type -> IdlGenericArg.Type(it.type.toModel())
                is AnchorIdl.GenericArg.Const -> IdlGenericArg.Const(it.value)
            }
        },
    )
    is AnchorIdl.Type.Generic -> IdlType.Generic(generic)
}

/**
 * Anchor writes a const parameter passed on to another generic type as a type argument naming it,
 * `{"kind": "type", "type": {"generic": "N"}}`. Rewrite each such argument whose target parameter is
 * a const into [IdlGenericArg.ConstParam].
 */
private class ConstForwarding(val types: List<IdlTypeDef>) {
    private val params = types.associate { it.name to it.generics }

    fun typeDef(def: IdlTypeDef): IdlTypeDef = def.copy(
        body = when (val body = def.body) {
            is IdlTypeBody.Struct -> IdlTypeBody.Struct(fields(body.fields))
            is IdlTypeBody.Enum -> IdlTypeBody.Enum(body.variants.map { it.copy(fields = fields(it.fields)) })
            is IdlTypeBody.Alias -> IdlTypeBody.Alias(type(body.type))
        },
    )

    fun field(field: IdlField): IdlField = field.copy(type = type(field.type))

    private fun fields(fields: IdlFields): IdlFields = when (fields) {
        IdlFields.None -> fields
        is IdlFields.Named -> IdlFields.Named(fields.fields.map(::field))
        is IdlFields.Tuple -> IdlFields.Tuple(fields.types.map(::type))
    }

    fun type(type: IdlType): IdlType = when (type) {
        is IdlType.Primitive, is IdlType.Generic -> type
        is IdlType.Option -> IdlType.Option(type(type.inner))
        is IdlType.Vec -> IdlType.Vec(type(type.element))
        is IdlType.Array -> IdlType.Array(type(type.element), type.length)
        is IdlType.Defined -> {
            val targetParams = params[type.name]
            IdlType.Defined(
                type.name,
                type.generics.mapIndexed { i, arg ->
                    val generic = (arg as? IdlGenericArg.Type)?.type as? IdlType.Generic
                    when {
                        generic != null && targetParams?.getOrNull(i) is IdlGenericParam.Const -> IdlGenericArg.ConstParam(generic.name)
                        arg is IdlGenericArg.Type -> IdlGenericArg.Type(type(arg.type))
                        else -> arg
                    }
                },
            )
        }
    }
}
