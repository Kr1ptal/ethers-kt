package io.ethers.providers.types

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Result represents a value that can be either a [Success] or a [Failure].
 * */
// code is optimized to avoid branching operators (if/else) for better JIT compiler optimizations
sealed class Result<out T : Any?, out E : Result.Error> {
    class Success<T : Any?>(val value: T) : Result<T, Nothing>() {
        override fun <R> fold(
            onSuccess: ResultTransformer<Success<T>, R>,
            onFailure: ResultTransformer<Failure<Nothing>, R>,
        ): R {
            return onSuccess(this)
        }

        override fun toString() = "Success($value)"
    }

    class Failure<E : Error>(val error: E) : Result<Nothing, E>() {
        override fun <R> fold(
            onSuccess: ResultTransformer<Success<Nothing>, R>,
            onFailure: ResultTransformer<Failure<E>, R>,
        ): R {
            return onFailure(this)
        }

        override fun toString() = "Failure($error)"
    }

    // ideally, "isSuccess"/"isFailure" would be properties, but we cannot define contracts for properties

    /**
     * Returns true if [Result] is [Success], false otherwise.
     * */
    @OptIn(ExperimentalContracts::class)
    fun isSuccess(): Boolean {
        contract {
            returns(true) implies (this@Result is Success<*>)
            returns(false) implies (this@Result is Failure<*>)
        }
        return this is Success<T>
    }

    /**
     * Returns true if [Result] is [Failure], false otherwise.
     * */
    @OptIn(ExperimentalContracts::class)
    fun isFailure(): Boolean {
        contract {
            returns(false) implies (this@Result is Success<*>)
            returns(true) implies (this@Result is Failure<*>)
        }
        return this is Failure<E>
    }

    /**
     * Maps a [Result]<[T], [E]> to [Result]<[R], [E]> by applying a function to a [Success] value, leaving a
     * [Failure] value untouched.
     * */
    fun <R : Any?> map(mapper: ResultTransformer<in T, R>): Result<R, E> = fold({ Success(mapper(it.value)) }, { it })

    /**
     * Maps a [Result]<[T], [E]> to [Result]<[T], [R]> by applying a function to a [Failure], leaving a
     * [Success] value untouched.
     * */
    fun <R : Error> mapError(mapper: ResultTransformer<in E, out R>): Result<T, R> {
        return fold({ it }, { Failure(mapper(it.error)) })
    }

    /**
     * Call the function with value of [Success], expecting another result, and skipping if [Result] is [Failure].
     * Useful when chaining multiple fallible operations on the result.
     * */
    fun <R : Any?> andThen(mapper: ResultTransformer<in T, Result<R, @UnsafeVariance E>>): Result<R, E> {
        return fold({ mapper(it.value) }, { it })
    }

    /**
     * Call the function with error of [Failure], expecting another result, and skipping if [Result] is [Success].
     * Useful when chaining multiple fallible operations on the error (e.g. trying to recover from an error).
     * */
    fun <R : Error> orElse(mapper: ResultTransformer<in E, Result<@UnsafeVariance T, R>>): Result<T, R> {
        return fold({ it }, { mapper(it.error) })
    }

    /**
     * Unwrap the value if [Result] is [Success], or throw an exception if [Result] is [Failure].
     * */
    fun unwrap(): T = fold({ it.value }, { it.error.doThrow() })

    /**
     * Unwrap the value if [Result] is [Success], or return [default] if [Result] is [Failure].
     * */
    fun unwrapElse(default: @UnsafeVariance T): T = fold({ it.value }, { default })

    /**
     * Unwrap the value if [Result] is [Success], or return the result of [default] function if [Result] is [Failure].
     * */
    fun unwrapOrElse(default: ResultTransformer<in E, @UnsafeVariance T>): T {
        return fold({ it.value }, { default(it.error) })
    }

    /**
     * Unwrap the error if [Result] is [Failure], or throw an exception if [Result] is [Success].
     * */
    fun unwrapError(): E = fold({ throw IllegalStateException("Cannot unwrap success as error") }, { it.error })

    /**
     * Unwrap the error if [Result] is [Failure], or return [default] if [Result] is [Success].
     * */
    fun unwrapErrorElse(default: @UnsafeVariance E): E = fold({ default }, { it.error })

    /**
     * Unwrap the error if [Result] is [Failure], or return the result of [default] function if [Result] is [Success].
     * */
    fun unwrapErrorOrElse(default: ResultTransformer<in T, @UnsafeVariance E>): E {
        return fold({ default(it.value) }, { it.error })
    }

    /**
     * Callback called if [Result] is [Success].
     * */
    fun onSuccess(block: ResultConsumer<in T>) = fold({ block(it.value) }, {})

    /**
     * Callback called if [Result] is [Failure].
     * */
    fun onFailure(block: ResultConsumer<in E>) = fold({}, { block(it.error) })

    /**
     * Call [onSuccess] if [Result] is [Success] or [onFailure] if [Result] is [Failure], returning the result of
     * the called function.
     * */
    abstract fun <R> fold(
        onSuccess: ResultTransformer<Success<@UnsafeVariance T>, R>,
        onFailure: ResultTransformer<Failure<@UnsafeVariance E>, R>,
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
     * Cast [Error] to [T] or return null if error is not of type [T].
     * Useful for accessing details of specific error subclass.
     */
    inline fun <reified T : Error> Error.asTypeOrNull(): T? {
        return asTypeOrNull(T::class.java)
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

/**
 * Custom functional interface for better java interop with kotlin lambdas. Prevents the java caller having to
 * explicitly return `Unit.INSTANCE`.
 * */
fun interface ResultConsumer<T> {
    operator fun invoke(t: T)
}

/**
 * Custom functional interface for better java interop with kotlin lambdas. Prevents the java caller having to
 * explicitly return `Unit.INSTANCE`.
 * */
fun interface ResultTransformer<T, R> {
    operator fun invoke(t: T): R
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
