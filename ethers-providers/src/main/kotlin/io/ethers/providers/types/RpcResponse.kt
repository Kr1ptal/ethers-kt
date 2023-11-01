package io.ethers.providers.types

import java.util.function.Function

class RpcResponse<T> private constructor(
    @get:JvmName("result")
    val result: T?,
    @get:JvmName("error")
    val error: Error?,
) {
    @get:JvmName("isResult")
    val isResult get() = result != null

    @get:JvmName("isError")
    val isError get() = error != null

    @JvmSynthetic
    operator fun component1() = result

    @JvmSynthetic
    operator fun component2() = error

    /**
     * If this [RpcResponse] is an error, cast its type [T] to type [R] to save an allocation of an instance with
     * different type. This cast is safe because the result is null.
     *
     * Call to this function should always be preceded by a check of [isError] property. In case this [RpcResponse]
     * is not an error, an [IllegalStateException] is thrown.
     * */
    fun <R> propagateError(): RpcResponse<R> {
        if (!isError) {
            throw IllegalStateException("Cannot propagate non-error response")
        }

        // result is null, safe cast
        @Suppress("UNCHECKED_CAST")
        return this as RpcResponse<R>
    }

    /**
     * Return [result] if the call was successful or throw [error] if the call failed.
     * */
    @Suppress("IfThenToSafeAccess")
    fun resultOrThrow(): T {
        if (error != null) {
            error.doThrow()
        }
        return result!!
    }

    /**
     * Return [result] if the call was successful or return [default] if the call failed.
     * */
    fun resultOr(default: T): T {
        if (error != null) {
            return default
        }
        return result!!
    }

    /**
     * Remap the RPC [result] if the call was successful.
     */
    fun <R> map(mapper: Function<T, R>): RpcResponse<R> {
        if (isError) {
            return propagateError()
        }
        return result(result!!.let(mapper::apply))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RpcResponse<*>

        if (result != other.result) return false
        if (error != other.error) return false

        return true
    }

    override fun hashCode(): Int {
        var result1 = result?.hashCode() ?: 0
        result1 = 31 * result1 + (error?.hashCode() ?: 0)
        return result1
    }

    override fun toString(): String {
        return "RpcResponse(result=$result, error=$error)"
    }

    /**
     * Base class for [RpcResponse] errors.
     */
    abstract class Error {
        /**
         * Throw this [Error] as an exception. If implementation wraps an exception, this method should be overridden
         * to provide an accurate stacktrace.
         * */
        open fun doThrow() {
            throw RuntimeException(this.toString())
        }

        /**
         * Cast [Error] to [T] or return null if error is not of type [T].
         * Useful for accessing details of specific error subclass.
         */
        inline fun <reified T : Error> asTypeOrNull(): T? {
            return asTypeOrNull(T::class.java)
        }

        /**
         * Cast [Error] to a given class or return null if error is not of type [T].
         * Useful for accessing details of specific error subclass.
         */
        fun <T : Error> asTypeOrNull(type: Class<T>): T? {
            return if (type.isAssignableFrom(this::class.java)) this as T else null
        }
    }

    /**
     * Internal JSON-RPC error, returned when the RPC call fails.
     */
    data class RpcError(val code: Int, val message: String, val data: String?) : Error()

    companion object {
        @JvmStatic
        fun <T> result(result: T): RpcResponse<T> = RpcResponse(result, null)

        @JvmStatic
        fun <T> error(error: Error): RpcResponse<T> = RpcResponse(null, error)
    }
}
