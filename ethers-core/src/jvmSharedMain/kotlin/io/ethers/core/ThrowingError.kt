package io.ethers.core

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Type used by errors that can be converted to thrown exceptions.
 */
interface ThrowingError {
    /**
     * Convert this error to an exception. If the implementation wraps another error or exception, this method should
     * return an exception with the wrapped value as its cause.
     */
    fun toException(): RuntimeException {
        return RuntimeException(toString())
    }

    /**
     * Cast this error to a given class or return null if it is not of type [T].
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : ThrowingError> asTypeOrNull(type: Class<T>): T? {
        return if (type.isAssignableFrom(this::class.java)) this as T else null
    }
}

/**
 * Throw this error as an exception.
 */
fun ThrowingError.doThrow(): Nothing {
    throw toException()
}

/**
 * Cast [ThrowingError] to [T] or return null if error is not of type [T].
 */
inline fun <reified T : ThrowingError> ThrowingError.asTypeOrNull(): T? {
    return asTypeOrNull(T::class.java)
}

/**
 * Unwrap the value if [Result] is ok, or throw the error if [Result] is err.
 */
fun <T, E : ThrowingError> Result<T, E>.unwrap(): T {
    return fold({ it }, { it.doThrow() })
}

/**
 * Unwrap the value if [Result] is ok, or throw the [Throwable] error if [Result] is err.
 */
@JvmName("unwrapThrowable")
fun <T, E : Throwable> Result<T, E>.unwrap(): T {
    return fold({ it }, { throw it })
}

/**
 * Unwrap the value if [Result] is ok, or call [onFailure], which must throw or return from the enclosing function.
 */
@OptIn(ExperimentalContracts::class)
inline fun <R, T : R, E> Result<T, E>.unwrapOrReturn(onFailure: (E) -> Nothing): R {
    contract { callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE) }

    return fold({ it }, { onFailure(it) })
}

/**
 * An error that wraps an exception.
 */
data class ExceptionalError(val cause: Throwable) : ThrowingError {
    override fun toException(): RuntimeException {
        return RuntimeException("Exceptional execution", cause)
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
 * Unwrap the value if [kotlin.Result] is success, or call [onFailure], which must throw or return from the enclosing
 * function.
 */
@OptIn(ExperimentalContracts::class)
inline fun <R, T : R> kotlin.Result<T>.unwrapOrReturn(onFailure: (ExceptionalError) -> Nothing): R {
    contract { callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE) }

    if (isSuccess) {
        return this.getOrThrow()
    }
    onFailure(ExceptionalError(this.exceptionOrNull()!!))
}
