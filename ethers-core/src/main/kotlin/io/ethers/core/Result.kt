package io.ethers.core

import io.ethers.core.Result.Failure
import io.ethers.core.Result.Success
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Result represents a value that can be either a [Success] or a [Failure].
 * */
// code is optimized to avoid branching operators (if/else) for better JIT compiler optimizations
sealed class Result<out T : Any?, out E : Result.Error> {
    class Success<out T : Any?>(val value: T) : Result<T, Nothing>() {
        override fun <R> fold(
            onSuccess: Transformer<Success<T>, R>,
            onFailure: Transformer<Failure<Nothing>, R>,
        ): R {
            return onSuccess(this)
        }

        override fun toString() = "Success($value)"
    }

    class Failure<out E : Error>(val error: E) : Result<Nothing, E>() {
        override fun <R> fold(
            onSuccess: Transformer<Success<Nothing>, R>,
            onFailure: Transformer<Failure<E>, R>,
        ): R {
            return onFailure(this)
        }

        override fun toString() = "Failure($error)"
    }

    /**
     * Java bridge for [isSuccess] function. Should not be used from kotlin, use extension function instead since
     * it provides smart casting.
     * */
    @JvmName("isSuccess")
    fun isInstanceOfSuccessJavaBridge(): Boolean = this.isSuccess()

    /**
     * Java bridge for [isFailure] function. Should not be used from kotlin, use extension function instead since
     * it provides smart casting.
     * */
    @JvmName("isFailure")
    fun isInstanceOfFailureJavaBridge(): Boolean = this.isFailure()

    /**
     * Maps a [Result]<[T], [E]> to [Result]<[R], [E]> by applying a function to a [Success] value, leaving a
     * [Failure] value untouched.
     * */
    fun <R : Any?> map(mapper: Transformer<T, R>): Result<R, E> = fold({ Success(mapper(it.value)) }, { it })

    /**
     * Maps a [Result]<[T], [E]> to [Result]<[T], [R]> by applying a function to a [Failure], leaving a
     * [Success] value untouched.
     * */
    fun <R : Error> mapError(mapper: Transformer<E, R>): Result<T, R> {
        return fold({ it }, { Failure(mapper(it.error)) })
    }

    /**
     * Call the function with value of [Success], expecting another result, and skipping if [Result] is [Failure].
     * Useful when chaining multiple fallible operations on the result.
     * */
    fun <R : Any?> andThen(mapper: Transformer<T, Result<R, @UnsafeVariance E>>): Result<R, E> {
        return fold({ mapper(it.value) }, { it })
    }

    /**
     * Call the function with error of [Failure], expecting another result, and skipping if [Result] is [Success].
     * Useful when chaining multiple fallible operations on the error (e.g. trying to recover from an error).
     * */
    fun <R : Error> orElse(mapper: Transformer<E, Result<@UnsafeVariance T, R>>): Result<T, R> {
        return fold({ it }, { mapper(it.error) })
    }

    /**
     * Unwrap the value if [Result] is [Success], or throw an exception if [Result] is [Failure].
     * */
    fun unwrap(): T = fold({ it.value }, { it.error.doThrow() })

    /**
     * Unwrap the value if [Result] is [Success], or return null if [Result] is [Failure].
     * */
    fun unwrapOrNull(): T? = fold({ it.value }, { null })

    /**
     * Unwrap the value if [Result] is [Success], or return [default] if [Result] is [Failure].
     * */
    fun unwrapElse(default: @UnsafeVariance T): T = fold({ it.value }, { default })

    /**
     * Unwrap the value if [Result] is [Success], or return the result of [default] function if [Result] is [Failure].
     * */
    fun unwrapOrElse(default: Transformer<E, @UnsafeVariance T>): T {
        return fold({ it.value }, { default(it.error) })
    }

    /**
     * Unwrap the error if [Result] is [Failure], or throw an exception if [Result] is [Success].
     * */
    fun unwrapError(): E = fold({ throw IllegalStateException("Cannot unwrap success as error") }, { it.error })

    /**
     * Unwrap the error if [Result] is [Failure], or return null if [Result] is [Success].
     * */
    fun unwrapErrorOrNull(): E? = fold({ null }, { it.error })

    /**
     * Unwrap the error if [Result] is [Failure], or return [default] if [Result] is [Success].
     * */
    fun unwrapErrorElse(default: @UnsafeVariance E): E = fold({ default }, { it.error })

    /**
     * Unwrap the error if [Result] is [Failure], or return the result of [default] function if [Result] is [Success].
     * */
    fun unwrapErrorOrElse(default: Transformer<T, @UnsafeVariance E>): E {
        return fold({ default(it.value) }, { it.error })
    }

    /**
     * Callback called if [Result] is [Success].
     * */
    fun onSuccess(block: Consumer<T>) = fold({ block(it.value) }, {})

    /**
     * Callback called if [Result] is [Failure].
     * */
    fun onFailure(block: Consumer<E>) = fold({}, { block(it.error) })

    /**
     * Call [onSuccess] if [Result] is [Success] or [onFailure] if [Result] is [Failure], returning the result of
     * the called function.
     * */
    abstract fun <R> fold(
        onSuccess: Transformer<Success<T>, R>,
        onFailure: Transformer<Failure<E>, R>,
    ): R

    /**
     * Type used for encapsulating error details within [Result.Failure].
     * */
    interface Error {
        /**
         * Throw this [Error] as an exception. If implementation wraps an exception, this method should be overridden
         * to provide an accurate stacktrace.
         * */
        fun doThrow(): Nothing {
            throw RuntimeException(this.toString())
        }

        /**
         * Cast [Error] to a given class or return null if error is not of type [T].
         * Useful for accessing details of specific error subclass.
         */
        @Suppress("UNCHECKED_CAST")
        fun <T : Error> asTypeOrNull(type: Class<T>): T? {
            return if (type.isAssignableFrom(this::class.java)) this as T else null
        }
    }

    /**
     * Custom functional interface for better java interop with kotlin lambdas. Prevents the java caller having to
     * explicitly return `Unit.INSTANCE`.
     * */
    fun interface Consumer<in T> {
        operator fun invoke(t: T)
    }

    /**
     * Custom functional interface for better java interop with kotlin lambdas. Prevents the java caller having to
     * explicitly return `Unit.INSTANCE`.
     * */
    fun interface Transformer<in T, out R> {
        operator fun invoke(t: T): R
    }

    companion object {
        /**
         * Return a [Result.Success] with the given [value].
         * */
        @JvmStatic
        fun <T : Any?, E : Error> success(value: T): Result<T, E> = Success(value)

        /**
         * Return a [Result.Failure] with the given [error].
         * */
        @JvmStatic
        fun <T : Any?, E : Error> failure(error: E): Result<T, E> = Failure(error)
    }
}

// Ideally, "isSuccess"/"isFailure" would be properties, but we cannot define contracts for properties.
// They would also be defined as a methods on Result, but due to a compiler bug, the "implies" in contracts
// would not work. We use extension functions in kotlin, and normal methods from java.
//
// see: https://youtrack.jetbrains.com/issue/KT-57869/Unresolved-reference-from-kotlin-contract-when-building-project#focus=Comments-27-7316591.0-0

/**
 * Returns true if [Result] is [Success], false otherwise.
 * */
@OptIn(ExperimentalContracts::class)
fun <T, E : Result.Error> Result<T, E>.isSuccess(): Boolean {
    contract {
        returns(true) implies (this@isSuccess is Success<T>)
        returns(false) implies (this@isSuccess is Failure<E>)
    }
    return this is Success<T>
}

/**
 * Returns true if [Result] is [Failure], false otherwise.
 * */
@OptIn(ExperimentalContracts::class)
fun <T, E : Result.Error> Result<T, E>.isFailure(): Boolean {
    contract {
        returns(false) implies (this@isFailure is Success<T>)
        returns(true) implies (this@isFailure is Failure<E>)
    }
    return this is Failure<E>
}

/**
 * Cast [Error] to [T] or return null if error is not of type [T].
 * Useful for accessing details of specific error subclass.
 */
inline fun <reified T : Result.Error> Result.Error.asTypeOrNull(): T? {
    return asTypeOrNull(T::class.java)
}

/**
 * Return a [Result.Success] with the given [value].
 * */
@JvmSynthetic
fun <T : Any?> success(value: T): Result<T, Nothing> = Result.success(value)

/**
 * Return a [Result.Failure] with the given [error].
 * */
@JvmSynthetic
fun <E : Result.Error> failure(error: E) = Result.failure<Nothing, E>(error)

/**
 * Unwrap the value if [Result] is [Success], or call the [onFailure] function which must either
 * throw or return from the enclosing function. This is useful when short-circuiting execution by
 * returning from a function on error, e.g.:
 *
 * ```kotlin
 * val input = "invalid input"
 * val addr = response.unwrapOrReturn { cause ->
 *     return failure(CustomError.InvalidAddress(input, cause))
 * }
 * ```
 * */
@OptIn(ExperimentalContracts::class)
inline fun <R, T : R, E : Result.Error> Result<T, E>.unwrapOrReturn(onFailure: (E) -> Nothing): R {
    contract { callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE) }

    if (isSuccess()) {
        return this.value
    } else {
        onFailure(this.unwrapError())
    }
}

/**
 * An error that wraps an exception.
 * */
data class ExceptionalError(val cause: Throwable) : Result.Error {
    override fun doThrow(): Nothing {
        throw RuntimeException("Exceptional execution", cause)
    }
}

// ---------------------------------------------------- //
// ------------- kotlin.Result Extensions ------------- //
// ---------------------------------------------------- //

@OptIn(ExperimentalContracts::class)
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
// value returned from mapper will be boxed
inline fun <T, R> kotlin.Result<T>.andThen(mapper: (T) -> kotlin.Result<R>): kotlin.Result<R> {
    contract { callsInPlace(mapper, InvocationKind.AT_MOST_ONCE) }

    return when (val v = getOrNull()) {
        null -> kotlin.Result(value)
        else -> mapper(v)
    }
}

@OptIn(ExperimentalContracts::class)
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
// value returned from mapper will be boxed
inline fun <T, R> kotlin.Result<T>.andThenCatching(mapper: (T) -> kotlin.Result<R>): kotlin.Result<R> {
    contract { callsInPlace(mapper, InvocationKind.AT_MOST_ONCE) }

    return when (val v = getOrNull()) {
        null -> kotlin.Result(value)
        else -> runCatching { mapper(v).getOrThrow() }
    }
}

/**
 * Unwrap the value if [kotlin.Result] is success, or call the [onFailure] function which must either
 * throw or return from the enclosing function. This is useful when short-circuiting execution by
 * returning from a function on error, e.g.:
 *
 * ```kotlin
 * val input = "invalid input"
 * val addr = runCatching { Address(input) }.unwrapOrReturn { cause ->
 *     return failure(CustomError.InvalidAddress(input, cause))
 * }
 * ```
 * */
@OptIn(ExperimentalContracts::class)
inline fun <R, T : R> kotlin.Result<T>.unwrapOrReturn(onFailure: (ExceptionalError) -> Nothing): R {
    contract { callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE) }

    if (isSuccess) {
        return this.getOrThrow()
    }
    onFailure(ExceptionalError(this.exceptionOrNull()!!))
}

/**
 * Transform [kotlin.Result] into [Result], wrapping the exception in [ExceptionalError] if it holds a failure.
 * */
@Suppress("NOTHING_TO_INLINE")
inline fun <T> kotlin.Result<T>.toResult(): Result<T, ExceptionalError> {
    if (isSuccess) {
        return success(this.getOrThrow())
    }
    return failure(ExceptionalError(this.exceptionOrNull()!!))
}
