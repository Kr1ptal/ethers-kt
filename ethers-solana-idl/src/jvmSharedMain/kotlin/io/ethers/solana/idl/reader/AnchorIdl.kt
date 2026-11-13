@file:OptIn(ExperimentalSerializationApi::class)

package io.ethers.solana.idl.reader

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int

// Anchor IDL spec 0.1.0, mirroring https://github.com/coral-xyz/anchor/blob/master/idl/spec/src/lib.rs

@Serializable
internal class AnchorIdl(
    val address: String,
    val metadata: Metadata,
    val docs: List<String> = emptyList(),
    val instructions: List<Instruction>,
    val accounts: List<Discriminated> = emptyList(),
    val events: List<Discriminated> = emptyList(),
    val errors: List<ErrorCode> = emptyList(),
    val types: List<TypeDef> = emptyList(),
    val constants: List<Const> = emptyList(),
) {
    @Serializable
    class Metadata(val name: String, val version: String, val spec: String)

    @Serializable
    class Instruction(
        val name: String,
        val docs: List<String> = emptyList(),
        val discriminator: List<Int>,
        val accounts: List<AccountItem>,
        val args: List<Field>,
        val returns: Type? = null,
    )

    /** A group has a nested `accounts` list; anything else is a single account. */
    @Serializable(with = AccountItemSerializer::class)
    sealed interface AccountItem {
        val name: String
    }

    @Serializable
    class Account(
        override val name: String,
        val docs: List<String> = emptyList(),
        val writable: Boolean = false,
        val signer: Boolean = false,
        val optional: Boolean = false,
        val address: String? = null,
        val pda: Pda? = null,
        val relations: List<String> = emptyList(),
    ) : AccountItem

    @Serializable
    class Accounts(override val name: String, val accounts: List<AccountItem>) : AccountItem

    @Serializable
    class Pda(val seeds: List<Seed>, val program: Seed? = null)

    @Serializable
    @JsonClassDiscriminator("kind")
    sealed interface Seed {
        @Serializable
        @SerialName("const")
        class Const(val value: List<Int>) : Seed

        @Serializable
        @SerialName("arg")
        class Arg(val path: String) : Seed

        @Serializable
        @SerialName("account")
        class Account(val path: String, val account: String? = null) : Seed
    }

    /** An account or event: its layout is the type definition with the same name. */
    @Serializable
    class Discriminated(val name: String, val discriminator: List<Int>)

    @Serializable
    class ErrorCode(val code: Long, val name: String, val msg: String? = null)

    @Serializable
    class Const(val name: String, val docs: List<String> = emptyList(), val type: Type, val value: String)

    @Serializable
    class Field(val name: String, val docs: List<String> = emptyList(), val type: Type)

    @Serializable
    class TypeDef(
        val name: String,
        val docs: List<String> = emptyList(),
        val serialization: Serialization = Serialization.Builtin.BORSH,
        val repr: Repr? = null,
        val generics: List<GenericParam> = emptyList(),
        val type: TypeDefTy,
    )

    /** A built-in name such as `"bytemuck"`, or `{"custom": "..."}`. */
    @Serializable(with = SerializationSerializer::class)
    sealed interface Serialization {
        @Serializable
        enum class Builtin : Serialization {
            @SerialName("borsh")
            BORSH,

            @SerialName("bytemuck")
            BYTEMUCK,

            @SerialName("bytemuckunsafe")
            BYTEMUCK_UNSAFE,
        }

        @Serializable
        class Custom(val custom: String) : Serialization
    }

    @Serializable
    @JsonClassDiscriminator("kind")
    sealed interface Repr {
        @Serializable
        @SerialName("rust")
        class Rust(val packed: Boolean = false, val align: Int? = null) : Repr

        @Serializable
        @SerialName("c")
        class C(val packed: Boolean = false, val align: Int? = null) : Repr

        @Serializable
        @SerialName("transparent")
        data object Transparent : Repr
    }

    @Serializable
    @JsonClassDiscriminator("kind")
    sealed interface GenericParam {
        @Serializable
        @SerialName("type")
        class Type(val name: String) : GenericParam

        @Serializable
        @SerialName("const")
        class Const(val name: String, val type: String) : GenericParam
    }

    @Serializable
    @JsonClassDiscriminator("kind")
    sealed interface TypeDefTy {
        @Serializable
        @SerialName("struct")
        class Struct(val fields: Fields? = null) : TypeDefTy

        @Serializable
        @SerialName("enum")
        class Enum(val variants: List<Variant>) : TypeDefTy

        @Serializable
        @SerialName("type")
        class Alias(val alias: Type) : TypeDefTy
    }

    @Serializable
    class Variant(val name: String, val fields: Fields? = null)

    /** Named fields are objects with a `name`; anything else is a tuple of types. */
    @Serializable(with = FieldsSerializer::class)
    sealed interface Fields {
        class Named(val fields: List<Field>) : Fields

        class Tuple(val types: List<Type>) : Fields
    }

    /** A primitive name such as `"u64"`, or an object with a single key naming the compound type. */
    @Serializable(with = TypeSerializer::class)
    sealed interface Type {
        @Serializable
        enum class Primitive : Type {
            @SerialName("bool")
            BOOL,

            @SerialName("u8")
            U8,

            @SerialName("i8")
            I8,

            @SerialName("u16")
            U16,

            @SerialName("i16")
            I16,

            @SerialName("u32")
            U32,

            @SerialName("i32")
            I32,

            @SerialName("f32")
            F32,

            @SerialName("u64")
            U64,

            @SerialName("i64")
            I64,

            @SerialName("f64")
            F64,

            @SerialName("u128")
            U128,

            @SerialName("i128")
            I128,

            @SerialName("u256")
            U256,

            @SerialName("i256")
            I256,

            @SerialName("bytes")
            BYTES,

            @SerialName("string")
            STRING,

            @SerialName("pubkey")
            PUBKEY,
        }

        @Serializable
        class Option(val option: Type) : Type

        @Serializable
        class Vec(val vec: Type) : Type

        @Serializable
        class Array(val array: ArrayTuple) : Type

        @Serializable
        class Defined(val defined: DefinedRef) : Type

        @Serializable
        class Generic(val generic: String) : Type
    }

    /** `[type, length]`, where the length is a number or `{"generic": name}`. */
    @Serializable(with = ArrayTupleSerializer::class)
    class ArrayTuple(val type: Type, val length: Int?, val generic: String?)

    @Serializable
    class DefinedRef(val name: String, val generics: List<GenericArg> = emptyList())

    @Serializable
    @JsonClassDiscriminator("kind")
    sealed interface GenericArg {
        @Serializable
        @SerialName("type")
        class Type(val type: AnchorIdl.Type) : GenericArg

        @Serializable
        @SerialName("const")
        class Const(val value: String) : GenericArg
    }
}

private val COMPOUND_TYPES = mapOf(
    "option" to AnchorIdl.Type.Option.serializer(),
    "vec" to AnchorIdl.Type.Vec.serializer(),
    "array" to AnchorIdl.Type.Array.serializer(),
    "defined" to AnchorIdl.Type.Defined.serializer(),
    "generic" to AnchorIdl.Type.Generic.serializer(),
)

internal object TypeSerializer : JsonContentPolymorphicSerializer<AnchorIdl.Type>(AnchorIdl.Type::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<AnchorIdl.Type> = when (element) {
        is JsonPrimitive -> AnchorIdl.Type.Primitive.serializer()
        is JsonObject -> {
            val key = element.keys.singleOrNull() ?: throw SerializationException("A type object must have exactly one key, got ${element.keys}")
            COMPOUND_TYPES[key] ?: throw SerializationException("Unsupported type '$key'")
        }
        else -> throw SerializationException("Expected a type name or object, got $element")
    }
}

internal object AccountItemSerializer : JsonContentPolymorphicSerializer<AnchorIdl.AccountItem>(AnchorIdl.AccountItem::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<AnchorIdl.AccountItem> {
        return if (element is JsonObject && "accounts" in element) AnchorIdl.Accounts.serializer() else AnchorIdl.Account.serializer()
    }
}

internal object SerializationSerializer : JsonContentPolymorphicSerializer<AnchorIdl.Serialization>(AnchorIdl.Serialization::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<AnchorIdl.Serialization> {
        return if (element is JsonObject) AnchorIdl.Serialization.Custom.serializer() else AnchorIdl.Serialization.Builtin.serializer()
    }
}

/** Decode-only serializer for an untagged shape that no class layout can describe. */
internal abstract class JsonShapeSerializer<T>(name: String) : KSerializer<T> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(name)

    override fun serialize(encoder: Encoder, value: T) = throw SerializationException("${descriptor.serialName} is read-only")

    override fun deserialize(decoder: Decoder): T {
        val json = decoder as? JsonDecoder ?: throw SerializationException("${descriptor.serialName} can only be read from JSON")
        return read(json, json.decodeJsonElement())
    }

    abstract fun read(decoder: JsonDecoder, element: JsonElement): T
}

internal object FieldsSerializer : JsonShapeSerializer<AnchorIdl.Fields>("AnchorIdl.Fields") {
    override fun read(decoder: JsonDecoder, element: JsonElement): AnchorIdl.Fields {
        val array = element as? JsonArray ?: throw SerializationException("Expected an array of fields, got $element")
        val named = array.firstOrNull().let { it is JsonObject && "name" in it }
        return if (named) {
            AnchorIdl.Fields.Named(decoder.json.decodeFromJsonElement(ListSerializer(AnchorIdl.Field.serializer()), array))
        } else {
            AnchorIdl.Fields.Tuple(decoder.json.decodeFromJsonElement(ListSerializer(TypeSerializer), array))
        }
    }
}

internal object ArrayTupleSerializer : JsonShapeSerializer<AnchorIdl.ArrayTuple>("AnchorIdl.ArrayTuple") {
    override fun read(decoder: JsonDecoder, element: JsonElement): AnchorIdl.ArrayTuple {
        val array = element as? JsonArray
        if (array == null || array.size != 2) throw SerializationException("Expected [type, length], got $element")
        val type = decoder.json.decodeFromJsonElement(TypeSerializer, array[0])
        return when (val length = array[1]) {
            is JsonPrimitive -> AnchorIdl.ArrayTuple(type, length.int, null)
            is JsonObject -> {
                val generic = (length["generic"] as? JsonPrimitive)?.takeIf { it.isString && length.size == 1 }
                    ?: throw SerializationException("Expected an array length or {\"generic\": name}, got $length")
                AnchorIdl.ArrayTuple(type, null, generic.content)
            }
            else -> throw SerializationException("Expected an array length, got $length")
        }
    }
}
