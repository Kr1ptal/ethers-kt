package io.ethers.abi

import kotlin.reflect.KClass

/**
 * A contract-defined struct. Supports converting its fields to a tuple, and creating a struct from a tuple
 * via [StructFactory].
 *
 * This interface represents smart contract struct types that can be encoded/decoded to/from ABI tuples.
 * Implementations are typically generated automatically by the ethers-abigen module.
 *
 * @see StructFactory for creating instances from tuple data
 */
interface ContractStruct {
    /**
     * Converts this struct to a tuple representation for ABI encoding.
     *
     * @return ordered list of field values that can be ABI-encoded as a tuple
     */
    val tuple: List<Any>
}

/**
 * Factory interface for creating [ContractStruct] instances from tuple data.
 *
 * This interface is typically implemented by companion objects of generated struct classes
 * to provide both ABI type information and deserialization capabilities. It serves as the
 * single source of truth for a struct's ABI definition and construction logic.
 *
 * @param T the specific [ContractStruct] type this factory creates
 * @see ContractStruct for the struct interface
 */
interface StructFactory<T : ContractStruct> {
    /**
     * The ABI definition for this struct.
     *
     * This property provides the complete ABI tuple specification for the struct,
     * including field types and nested struct definitions. It serves as the canonical
     * reference for ABI encoding/decoding operations and is used throughout the
     * codebase to avoid duplicate ABI definitions.
     *
     * @return ABI tuple type parameterized with the struct type
     */
    val abi: AbiStruct<T>

    /**
     * Creates a struct instance from tuple data.
     *
     * Deserializes ABI-decoded tuple data into a typed struct instance. The data
     * list must contain values in the same order as defined in the struct's ABI
     * tuple specification.
     *
     * @param data ordered list of decoded values matching the struct's field types
     * @return typed struct instance with populated fields
     * @throws IndexOutOfBoundsException if data list doesn't match expected field count
     * @throws ClassCastException if data types don't match expected field types
     */
    fun fromTuple(data: List<Any>): T
}

data class AbiStruct<T : ContractStruct>(
    val name: String,
    val fields: List<Field>,
    val type: AbiType.Tuple<T>,
) {
    constructor(clazz: KClass<T>, vararg fields: Field) : this(
        name = clazz.simpleName!!,
        fields = fields.toList(),
        type = AbiType.Tuple.struct(clazz, *fields.map { it.type }.toTypedArray()),
    )

    data class Field(val name: String, val type: AbiType<*>)
}
