package io.ethers.abi

import io.ethers.crypto.Hashing
import java.math.BigInteger
import java.util.function.Function
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObject

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
            if (length <= 0 || length > 32) {
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

    data class FixedArray<T : Any>(val length: kotlin.Int, val type: AbiType<T>) : AbiType<kotlin.Array<T>> {
        override val abiType: kotlin.String = "${type.abiType}[$length]"

        @Suppress("UNCHECKED_CAST")
        override val classType = ArrayReflect.newInstance(type.classType, 0)::class.java as Class<kotlin.Array<T>>
        override val isDynamic: Boolean = type.isDynamic
    }

    data class Array<T : Any>(val type: AbiType<T>) : AbiType<kotlin.Array<T>> {
        override val abiType: kotlin.String = "${type.abiType}[]"

        @Suppress("UNCHECKED_CAST")
        override val classType = ArrayReflect.newInstance(type.classType, 0)::class.java as Class<kotlin.Array<T>>
        override val isDynamic: Boolean = true
    }

    class Tuple<T : Any> private constructor(
        override val classType: Class<T>,
        val factory: Function<kotlin.Array<out Any>, *>,
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
        fun dataAsTuple(data: Any): kotlin.Array<Any> {
            if (classType == CLASS_TYPE_TUPLE) {
                return data as kotlin.Array<Any>
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
            return "Tuple(classType=$classType, types=$types)"
        }

        companion object {
            /**
             * Create a [Tuple] type from [fieldTypes], represented as an [ContractStruct] instance. This function expects
             * the [classType] to have a companion object that implements [StructFactory], which is used for decoding
             * the [Tuple] into the [classType].
             * */
            @JvmSynthetic
            fun <T : ContractStruct> struct(
                classType: KClass<T>,
                vararg fieldTypes: AbiType<*>,
            ): Tuple<T> {
                val companion = classType.companionObject?.objectInstance
                    ?: throw IllegalArgumentException("Class must have a companion object")

                if (companion !is StructFactory<*>) {
                    throw IllegalArgumentException("Companion object must implement StructFactory")
                }

                @Suppress("UNCHECKED_CAST")
                val factory = companion as StructFactory<T>
                return struct(classType.java, { factory.fromTuple(it) }, fieldTypes.toList())
            }

            /**
             * Create a [Tuple] type from [fieldTypes], represented as an [ContractStruct] instance.
             * */
            @JvmStatic
            fun <T : ContractStruct> struct(
                classType: Class<T>,
                factory: Function<kotlin.Array<out Any>, T>,
                vararg fieldTypes: AbiType<*>,
            ): Tuple<T> {
                return struct(classType, factory, fieldTypes.toList())
            }

            /**
             * Create a [Tuple] type from [fieldTypes], represented as an [ContractStruct] instance.
             * */
            @JvmStatic
            fun <T : ContractStruct> struct(
                classType: Class<T>,
                factory: Function<kotlin.Array<out Any>, T>,
                fieldTypes: List<AbiType<*>>,
            ): Tuple<T> {
                return Tuple(classType, factory, fieldTypes)
            }

            /**
             * Create a raw [Tuple] type from [types], represented as an array of elements.
             * */
            @JvmStatic
            fun raw(vararg types: AbiType<*>): Tuple<out kotlin.Array<*>> = raw(types.toList())

            /**
             * Create a raw [Tuple] type from [types], represented as an array of elements.
             * */
            @JvmStatic
            fun raw(types: List<AbiType<*>>): Tuple<out kotlin.Array<*>> {
                return Tuple(CLASS_TYPE_TUPLE, Function.identity(), types)
            }
        }
    }

    companion object {
        private val CLASS_TYPE_TUPLE = emptyArray<Any>()::class.java

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
            @Suppress("NAME_SHADOWING")
            var signature = signature.replace(" ", "")
            val result = ArrayList<AbiType<*>>()
            var rawType = getNextType(signature)
            while (rawType.isNotBlank()) {
                val type = when (rawType) {
                    "address" -> Address
                    "bytes" -> Bytes
                    "int" -> Int(256)
                    "uint" -> UInt(256)
                    "bool" -> Bool
                    "string" -> String
                    else -> null
                }
                if (type != null) {
                    result.add(type)
                } else if (rawType.startsWith("(") && rawType.endsWith(")")) {
                    // tuple
                    val types = parseSignature(rawType.substring(1, rawType.length - 1))
                    result.add(Tuple.raw(types))
                } else if (rawType.endsWith("[]")) {
                    // dynamic array
                    result.add(Array(parseSignature(rawType.substring(0, rawType.length - 2)).first()))
                } else if (rawType.contains("[")) {
                    // fixed array
                    val index = rawType.lastIndexOf("[")
                    val length = rawType.substring(index + 1, rawType.length - 1).toInt()
                    result.add(FixedArray(length, parseSignature(rawType.substring(0, index)).first()))
                } else if (rawType.startsWith("int")) {
                    val bitSize = rawType.substring(3).toInt()
                    result.add(Int(bitSize))
                } else if (rawType.startsWith("uint")) {
                    val bitSize = rawType.substring(4).toInt()
                    result.add(UInt(bitSize))
                } else if (rawType.startsWith("bytes")) {
                    val length = rawType.substring(5).toInt()
                    result.add(FixedBytes(length))
                } else {
                    throw IllegalArgumentException("Invalid token type: $rawType")
                }

                signature = signature.removePrefix(rawType).removePrefix(",")
                rawType = getNextType(signature)
            }
            return result
        }

        private fun getNextType(signature: kotlin.String): kotlin.String {
            var nestingDepth = 0
            for (i in signature.indices) {
                val c = signature[i]
                if (c == '(') {
                    nestingDepth++
                } else if (c == ')') {
                    nestingDepth--
                } else if (c == ',' && nestingDepth == 0) {
                    return signature.substring(0, i)
                }
            }
            return signature
        }
    }
}
