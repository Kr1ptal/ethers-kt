package io.ethers.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class ResultTest : FunSpec({
    val successValue = Result.success<String, ThrowableError>("hello")
    val testError = object : ThrowableError {
        override fun toString() = "TestError"
    }
    val failureValue = Result.failure<String, ThrowableError>(testError)

    context("map") {
        test("transforms Success value") {
            val mapped = successValue.map { it.length }
            mapped.unwrap() shouldBe 5
        }

        test("passes through Failure") {
            val mapped = failureValue.map { it.length }
            mapped.isFailure() shouldBe true
        }
    }

    context("mapError") {
        test("transforms Failure error") {
            val newError = object : ThrowableError {
                override fun toString() = "MappedError"
            }
            val mapped = failureValue.mapError { newError }
            mapped.unwrapError().toString() shouldBe "MappedError"
        }

        test("passes through Success") {
            val newError = object : ThrowableError {}
            val mapped = successValue.mapError { newError }
            mapped.unwrap() shouldBe "hello"
        }
    }

    context("andThen") {
        test("chains on Success") {
            val result = successValue.andThen { success(it.length) }
            result.unwrap() shouldBe 5
        }

        test("short-circuits on Failure") {
            var called = false
            val result = failureValue.andThen {
                called = true
                success(it.length)
            }
            called shouldBe false
            result.isFailure() shouldBe true
        }
    }

    context("orElse") {
        test("chains on Failure") {
            val result = failureValue.orElse { success("recovered") }
            result.unwrap() shouldBe "recovered"
        }

        test("passes through Success") {
            var called = false
            val result = successValue.orElse {
                called = true
                success("other")
            }
            called shouldBe false
            result.unwrap() shouldBe "hello"
        }
    }

    context("unwrap") {
        test("returns value on Success") {
            successValue.unwrap() shouldBe "hello"
        }

        test("throws on Failure") {
            shouldThrow<RuntimeException> { failureValue.unwrap() }
        }
    }

    context("unwrapOrNull") {
        test("returns value on Success") {
            successValue.unwrapOrNull() shouldBe "hello"
        }

        test("returns null on Failure") {
            failureValue.unwrapOrNull() shouldBe null
        }
    }

    context("unwrapElse") {
        test("returns value on Success") {
            successValue.unwrapElse("default") shouldBe "hello"
        }

        test("returns default on Failure") {
            failureValue.unwrapElse("default") shouldBe "default"
        }
    }

    context("unwrapOrElse") {
        test("returns value on Success") {
            successValue.unwrapOrElse { "default" } shouldBe "hello"
        }

        test("returns function result on Failure") {
            failureValue.unwrapOrElse { "from-error" } shouldBe "from-error"
        }
    }

    context("unwrapError") {
        test("returns error on Failure") {
            failureValue.unwrapError() shouldBe testError
        }

        test("throws on Success") {
            shouldThrow<IllegalStateException> { successValue.unwrapError() }
        }
    }

    context("unwrapErrorOrNull") {
        test("returns error on Failure") {
            failureValue.unwrapErrorOrNull() shouldBe testError
        }

        test("returns null on Success") {
            successValue.unwrapErrorOrNull() shouldBe null
        }
    }

    context("unwrapErrorElse") {
        test("returns error on Failure") {
            failureValue.unwrapErrorElse(testError) shouldBe testError
        }

        test("returns default on Success") {
            val defaultError = object : ThrowableError {}
            successValue.unwrapErrorElse(defaultError) shouldBe defaultError
        }
    }

    context("unwrapErrorOrElse") {
        test("returns error on Failure") {
            failureValue.unwrapErrorOrElse { testError } shouldBe testError
        }

        test("returns function result on Success") {
            val defaultError = object : ThrowableError {}
            successValue.unwrapErrorOrElse { defaultError } shouldBe defaultError
        }
    }

    context("onSuccess / onFailure") {
        test("onSuccess fires on Success") {
            var captured: String? = null
            successValue.onSuccess { captured = it }
            captured shouldBe "hello"
        }

        test("onSuccess does not fire on Failure") {
            var called = false
            failureValue.onSuccess { called = true }
            called shouldBe false
        }

        test("onFailure fires on Failure") {
            var captured: ThrowableError? = null
            failureValue.onFailure { captured = it }
            captured shouldBe testError
        }

        test("onFailure does not fire on Success") {
            var called = false
            successValue.onFailure { called = true }
            called shouldBe false
        }
    }

    context("isSuccess / isFailure / isNullOrFailure") {
        test("isSuccess returns true for Success") {
            successValue.isSuccess() shouldBe true
        }

        test("isSuccess returns false for Failure") {
            failureValue.isSuccess() shouldBe false
        }

        test("isFailure returns true for Failure") {
            failureValue.isFailure() shouldBe true
        }

        test("isFailure returns false for Success") {
            successValue.isFailure() shouldBe false
        }

        test("isNullOrFailure returns true for null") {
            (null as Result<String, ThrowableError>?).isNullOrFailure() shouldBe true
        }

        test("isNullOrFailure returns true for Failure") {
            (failureValue as Result<String, ThrowableError>?).isNullOrFailure() shouldBe true
        }

        test("isNullOrFailure returns false for Success") {
            (successValue as Result<String, ThrowableError>?).isNullOrFailure() shouldBe false
        }

        test("isNullOrFailure smart-casts to Success of the value type") {
            val result: Result<String, HexDecodingError>? = success("hi")

            if (result.isNullOrFailure()) {
                throw IllegalStateException("should be a success")
            }

            // the explicit type pins the smart cast: a contract implying Success<E> would type
            // this as HexDecodingError and fail to compile
            val value: String = result.value
            value shouldBe "hi"
        }
    }

    context("companion factory methods") {
        test("success creates Success") {
            Result.success<Int, ThrowableError>(42).shouldBeInstanceOf<Result.Success<Int>>()
        }

        test("failure creates Failure") {
            Result.failure<Int, ThrowableError>(testError).shouldBeInstanceOf<Result.Failure<ThrowableError>>()
        }

        test("top-level success helper") {
            success(42).unwrap() shouldBe 42
        }

        test("top-level failure helper") {
            failure(testError).unwrapError() shouldBe testError
        }
    }

    context("Success equals and hashCode") {
        test("equal values") {
            val a = Result.Success("hello")
            val b = Result.Success("hello")
            a shouldBe b
            a.hashCode() shouldBe b.hashCode()
        }

        test("different values") {
            val a = Result.Success("hello")
            val b = Result.Success("world")
            (a == b) shouldBe false
        }

        test("not equal to null or different type") {
            val a = Result.Success("hello")
            a.equals(null) shouldBe false
            a.equals("hello") shouldBe false
        }
    }

    context("Failure equals and hashCode") {
        test("equal errors") {
            val err = object : ThrowableError {
                override fun equals(other: Any?) = other === this
                override fun hashCode() = 42
            }
            val a = Result.Failure(err)
            val b = Result.Failure(err)
            a shouldBe b
            a.hashCode() shouldBe b.hashCode()
        }

        test("not equal to null or different type") {
            val a = Result.Failure(testError)
            a.equals(null) shouldBe false
            a.equals("string") shouldBe false
        }
    }

    context("unwrapOrReturn") {
        test("returns value on Success") {
            fun doWork(): String {
                val result: Result<String, ThrowableError> = success("value")
                return result.unwrapOrReturn { error("should not be called") }
            }
            doWork() shouldBe "value"
        }

        test("calls onFailure on Failure") {
            fun doWork(): Result<String, ThrowableError> {
                val result: Result<String, ThrowableError> = failure(testError)
                val value = result.unwrapOrReturn { return failure(it) }
                return success(value)
            }
            doWork().isFailure() shouldBe true
        }
    }

    context("ThrowableError") {
        test("default toException wraps toString") {
            val exception = testError.toException()
            exception.message shouldBe "TestError"
            exception.cause shouldBe null
        }

        test("unwrap throws the exception returned by toException") {
            val expected = RuntimeException("custom")
            val err = object : ThrowableError {
                override fun toException() = expected
            }
            val thrown = shouldThrow<RuntimeException> { failure(err).unwrap() }
            thrown shouldBe expected
        }

        test("message and cause are carried into the exception") {
            val cause = IllegalStateException("underlying")
            val err = object : ThrowableError {
                override val message get() = "explicit message"
                override val cause get() = cause
            }

            val exception = err.toException()
            exception.message shouldBe "explicit message"
            exception.cause shouldBe cause
        }

        test("the thrown exception retains the error it was built from") {
            val err = HexDecodingError("Invalid hex: 0xzz")
            val thrown = shouldThrow<ThrowableError.Exception> { failure(err).unwrap() }

            thrown.error shouldBe err
            thrown.error.asTypeOrNull<HexDecodingError>()?.message shouldBe "Invalid hex: 0xzz"
        }

        test("an explicit message renders with the error type name") {
            HexDecodingError("Invalid hex: 0xzz").toException().toString() shouldBe
                "HexDecodingError: Invalid hex: 0xzz"
        }

        test("without an explicit message the error's own toString is used, avoiding a duplicated type name") {
            val err = ExampleDataError("boom")
            err.toException().toString() shouldBe "ExampleDataError(detail=boom)"
        }
    }

    context("ThrowableError.asTypeOrNull") {
        test("returns typed error when matching") {
            val err = HexDecodingError("test")
            val typed = err.asTypeOrNull<HexDecodingError>()
            typed shouldBe err
        }

        test("returns null when not matching") {
            val typed = testError.asTypeOrNull<HexDecodingError>()
            typed shouldBe null
        }
    }

    context("unwrap error dispatch") {
        test("throws a Throwable error directly") {
            val cause = IllegalStateException("boom")
            shouldThrow<IllegalStateException> { failure(cause).unwrap() } shouldBe cause
        }

        test("throws generically for an error that is neither ThrowableError nor Throwable") {
            val thrown = shouldThrow<IllegalStateException> { failure("plain string").unwrap() }
            thrown.message shouldBe "Value is not success: plain string"
        }

        test("throws generically for a null error") {
            val thrown = shouldThrow<IllegalStateException> { failure(null).unwrap() }
            thrown.message shouldBe "Value is not success: null"
        }
    }

    context("kotlin.Result extensions") {
        test("andThen chains on success") {
            val result = kotlin.Result.success("hello").andThen { kotlin.Result.success(it.length) }
            result.getOrThrow() shouldBe 5
        }

        test("andThen passes through failure") {
            val ex = RuntimeException("fail")
            val result = kotlin.Result.failure<String>(ex).andThen { kotlin.Result.success(it.length) }
            result.isFailure shouldBe true
        }

        test("andThen maps a success holding null") {
            // a null success value must not be mistaken for a failure, which is what distinguishing
            // the two via getOrNull() used to do
            val result = kotlin.Result.success<String?>(null).andThen { kotlin.Result.success("mapped") }
            result.getOrThrow() shouldBe "mapped"
        }

        test("andThenCatching catches exceptions in mapper") {
            val result = kotlin.Result.success("hello").andThenCatching<String, String> { throw IllegalStateException("boom") }
            result.isFailure shouldBe true
        }

        test("andThenCatching maps a success holding null") {
            val result = kotlin.Result.success<String?>(null).andThenCatching<String?, String> { kotlin.Result.success("mapped") }
            result.getOrThrow() shouldBe "mapped"
        }

        test("andThenCatching succeeds normally without throwing") {
            val result = kotlin.Result.success("hello").andThenCatching<String, Int> { kotlin.Result.success(it.length) }
            result.getOrThrow() shouldBe 5
        }

        test("andThenCatching passes through failure") {
            val ex = RuntimeException("fail")
            val result = kotlin.Result.failure<String>(ex).andThenCatching<String, Int> { kotlin.Result.success(it.length) }
            result.isFailure shouldBe true
        }

        test("toResult converts success") {
            val result = kotlin.Result.success("hello").toResult()
            result.unwrap() shouldBe "hello"
        }

        test("toResult converts failure") {
            val ex = RuntimeException("fail")
            val result = kotlin.Result.failure<String>(ex).toResult()
            result.isFailure() shouldBe true
            result.unwrapError() shouldBe ex
        }

        test("unwrapOrReturn returns value on success") {
            fun doWork(): String {
                val result = kotlin.Result.success("value")
                return result.unwrapOrReturn { error("should not be called") }
            }
            doWork() shouldBe "value"
        }

        test("unwrapOrReturn calls onFailure on failure") {
            fun doWork(): String {
                val result = kotlin.Result.failure<String>(RuntimeException("fail"))
                return result.unwrapOrReturn { return "recovered" }
            }
            doWork() shouldBe "recovered"
        }
    }
})

private data class ExampleDataError(val detail: String) : ThrowableError
