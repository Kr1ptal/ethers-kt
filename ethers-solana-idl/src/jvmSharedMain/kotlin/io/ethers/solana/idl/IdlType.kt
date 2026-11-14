package io.ethers.solana.idl

/** A type reference in a program schema. */
sealed interface IdlType {
    enum class Primitive : IdlType {
        BOOL,
        U8,
        I8,
        U16,
        I16,
        U32,
        I32,
        F32,
        U64,
        I64,
        F64,
        U128,
        I128,
        U256,
        I256,
        BYTES,
        STRING,
        PUBKEY,
    }

    data class Option(val inner: IdlType) : IdlType

    data class Vec(val element: IdlType) : IdlType

    data class Array(val element: IdlType, val length: IdlArrayLength) : IdlType

    /** A reference to a type definition, with arguments for its generic parameters. */
    data class Defined(val name: String, val generics: List<IdlGenericArg> = emptyList()) : IdlType

    /** A type parameter of the enclosing type definition. */
    data class Generic(val name: String) : IdlType
}

sealed interface IdlArrayLength {
    data class Fixed(val size: Int) : IdlArrayLength {
        init {
            require(size >= 0) { "Array length $size must not be negative" }
        }
    }

    /** A const parameter of the enclosing type definition. */
    data class Generic(val name: String) : IdlArrayLength
}

sealed interface IdlGenericArg {
    data class Type(val type: IdlType) : IdlGenericArg

    /** A const argument, kept as the IDL's decimal text. */
    data class Const(val value: String) : IdlGenericArg {
        init {
            require(value.toULongOrNull() != null) { "Const generic argument '$value' is not a non-negative integer" }
        }
    }

    /** A const parameter of the enclosing type definition, passed on as a const argument. */
    data class ConstParam(val name: String) : IdlGenericArg
}

sealed interface IdlGenericParam {
    val name: String

    data class Type(override val name: String) : IdlGenericParam

    /** A const parameter; [type] is the Rust type name, such as `usize`. */
    data class Const(override val name: String, val type: String) : IdlGenericParam
}
