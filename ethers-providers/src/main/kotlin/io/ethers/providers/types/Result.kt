package io.ethers.providers.types

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

// code is optimized to avoid branching operators (if/else) for better JIT compiler optimizations
sealed class Result<T> {
    class Success<T>(val value: T) : Result<T>() {
        override fun <R> fold(onSuccess: (Success<T>) -> R, onFailure: (Failure<T>) -> R): R {
            return onSuccess(this)
        }

        override fun toString() = "Success($value)"
    }

    class Failure<T>(val error: Error) : Result<T>() {
        override fun <R> fold(onSuccess: (Success<T>) -> R, onFailure: (Failure<T>) -> R): R {
            return onFailure(this)
        }

        override fun toString() = "Failure($error)"
    }

    @OptIn(ExperimentalContracts::class)
    fun isSuccess(): Boolean {
        contract {
            returns(true) implies (this@Result is Success<T>)
            returns(false) implies (this@Result is Failure<T>)
        }
        return this is Success<T>
    }

    @OptIn(ExperimentalContracts::class)
    fun isFailure(): Boolean {
        contract {
            returns(false) implies (this@Result is Success<T>)
            returns(true) implies (this@Result is Failure<T>)
        }
        return this is Failure<T>
    }

    @Suppress("UNCHECKED_CAST")
    fun <R> map(mapper: (T) -> R) = fold({ Success(mapper(it.value)) }, { it as Failure<R> })
    fun mapError(mapper: (Error) -> Error) = fold({ it }, { Failure(mapper(it.error)) })

    inline fun <reified E: Error> mapTypedError(crossinline mapper: (E) -> Error): Result<T> {
        return mapError { it.asTypeOrNull<E>()?.let(mapper) ?: it }
    }

    @Suppress("UNCHECKED_CAST")
    fun <R> andThen(mapper: (T) -> Result<R>) = fold({ mapper(it.value) }, { it as Failure<R> })
    fun orElse(mapper: (Error) -> Result<T>) = fold({ it }, { mapper(it.error) })

    fun unwrap(): T = fold({ it.value }, { it.error.doThrow() })
    fun unwrapElse(default: T): T = fold({ it.value }, { default })
    fun unwrapOrElse(default: (Error) -> T): T = fold({ it.value }, { default(it.error) })

    fun onSuccess(block: (T) -> Unit) = fold({ block(it.value) }, {})
    fun onFailure(block: (Error) -> Unit) = fold({}, { block(it.error) })

    protected abstract fun <R> fold(
        onSuccess: (Success<T>) -> R,
        onFailure: (Failure<T>) -> R
    ): R
}

fun <T> success(value: T): Result<T> = Result.Success(value)
fun <T> failure(error: Error): Result<T> = Result.Failure(error)

fun <R> catching(block: () -> R): Result<R> {
    return try {
        success(block())
    } catch (e: Exception) {
        failure(ExceptionError(e))
    }
}

private data class ExceptionError(val exception: Exception) : Error {
    override fun doThrow(): Nothing {
        throw RuntimeException("Exceptional execution", exception)
    }
}

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

fun main() {
    val response = RpcResponse.result(1)

    val result = success(1)
        .map { it.toString() }
        .andThen { catching { it.toInt() } }
}
