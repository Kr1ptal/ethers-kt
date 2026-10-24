package io.ethers.core

import io.ethers.crypto.Base58
import io.ethers.crypto.Hashing
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.io.IOException

/**
 * The `@Throws` annotations added for Swift also reach the JVM signatures. These pin them, so Java callers are never
 * made to handle a checked exception, and pin that the JVM keeps throwing checked errors unwrapped. The function type
 * overloads added for Swift are pinned as synthetic, so Java keeps seeing only the Consumer and Transformer ones.
 */
class ResultJavaSignatureTest : FunSpec({
    test("unwrap declares only unchecked exceptions") {
        Result::class.java.getMethod("unwrap").exceptionTypes.toList() shouldContainExactly listOf(RuntimeException::class.java, Error::class.java)
    }

    test("unwrapError declares only IllegalStateException") {
        Result::class.java.getMethod("unwrapError").exceptionTypes.toList() shouldContainExactly listOf(IllegalStateException::class.java)
    }

    test("Java sees only the Consumer and Transformer overloads of the callback functions") {
        val visible = Result::class.java.methods.filter { !it.isSynthetic && it.name in callbackFunctions }
        visible.map { it.name to it.parameterTypes.toList() } shouldContainExactlyInAnyOrder listOf(
            "onSuccess" to listOf(Result.Consumer::class.java),
            "onFailure" to listOf(Result.Consumer::class.java),
            "unwrapOrElse" to listOf(Result.Transformer::class.java),
            "unwrapErrorOrElse" to listOf(Result.Transformer::class.java),
        )
    }

    test("the function type overloads of the callback functions are synthetic") {
        val synthetic = Result::class.java.methods.filter { it.isSynthetic && it.name in callbackFunctions }
        synthetic.map { it.name to it.parameterTypes.toList() } shouldContainExactlyInAnyOrder callbackFunctions.map {
            it to listOf(Function1::class.java)
        }
    }

    test("no annotated ethers-common declaration declares a checked exception") {
        val checked = listOf(FastHex::class.java, Base58::class.java, Hashing::class.java, Class.forName("io.ethers.core.JsonValueConvertersKt"))
            .flatMap { it.declaredMethods.toList() }
            .flatMap { method ->
                method.exceptionTypes
                    .filterNot { RuntimeException::class.java.isAssignableFrom(it) || Error::class.java.isAssignableFrom(it) }
                    .map { "$method throws ${it.name}" }
            }
        checked.shouldBeEmpty()
    }

    test("unwrap rethrows a checked exception error as is") {
        val cause = IOException("checked")
        val thrown = shouldThrow<IOException> { Result.failure<String, Throwable>(cause).unwrap() }
        thrown shouldBeSameInstanceAs cause
    }
})

private val callbackFunctions = setOf("onSuccess", "onFailure", "unwrapOrElse", "unwrapErrorOrElse")
