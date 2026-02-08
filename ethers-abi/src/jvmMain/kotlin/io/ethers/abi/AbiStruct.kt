package io.ethers.abi

import io.ethers.abi.eip712.EIP712Codec
import io.ethers.abi.eip712.EIP712Domain
import io.ethers.abi.eip712.EIP712Field
import io.ethers.abi.eip712.EIP712TypedData

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
     * See [EIP712Codec.toMessage] for details.
     */
    fun toEIP712Message(): Map<String, Any> {
        return EIP712Codec.toMessage(this)
    }

    /**
     * Converts a [ContractStruct] to an EIP712 type definitions map.
     *
     * See [EIP712Codec.toTypeMap] for details.
     */
    fun toEIP712TypeMap(): Map<String, List<EIP712Field>> {
        return EIP712Codec.toTypeMap(this)
    }

    /**
     * Converts a [ContractStruct] to an [EIP712TypedData].
     *
     * @param domain the [EIP712Domain]
     *
     * See [EIP712TypedData.from] for details.
     */
    fun toEIP712TypedData(domain: EIP712Domain): EIP712TypedData {
        return EIP712TypedData.from(this, domain)
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
