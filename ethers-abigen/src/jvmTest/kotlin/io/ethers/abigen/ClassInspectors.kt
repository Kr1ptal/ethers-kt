package io.ethers.abigen

import io.ethers.abi.AbiFunction
import io.ethers.abi.ContractEvent
import io.ethers.abi.ContractStruct
import io.ethers.abi.EventFactory
import io.ethers.abi.error.CustomContractError
import io.ethers.abi.error.CustomErrorFactory
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.primaryConstructor

fun KClass<*>.nestedClass(name: String): KClass<*> {
    return this.nestedClasses.find { it.simpleName == name }
        ?: throw IllegalStateException("Class not found: $name")
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> KClass<*>.typedNestedClass(name: String): KClass<T> {
    return this.nestedClasses.find { it.simpleName == name } as? KClass<T>
        ?: throw IllegalStateException("Class not found: $name")
}

fun KClass<*>.getAbiFunctionField(name: String): AbiFunction {
    return companionObject!!.members
        .single { it.name == name }
        .call(companionObjectInstance) as AbiFunction
}

fun KClass<*>.getDeclaredStructs(): DeclaredStructs {
    val classes = this.nestedClasses.filter { it.isSubclassOf(ContractStruct::class) }
    val descriptors = classes.map { struct ->
        val args = struct.primaryConstructor!!.parameters
            .map { ArgDescriptor(it.name!!, it.type) }

        ClassDescriptor(
            name = struct.simpleName!!,
            arguments = args,
        )
    }

    return DeclaredStructs(classes, descriptors)
}

fun KClass<*>.getDeclaredEvents(): DeclaredEvents {
    val classes = this.nestedClasses.filter { !it.isSealed && it.isSubclassOf(ContractEvent::class) }
    val descriptors = classes.map { struct ->
        val args = struct.primaryConstructor!!.parameters
            .map { ArgDescriptor(it.name!!, it.type) }

        ClassDescriptor(
            name = struct.simpleName!!,
            arguments = args,
        )
    }

    return DeclaredEvents(
        this.nestedClasses.single { it.isSealed && it.isSubclassOf(ContractEvent::class) },
        classes,
        descriptors,
        classes.map { it.companionObjectInstance as EventFactory<*> },
    )
}

fun KClass<*>.getDeclaredErrors(): DeclaredErrors {
    val classes = this.nestedClasses.filter { !it.isSealed && it.isSubclassOf(CustomContractError::class) }
    val descriptors = classes.map { struct ->
        val args = struct.primaryConstructor!!.parameters
            .map { ArgDescriptor(it.name!!, it.type) }

        ClassDescriptor(
            name = struct.simpleName!!,
            arguments = args,
        )
    }

    return DeclaredErrors(
        this.nestedClasses.single { it.isSealed && it.isSubclassOf(CustomContractError::class) },
        classes,
        descriptors,
        classes.map { it.companionObjectInstance as CustomErrorFactory<*> },
    )
}

fun KClass<*>.getDeclaredFunctions(): List<FunctionDescriptor> {
    return this.memberFunctions.map { method ->
        val args = method.parameters
            .asSequence()
            .drop(1)
            .map { ArgDescriptor(it.name!!, it.type) }
            .toList()

        FunctionDescriptor(
            name = method.name,
            arguments = args,
            returnType = method.returnType,
        )
    }
}

fun KClass<*>.parametrizedBy(vararg args: KClass<*>): KType {
    return this.createType(args.map { KTypeProjection.invariant(it.createType()) })
}

fun KClass<*>.parametrizedBy(vararg args: KType): KType {
    return this.createType(args.map { KTypeProjection.invariant(it) })
}

data class DeclaredStructs(
    val classes: List<KClass<*>>,
    val descriptors: List<ClassDescriptor>,
)

data class DeclaredEvents(
    val baseClass: KClass<*>,
    val classes: List<KClass<*>>,
    val descriptors: List<ClassDescriptor>,
    val factories: List<EventFactory<*>>,
)

data class DeclaredErrors(
    val baseClass: KClass<*>,
    val classes: List<KClass<*>>,
    val descriptors: List<ClassDescriptor>,
    val factories: List<CustomErrorFactory<*>>,
)

data class FunctionDescriptor(
    val name: String,
    val arguments: List<ArgDescriptor>,
    val returnType: KType,
)

data class ClassDescriptor(
    val name: String,
    val arguments: List<ArgDescriptor>,
)

data class ArgDescriptor(
    val name: String,
    val type: KType,
) {
    constructor(name: String, type: KClass<*>) : this(name, type.createType())
}
