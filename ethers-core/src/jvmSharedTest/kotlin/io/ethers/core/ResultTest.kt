package io.ethers.core

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getError
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class ResultTest : FunSpec({
    context("unwrap") {
        test("returns ok value for ThrowingError results") {
            val result: Result<String, TestError> = Ok("hello")

            result.unwrap() shouldBe "hello"
        }

        test("throws via ThrowingError.doThrow for err results") {
            val error = TestError()
            val result: Result<String, TestError> = Err(error)

            shouldThrow<IllegalStateException> { result.unwrap() } shouldBe error.exception
        }

        test("throws throwable errors directly") {
            val exception = IllegalArgumentException("boom")
            val result: Result<String, IllegalArgumentException> = Err(exception)

            shouldThrow<IllegalArgumentException> { result.unwrap() } shouldBe exception
        }
    }

    context("unwrapOrReturn") {
        test("returns ok value") {
            fun doWork(): String {
                val result: Result<String, String> = Ok("value")
                return result.unwrapOrReturn { error("should not be called") }
            }

            doWork() shouldBe "value"
        }

        test("lets caller return from failure branch") {
            fun doWork(): Result<String, String> {
                val result: Result<String, String> = Err("bad")
                val value = result.unwrapOrReturn { return Err(it) }
                return Ok(value)
            }

            doWork().getError() shouldBe "bad"
        }
    }

    context("ThrowingError.asTypeOrNull") {
        test("returns typed error when matching") {
            val error = ExceptionalError(RuntimeException("test"))

            error.asTypeOrNull<ExceptionalError>() shouldBe error
        }

        test("returns null when not matching") {
            TestError().asTypeOrNull<ExceptionalError>() shouldBe null
        }
    }

    context("ExceptionalError") {
        test("converts cause to exception") {
            val cause = RuntimeException("original")
            val error = ExceptionalError(cause)

            error.toException().cause shouldBe cause
        }

        test("wraps cause when thrown") {
            val cause = RuntimeException("original")
            val error = ExceptionalError(cause)

            shouldThrow<RuntimeException> { error.doThrow() }.cause shouldBe cause
        }
    }

    context("kotlin.Result extensions") {
        test("andThen chains successful results") {
            val result = kotlin.Result.success("hello")
                .andThen { kotlin.Result.success(it.length) }

            result.getOrThrow() shouldBe 5
        }

        test("andThen passes through failures") {
            val exception = RuntimeException("fail")
            val result = kotlin.Result.failure<String>(exception)
                .andThen { kotlin.Result.success(it.length) }

            result.exceptionOrNull() shouldBe exception
        }

        test("andThenCatching catches mapper exceptions") {
            val result = kotlin.Result.success("hello")
                .andThenCatching<String, Int> { throw IllegalStateException("boom") }

            result.exceptionOrNull().shouldBeInstanceOf<IllegalStateException>()
        }

        test("unwrapOrReturn returns success") {
            fun doWork(): String {
                val result = kotlin.Result.success("value")
                return result.unwrapOrReturn { error("should not be called") }
            }

            doWork() shouldBe "value"
        }

        test("unwrapOrReturn lets caller return from failure branch") {
            fun doWork(): String {
                val result = kotlin.Result.failure<String>(RuntimeException("fail"))
                return result.unwrapOrReturn { return "recovered" }
            }

            doWork() shouldBe "recovered"
        }
    }
})

private class TestError : ThrowingError {
    val exception = IllegalStateException("test error")

    override fun toException(): RuntimeException {
        return exception
    }

    override fun toString(): String = "TestError"
}
