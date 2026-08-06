package io.ethers.core

import kotlin.reflect.KClass

/**
 * Type used by errors that can be converted to thrown exceptions.
 * */
interface ThrowableError {
    /**
     * Convert this error to an exception. If the implementation wraps another error or exception, this method should
     * return an exception with the wrapped value as its cause.
     * */
    fun toException(): RuntimeException {
        return RuntimeException(toString())
    }

    /**
     * Cast this error to a given class or return null if it is not of type [T].
     * Useful for accessing details of specific error subclass.
     * */
    @Suppress("UNCHECKED_CAST")
    fun <T : ThrowableError> asTypeOrNull(type: KClass<T>): T? {
        return if (type.isInstance(this)) this as T else null
    }
}

/**
 * Throw this error as an exception.
 * */
fun ThrowableError.doThrow(): Nothing {
    throw toException()
}

/**
 * Cast [ThrowableError] to [T] or return null if error is not of type [T].
 * Useful for accessing details of specific error subclass.
 * */
inline fun <reified T : ThrowableError> ThrowableError.asTypeOrNull(): T? {
    return asTypeOrNull(T::class)
}

/**
 * Unwrap the value if [Result] is [Result.Success], or throw the error if [Result] is [Result.Failure].
 * */
fun <T, E : ThrowableError> Result<T, E>.unwrap(): T {
    return fold({ it.value }, { it.error.doThrow() })
}

/**
 * An error that wraps an exception.
 * */
data class ExceptionalError(val cause: Throwable) : ThrowableError {
    override fun toException(): RuntimeException {
        return RuntimeException("Exceptional execution", cause)
    }
}
