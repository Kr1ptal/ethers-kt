package io.ethers.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

class ResultNativeTest : FunSpec({
    test("unwrap wraps an exception it does not declare, so it cannot abort a Swift caller") {
        val cause = CharacterCodingException("not declared")
        val thrown = shouldThrow<RuntimeException> { Result.failure<String, Throwable>(cause).unwrap() }
        thrown.cause shouldBeSameInstanceAs cause
        thrown.message shouldBe "not declared"
    }
})
