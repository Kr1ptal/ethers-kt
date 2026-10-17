package io.ethers.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.io.IOException

/**
 * The `@Throws` annotations added for Swift also reach the JVM signatures. These pin them, so Java callers are never
 * made to handle a checked exception, and pin that the JVM keeps throwing checked errors unwrapped.
 */
class ResultJavaSignatureTest : FunSpec({
    test("unwrap declares only unchecked exceptions") {
        Result::class.java.getMethod("unwrap").exceptionTypes.toList() shouldContainExactly listOf(RuntimeException::class.java, Error::class.java)
    }

    test("unwrapError declares only IllegalStateException") {
        Result::class.java.getMethod("unwrapError").exceptionTypes.toList() shouldContainExactly listOf(IllegalStateException::class.java)
    }

    test("unwrap rethrows a checked exception error as is") {
        val cause = IOException("checked")
        val thrown = shouldThrow<IOException> { Result.failure<String, Throwable>(cause).unwrap() }
        thrown shouldBeSameInstanceAs cause
    }
})
