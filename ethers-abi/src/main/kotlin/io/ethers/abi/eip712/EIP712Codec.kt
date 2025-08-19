package io.ethers.abi.eip712

import io.ethers.abi.AbiCodec
import io.ethers.abi.AbiType
import io.ethers.abi.ContractStruct
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.crypto.Hashing
import java.math.BigInteger
import java.nio.ByteBuffer

object EIP712Codec {
    /**
     * Converts a [ContractStruct] to an EIP712 message map.
     *
     * This function transforms a ContractStruct's tuple data into a nested Map<String, Any>
     * structure suitable for EIP712 typed data. It maps field names from the struct's ABI definition
     * to their corresponding values, recursively handling nested structs and arrays.
     *
     * @return Map where keys are field names and values are properly converted field values
     */
    fun toMessage(struct: ContractStruct): Map<String, Any> {
        val tuple = struct.tuple
        val abiType = struct.abiType
        val ret = HashMap<String, Any>(tuple.size, 1.0f)
        for (i in 0 until tuple.size) {
            val value = tuple[i]
            val field = abiType.fields[i]
            ret[field.name] = toMessageRecursive(value, field.type)
        }
        return ret
    }

    /**
     * Recursively converts a value based on its ABI type for EIP712 message representation.
     *
     * @param value The value to convert
     * @param abiType The ABI type definition for the value
     * @return Converted value suitable for EIP712 message map
     */
    private fun toMessageRecursive(value: Any, abiType: AbiType<*>): Any {
        return when (abiType) {
            is AbiType.Struct<*> -> (value as ContractStruct).toEIP712Message()
            is AbiType.Array<*> -> (value as List<*>).map { toMessageRecursive(it!!, abiType.type) }
            is AbiType.FixedArray<*> -> (value as List<*>).map { toMessageRecursive(it!!, abiType.type) }
            else -> value
        }
    }

    /**
     * Converts a [ContractStruct] to an EIP712 type definitions map.
     *
     * This function extracts all struct types used by the given ContractStruct and converts them
     * into the EIP712 types format required for typed data signing. It recursively processes
     * nested structs and arrays to build a complete type map.
     *
     * @param struct The ContractStruct to convert
     * @return Map where keys are struct type names and values are lists of field definitions
     */
    fun toTypeMap(struct: ContractStruct): Map<String, List<EIP712Field>> {
        return toTypeMap(struct.abiType)
    }

    /**
     * Recursively builds an EIP712 type definitions map from an ABI type.
     *
     * This function processes ABI types and extracts all struct definitions needed for EIP712
     * typed data signing. It handles arrays, nested structs, and primitive types, building
     * a complete map of type names to their field definitions.
     *
     * @param abi The ABI type to process
     * @param typeMap Mutable map to accumulate type definitions (for recursive calls)
     * @return Complete map of type names to field definitions
     */
    @JvmOverloads
    fun toTypeMap(
        abi: AbiType<*>,
        typeMap: MutableMap<String, List<EIP712Field>> = HashMap(),
    ): Map<String, List<EIP712Field>> {
        return when (abi) {
            is AbiType.Array<*> -> toTypeMap(abi.type, typeMap)
            is AbiType.FixedArray<*> -> toTypeMap(abi.type, typeMap)
            is AbiType.Struct<*> -> {
                typeMap[abi.name] = abi.fields.map { EIP712Field(it.name, it.type.getEIP712TypeDef()) }
                abi.fields.forEach { toTypeMap(it.type, typeMap) }

                typeMap
            }

            else -> typeMap
        }
    }

    /**
     * Generates the EIP712 type hash for a ContractStruct.
     *
     * The type hash is the keccak256 hash of the encoded type string according to EIP712.
     * This hash is used as the first 32 bytes when computing struct hashes.
     *
     * @param struct The ContractStruct to generate type hash for
     * @return 32-byte keccak256 hash of the encoded type string
     */
    fun typeHash(struct: ContractStruct): ByteArray {
        return typeHash(struct.abiType)
    }

    /**
     * Generates the EIP712 type hash for an ABI struct type.
     *
     * The type hash is computed as keccak256(encodeType(primaryType, types)) according to EIP712.
     * This hash serves as the type identifier in struct hash calculations.
     *
     * @param abi The ABI struct type to generate type hash for
     * @return 32-byte keccak256 hash of the encoded type string
     */
    fun typeHash(abi: AbiType.Struct<*>): ByteArray {
        val encodedType = encodeType(abi)
        return Hashing.keccak256(encodedType.toByteArray(Charsets.UTF_8))
    }

    /**
     * Encodes only the root struct type definition without dependent types.
     *
     * This function generates the EIP712 type string for a single struct without including
     * definitions of any dependent struct types. The format is:
     * TypeName(field1Type field1Name,field2Type field2Name,...)
     *
     * @param abi The struct ABI type to encode
     * @return The root type definition string
     */
    fun encodeRootType(abi: AbiType.Struct<*>): String = buildString {
        append(abi.name)
        append('(')

        for (i in 0 until abi.fields.size) {
            if (i > 0) append(',')
            val field = abi.fields[i]
            append(field.type.getEIP712TypeDef()).append(' ').append(field.name)
        }

        append(')')
    }

    /**
     * Encodes the type definitions for EIP712 signing from a ContractStruct.
     *
     * This function generates the complete EIP712 type string including the primary type
     * and all dependent types, sorted alphabetically. The format follows EIP712 specification:
     * PrimaryType(field1Type field1Name,...)DependentType1(...)DependentType2(...)
     *
     * @param struct The ContractStruct to encode
     * @return The complete encoded type string
     */
    fun encodeType(struct: ContractStruct): String {
        return encodeType(struct.abiType)
    }

    /**
     * Encodes the type definitions for EIP712 signing from an ABI struct type.
     *
     * This function generates the complete EIP712 type string according to the specification.
     * The primary type appears first, followed by all dependent types in alphabetical order.
     * This ensures consistent type hash generation across implementations.
     *
     * @param abi The ABI struct type to encode
     * @param builder Optional StringBuilder for efficient string building
     * @return The complete encoded type string
     */
    @JvmOverloads
    fun encodeType(abi: AbiType.Struct<*>, builder: StringBuilder = StringBuilder()): String {
        // ASC-sorted component types
        val sortedComponents = getSortedComponents(abi)

        // remove the root type so it's always first
        sortedComponents.remove(abi.eip712RootType)

        // add the root type first
        builder.append(abi.eip712RootType)
        for (component in sortedComponents) {
            builder.append(component)
        }

        return builder.toString()
    }

    /**
     * Encodes the type definitions for EIP712 signing from EIP712TypedData fields.
     *
     * This function generates the complete EIP712 type string from explicit primary type
     * and types map. The primary type appears first, followed by all dependent types
     * in alphabetical order according to EIP712 specification.
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

        val primaryEip712Type = encodeRootType(primaryType, primaryFields)

        // remove the root type so it's always first
        sortedComponents.remove(primaryEip712Type)

        builder.append(primaryEip712Type)

        // Add all dependent types in sorted order (excluding primary)
        for (component in sortedComponents) {
            builder.append(component)
        }

        return builder.toString()
    }

    /**
     * Hashes a ContractStruct according to EIP712 specification.
     *
     * The struct hash is computed as keccak256(typeHash || encodeData(struct))
     * where typeHash is the hash of the encoded type string and encodeData
     * encodes each field value according to its type.
     *
     * @param struct The ContractStruct to hash
     * @return 32-byte keccak256 hash of the struct
     */
    fun hashStruct(struct: ContractStruct): ByteArray {
        return hashStruct(struct.abiType, struct.tuple)
    }

    /**
     * Hashes a struct using ABI type definition and tuple data.
     *
     * This function implements the EIP712 struct hashing algorithm:
     * hashStruct(s) = keccak256(typeHash || encodeData(s))
     * where typeHash identifies the struct type and encodeData encodes field values.
     *
     * @param abi The ABI struct type definition
     * @param tuple The struct field values in order
     * @return 32-byte keccak256 hash of the struct
     */
    fun hashStruct(abi: AbiType.Struct<*>, tuple: List<Any>): ByteArray {
        // typeHash + encoded data words
        val buff = ByteBuffer.allocate(
            AbiCodec.WORD_SIZE_BYTES * (1 + abi.fields.size),
        )

        // typeHash
        val encodedType = encodeType(abi)
        buff.put(Hashing.keccak256(encodedType.toByteArray(Charsets.UTF_8)))

        // encoded data words
        for (i in 0 until tuple.size) {
            val type = abi.fields[i].type
            val value = tuple[i]

            buff.put(encodeDataWord(value, type))
        }

        return Hashing.keccak256(buff.array())
    }

    /**
     * Hashes a struct using EIP712TypedData.
     *
     * @param typedData The EIP712 typed data containing types, primary type and message
     * @return The keccak256 hash of the struct
     */
    fun hashStruct(typedData: EIP712TypedData): ByteArray {
        return hashStruct(typedData.primaryType, typedData.types, typedData.message)
    }

    /**
     * Hashes a struct using the primary type, types map, and message data.
     *
     * @param primaryType The name of the struct type to hash
     * @param types Map of type definitions
     * @param message The struct data as a map of field names to values
     * @return The keccak256 hash of the struct
     */
    fun hashStruct(
        primaryType: String,
        types: Map<String, List<EIP712Field>>,
        message: Map<String, Any>,
    ): ByteArray {
        // Get fields for the primary type
        val fields = types[primaryType]
            ?: throw IllegalArgumentException("Type '$primaryType' not found in types map")

        // Calculate typeHash
        val encodedType = encodeType(primaryType, types)
        val typeHash = Hashing.keccak256(encodedType.toByteArray(Charsets.UTF_8))

        // Allocate buffer for typeHash + encoded data words
        val buff = ByteBuffer.allocate(
            AbiCodec.WORD_SIZE_BYTES * (1 + fields.size),
        )

        // Add typeHash
        buff.put(typeHash)

        // Encode each field
        for (field in fields) {
            val value = message[field.name]
                ?: throw IllegalArgumentException("Field '${field.name}' not found in message")

            buff.put(encodeDataWord(value, field.type, types))
        }

        return Hashing.keccak256(buff.array())
    }

    /**
     * Encodes a single data field for EIP712 struct hashing using ABI type definition.
     *
     * This function encodes individual struct field values according to EIP712 specification.
     * Primitive types are ABI-encoded directly, while dynamic types (strings, bytes, arrays)
     * and nested structs are keccak256 hashed.
     *
     * @param value The field value to encode
     * @param type The ABI type definition for the field
     * @return 32-byte encoded data word
     * */
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

    /**
     * Encodes a data word from a message value based on its field type string.
     *
     * This function encodes field values for EIP712 struct hashing when working with
     * string-based type definitions (as used in EIP712TypedData). It handles type
     * conversion and validation for all EVM types including primitives, dynamic types,
     * arrays, and nested structs.
     *
     * @param value The field value to encode
     * @param fieldType The EIP712 type string (e.g., "address", "uint256", "string", "bytes32[]")
     * @param types Map of struct type definitions for resolving nested structs
     * @return 32-byte encoded data word
     * @throws IllegalArgumentException for invalid values or unknown types
     */
    private fun encodeDataWord(
        value: Any,
        fieldType: String,
        types: Map<String, List<EIP712Field>>,
    ): ByteArray {
        return when {
            // Primitive types that can be encoded directly
            fieldType == "address" -> {
                val address = when (value) {
                    is Address -> value
                    is String -> Address(value)
                    else -> throw IllegalArgumentException("Invalid address value: $value")
                }
                AbiCodec.encode(AbiType.Address, address)
            }

            fieldType == "bool" -> {
                val bool = value as? Boolean
                    ?: throw IllegalArgumentException("Invalid bool value: $value")
                AbiCodec.encode(AbiType.Bool, bool)
            }

            fieldType.startsWith("uint") -> {
                val bits = if (fieldType == "uint") 256 else fieldType.substring(4).toInt()
                val bigInt = when (value) {
                    is BigInteger -> value
                    is Number -> BigInteger.valueOf(value.toLong())
                    is String -> BigInteger(value)
                    else -> throw IllegalArgumentException("Invalid uint value: $value")
                }
                AbiCodec.encode(AbiType.UInt(bits), bigInt)
            }

            fieldType.startsWith("int") && !fieldType.contains("uint") -> {
                val bits = if (fieldType == "int") 256 else fieldType.substring(3).toInt()
                val bigInt = when (value) {
                    is BigInteger -> value
                    is Number -> BigInteger.valueOf(value.toLong())
                    is String -> BigInteger(value)
                    else -> throw IllegalArgumentException("Invalid int value: $value")
                }
                AbiCodec.encode(AbiType.Int(bits), bigInt)
            }

            fieldType.startsWith("bytes") && fieldType.length > 5 -> {
                // Fixed bytes
                val length = fieldType.substring(5).toInt()
                val bytes = when (value) {
                    is Bytes -> value
                    is ByteArray -> Bytes(value)
                    is String -> Bytes(value)
                    else -> throw IllegalArgumentException("Invalid bytes value: $value")
                }
                AbiCodec.encode(AbiType.FixedBytes(length), bytes)
            }

            // Dynamic types - need to be hashed
            fieldType == "string" -> {
                val str = value as? String
                    ?: throw IllegalArgumentException("Invalid string value: $value")
                Hashing.keccak256(str.toByteArray(Charsets.UTF_8))
            }

            fieldType == "bytes" -> {
                val bytes = when (value) {
                    is Bytes -> value.asByteArray()
                    is ByteArray -> value
                    is String -> Bytes(value).asByteArray()
                    else -> throw IllegalArgumentException("Invalid bytes value: $value")
                }
                Hashing.keccak256(bytes)
            }

            // Array types
            fieldType.endsWith("]") -> {
                val array = value as? List<*>
                    ?: throw IllegalArgumentException("Invalid array value: $value")
                val baseType = fieldType.substringBefore('[')

                // For arrays, hash the concatenated encoded elements
                val buff = ByteBuffer.allocate(array.size * AbiCodec.WORD_SIZE_BYTES)
                for (element in array) {
                    buff.put(encodeDataWord(element!!, baseType, types))
                }
                Hashing.keccak256(buff.array())
            }

            // Custom struct type
            else -> {
                // Must be a struct type
                if (!types.containsKey(fieldType)) {
                    throw IllegalArgumentException("Unknown type: $fieldType")
                }

                val structData = value as? Map<*, *>
                    ?: throw IllegalArgumentException("Invalid struct value for type $fieldType: $value")

                @Suppress("UNCHECKED_CAST")
                hashStruct(fieldType, types, structData as Map<String, Any>)
            }
        }
    }

    /**
     * Recursively collects all component types for EIP712 type encoding from ABI types.
     *
     * This function traverses the type hierarchy to find all struct dependencies,
     * ensuring they are included in the final type encoding. It maintains cycle
     * detection to prevent infinite recursion.
     *
     * @param type The ABI type to process
     * @param components Accumulator for sorted component type strings
     * @param visiting Set to track currently visiting types for cycle detection
     * @return Set of all component type strings in sorted order
     * @throws IllegalStateException if circular references are detected
     */
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

                components.add(type.eip712RootType)

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
     *
     * This function recursively traverses the type definitions map to collect all
     * struct types that are dependencies of the primary type. It builds the complete
     * set of type strings needed for EIP712 type encoding, including cycle detection.
     *
     * @param primaryType The name of the primary struct type
     * @param types Map of type names to their field definitions
     * @param components Accumulator for sorted component type strings
     * @param visiting Set to track currently visiting types for cycle detection
     * @return Set of all component type strings in sorted order
     * @throws IllegalArgumentException if a referenced type is not found
     * @throws IllegalStateException if circular references are detected
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
        components.add(encodeRootType(primaryType, fields))

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
     *
     * This function generates the type definition string for a single struct type
     * following the EIP712 format: TypeName(field1Type field1Name,field2Type field2Name,...)
     * Field types and names are comma-separated within parentheses.
     *
     * @param typeName The name of the struct type
     * @param fields List of field definitions for the struct
     * @return The formatted type string for this struct
     */
    private fun encodeRootType(typeName: String, fields: List<EIP712Field>): String = buildString {
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
     *
     * This function determines whether a given type string represents a primitive
     * EVM type that doesn't require recursive type resolution. Primitive types include
     * address, bool, string, bytes, fixed-size bytes (bytes1-bytes32), and integer
     * types (int8-int256, uint8-uint256).
     *
     * @param type The type string to check
     * @return true if the type is primitive, false if it's a custom struct type
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

    /**
     * Extension function to convert an AbiType to its EIP712 type definition string.
     *
     * This function converts ABI type instances to their corresponding EIP712 type strings
     * used in type definitions. It handles primitive types, arrays, structs, and validates
     * that tuples are not used (as they're not supported by EIP712).
     *
     * @return The EIP712 type definition string for this ABI type
     * @throws IllegalStateException if called on an unsupported Tuple type
     */
    private fun AbiType<*>.getEIP712TypeDef(): String {
        return when (this) {
            AbiType.Address,
            AbiType.Bool,
            AbiType.String,
            AbiType.Bytes,
            is AbiType.FixedBytes,
            is AbiType.Int,
            is AbiType.UInt,
            -> abiType

            is AbiType.Array<*> -> "${type.getEIP712TypeDef()}[]"
            is AbiType.FixedArray<*> -> "${type.getEIP712TypeDef()}[$length]"
            is AbiType.Struct<*> -> name
            is AbiType.Tuple<*> -> throw IllegalStateException("Can't convert $this to EIP712 type definition. Raw Tuples not supported by EIP712")
        }
    }
}
