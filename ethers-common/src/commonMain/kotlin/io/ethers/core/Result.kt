package io.ethers.core

import io.ethers.core.Result.Failure
import io.ethers.core.Result.Success
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic
import kotlin.native.HiddenFromObjC
import kotlin.native.ObjCName

/**
 * Result represents a value that can be either a [Success] or a [Failure].
 *
 * From Swift, call [unwrap] with `try`: a [Failure] is thrown as an `Error`, and the original
 * error stays reachable through `(error as NSError).kotlinException`. The combinators taking a [Transformer] or
 * [Consumer] are hidden from Objective-C, since those interfaces are exported as protocols that Swift closures
 * cannot satisfy. [onSuccess], [onFailure], [unwrapOrElse] and [unwrapErrorOrElse] have function type overloads
 * for Swift instead. The combinators that introduce a new type parameter have none, since Objective-C erases a
 * function's own type parameters to `id`.
 * */
// code is optimized to avoid branching operators (if/else) for better JIT compiler optimizations
@ObjCName("EthersResult", exact = true)
sealed class Result<out T, out E> {
    @ObjCName("EthersResultSuccess", exact = true)
    class Success<out T>(val value: T) : Result<T, Nothing>() {
        @HiddenFromObjC
        override fun <R> fold(
            onSuccess: Transformer<Success<T>, R>,
            onFailure: Transformer<Failure<Nothing>, R>,
        ): R {
            return onSuccess(this)
        }

        override fun toString() = "Success($value)"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Success<*>

            return value == other.value
        }

        override fun hashCode() = value.hashCode()
    }

    @ObjCName("EthersResultFailure", exact = true)
    class Failure<out E>(val error: E) : Result<Nothing, E>() {
        @HiddenFromObjC
        override fun <R> fold(
            onSuccess: Transformer<Success<Nothing>, R>,
            onFailure: Transformer<Failure<E>, R>,
        ): R {
            return onFailure(this)
        }

        override fun toString() = "Failure($error)"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Failure<*>

            return error == other.error
        }

        override fun hashCode() = error.hashCode()
    }

    /**
     * Returns true if [Result] is [Success], false otherwise.
     *
     * **NOTE**: Java bridge for [isSuccess] function. Should not be used from kotlin, use extension function instead
     * since it provides smart casting.
     * */
    @JvmName("isSuccess")
    @ObjCName("isSuccess")
    fun isInstanceOfSuccessJavaBridge(): Boolean = this.isSuccess()

    /**
     * Returns true if [Result] is [Failure], false otherwise.
     *
     * **NOTE**: Java bridge for [isFailure] function. Should not be used from kotlin, use extension function instead
     * since it provides smart casting.
     * */
    @JvmName("isFailure")
    @ObjCName("isFailure")
    fun isInstanceOfFailureJavaBridge(): Boolean = this.isFailure()

    /**
     * Maps a [Result]<[T], [E]> to [Result]<[R], [E]> by applying a function to a [Success] value, leaving a
     * [Failure] value untouched.
     * */
    @HiddenFromObjC
    fun <R> map(mapper: Transformer<T, R>): Result<R, E> = fold({ Success(mapper(it.value)) }, { it })

    /**
     * Maps a [Result]<[T], [E]> to [Result]<[T], [R]> by applying a function to a [Failure], leaving a
     * [Success] value untouched.
     * */
    @HiddenFromObjC
    fun <R> mapError(mapper: Transformer<E, R>): Result<T, R> {
        return fold({ it }, { Failure(mapper(it.error)) })
    }

    /**
     * Call the function with value of [Success], expecting another result, and skipping if [Result] is [Failure].
     * Useful when chaining multiple fallible operations on the result.
     * */
    @HiddenFromObjC
    fun <R> andThen(mapper: Transformer<T, Result<R, @UnsafeVariance E>>): Result<R, E> {
        return fold({ mapper(it.value) }, { it })
    }

    /**
     * Call the function with error of [Failure], expecting another result, and skipping if [Result] is [Success].
     * Useful when chaining multiple fallible operations on the error (e.g. trying to recover from an error).
     * */
    @HiddenFromObjC
    fun <R> orElse(mapper: Transformer<E, Result<@UnsafeVariance T, R>>): Result<T, R> {
        return fold({ it }, { mapper(it.error) })
    }

    /**
     * Unwrap the value if [Result] is [Success], or throw an exception if [Result] is [Failure].
     *
     * A [ThrowableError] error is thrown via [ThrowableError.toException], and a [Throwable] error is thrown
     * directly. Any other error type is not convertible to an exception, so an [IllegalStateException] describing
     * the error is thrown.
     *
     * Only unchecked exceptions are declared, so Java callers are not forced to handle a checked one. On
     * Kotlin/Native, where an undeclared exception reaching Swift aborts the process, a [Throwable] error that is
     * neither a [RuntimeException] nor an [Error] is therefore wrapped in a [RuntimeException] whose cause it is.
     * Swift then always receives a thrown `Error`, with the original reachable through `kotlinException`.
     * */
    @Throws(RuntimeException::class, Error::class)
    fun unwrap(): T = fold({ it.value }) {
        when (val e = it.error) {
            is ThrowableError -> throw e.toException()
            is Throwable -> throwUnwrapped(e)
            else -> throw IllegalStateException("Value is not success: $e")
        }
    }

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
    @HiddenFromObjC
    fun unwrapOrElse(default: Transformer<E, @UnsafeVariance T>): T {
        return fold({ it.value }, { default(it.error) })
    }

    /**
     * Unwrap the value if [Result] is [Success], or return the result of [default] function if [Result] is [Failure].
     *
     * Function type overload of [unwrapOrElse], callable with a Swift closure. Hidden from Java, which uses the
     * [Transformer] overload.
     * */
    @JvmSynthetic
    fun unwrapOrElse(default: (E) -> @UnsafeVariance T): T = when (this) {
        is Success -> value
        is Failure -> default(error)
    }

    /**
     * Unwrap the error if [Result] is [Failure], or throw an exception if [Result] is [Success].
     * */
    @Throws(IllegalStateException::class)
    fun unwrapError(): E = fold({ throw IllegalStateException("Value is not error: ${it.value}") }, { it.error })

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
    @HiddenFromObjC
    fun unwrapErrorOrElse(default: Transformer<T, @UnsafeVariance E>): E {
        return fold({ default(it.value) }, { it.error })
    }

    /**
     * Unwrap the error if [Result] is [Failure], or return the result of [default] function if [Result] is [Success].
     *
     * Function type overload of [unwrapErrorOrElse], callable with a Swift closure. Hidden from Java, which uses the
     * [Transformer] overload.
     * */
    @JvmSynthetic
    fun unwrapErrorOrElse(default: (T) -> @UnsafeVariance E): E = when (this) {
        is Success -> default(value)
        is Failure -> error
    }

    /**
     * Callback called if [Result] is [Success].
     * */
    @HiddenFromObjC
    fun onSuccess(block: Consumer<T>) = fold({ block(it.value) }, {})

    /**
     * Callback called if [Result] is [Success].
     *
     * Function type overload of [onSuccess], callable with a Swift closure. Hidden from Java, which uses the
     * [Consumer] overload.
     * */
    @JvmSynthetic
    fun onSuccess(block: (T) -> Unit) {
        if (this is Success) block(value)
    }

    /**
     * Callback called if [Result] is [Failure].
     * */
    @HiddenFromObjC
    fun onFailure(block: Consumer<E>) = fold({}, { block(it.error) })

    /**
     * Callback called if [Result] is [Failure].
     *
     * Function type overload of [onFailure], callable with a Swift closure. Hidden from Java, which uses the
     * [Consumer] overload.
     * */
    @JvmSynthetic
    fun onFailure(block: (E) -> Unit) {
        if (this is Failure) block(error)
    }

    /**
     * Call [onSuccess] if [Result] is [Success] or [onFailure] if [Result] is [Failure], returning the result of
     * the called function.
     * */
    @HiddenFromObjC
    abstract fun <R> fold(
        onSuccess: Transformer<Success<T>, R>,
        onFailure: Transformer<Failure<E>, R>,
    ): R

    /**
     * Custom functional interface for better java interop with kotlin lambdas. Prevents the java caller having to
     * explicitly return `Unit.INSTANCE`.
     * */
    @HiddenFromObjC
    fun interface Consumer<in T> {
        operator fun invoke(t: T)
    }

    /**
     * Custom functional interface for better java interop with kotlin lambdas. Prevents the java caller having to
     * explicitly return `Unit.INSTANCE`.
     * */
    @HiddenFromObjC
    fun interface Transformer<in T, out R> {
        operator fun invoke(t: T): R
    }

    companion object {
        /**
         * Return a [Result.Success] with the given [value].
         * */
        @JvmStatic
        fun <T, E> success(value: T): Result<T, E> = Success(value)

        /**
         * Return a [Result.Failure] with the given [error].
         * */
        @JvmStatic
        fun <T, E> failure(error: E): Result<T, E> = Failure(error)
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
// hidden from Objective-C, where it would clash with the member exported under the same name
@HiddenFromObjC
@OptIn(ExperimentalContracts::class)
fun <T, E> Result<T, E>.isSuccess(): Boolean {
    contract {
        returns(true) implies (this@isSuccess is Success<T>)
        returns(false) implies (this@isSuccess is Failure<E>)
    }
    return this is Success<T>
}

/**
 * Returns true if [Result] is [Failure], false otherwise.
 * */
@HiddenFromObjC
@OptIn(ExperimentalContracts::class)
fun <T, E> Result<T, E>.isFailure(): Boolean {
    contract {
        returns(false) implies (this@isFailure is Success<T>)
        returns(true) implies (this@isFailure is Failure<E>)
    }
    return this is Failure<E>
}

/**
 * Returns true if [Result] is either **null** or [Failure], false otherwise.
 * */
@OptIn(ExperimentalContracts::class)
fun <T, E> Result<T, E>?.isNullOrFailure(): Boolean {
    contract {
        returns(true) implies (this@isNullOrFailure is Failure<E> || this@isNullOrFailure == null)
        returns(false) implies (this@isNullOrFailure is Success<T>)
    }
    return this == null || this is Failure<E>
}

/**
 * Return a [Result.Success] with the given [value].
 * */
fun <T> success(value: T): Result<T, Nothing> = Result.success(value)

/**
 * Return a [Result.Failure] with the given [error].
 * */
fun <E> failure(error: E) = Result.failure<Nothing, E>(error)

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
// a Swift block cannot return Nothing, so a Failure would run past it; Kotlin-only
@HiddenFromObjC
@OptIn(ExperimentalContracts::class)
inline fun <R, T : R, E> Result<T, E>.unwrapOrReturn(onFailure: (E) -> Nothing): R {
    contract { callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE) }

    if (isSuccess()) {
        return this.value
    } else {
        onFailure(this.unwrapError())
    }
}

// ---------------------------------------------------- //
// ------------- kotlin.Result Extensions ------------- //
// ---------------------------------------------------- //

@OptIn(ExperimentalContracts::class)
// value returned from mapper will be boxed
inline fun <T, R> kotlin.Result<T>.andThen(mapper: (T) -> kotlin.Result<R>): kotlin.Result<R> {
    contract { callsInPlace(mapper, InvocationKind.AT_MOST_ONCE) }

    return fold({ mapper(it) }, { kotlin.Result.failure(it) })
}

@OptIn(ExperimentalContracts::class)
// value returned from mapper will be boxed
inline fun <T, R> kotlin.Result<T>.andThenCatching(mapper: (T) -> kotlin.Result<R>): kotlin.Result<R> {
    contract { callsInPlace(mapper, InvocationKind.AT_MOST_ONCE) }

    return fold({ runCatching { mapper(it).getOrThrow() } }, { kotlin.Result.failure(it) })
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
// a Swift block cannot return Nothing, so a Failure would run past it; Kotlin-only
@HiddenFromObjC
@OptIn(ExperimentalContracts::class)
inline fun <R, T : R> kotlin.Result<T>.unwrapOrReturn(onFailure: (Throwable) -> Nothing): R {
    contract { callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE) }

    if (isSuccess) {
        return this.getOrThrow()
    }
    onFailure(this.exceptionOrNull()!!)
}

/**
 * Transform [kotlin.Result] into [Result], holding the thrown exception as the error if it holds a failure.
 * */
@Suppress("NOTHING_TO_INLINE")
inline fun <T> kotlin.Result<T>.toResult(): Result<T, Throwable> {
    if (isSuccess) {
        return success(this.getOrThrow())
    }
    return failure(this.exceptionOrNull()!!)
}
