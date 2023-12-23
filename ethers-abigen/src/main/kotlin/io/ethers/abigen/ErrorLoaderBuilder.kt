package io.ethers.abigen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import java.io.File
import java.util.Collections

/**
 * Builder for a custom error loader class, which is used to force the loading of all classes added
 * via [addContract] function. The generated class has a dummy "load()" function which is called
 * to force-load the loader class, which in turn forces the loading of all added contracts.
 * */
class ErrorLoaderBuilder(
    loaderNamePrefix: String,
    private val destination: File,
) {
    private val contracts = Collections.synchronizedList(ArrayList<ClassName>())
    private val className = ClassName(PACKAGE_NAME, loaderName(loaderNamePrefix))

    val canonicalName: String
        get() = className.canonicalName

    fun addContract(contractCanonicalName: String) {
        contracts.add(ClassName.bestGuess(contractCanonicalName))
    }

    fun build() {
        val fileBuilder = FileSpec.builder(className)
            .indent("    ") // 1 tab / 4 spaces

        val loaderBuilder = TypeSpec.objectBuilder(className)

        val companions = contracts.map { it.nestedClass("Companion") }
        loaderBuilder.addProperty(
            PropertySpec
                .builder("contracts", List::class.parameterizedBy(Any::class))
                .addModifiers(KModifier.PRIVATE)
                .initializer(companions.joinToString(prefix = "listOf(", postfix = ")"))
                .build(),
        )

        loaderBuilder.addFunction(
            FunSpec.builder("load")
                .addModifiers(KModifier.PUBLIC)
                .addComment("Dummy function to force the class to be loaded")
                .build(),
        )

        fileBuilder.addType(loaderBuilder.build())
        fileBuilder.build().writeTo(destination)
    }

    companion object {
        private const val PACKAGE_NAME = "io.ethers.abigen.loaders"

        private fun loaderName(prefix: String): String {
            return "${prefix}CustomErrorLoader"
        }
    }
}
