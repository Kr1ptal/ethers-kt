package io.ethers.abigen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import io.ethers.abi.AbiConstructor
import io.ethers.abi.AbiContract
import io.ethers.abi.AbiEvent
import io.ethers.abi.AbiFunction
import io.ethers.abi.AbiType
import io.ethers.abi.AnonymousEventFilter
import io.ethers.abi.ContractEvent
import io.ethers.abi.EventFactory
import io.ethers.abi.EventFilter
import io.ethers.abi.call.ConstructorCall
import io.ethers.abi.call.FunctionCall
import io.ethers.abi.call.PayableConstructorCall
import io.ethers.abi.call.PayableFunctionCall
import io.ethers.abi.call.ReadFunctionCall
import io.ethers.abi.call.ReceiveFunctionCall
import io.ethers.abi.error.CustomContractError
import io.ethers.abi.error.CustomErrorFactory
import io.ethers.abi.error.CustomErrorFactoryResolver
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Log
import io.ethers.providers.middleware.Middleware
import java.io.File
import java.math.BigInteger
import java.util.function.Function
import javax.lang.model.SourceVersion

class AbiContractBuilder(
    private val contractName: String,
    private val packageName: String,
    private val destination: File,
    private val artifact: JsonAbi,
    private val functionRenames: Map<String, String>,
) : Runnable {
    private val uniqueClassNames = HashSet<String>()
    private val constants = HashMap<String, PropertySpec>()
    private val structs = HashMap<String, AbiTypeParameter.Struct>()
    private val resultClasses = HashMap<String, TypeSpec>()

    override fun run() {
        val fileBuilder = FileSpec.builder(packageName, contractName)
            .indent("    ") // 1 tab / 4 spaces

        fileBuilder.addImport(AbiType::class.java.packageName, AbiType::class.java.simpleName)

        val contractName = ClassName("", this.contractName)
        val contractBuilder = TypeSpec.classBuilder(contractName)

        val constructor = FunSpec.constructorBuilder()
        val providerArg = ParameterSpec.builder("provider", Middleware::class.asClassName()).build()
        val addressArg = ParameterSpec.builder("address", Address::class.asClassName()).build()

        constructor.addParameter(providerArg)
        constructor.addParameter(addressArg)

        contractBuilder.superclass(AbiContract::class)
        contractBuilder.addSuperclassConstructorParameter("%N, %N", providerArg, addressArg)
        contractBuilder.primaryConstructor(constructor.build())

        val companionInitCode = ArrayList<CodeBlock>()
        val companion = TypeSpec.companionObjectBuilder()

        val errorFactories = ArrayList<String>()
        var errorSuperclass: ClassName? = null
        if (artifact.errors.isNotEmpty()) {
            errorSuperclass = contractName.nestedClass("Error")
            contractBuilder.addType(
                TypeSpec.classBuilder(errorSuperclass)
                    .addModifiers(KModifier.SEALED)
                    .superclass(CustomContractError::class)
                    .build(),
            )
        }

        val eventFactories = ArrayList<String>()
        var eventSuperclass: ClassName? = null
        if (artifact.events.isNotEmpty()) {
            eventSuperclass = contractName.nestedClass("Event")
            contractBuilder.addType(
                TypeSpec.classBuilder(eventSuperclass)
                    .addModifiers(KModifier.SEALED)
                    .addSuperinterface(ContractEvent::class)
                    .build(),
            )
        }

        val haveValidBytecode = !artifact.bytecode.isNullOrBlank() && artifact.bytecode != "0x"

        artifact.receive?.let { contractBuilder.addFunction(createReceiveFunction()) }
        artifact.fallback?.let { contractBuilder.addFunction(createFallbackFunction(it)) }
        artifact.functions.forEach { contractBuilder.addFunction(createFunction(it)) }

        artifact.errors.forEach {
            val err = createCustomErrorClass(it, errorSuperclass!!)
            contractBuilder.addType(err)
            errorFactories.add(err.name!!)
        }

        artifact.events.forEach {
            val event = createEventClass(it, eventSuperclass!!)
            contractBuilder.addType(event)
            eventFactories.add(event.name!!)
        }

        if (haveValidBytecode) {
            val abiTypes = artifact.constructor?.inputs?.toAbiTypeParameters() ?: emptyList()
            val abiProperty = PropertySpec.builder("CONSTRUCTOR_ABI", AbiConstructor::class)
                .addAnnotation(JvmField::class)
                .initializer(
                    "%T(%T(%S), listOf(%L))",
                    AbiConstructor::class,
                    Bytes::class,
                    artifact.bytecode,
                    abiTypes.joinToString(", ") { it.abiTypeInitializer },
                ).build()

            companion.addProperty(abiProperty)

            // if we have bytecode but no explicit constructor, create a no-arg deploy function
            val contractConstructor = artifact.constructor ?: JsonAbiConstructor(
                emptyList(),
                false,
            )
            companion.addFunction(createDeployStaticFunction(contractConstructor, contractName, abiProperty))
        }

        resultClasses.values.forEach { contractBuilder.addType(it) }
        structs.values.forEach { contractBuilder.addType(it.toAbiStructClass()) }

        if (errorSuperclass != null) {
            val errorArrayType = Array::class.asClassName().parameterizedBy(
                CustomErrorFactory::class.asClassName().parameterizedBy(WildcardTypeName.producerOf(errorSuperclass)),
            )

            companion.addProperty(
                PropertySpec.builder("ERRORS", errorArrayType)
                    .addAnnotation(JvmField::class)
                    .initializer("arrayOf(%L)", errorFactories.joinToString(","))
                    .build(),
            )

            companion.addFunction(
                FunSpec.builder("decodeError")
                    .addAnnotation(JvmStatic::class)
                    .addParameter("error", Bytes::class)
                    .returns(errorSuperclass.copy(nullable = true))
                    .addCode(
                        CodeBlock.builder().apply {
                            beginControlFlow("for (err in ERRORS)")
                            addStatement("return err.decode(error) ?: continue")
                            endControlFlow()
                            addStatement("return null")
                        }.build(),
                    )
                    .build(),
            )

            companionInitCode.add(
                CodeBlock.of(
                    "%T.addFactories(ERRORS)",
                    CustomErrorFactoryResolver::class.asClassName(),
                ),
            )
        }

        if (eventSuperclass != null) {
            val eventArrayType = Array::class.asClassName().parameterizedBy(
                EventFactory::class.asClassName().parameterizedBy(WildcardTypeName.producerOf(eventSuperclass)),
            )

            companion.addProperty(
                PropertySpec.builder("EVENTS", eventArrayType)
                    .addAnnotation(JvmField::class)
                    .initializer("arrayOf(%L)", eventFactories.joinToString(","))
                    .build(),
            )

            companion.addFunction(
                FunSpec.builder("decodeEvent")
                    .addAnnotation(JvmStatic::class)
                    .addParameter("log", Log::class)
                    .returns(eventSuperclass.copy(nullable = true))
                    .addCode(
                        CodeBlock.builder().apply {
                            beginControlFlow("for (event in EVENTS)")
                            addStatement("return event.decode(log) ?: continue")
                            endControlFlow()
                            addStatement("return null")
                        }.build(),
                    )
                    .build(),
            )
        }

        constants.values.forEach { companion.addProperty(it) }
        companion.addInitializerBlock(CodeBlock.of(companionInitCode.joinToString("\n")))

        contractBuilder.addType(companion.build())
        fileBuilder.addType(contractBuilder.build())
        fileBuilder.build().writeTo(destination)
    }

    private fun createReceiveFunction(): FunSpec {
        val callClass = ReceiveFunctionCall::class.asClassName()

        val function = FunSpec.builder("receive").apply {
            addParameter("value", BigInteger::class)
            addStatement("return %T(provider, address, value)", callClass)
            returns(callClass)
        }

        return function.build()
    }

    private fun createFallbackFunction(fallback: JsonAbiFallback): FunSpec {
        val callClass = if (fallback.isPayable) {
            PayableFunctionCall::class
        } else {
            FunctionCall::class
        }.asClassName()

        val function = FunSpec.builder("fallback").apply {
            addParameter("data", Bytes::class)
            addStatement("return %T(provider, address, data, %T.identity())", callClass, Function::class)
            returns(callClass.parameterizedBy(Bytes::class.asClassName()))
        }

        return function.build()
    }

    private fun createFunction(function: JsonAbiFunction): FunSpec {
        var generatedFunctionName = functionRenames[function.name] ?: function.name
        if (isReservedJavaName(generatedFunctionName)) {
            generatedFunctionName = "_$generatedFunctionName"
        }

        val inputs = function.inputs.toAbiTypeParameters()
        val outputs = function.outputs.toAbiTypeParameters()
        val abiFunctionProperty = createAbiFunctionProperty(generatedFunctionName, function.name, inputs, outputs)
        val callClass = when {
            function.isPayable -> PayableFunctionCall::class
            function.isReadOnly -> ReadFunctionCall::class
            else -> FunctionCall::class
        }.asClassName()

        val builder = FunSpec.builder(generatedFunctionName)

        // add function args
        inputs.forEach { builder.addParameter(it.toParameterSpec(builder)) }

        // create contract call inputs from args
        val encodeArgs = if (inputs.isEmpty()) {
            CodeBlock.of("emptyArray()")
        } else {
            val argsBuilder = StringBuilder().append("arrayOf(")
            repeat(builder.parameters.size) { argsBuilder.append("%N,") }
            argsBuilder.deleteCharAt(argsBuilder.length - 1).append(")")

            CodeBlock.of(argsBuilder.toString(), *builder.parameters.toTypedArray())
        }

        val body = CodeBlock.builder().beginControlFlow(
            "return %T(provider, address, %N.encodeCall(%L)) {",
            callClass,
            abiFunctionProperty,
            encodeArgs,
        )

        if (outputs.isEmpty()) {
            builder.returns(callClass.parameterizedBy(Unit::class.asClassName()))
        } else if (outputs.size == 1) {
            val retType = outputs.single().apiType
            body.addStatement("val data = %N.decodeResponse(it)", abiFunctionProperty)
            body.addStatement("data[0] as %L", retType)

            builder.returns(callClass.parameterizedBy(retType))
        } else {
            // if we have multiple outputs, we need to create a class to return them
            val resultClassName = ClassName(
                "",
                getNextUniqueName(generatedFunctionName.replaceFirstChar { it.titlecase() } + "Result"),
            )
            resultClasses[resultClassName.simpleName] = CodeFactory.createClass(
                resultClassName,
                outputs,
                KModifier.DATA,
            )

            var index = 0
            val initializer = StringBuilder().append("%T(")
            repeat(outputs.size) { initializer.append("data[${index++}] as %L,") }
            initializer.deleteCharAt(initializer.length - 1).append(")")

            body.addStatement("val data = %N.decodeResponse(it)", abiFunctionProperty)
            body.addStatement(
                initializer.toString(),
                resultClassName,
                *outputs.map { it.apiType }.toTypedArray(),
            )

            builder.returns(callClass.parameterizedBy(resultClassName))
        }

        body.endControlFlow()

        return builder.addCode(body.build()).build()
    }

    private fun createCustomErrorClass(error: JsonAbiError, errorSuperclass: ClassName): TypeSpec {
        val inputs = error.inputs.toAbiTypeParameters()
        val errorClassName = ClassName("", getNextUniqueName(error.name.replaceFirstChar { it.titlecase() }))

        val factoryBuilder = TypeSpec.companionObjectBuilder()
            .addSuperinterface(CustomErrorFactory::class.asClassName().parameterizedBy(errorClassName))
            .addProperty(createAbiErrorProperty(error.name, inputs))

        val decodeFunction = FunSpec.builder("decode")
            .addAnnotation(JvmStatic::class)
            .addModifiers(KModifier.OVERRIDE)
            .addParameter(ParameterSpec.builder("data", Array::class.parameterizedBy(Any::class)).build())
            .returns(errorClassName)

        return CodeFactory.createClass(errorClassName, inputs) { builder, _ ->
            builder.superclass(errorSuperclass)

            // codegen logic:
            // - no inputs: generate a normal class, overriding toString(), and creating an "INSTANCE" property in factory
            // - inputs: generate a data class, with a primary constructor, and a factory method that decodes the inputs
            if (inputs.isEmpty()) {
                builder.addFunction(
                    FunSpec.builder("toString")
                        .addModifiers(KModifier.OVERRIDE)
                        .returns(String::class)
                        .addStatement("return %S", errorClassName.simpleName)
                        .build(),
                )

                factoryBuilder.addProperty(
                    PropertySpec.builder("INSTANCE", errorClassName)
                        .addAnnotation(JvmField::class)
                        .initializer("%T()", errorClassName)
                        .build(),
                )

                decodeFunction.addStatement("return INSTANCE")
            } else {
                builder.addModifiers(KModifier.DATA)

                var index = 0
                val initializer = StringBuilder().append("return %T(")
                inputs.forEach { _ -> initializer.append("data[${index++}] as %L,") }
                initializer.deleteCharAt(initializer.length - 1).append(")")
                decodeFunction.addStatement(
                    initializer.toString(),
                    errorClassName,
                    *inputs.map { it.apiType }.toTypedArray(),
                )
            }

            factoryBuilder.addFunction(decodeFunction.build())
            builder.addType(factoryBuilder.build())
        }
    }

    private fun createEventClass(event: JsonAbiEvent, eventSuperclass: ClassName): TypeSpec {
        val eventClassName = ClassName("", getNextUniqueName(event.name.replaceFirstChar { it.titlecase() }))
        /*if (!eventName.contains("Event")) {
            eventName = "${eventName}Event"
        }*/

        val inputs = event.inputs.toAbiTypeParameters()
        val factoryBuilder = TypeSpec.companionObjectBuilder()
            .addSuperinterface(EventFactory::class.asClassName().parameterizedBy(eventClassName))
            .addProperty(createAbiEventProperty(event.name, inputs, event.anonymous))

        // filter function
        val filterReturnClass = if (event.anonymous) AnonymousEventFilter::class else EventFilter::class
        factoryBuilder.addFunction(
            FunSpec.builder("filter")
                .addAnnotation(JvmStatic::class)
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("provider", Middleware::class)
                .addCode("return %T(provider, this)", filterReturnClass)
                .returns(filterReturnClass.asClassName().parameterizedBy(eventClassName))
                .build(),
        )

        // override decode to make it statically accessible from java
        factoryBuilder.addFunction(
            FunSpec.builder("decode")
                .addAnnotation(JvmStatic::class)
                .addModifiers(KModifier.OVERRIDE)
                .addParameter(ParameterSpec.builder("log", Log::class).build())
                .addCode("return super.decode(log)")
                .returns(eventClassName.copy(nullable = true))
                .build(),
        )

        // decode function
        var index = 0
        val initializer = StringBuilder().append("return %T(")
        inputs.forEach { _ -> initializer.append("data[${index++}] as %L,") }
        initializer.append("log)")
        factoryBuilder.addFunction(
            FunSpec.builder("decode")
                .addAnnotation(JvmStatic::class)
                .addModifiers(KModifier.OVERRIDE)
                .addParameter(ParameterSpec.builder("log", Log::class).build())
                .addParameter(ParameterSpec.builder("data", Array::class.parameterizedBy(Any::class)).build())
                .returns(eventClassName)
                .addStatement(
                    initializer.toString(),
                    eventClassName,
                    *inputs.map { it.apiType }.toTypedArray(),
                )
                .build(),
        )

        return CodeFactory.createClass(
            eventClassName,
            inputs,
            KModifier.DATA,
        ) { builder, constructor ->
            builder.superclass(eventSuperclass)

            // add "log" parameter to constructor as last argument
            val logParam = ParameterSpec.builder("log", Log::class).build()
            CodeFactory.addConstructorValParameter(
                builder,
                constructor,
                logParam,
                KModifier.OVERRIDE,
            )

            // add factory companion object
            builder.addType(factoryBuilder.build())
        }
    }

    private fun createDeployStaticFunction(
        constructor: JsonAbiConstructor,
        contractName: ClassName,
        abiProperty: PropertySpec,
    ): FunSpec {
        val arguments = constructor.inputs.toAbiTypeParameters()
        val callClass = if (constructor.isPayable) {
            PayableConstructorCall::class
        } else {
            ConstructorCall::class
        }.asClassName()

        val function = FunSpec.builder("deploy").addAnnotation(JvmStatic::class)

        // add function args
        function.addParameter("provider", Middleware::class)
        arguments.forEach { function.addParameter(it.toParameterSpec(function)) }

        // add function body
        function.addStatement(
            "return %T(provider, ${abiProperty.name}.encode(arrayOf(%L)), ::%T)",
            callClass,
            arguments.joinToString(", ") { it.name },
            contractName,
        )

        return function.returns(callClass.parameterizedBy(contractName)).build()
    }

    private fun createAbiFunctionProperty(
        generatedFunctionName: String,
        abiFunctionName: String,
        inputTypes: List<AbiTypeParameter>,
        outputTypes: List<AbiTypeParameter>,
    ): PropertySpec {
        val propName = getNextUniqueName("FUNCTION_${generatedFunctionName.toConstantCase()}")
        val prop = PropertySpec.builder(propName, AbiFunction::class)
            .addAnnotation(JvmField::class)
            .initializer(
                "%T(%S, listOf(%L), listOf(%L))",
                AbiFunction::class,
                abiFunctionName,
                inputTypes.joinToString(", ") { it.abiTypeInitializer },
                outputTypes.joinToString(", ") { it.abiTypeInitializer },
            ).build()

        constants[propName] = prop
        return prop
    }

    private fun createAbiErrorProperty(name: String, inputTypes: List<AbiTypeParameter>): PropertySpec {
        return PropertySpec.builder("abi", AbiFunction::class)
            .addModifiers(KModifier.OVERRIDE)
            .addAnnotation(JvmStatic::class)
            .initializer(
                "%T(%S, listOf(%L), emptyList())",
                AbiFunction::class,
                name,
                inputTypes.joinToString(", ") { it.abiTypeInitializer },
            ).build()
    }

    private fun createAbiEventProperty(
        name: String,
        inputTypes: List<AbiTypeParameter>,
        anonymous: Boolean,
    ): PropertySpec {
        return PropertySpec.builder("abi", AbiEvent::class)
            .addModifiers(KModifier.OVERRIDE)
            .addAnnotation(JvmStatic::class)
            .initializer(
                "%T(%S, listOf(%L), %L)",
                AbiEvent::class,
                name,
                inputTypes.joinToString(", ") { "AbiEvent.Token(${it.abiTypeInitializer}, ${it.indexed})" },
                anonymous,
            ).build()
    }

    private fun String.toConstantCase(): String {
        val words = ArrayList<String>()
        var lastWordStartIndex = 0
        this.forEachIndexed { index, char ->
            if (index == 0 || index == this.length - 1) {
                return@forEachIndexed
            }

            // Split on uppercase letters only if next one is lowercase - this handles cases like "JsonABI" so that it produces ["Json", "ABI"]
            if (char.isUpperCase() && !(this[index + 1].isUpperCase() || !this[index + 1].isLetter())) {
                words.add(this.substring(lastWordStartIndex, index))
                lastWordStartIndex = index
                return@forEachIndexed
            }
        }

        if (lastWordStartIndex != this.length) {
            words.add(this.substring(lastWordStartIndex, this.length))
        }

        return words.filter { it.isNotBlank() }.joinToString(separator = "_") { it.uppercase() }
    }

    private fun List<JsonAbiItem.Component>.toAbiTypeParameters(): List<AbiTypeParameter> {
        return map {
            when {
                it.type.startsWith("tuple") -> {
                    if (it.indexed) {
                        return@map AbiTypeParameter.fromAbiType(
                            AbiEvent.NON_VALUE_INDEXED_TYPE,
                            it.name,
                            true,
                        )
                    }

                    var suffix = it.type.substringAfter("tuple")
                    var structName = it.internalType!!
                        .removePrefix("struct ")
                        .split(".")
                        .last()
                        .substringBefore("[")

                    if (isReservedJavaName(structName)) {
                        structName = "${structName}Struct"
                    }

                    val struct = AbiTypeParameter.Struct(
                        it.name,
                        ClassName("", structName),
                        it.components.toAbiTypeParameters(),
                        false,
                    ).toUniqueStruct()

                    var abiType: AbiType = struct.abiType
                    var ret: AbiTypeParameter = struct

                    // if tuple has a suffix, it's an array
                    while (suffix.isNotBlank()) {
                        val arraySize = suffix.substringAfter("[").substringBefore("]").toIntOrNull()
                        suffix = suffix.substringAfter(']')

                        abiType = if (arraySize != null) {
                            AbiType.FixedArray(arraySize, abiType)
                        } else {
                            AbiType.Array(abiType)
                        }

                        ret = AbiTypeParameter.Collection(
                            it.name,
                            abiType,
                            ret,
                            false,
                        )
                    }

                    return@map ret
                }

                else -> {
                    val type = AbiType.parseType(it.type)
                    if (it.indexed) {
                        return@map AbiTypeParameter.fromAbiType(
                            AbiEvent.getTopicAbiType(type),
                            it.name,
                            true,
                        )
                    }

                    return@map AbiTypeParameter.fromAbiType(
                        type,
                        it.name,
                        false,
                    )
                }
            }
        }
    }

    private fun AbiTypeParameter.Struct.toUniqueStruct(): AbiTypeParameter.Struct {
        val name = this.className.simpleName
        val existing = structs[name]
        if (existing == null) {
            structs[name] = this
            uniqueClassNames.add(name)
            return this
        }

        // same abi, can be reused
        if (existing.abiType == this.abiType) {
            // copy properties of original struct (param name and indexed flag)
            return existing.copy(name = this.name, indexed = this.indexed)
        }

        val uniqueName = getNextUniqueName(name)
        val ret = this.copy(className = ClassName(this.className.packageName, uniqueName))
        structs[uniqueName] = ret
        return ret
    }

    /**
     * Create unique, non-java-keyword name from [baseName] by appending a number to it. If [baseName] is already unique,
     * it is returned. Uniqueness is determined by checking if [uniqueClassNames] contains a name. Each new unique name
     * is added to this set.
     * */
    private fun getNextUniqueName(baseName: String): String {
        if (!isReservedJavaName(baseName) && uniqueClassNames.add(baseName)) {
            return baseName
        }

        var uniqueName: String? = null
        for (i in 1..100) {
            uniqueName = "${baseName}_$i"

            if (uniqueClassNames.add(uniqueName)) {
                break
            }
        }

        if (uniqueName == null) {
            throw IllegalStateException("Could not create unique name for $baseName")
        }

        return uniqueName
    }

    /**
     * Checks if [name] is a reserved java keyword or identifier. Manually include "var" since it's missing in the
     * current version of [SourceVersion] (Temurin JDK 11).
     * */
    private fun isReservedJavaName(name: String): Boolean {
        if (name == "var") {
            return true
        }
        if (!SourceVersion.isName(name)) {
            return true
        }

        return false
    }
}
