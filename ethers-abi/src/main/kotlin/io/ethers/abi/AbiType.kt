package io.ethers.abi

import io.ethers.abi.AbiType.Array
import io.ethers.abi.AbiType.Companion.PRIMITIVE_TYPES
import io.ethers.abi.AbiType.Companion.canonicalSignature
import io.ethers.abi.AbiType.FixedArray
import io.ethers.abi.AbiType.Tuple
import io.ethers.abi.AbiType.Tuple.Companion.invoke
import io.ethers.abi.eip712.EIP712Codec
import io.ethers.abi.StructFactory
import io.ethers.crypto.Hashing
import java.lang.reflect.Modifier
import java.math.BigInteger
import java.util.function.Function
import kotlin.reflect.KClass

/**
 * Abi type definitions.
 *
 * See: [docs](https://docs.soliditylang.org/en/latest/abi-spec.html#types)
 * */
sealed interface AbiType<T : Any> {
    /**
     * Actual abi type name.
     * */
    val abiType: kotlin.String

    /**
     * Java class type of this abi type.
     * */
    val classType: Class<T>

    /**
     * Whether this type is dynamic or has a fixed size when encoded.
     * */
    val isDynamic: Boolean

    data object Address : AbiType<io.ethers.core.types.Address> {
        override val abiType: kotlin.String = "address"
        override val classType = io.ethers.core.types.Address::class.java
        override val isDynamic: Boolean = false
    }

    data class FixedBytes(val length: kotlin.Int) : AbiType<io.ethers.core.types.Bytes> {
        override val abiType: kotlin.String = "bytes$length"
        override val classType = io.ethers.core.types.Bytes::class.java
        override val isDynamic: Boolean = false

        init {
            if (length !in 1..32) {
                throw IllegalArgumentException("FixedBytes length must be between 1 and 32, got: $length")
            }
        }
    }

    data object Bytes : AbiType<io.ethers.core.types.Bytes> {
        override val abiType: kotlin.String = "bytes"
        override val classType = io.ethers.core.types.Bytes::class.java
        override val isDynamic: Boolean = true
    }

    data class Int(val bitSize: kotlin.Int) : AbiType<BigInteger> {
        override val abiType: kotlin.String = "int$bitSize"
        override val classType = BigInteger::class.java
        override val isDynamic: Boolean = false

        init {
            if (bitSize % 8 != 0 || bitSize <= 0 || bitSize > 256) {
                throw IllegalArgumentException("Unsupported bit size: $bitSize. Must be a multiple of 8 and between 8 and 256.")
            }
        }
    }

    data class UInt(val bitSize: kotlin.Int) : AbiType<BigInteger> {
        override val abiType: kotlin.String = "uint$bitSize"
        override val classType = BigInteger::class.java
        override val isDynamic: Boolean = false

        init {
            if (bitSize % 8 != 0 || bitSize <= 0 || bitSize > 256) {
                throw IllegalArgumentException("Unsupported bit size: $bitSize. Must be a multiple of 8 and between 8 and 256.")
            }
        }
    }

    data object Bool : AbiType<Boolean> {
        override val abiType: kotlin.String = "bool"
        override val classType = Boolean::class.java
        override val isDynamic: Boolean = false
    }

    data object String : AbiType<kotlin.String> {
        override val abiType: kotlin.String = "string"
        override val classType = kotlin.String::class.java
        override val isDynamic: Boolean = true
    }

    data class FixedArray<T : Any>(val length: kotlin.Int, val type: AbiType<T>) : AbiType<List<T>> {
        override val abiType: kotlin.String = "${type.abiType}[$length]"

        @Suppress("UNCHECKED_CAST")
        override val classType = List::class.java as Class<List<T>>
        override val isDynamic: Boolean = type.isDynamic

        companion object {
            @JvmStatic
            @JvmName("ofStruct")
            operator fun <T : ContractStruct> invoke(length: kotlin.Int, factory: StructFactory<T>): FixedArray<T> {
                return FixedArray(length, factory.abi)
            }
        }
    }

    data class Array<T : Any>(val type: AbiType<T>) : AbiType<List<T>> {
        override val abiType: kotlin.String = "${type.abiType}[]"

        @Suppress("UNCHECKED_CAST")
        override val classType = List::class.java as Class<List<T>>
        override val isDynamic: Boolean = true

        companion object {
            @JvmStatic
            @JvmName("ofStruct")
            operator fun <T : ContractStruct> invoke(factory: StructFactory<T>): Array<T> {
                return Array(factory.abi)
            }
        }
    }

    class Struct<T : ContractStruct>(
        classType: Class<T>,
        factory: Function<List<Any>, T>,
        val fields: List<Field>,
    ) : Tuple<T>(classType, factory, fields.map { it.type }), AbiType<T> {
        constructor(classType: Class<T>, factory: Function<List<Any>, T>, vararg fields: Field) : this(
            classType,
            factory,
            fields.toList(),
        )

        constructor(classType: Class<T>, factory: StructFactory<T>, vararg fields: Field) : this(
            classType,
            factory.asTupleFactory(),
            fields.toList(),
        )

        constructor(classType: Class<T>, factory: StructFactory<T>, fields: List<Field>) : this(
            classType,
            factory.asTupleFactory(),
            fields,
        )

        @Deprecated(
            message = "Use the Class-based overload instead.",
            replaceWith = ReplaceWith("AbiType.Struct(classType.java, factory, *fields)"),
        )
        constructor(classType: KClass<T>, factory: Function<List<Any>, T>, vararg fields: Field) : this(
            classType.java,
            factory,
            fields.toList(),
        )

        @Deprecated(
            message = "Use the Class-based overload instead.",
            replaceWith = ReplaceWith("AbiType.Struct(classType.java, structFactory, *fields)"),
        )
        constructor(classType: KClass<T>, factory: StructFactory<T>, vararg fields: Field) : this(
            classType.java,
            factory,
            fields.toList(),
        )

        @Deprecated(
            message = "Use the overload that accepts StructFactory explicitly.",
            replaceWith = ReplaceWith("AbiType.Struct(classType.java, structFactory, *fields)"),
        )
        constructor(classType: KClass<T>, vararg fields: Field) : this(
            classType.java,
            classType.java.getFactoryOrThrow(),
            fields.toList(),
        )

        /**
         * Get the name of the struct.
         * */
        val name: kotlin.String = classType.simpleName

        /**
         * Get the root EIP-712 definition of this struct.
         *
         * Example:
         * ```
         * Mail(Person from,Person to,string contents)
         * ```
         * */
        val eip712RootType: kotlin.String = EIP712Codec.encodeRootType(this)

        data class Field(val name: kotlin.String, val type: AbiType<*>) {
            constructor(name: kotlin.String, factory: StructFactory<*>) : this(name, factory.abi)
        }

        companion object {
            private fun <T : ContractStruct> StructFactory<T>.asTupleFactory(): Function<List<Any>, T> {
                return Function { fromTuple(it) }
            }

            private fun <T : ContractStruct> Class<T>.getFactoryOrThrow(): StructFactory<T> {
                val companionField = try {
                    getDeclaredField("Companion")
                } catch (_: NoSuchFieldException) {
                    throw IllegalArgumentException("Class must have a companion object")
                }

                if (!Modifier.isStatic(companionField.modifiers)) {
                    throw IllegalArgumentException("Companion object must be static")
                }

                companionField.isAccessible = true
                val instance = companionField.get(null)
                    ?: throw IllegalArgumentException("Companion object must implement StructFactory")

                if (instance !is StructFactory<*>) {
                    throw IllegalArgumentException("Companion object must implement StructFactory")
                }

                @Suppress("UNCHECKED_CAST")
                return instance as StructFactory<T>
            }
        }
    }

    open class Tuple<T : Any>(
        override val classType: Class<T>,
        val factory: Function<List<Any>, *>,
        val types: List<AbiType<*>>,
    ) : AbiType<T> {
        override val abiType: kotlin.String = run {
            val builder = StringBuilder("(")
            for (i in types.indices) {
                if (i != 0) {
                    builder.append(",")
                }
                builder.append(types[i].abiType)
            }

            builder.append(")").toString()
        }

        override val isDynamic: Boolean = types.any { it.isDynamic }

        @Suppress("UNCHECKED_CAST")
        fun dataAsTuple(data: Any): List<Any> {
            if (classType == CLASS_TYPE_TUPLE) {
                return data as List<Any>
            }
            return (data as ContractStruct).tuple
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Tuple<*>

            if (classType != other.classType) return false
            if (types != other.types) return false

            return true
        }

        override fun hashCode(): kotlin.Int {
            var result = classType.hashCode()
            result = 31 * result + types.hashCode()
            return result
        }

        override fun toString(): kotlin.String {
            return "${javaClass.simpleName}(classType=$classType, types=$types)"
        }

        companion object {
            private val CLASS_TYPE_TUPLE = emptyList<Any>()::class.java
            private val TUPLE_FACTORY = Function.identity<List<Any>>()

            /**
             * Create a raw [Tuple] type from [types], represented as a list of elements.
             * */
            @JvmStatic
            @JvmName("ofTypes")
            operator fun invoke(vararg types: AbiType<*>): Tuple<out List<Any>> = invoke(types.toList())

            /**
             * Create a raw [Tuple] type from [types], represented as a list of elements.
             * */
            @JvmStatic
            @JvmName("ofTypes")
            operator fun invoke(types: List<AbiType<*>>): Tuple<out List<Any>> {
                return Tuple(CLASS_TYPE_TUPLE, TUPLE_FACTORY, types)
            }
        }
    }

    companion object {
        /**
         * A mapping of string representation to [AbiType] for all valid primitive types.
         *
         * A primitive EVM type is a type that doesn't require recursive type resolution. Primitive types
         * include address, bool, string, bytes, fixed-size bytes (bytes1-bytes32), and integer
         * types (int8-int256, uint8-uint256).
         */
        val PRIMITIVE_TYPES = createPrimitiveTypes()

        private fun createPrimitiveTypes(): Map<kotlin.String, AbiType<*>> {
            val ints = (8..256 step 8).associate { "int$it" to Int(it) }
            val uints = (8..256 step 8).associate { "uint$it" to UInt(it) }
            val fixedBytes = (1..32).associate { "bytes$it" to FixedBytes(it) }

            return ints + uints + fixedBytes + mapOf(
                "address" to Address,
                "bytes" to Bytes,
                "int" to Int(256),
                "uint" to UInt(256),
                "bool" to Bool,
                "string" to String,
            )
        }

        /**
         * Construct [canonicalSignature] from name and types, and compute [Hashing.keccak256] hash of it.
         * */
        @JvmStatic
        fun computeSignatureHash(name: kotlin.String, types: List<AbiType<*>>): ByteArray {
            return Hashing.keccak256(canonicalSignature(name, types).toByteArray(Charsets.UTF_8))
        }

        /**
         * Construct canonical signature from [name] and [types].
         * */
        @JvmStatic
        fun canonicalSignature(name: kotlin.String, types: List<AbiType<*>>): kotlin.String {
            return "$name(${types.joinToString(",") { it.abiType }})"
        }

        /**
         * Parse [signature] into a single [AbiType]. Only raw [Tuple] is supported.
         * */
        @JvmStatic
        fun parseType(signature: kotlin.String): AbiType<*> {
            val types = parseSignature(signature)
            if (types.size != 1) {
                throw IllegalArgumentException("Expected a single type, got: $signature")
            }
            return types.single()
        }

        /**
         * Parse [signature] into a list of [AbiType]. Only raw [Tuple] is supported.
         * */
        @JvmStatic
        fun parseSignature(signature: kotlin.String): List<AbiType<*>> {
            return AbiTypeSignatureParser(signature).parseTypes()
        }
    }
}

private class AbiTypeSignatureParser(private val signature: String) {
    private var pos = 0

    fun parseTypes(): List<AbiType<*>> {
        val result = ArrayList<AbiType<*>>()

        while (pos < signature.length) {
            result.add(parseNextType())

            if (pos < signature.length && signature[pos] == ',') {
                pos++ // Skip comma
            } else {
                break // No more types
            }
        }

        return result
    }

    private fun parseNextType(): AbiType<*> {
        skipWhitespace()

        if (pos >= signature.length) {
            throw IllegalArgumentException("Unexpected end of signature")
        }

        // Parse tuple
        val type = when {
            signature[pos] == '(' -> {
                pos++ // Skip opening parenthesis
                val types = parseTypes()
                if (pos >= signature.length || signature[pos] != ')') {
                    throw IllegalArgumentException("Expected closing parenthesis")
                }

                pos++ // Skip closing parenthesis

                Tuple(types)
            }
            // Parse base type name - only until space, bracket, comma, or closing parenthesis
            else -> {
                val start = pos
                while (pos < signature.length &&
                    signature[pos] != ',' &&
                    signature[pos] != ')' &&
                    signature[pos] != '[' &&
                    !signature[pos].isWhitespace()
                ) {
                    pos++
                }

                if (pos == start) {
                    throw IllegalArgumentException("Expected type name at position $pos in: $signature")
                }

                val typeName = signature.substring(start, pos)
                PRIMITIVE_TYPES[typeName] ?: throw IllegalArgumentException("Invalid token type: $typeName")
            }
        }

        // Check for array suffix
        val typeWithArrays = parseArraySuffix(type)
        skipToNextTypeOrEnd()
        return typeWithArrays
    }

    private fun parseArraySuffix(baseType: AbiType<*>): AbiType<*> {
        skipWhitespace()

        var currentType = baseType
        while (pos < signature.length && signature[pos] == '[') {
            pos++ // Skip opening bracket
            skipWhitespace()

            when {
                // Dynamic array
                pos < signature.length && signature[pos] == ']' -> {
                    pos++ // Skip closing bracket
                    currentType = Array(currentType)
                }
                // Fixed array - parse length
                else -> {
                    val lengthStart = pos
                    while (pos < signature.length && signature[pos] != ']') {
                        if (!signature[pos].isDigit()) {
                            throw IllegalArgumentException("Invalid array length")
                        }
                        pos++
                    }

                    if (pos >= signature.length) {
                        throw IllegalArgumentException("Expected closing bracket")
                    }

                    val length = signature.substring(lengthStart, pos).toInt()
                    pos++ // Skip closing bracket
                    currentType = FixedArray(length, currentType)
                }
            }

            skipWhitespace()
        }

        return currentType
    }

    private fun skipToNextTypeOrEnd() {
        // Skip everything until we reach comma, closing paren, or end of string
        while (pos < signature.length &&
            signature[pos] != ',' &&
            signature[pos] != ')'
        ) {
            pos++
        }
    }

    private fun skipWhitespace() {
        while (pos < signature.length && signature[pos].isWhitespace()) {
            pos++
        }
    }
}
