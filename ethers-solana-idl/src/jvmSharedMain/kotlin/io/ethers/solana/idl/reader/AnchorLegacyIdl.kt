@file:OptIn(ExperimentalSerializationApi::class)

package io.ethers.solana.idl.reader

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int

// Legacy Anchor IDL, before Anchor 0.30, mirroring the legacy model in
// https://github.com/coral-xyz/anchor/blob/master/idl/src/convert.rs

@Serializable
internal class AnchorLegacyIdl(
    val version: String,
    val name: String,
    val docs: List<String> = emptyList(),
    val constants: List<Const> = emptyList(),
    val instructions: List<Instruction>,
    val accounts: List<TypeDef> = emptyList(),
    val types: List<TypeDef> = emptyList(),
    val events: List<Event> = emptyList(),
    val errors: List<ErrorCode> = emptyList(),
    val metadata: JsonObject? = null,
) {
    @Serializable
    class Const(val name: String, val type: Type, val value: String)

    @Serializable
    class Instruction(
        val name: String,
        val docs: List<String> = emptyList(),
        val accounts: List<AccountItem>,
        val args: List<Field>,
        val returns: Type? = null,
    )

    /** A group has a nested `accounts` list; anything else is a single account. */
    @Serializable(with = LegacyAccountItemSerializer::class)
    sealed interface AccountItem {
        val name: String
    }

    @Serializable
    class Account(
        override val name: String,
        val isMut: Boolean,
        val isSigner: Boolean,
        val isOptional: Boolean = false,
        val docs: List<String> = emptyList(),
        val pda: Pda? = null,
        val relations: List<String> = emptyList(),
    ) : AccountItem

    @Serializable
    class Accounts(override val name: String, val accounts: List<AccountItem>) : AccountItem

    @Serializable
    class Pda(val seeds: List<Seed>, val programId: Seed? = null)

    /** Each seed records the type of its value; a constant's value is a JSON value of that type. */
    @Serializable
    @JsonClassDiscriminator("kind")
    sealed interface Seed {
        @Serializable
        @SerialName("const")
        class Const(val type: Type, val value: JsonElement) : Seed

        @Serializable
        @SerialName("arg")
        class Arg(val type: Type, val path: String) : Seed

        @Serializable
        @SerialName("account")
        class Account(val type: Type, val account: String? = null, val path: String) : Seed
    }

    @Serializable
    class Field(val name: String, val docs: List<String> = emptyList(), val type: Type)

    @Serializable
    class Event(val name: String, val fields: List<EventField>)

    @Serializable
    class EventField(val name: String, val type: Type, val index: Boolean = false)

    @Serializable
    class ErrorCode(val code: Long, val name: String, val msg: String? = null)

    @Serializable
    class TypeDef(val name: String, val docs: List<String> = emptyList(), val generics: List<String> = emptyList(), val type: TypeDefTy)

    @Serializable
    @JsonClassDiscriminator("kind")
    sealed interface TypeDefTy {
        @Serializable
        @SerialName("struct")
        class Struct(val fields: List<Field> = emptyList()) : TypeDefTy

        @Serializable
        @SerialName("enum")
        class Enum(val variants: List<Variant>) : TypeDefTy

        @Serializable
        @SerialName("alias")
        class Alias(val value: Type) : TypeDefTy
    }

    @Serializable
    class Variant(val name: String, val fields: Fields? = null)

    /** Named fields are objects with a `name`; anything else is a tuple of types. */
    @Serializable(with = LegacyFieldsSerializer::class)
    sealed interface Fields {
        class Named(val fields: List<Field>) : Fields

        class Tuple(val types: List<Type>) : Fields
    }

    /** A primitive name such as `"publicKey"`, or an object with a single key naming the compound type. */
    @Serializable(with = LegacyTypeSerializer::class)
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

            @SerialName("publicKey")
            PUBLIC_KEY,
        }

        @Serializable
        class Defined(val defined: String) : Type

        @Serializable
        class Option(val option: Type) : Type

        @Serializable
        class Vec(val vec: Type) : Type

        /** `[type, length]`. */
        class Array(val type: Type, val length: Int) : Type

        /** `[type, generic]`, an array whose length is a const generic parameter. */
        class GenericLenArray(val type: Type, val length: String) : Type

        @Serializable
        class Generic(val generic: String) : Type

        @Serializable
        class DefinedWithTypeArgs(val definedWithTypeArgs: Args) : Type {
            @Serializable
            class Args(val name: String, val args: List<TypeArg>)
        }
    }

    /** `{"generic": name}`, `{"value": value}` or `{"type": type}`. */
    @Serializable
    class TypeArg(val generic: String? = null, val value: String? = null, val type: Type? = null)
}

private val LEGACY_COMPOUND_TYPES = mapOf(
    "defined" to AnchorLegacyIdl.Type.Defined.serializer(),
    "option" to AnchorLegacyIdl.Type.Option.serializer(),
    "vec" to AnchorLegacyIdl.Type.Vec.serializer(),
    "generic" to AnchorLegacyIdl.Type.Generic.serializer(),
    "definedWithTypeArgs" to AnchorLegacyIdl.Type.DefinedWithTypeArgs.serializer(),
)

internal object LegacyTypeSerializer : JsonShapeSerializer<AnchorLegacyIdl.Type>("AnchorLegacyIdl.Type") {
    override fun read(decoder: JsonDecoder, element: JsonElement): AnchorLegacyIdl.Type = when (element) {
        is JsonPrimitive -> decoder.json.decodeFromJsonElement(AnchorLegacyIdl.Type.Primitive.serializer(), element)
        is JsonObject -> {
            val key = element.keys.singleOrNull() ?: throw SerializationException("A type object must have exactly one key, got ${element.keys}")
            when (key) {
                "array", "genericLenArray" -> {
                    val array = element.getValue(key) as? JsonArray
                    if (array == null || array.size != 2) throw SerializationException("Expected [type, length], got ${element[key]}")
                    val type = decoder.json.decodeFromJsonElement(LegacyTypeSerializer, array[0])
                    val length = array[1] as? JsonPrimitive ?: throw SerializationException("Expected an array length, got ${array[1]}")
                    if (key == "array") AnchorLegacyIdl.Type.Array(type, length.int) else AnchorLegacyIdl.Type.GenericLenArray(type, length.content)
                }
                else -> decoder.json.decodeFromJsonElement(LEGACY_COMPOUND_TYPES[key] ?: throw SerializationException("Unsupported type '$key'"), element)
            }
        }
        else -> throw SerializationException("Expected a type name or object, got $element")
    }
}

internal object LegacyAccountItemSerializer : JsonContentPolymorphicSerializer<AnchorLegacyIdl.AccountItem>(AnchorLegacyIdl.AccountItem::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<AnchorLegacyIdl.AccountItem> {
        return if (element is JsonObject && "accounts" in element) AnchorLegacyIdl.Accounts.serializer() else AnchorLegacyIdl.Account.serializer()
    }
}

internal object LegacyFieldsSerializer : JsonShapeSerializer<AnchorLegacyIdl.Fields>("AnchorLegacyIdl.Fields") {
    override fun read(decoder: JsonDecoder, element: JsonElement): AnchorLegacyIdl.Fields {
        val array = element as? JsonArray ?: throw SerializationException("Expected an array of fields, got $element")
        val named = array.firstOrNull().let { it is JsonObject && "name" in it }
        return if (named) {
            AnchorLegacyIdl.Fields.Named(decoder.json.decodeFromJsonElement(ListSerializer(AnchorLegacyIdl.Field.serializer()), array))
        } else {
            AnchorLegacyIdl.Fields.Tuple(decoder.json.decodeFromJsonElement(ListSerializer(LegacyTypeSerializer), array))
        }
    }
}
