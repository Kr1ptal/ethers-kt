package io.ethers.abi

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

    /**
     * The [AbiType.Struct] definition of this [ContractStruct]. It is recommended that this
     * property delegates to [StructFactory].abi property.
     *
     * @return the [AbiType.Struct] definition of this struct.
     * */
    val abiType: AbiType.Struct<*>

    /**
     * Converts this struct to an EIP712 message map.
     *
     * This function transforms a ContractStruct's tuple data into a nested Map<String, Any>
     * structure suitable for EIP712 typed data. It maps field names from the struct's ABI definition
     * to their corresponding values, recursively handling nested structs and arrays.
     *
     * @return Map where keys are field names and values are properly converted field values
     */
    fun toEIP712Message(): Map<String, Any> {
        val ret = HashMap<String, Any>(tuple.size, 1.0f)
        for (i in 0 until tuple.size) {
            val value = tuple[i]
            val field = abiType.fields[i]
            ret[field.name] = toEIP712Message(value, field.type)
        }
        return ret
    }
}

/**
 * Recursively converts a value based on its ABI type for EIP712 message representation.
 *
 * @param value The value to convert
 * @param abiType The ABI type definition for the value
 * @return Converted value suitable for EIP712 message map
 */
private fun toEIP712Message(value: Any, abiType: AbiType<*>): Any {
    return when (abiType) {
        is AbiType.Struct<*> -> (value as ContractStruct).toEIP712Message()
        is AbiType.Array<*> -> (value as List<*>).map { toEIP712Message(it!!, abiType.type) }
        is AbiType.FixedArray<*> -> (value as List<*>).map { toEIP712Message(it!!, abiType.type) }
        else -> value
    }
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
     * @return [AbiType.Struct] parametrized by ContractStruct [T]
     */
    val abi: AbiType.Struct<T>

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
