package io.ethers.providers.types

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
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
})
