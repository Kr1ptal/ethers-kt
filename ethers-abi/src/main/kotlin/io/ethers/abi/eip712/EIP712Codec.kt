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

    fun <T : Any> encodeDataWord(value: T, type: AbiType<out T>): ByteArray {
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

                // Remove from visiting set after processing
                visiting.remove(type.name)

                components
            }

            else -> components
        }
    }
}
