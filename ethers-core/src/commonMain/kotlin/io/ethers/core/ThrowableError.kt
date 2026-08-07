package io.ethers.core

import kotlin.reflect.KClass

/**
 * Type used by errors that can be converted to thrown exceptions.
 *
 * Implementations should provide a [message] and, if they wrap another failure, a [cause]. The exception itself is
 * built by [toException], which retains the error so no detail is lost when crossing from [Result] into exceptions.
 * Overriding [toException] is only needed for exceptions that cannot be described by a message and a cause.
 * */
interface ThrowableError {
    /**
     * Description of this error, used as the message of [toException]. If null, the error's [toString] is used
     * instead, which for a data class already names the type and all of its fields.
     * */
    val message: String?
        get() = null

    /**
     * The failure that caused this error, chained into [toException]. An error wrapping another [ThrowableError]
     * should convert it with [toException] here, while still exposing it under its own type.
     * */
    val cause: Throwable?
        get() = null

    /**
     * Convert this error to an exception that retains it.
     * */
    fun toException(): RuntimeException {
        return Exception(this)
    }

    /**
     * Cast this error to a given class or return null if it is not of type [T].
     * Useful for accessing details of specific error subclass.
     * */
    @Suppress("UNCHECKED_CAST")
    fun <T : ThrowableError> asTypeOrNull(type: KClass<T>): T? {
        return if (type.isInstance(this)) this as T else null
    }

    /**
     * Exception thrown for a [ThrowableError]. It keeps a reference to the [error] it was created from, so the
     * original, fully typed error remains reachable from a `catch` block:
     *
     * ```kotlin
     * try {
     *     result.unwrap()
     * } catch (e: ThrowableError.Exception) {
     *     val decodingError = e.error.asTypeOrNull<DecodingError>()
     * }
     * ```
     *
     * **NOTE**: this shadows [kotlin.Exception] only where it is referenced as `ThrowableError.Exception` or
     * imported directly. Implementations of [ThrowableError] are unaffected, since Kotlin does not bring nested
     * classifiers of a supertype into the implementing class's scope.
     * */
    class Exception(
        val error: ThrowableError,
        private val description: String? = error.message,
    ) : RuntimeException(description ?: error.toString(), error.cause) {
        override fun toString(): String {
            // when there is no description, the error's toString already names the type
            val description = description ?: return error.toString()
            val name = error::class.simpleName ?: return description
            return "$name: $description"
        }
    }
}

/**
 * Cast [ThrowableError] to [T] or return null if error is not of type [T].
 * Useful for accessing details of specific error subclass.
 * */
inline fun <reified T : ThrowableError> ThrowableError.asTypeOrNull(): T? {
    return asTypeOrNull(T::class)
}
