package io.ethers.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class ResultTest : FunSpec({
    val successValue = Result.success<String, Result.Error>("hello")
    val testError = object : Result.Error {
        override fun toString() = "TestError"
    }
    val failureValue = Result.failure<String, Result.Error>(testError)

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
            val newError = object : Result.Error {
                override fun toString() = "MappedError"
            }
            val mapped = failureValue.mapError { newError }
            mapped.unwrapError().toString() shouldBe "MappedError"
        }

        test("passes through Success") {
            val newError = object : Result.Error {}
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
            val defaultError = object : Result.Error {}
            successValue.unwrapErrorElse(defaultError) shouldBe defaultError
        }
    }

    context("unwrapErrorOrElse") {
        test("returns error on Failure") {
            failureValue.unwrapErrorOrElse { testError } shouldBe testError
        }

        test("returns function result on Success") {
            val defaultError = object : Result.Error {}
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
            var captured: Result.Error? = null
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
            (null as Result<String, Result.Error>?).isNullOrFailure() shouldBe true
        }

        test("isNullOrFailure returns true for Failure") {
            (failureValue as Result<String, Result.Error>?).isNullOrFailure() shouldBe true
        }

        test("isNullOrFailure returns false for Success") {
            (successValue as Result<String, Result.Error>?).isNullOrFailure() shouldBe false
        }
    }

    context("companion factory methods") {
        test("success creates Success") {
            Result.success<Int, Result.Error>(42).shouldBeInstanceOf<Result.Success<Int>>()
        }

        test("failure creates Failure") {
            Result.failure<Int, Result.Error>(testError).shouldBeInstanceOf<Result.Failure<Result.Error>>()
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
            val err = object : Result.Error {
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
                val result: Result<String, Result.Error> = success("value")
                return result.unwrapOrReturn { error("should not be called") }
            }
            doWork() shouldBe "value"
        }

        test("calls onFailure on Failure") {
            fun doWork(): Result<String, Result.Error> {
                val result: Result<String, Result.Error> = failure(testError)
                val value = result.unwrapOrReturn { return failure(it) }
                return success(value)
            }
            doWork().isFailure() shouldBe true
        }
    }

    context("Error.asTypeOrNull") {
        test("returns typed error when matching") {
            val err = ExceptionalError(RuntimeException("test"))
            val typed = err.asTypeOrNull<ExceptionalError>()
            typed shouldBe err
        }

        test("returns null when not matching") {
            val typed = testError.asTypeOrNull<ExceptionalError>()
            typed shouldBe null
        }
    }

    context("ExceptionalError") {
        test("doThrow wraps cause") {
            val cause = RuntimeException("original")
            val err = ExceptionalError(cause)
            val thrown = shouldThrow<RuntimeException> { err.doThrow() }
            thrown.cause shouldBe cause
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

        test("andThen with null success value treats as failure") {
            val result = kotlin.Result.success<String?>(null).andThen { kotlin.Result.success("mapped") }
            // getOrNull returns null for success(null), so andThen treats it as failure path
            result.getOrNull() shouldBe null
        }

        test("andThenCatching catches exceptions in mapper") {
            val result = kotlin.Result.success("hello").andThenCatching<String, String> { throw IllegalStateException("boom") }
            result.isFailure shouldBe true
        }

        test("andThenCatching with null success value treats as failure") {
            val result = kotlin.Result.success<String?>(null).andThenCatching<String?, String> { kotlin.Result.success("mapped") }
            result.getOrNull() shouldBe null
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
            result.unwrapError().shouldBeInstanceOf<ExceptionalError>()
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
