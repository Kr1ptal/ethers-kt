package io.ethers.abi.eip712

import io.ethers.abi.AbiCodec
import io.ethers.abi.AbiType
import io.ethers.abi.ContractStruct
import io.ethers.core.types.Bytes
import io.ethers.crypto.Hashing
import java.nio.ByteBuffer

object EIP712Codec {
    fun encodeType(struct: ContractStruct): String {
        return encodeType(struct.abiType)
    }

    @JvmOverloads
    fun encodeType(abi: AbiType.Struct<*>, builder: StringBuilder = StringBuilder()): String {
        // ASC-sorted component types
        val sortedComponents = getSortedComponents(abi)

        // remove the root type so it's always first
        sortedComponents.remove(abi.eip712Type)

        // add the root type first
        builder.append(abi.eip712Type)
        for (component in sortedComponents) {
            builder.append(component)
        }

        return builder.toString()
    }

    /**
     * Encodes the type definitions for EIP712 signing from EIP712TypedData.
     *
     * @param typedData The EIP712 typed data containing types and primary type
     * @return The encoded type string with primary type first, followed by sorted dependencies
     */
    fun encodeType(typedData: EIP712TypedData): String {
        return encodeType(typedData.primaryType, typedData.types)
    }

    /**
     * Encodes the type definitions for EIP712 signing from EIP712TypedData fields.
     *
     * @param primaryType The name of the primary type
     * @param types Map of type names to their field definitions
     * @param builder Optional StringBuilder to append to
     * @return The encoded type string with primary type first, followed by sorted dependencies
     */
    @JvmOverloads
    fun encodeType(
        primaryType: String,
        types: Map<String, List<EIP712Field>>,
        builder: StringBuilder = StringBuilder(),
    ): String {
        // Get all dependent types in ASC-sorted order
        val sortedComponents = getSortedComponents(primaryType, types)

        // Build the primary type string first
        val primaryFields = types[primaryType]
            ?: throw IllegalArgumentException("Primary type '$primaryType' not found in types map")

        val primaryEip712Type = buildTypeString(primaryType, primaryFields)

        // remove the root type so it's always first
        sortedComponents.remove(primaryEip712Type)

        builder.append(primaryEip712Type)

        // Add all dependent types in sorted order (excluding primary)
        for (component in sortedComponents) {
            builder.append(component)
        }

        return builder.toString()
    }

    fun hashStruct(struct: ContractStruct): ByteArray {
        return hashStruct(struct.abiType, struct.tuple)
    }

    fun hashStruct(abi: AbiType.Struct<*>, tuple: List<Any>): ByteArray {
        // typeHash + encoded data words
        val buff = ByteBuffer.allocate(
            AbiCodec.WORD_SIZE_BYTES * (1 + abi.fields.size),
        )

        // typeHash
        buff.put(abi.eip712TypeHash)

        // encoded data words
        for (i in 0 until tuple.size) {
            val type = abi.fields[i].type
            val value = tuple[i]

            buff.put(encodeDataWord(value, type))
        }

        return Hashing.keccak256(buff.array())
    }

    private fun <T : Any> encodeDataWord(value: T, type: AbiType<out T>): ByteArray {
        val bytes = when (type) {
            AbiType.Address,
            AbiType.Bool,
            is AbiType.Int,
            is AbiType.UInt,
            is AbiType.FixedBytes,
            -> return AbiCodec.encode(type, value)

            is AbiType.Struct<*> -> return hashStruct(value as ContractStruct)
            is AbiType.Tuple<*> -> throw IllegalArgumentException("Raw Tuple type not supported. Use AbiType.Struct")

            AbiType.Bytes -> (value as Bytes).asByteArray()
            AbiType.String -> (value as String).toByteArray(Charsets.UTF_8)

            is AbiType.Array<*> -> {
                val array = value as List<*>
                val buff = ByteBuffer.allocate(array.size * AbiCodec.WORD_SIZE_BYTES)
                for (i in 0 until array.size) {
                    buff.put(encodeDataWord(array[i] as Any, type.type))
                }
                buff.array()
            }

            is AbiType.FixedArray<*> -> {
                val array = value as List<*>
                val buff = ByteBuffer.allocate(array.size * AbiCodec.WORD_SIZE_BYTES)
                for (i in 0 until array.size) {
                    buff.put(encodeDataWord(array[i] as Any, type.type))
                }
                buff.array()
            }
        }

        return Hashing.keccak256(bytes)
    }

    private fun getSortedComponents(
        type: AbiType<*>,
        components: MutableSet<String> = sortedSetOf(),
        visiting: MutableSet<String> = mutableSetOf(),
    ): MutableSet<String> {
        return when (type) {
            is AbiType.Array<*> -> getSortedComponents(type.type, components, visiting)
            is AbiType.FixedArray<*> -> getSortedComponents(type.type, components, visiting)
            is AbiType.Struct<*> -> {
                // Check for cycles
                if (!visiting.add(type.name)) {
                    throw IllegalStateException("Circular references are not allowed: struct '${type.name}' references itself")
                }

                components.add(type.eip712Type)

                for (field in type.fields) {
                    getSortedComponents(field.type, components, visiting)
                }

                // Remove from the visiting set after processing
                visiting.remove(type.name)

                components
            }

            else -> components
        }
    }

    /**
     * Gets all dependent types for a given primary type from the types map.
     * Returns a sorted set of type names including the primary type.
     */
    private fun getSortedComponents(
        primaryType: String,
        types: Map<String, List<EIP712Field>>,
        components: MutableSet<String> = sortedSetOf(),
        visiting: MutableSet<String> = mutableSetOf(),
    ): MutableSet<String> {
        // Check for cycles
        if (!visiting.add(primaryType)) {
            throw IllegalStateException("Circular references are not allowed: struct '$primaryType' references itself")
        }

        // Get fields for this type
        val fields = types[primaryType]
            ?: throw IllegalArgumentException("Type '$primaryType' not found in types map")

        // Add the current type
        components.add(buildTypeString(primaryType, fields))

        // Process each field
        for (field in fields) {
            val fieldType = field.type

            // Check if this is a custom type (struct) or array of a custom type
            when {
                // Array type: Type[] or Type[n]
                fieldType.endsWith(']') -> {
                    val baseType = fieldType.substringBefore('[')

                    // If it's not a primitive type and not in types map, it's an error
                    if (!isPrimitiveType(baseType) && !types.containsKey(baseType)) {
                        throw IllegalArgumentException("Type '$baseType' not found in types map")
                    }

                    if (types.containsKey(baseType)) {
                        getSortedComponents(baseType, types, components, visiting)
                    }
                }

                // Check if this is a known struct type
                !isPrimitiveType(fieldType) -> {
                    if (!types.containsKey(fieldType)) {
                        throw IllegalArgumentException("Type '$fieldType' not found in types map")
                    }

                    getSortedComponents(fieldType, types, components, visiting)
                }
            }
        }

        // Remove from visiting set after processing
        visiting.remove(primaryType)

        return components
    }

    /**
     * Builds the EIP712 type string for a single struct type.
     * Format: TypeName(field1Type field1Name,field2Type field2Name,...)
     */
    private fun buildTypeString(typeName: String, fields: List<EIP712Field>): String = buildString {
        append(typeName)
        append('(')

        for (i in fields.indices) {
            if (i > 0) append(',')
            val field = fields[i]
            append(field.type).append(' ').append(field.name)
        }

        append(')')
    }

    /**
     * Checks if a type name represents a primitive EVM type.
     */
    private fun isPrimitiveType(type: String): Boolean {
        return when {
            type == "address" -> true
            type == "bool" -> true
            type == "string" -> true
            type == "bytes" -> true

            // bytes1, bytes2, ... bytes32
            type.startsWith("bytes") && type.length > 5 -> {
                val suffix = type.substring(5)
                val length = suffix.toIntOrNull()
                length != null && length in 1..32
            }

            // uint, uint8, uint16, ... uint256
            type.contains("uint") -> {
                val suffix = type.substring(4)
                val bits = if (suffix.isEmpty()) 256 else suffix.toIntOrNull()
                bits != null && bits in 8..256 && bits % 8 == 0
            }

            // int, int8, int16, ... int256
            type.startsWith("int") -> {
                val suffix = type.substring(3)
                val bits = if (suffix.isEmpty()) 256 else suffix.toIntOrNull()
                bits != null && bits in 8..256 && bits % 8 == 0
            }

            else -> false
        }
    }
}
