package io.ethers.abigen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName

object CodeFactory {
    fun createClass(
        name: ClassName,
        fields: List<AbiTypeParameter>,
        vararg modifiers: KModifier,
        reservedFieldNames: Set<String> = emptySet(),
        decorator: (builder: TypeSpec.Builder, constructor: FunSpec.Builder) -> Unit = { _, _ -> },
    ): TypeSpec {
        val builder = TypeSpec.classBuilder(name).addModifiers(modifiers.toList())
        val constructor = FunSpec.constructorBuilder()
        fields.forEach {
            val param = it.toParameterSpec(constructor, reservedFieldNames)
            addConstructorValParameter(builder, constructor, param)
        }

        decorator(builder, constructor)
        addEqualsAndHashCode(name, builder, constructor.parameters)

        return builder.primaryConstructor(constructor.build()).build()
    }

    // add constructor "val" arg
    // based on: https://square.github.io/kotlinpoet/#example
    fun addConstructorValParameter(
        clazz: TypeSpec.Builder,
        constructor: FunSpec.Builder,
        param: ParameterSpec,
        vararg modifiers: KModifier,
    ) {
        constructor.addParameter(param)
        clazz.addProperty(
            PropertySpec.builder(param.name, param.type)
                .addModifiers(modifiers.toList())
                .initializer(param.name)
                .build(),
        )
    }

    private fun addEqualsAndHashCode(
        name: ClassName,
        clazz: TypeSpec.Builder,
        fields: List<ParameterSpec>,
    ) {
        val equals = FunSpec.builder("equals")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("other", Any::class.asClassName().copy(nullable = true))
            .returns(Boolean::class)
            .addStatement("if (this === other) return·true")
            .addStatement("if (javaClass != other?.javaClass) return·false")

        val hashCode = FunSpec.builder("hashCode")
            .addModifiers(KModifier.OVERRIDE)
            .returns(Int::class)

        if (fields.isEmpty()) {
            equals.addStatement("return·true")
            hashCode.addStatement("return·javaClass.hashCode()")
            clazz.addFunction(equals.build())
            clazz.addFunction(hashCode.build())
            return
        }

        // format based on intellij auto-generated equals/hashCode.
        // handles cases for array fields by using contentEquals/contentHashCode.
        // additionally, for hashCode, we initialize the result with the first field's hashCode.
        equals.addStatement("other as %T", name)
        fields.forEachIndexed { index, it ->
            if (it.type.toString().startsWith("kotlin.Array")) {
                equals.addStatement("if (!%L.contentEquals(other.%L)) return·false", it.name, it.name)
                if (index == 0) {
                    hashCode.addStatement("var result = %L.contentHashCode()", it.name)
                } else {
                    hashCode.addStatement("result = 31 * result + %L.contentHashCode()", it.name)
                }
            } else {
                equals.addStatement("if (%L != other.%L) return·false", it.name, it.name)
                if (index == 0) {
                    hashCode.addStatement("var result = %L.hashCode()", it.name)
                } else {
                    hashCode.addStatement("result = 31 * result + %L.hashCode()", it.name)
                }
            }
        }

        equals.addStatement("return·true")
        hashCode.addStatement("return·result")

        clazz.addFunction(equals.build())
        clazz.addFunction(hashCode.build())
    }
}
