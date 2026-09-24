package io.ethers.solana.idl

import io.ethers.crypto.Base58

/**
 * A program schema, independent of the format it was read from.
 *
 * Declarations keep the order of their source, so validation and generated output are
 * deterministic. [address] is the base58 deployment address the source declared.
 *
 * Each model type rejects values that are invalid on their own when constructed; [IdlValidator] checks
 * the references between them.
 */
data class IdlProgram(
    val name: String,
    val version: String,
    val address: String,
    val docs: List<String> = emptyList(),
    val instructions: List<IdlInstruction> = emptyList(),
    val accounts: List<IdlAccount> = emptyList(),
    val events: List<IdlEvent> = emptyList(),
    val errors: List<IdlErrorCode> = emptyList(),
    val types: List<IdlTypeDef> = emptyList(),
    val constants: List<IdlConstant> = emptyList(),
) {
    init {
        requireAddress(address)
    }

    /** The type definition named [name], or null. */
    fun type(name: String): IdlTypeDef? = types.firstOrNull { it.name == name }
}

/** Leading bytes that identify an instruction, account or event, as unsigned values. */
data class IdlDiscriminator(val bytes: List<Int>) {
    init {
        require(bytes.isNotEmpty()) { "Discriminator must not be empty" }
        requireBytes(bytes)
    }

    fun toByteArray(): ByteArray = ByteArray(bytes.size) { bytes[it].toByte() }

    /** Whether one of the two discriminators is a prefix of the other, so data cannot tell them apart. */
    fun overlaps(other: IdlDiscriminator): Boolean {
        val shorter = minOf(bytes.size, other.bytes.size)
        return bytes.subList(0, shorter) == other.bytes.subList(0, shorter)
    }

    override fun toString(): String = bytes.toString()
}

data class IdlInstruction(
    val name: String,
    val discriminator: IdlDiscriminator,
    val accounts: List<IdlAccountItem> = emptyList(),
    val args: List<IdlField> = emptyList(),
    val returns: IdlType? = null,
    val docs: List<String> = emptyList(),
)

/** An account role of an instruction: a single account, or a named group of them. */
sealed interface IdlAccountItem {
    val name: String

    data class Single(
        override val name: String,
        val writable: Boolean = false,
        val signer: Boolean = false,
        val optional: Boolean = false,
        /** A fixed base58 address the account must have. */
        val address: String? = null,
        val pda: IdlPda? = null,
        /** Names of accounts in the same instruction whose data refers to this one. */
        val relations: List<String> = emptyList(),
        val docs: List<String> = emptyList(),
    ) : IdlAccountItem {
        init {
            address?.let(::requireAddress)
        }
    }

    data class Group(override val name: String, val accounts: List<IdlAccountItem>) : IdlAccountItem
}

/** Seeds of a program derived account. [program] derives under another program when set. */
data class IdlPda(val seeds: List<IdlSeed>, val program: IdlSeed? = null)

sealed interface IdlSeed {
    data class Const(val bytes: List<Int>) : IdlSeed {
        init {
            requireBytes(bytes)
        }
    }

    /** An instruction argument, or a field path within one. */
    data class Arg(val path: String) : IdlSeed

    /** An instruction account, or a field path within its data, whose type is [account] when set. */
    data class Account(val path: String, val account: String? = null) : IdlSeed
}

/** An account type; its layout is the type definition with the same name. */
data class IdlAccount(val name: String, val discriminator: IdlDiscriminator)

/** An event type; its layout is the type definition with the same name. */
data class IdlEvent(val name: String, val discriminator: IdlDiscriminator)

/** A custom error. [code] is an unsigned 32-bit value. */
data class IdlErrorCode(val code: Long, val name: String, val message: String? = null) {
    init {
        require(code in 0..U32_MAX) { "Error '$name' code $code is outside the u32 range" }
    }
}

data class IdlField(val name: String, val type: IdlType, val docs: List<String> = emptyList())

data class IdlConstant(val name: String, val type: IdlType, val value: String, val docs: List<String> = emptyList())

data class IdlTypeDef(
    val name: String,
    val body: IdlTypeBody,
    val serialization: IdlSerialization = IdlSerialization.Borsh,
    val repr: IdlRepr? = null,
    val generics: List<IdlGenericParam> = emptyList(),
    val docs: List<String> = emptyList(),
)

sealed interface IdlTypeBody {
    data class Struct(val fields: IdlFields) : IdlTypeBody

    data class Enum(val variants: List<IdlEnumVariant>) : IdlTypeBody

    data class Alias(val type: IdlType) : IdlTypeBody
}

data class IdlEnumVariant(val name: String, val fields: IdlFields = IdlFields.None)

sealed interface IdlFields {
    data object None : IdlFields

    data class Named(val fields: List<IdlField>) : IdlFields

    data class Tuple(val types: List<IdlType>) : IdlFields
}

/** How a type definition's data is laid out. Only [Borsh] follows the standard Borsh rules. */
sealed interface IdlSerialization {
    data object Borsh : IdlSerialization

    data object Bytemuck : IdlSerialization

    data object BytemuckUnsafe : IdlSerialization

    data class Custom(val name: String) : IdlSerialization
}

/** The Rust `#[repr]` of a type definition. */
sealed interface IdlRepr {
    data class Rust(val packed: Boolean = false, val align: Int? = null) : IdlRepr

    data class C(val packed: Boolean = false, val align: Int? = null) : IdlRepr

    data object Transparent : IdlRepr
}

private const val U32_MAX = 4294967295L

private fun requireBytes(bytes: List<Int>) {
    for (byte in bytes) require(byte in 0..255) { "Byte $byte is outside 0..255" }
}

private fun requireAddress(address: String) {
    val bytes = try {
        Base58.decode(address)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid base58 address '$address'", e)
    }
    require(bytes.size == 32) { "Address '$address' decodes to ${bytes.size} bytes, expected 32" }
}
