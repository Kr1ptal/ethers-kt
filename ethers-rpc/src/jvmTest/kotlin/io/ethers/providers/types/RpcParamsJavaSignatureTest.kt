package io.ethers.providers.types

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JsonElement
import java.lang.reflect.Modifier

/**
 * Pins the builder Java callers use, which only reach [RpcParamsBuilder] through [RpcParams.builder]: Java can't call
 * the inline `buildRpcParams` that Kotlin uses.
 */
class RpcParamsJavaSignatureTest : FunSpec({
    test("RpcParams.builder is a public static method") {
        val method = RpcParams::class.java.getMethod("builder")
        Modifier.isStatic(method.modifiers) shouldBe true
        Modifier.isPublic(method.modifiers) shouldBe true
        method.returnType shouldBe RpcParamsBuilder::class.java
    }

    test("RpcParamsBuilder.build is public") {
        val method = RpcParamsBuilder::class.java.getMethod("build")
        Modifier.isPublic(method.modifiers) shouldBe true
        method.returnType shouldBe RpcParams::class.java
    }

    test("RpcParamsBuilder.add takes a value with its serializer, or a basic value") {
        val parameterTypes = listOf(
            listOf(Any::class.java, SerializationStrategy::class.java),
            listOf(String::class.java),
            listOf(Boolean::class.javaPrimitiveType),
            listOf(Int::class.javaPrimitiveType),
            listOf(Long::class.javaPrimitiveType),
            listOf(JsonElement::class.java),
        )
        for (types in parameterTypes) {
            val method = RpcParamsBuilder::class.java.getMethod("add", *types.toTypedArray())
            Modifier.isPublic(method.modifiers) shouldBe true
            Modifier.isStatic(method.modifiers) shouldBe false
            method.returnType shouldBe RpcParamsBuilder::class.java
        }
    }

    test("the reified add is hidden from Java, so add(Object) can't bind to it") {
        val method = RpcParamsBuilder::class.java.methods.single { it.name == "add" && it.parameterTypes.contentEquals(arrayOf(Any::class.java)) }
        method.isSynthetic shouldBe true
    }

    test("RpcParams.ofJson is a public static method") {
        val method = RpcParams::class.java.getMethod("ofJson", List::class.java)
        Modifier.isStatic(method.modifiers) shouldBe true
        method.returnType shouldBe RpcParams::class.java
    }
})
