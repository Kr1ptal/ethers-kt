package io.ethers.abigen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import io.ethers.abi.AbiType
import io.ethers.abi.ContractStruct
import io.ethers.abi.StructFactory

private val ABI_TYPE_SIMPLE_NAME = AbiType::class.java.simpleName

sealed interface AbiTypeParameter {
    val name: String
    val apiType: TypeName

    val abiType: AbiType<*>
    val abiTypeInitializer: String
    val indexed: Boolean

    data class Value(
        override val name: String,
        override val abiType: AbiType<*>,
        override val indexed: Boolean,
    ) : AbiTypeParameter {
        override val apiType: TypeName = abiType.classType.kotlin.asClassName()
        override val abiTypeInitializer: String

        init {
            when (abiType) {
                is AbiType.FixedArray<*>, is AbiType.Array<*>, is AbiType.Tuple -> {
                    throw IllegalArgumentException("AbiType.${javaClass.simpleName} is not a value type")
                }

                else -> {}
            }

            val simpleName = abiType::class.java.simpleName
            this.abiTypeInitializer = "$ABI_TYPE_SIMPLE_NAME." + when (abiType) {
                AbiType.Address, AbiType.Bool, AbiType.Bytes, AbiType.String -> simpleName
                is AbiType.FixedBytes -> "$simpleName(${abiType.length})"
                is AbiType.Int -> "$simpleName(${abiType.bitSize})"
                is AbiType.UInt -> "$simpleName(${abiType.bitSize})"
                else -> throw IllegalArgumentException("Unsupported AbiType: $abiType")
            }
        }
    }

    data class Struct(
        override val name: String,
        val className: ClassName,
        val fields: List<AbiTypeParameter>,
        override val indexed: Boolean,
    ) : AbiTypeParameter {
        override val apiType: TypeName = className

        // this can be raw Tuple, the correct one is created when generating the initializer
        override val abiType: AbiType.Tuple<*> = AbiType.Tuple.raw(fields.map { it.abiType })
        override val abiTypeInitializer: String

        init {
            val simpleName = abiType::class.java.simpleName
            val fieldTypes = fields.joinToString(", ") { it.abiTypeInitializer }

            this.abiTypeInitializer =
                "$ABI_TYPE_SIMPLE_NAME.$simpleName.struct(${className.simpleName}::class, $fieldTypes)"
        }

        fun toAbiStructClass(): TypeSpec = CodeFactory.createClass(className, fields, KModifier.DATA) { builder, _ ->
            builder.addSuperinterface(ContractStruct::class)

            val tupleInitializer = StringBuilder().append("arrayOf(")
            val fromTupleReader = StringBuilder().append("return %T(")

            // all parameters are named - struct field cannot have empty name
            fields.forEachIndexed { index, it ->
                tupleInitializer.append("${it.name}, ")
                fromTupleReader.append("data[$index] as %L, ")
            }

            // remove last ", "
            tupleInitializer.delete(tupleInitializer.length - 2, tupleInitializer.length).append(")")
            fromTupleReader.delete(fromTupleReader.length - 2, fromTupleReader.length).append(")")

            val tupleProperty = PropertySpec.builder("tuple", Array::class.parameterizedBy(Any::class))
                .addModifiers(KModifier.OVERRIDE)
                .initializer(tupleInitializer.toString())
                .build()

            val companion = TypeSpec.companionObjectBuilder()
                .addSuperinterface(StructFactory::class.asClassName().parameterizedBy(className))
                .addFunction(
                    FunSpec.builder("fromTuple")
                        .addAnnotation(JvmStatic::class)
                        .addModifiers(KModifier.OVERRIDE)
                        .addParameter("data", Array::class.asClassName().parameterizedBy(WildcardTypeName.producerOf(Any::class)))
                        .returns(className)
                        .addStatement(
                            fromTupleReader.toString(),
                            className,
                            *fields.map { it.apiType }.toTypedArray(),
                        )
                        .build(),
                )
                .build()

            builder.addProperty(tupleProperty)
            builder.addType(companion)
        }
    }

    data class Collection(
        override val name: String,
        override val abiType: AbiType<*>,
        val element: AbiTypeParameter,
        override val indexed: Boolean,
    ) : AbiTypeParameter {
        override val apiType: TypeName = Array::class.asClassName().parameterizedBy(element.apiType)
        override val abiTypeInitializer: String

        init {
            if (abiType !is AbiType.Array<*> && abiType !is AbiType.FixedArray<*>) {
                throw IllegalArgumentException("AbiType.${javaClass.simpleName} is not a collection type")
            }

            val simpleName = abiType::class.java.simpleName
            this.abiTypeInitializer = "$ABI_TYPE_SIMPLE_NAME." + when (abiType) {
                is AbiType.Array<*> -> "$simpleName(${element.abiTypeInitializer})"
                is AbiType.FixedArray<*> -> "$simpleName(${abiType.length}, ${element.abiTypeInitializer})"
                else -> throw IllegalArgumentException("Unsupported AbiType: $abiType")
            }
        }
    }

    fun toParameterSpec(builder: FunSpec.Builder, reservedNames: Set<String> = emptySet()): ParameterSpec {
        var paramName = name
        if (paramName.isBlank()) {
            paramName = "arg${builder.parameters.size}"
        }
        if (reservedNames.contains(paramName)) {
            paramName = "_$paramName"
        }
        return ParameterSpec.builder(paramName, apiType).build()
    }

    companion object {
        fun fromAbiType(abiType: AbiType<*>, name: String, indexed: Boolean): AbiTypeParameter {
            return when (abiType) {
                is AbiType.Tuple<*> -> throw IllegalArgumentException("Tuple must be handled manually because we need its generated class name")
                is AbiType.Array<*> -> Collection(
                    name,
                    abiType,
                    fromAbiType(abiType.type, name, false),
                    indexed,
                )

                is AbiType.FixedArray<*> -> Collection(
                    name,
                    abiType,
                    fromAbiType(abiType.type, name, false),
                    indexed,
                )

                else -> Value(name, abiType, indexed)
            }
        }
    }
}
