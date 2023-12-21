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
    data class RpcError(val code: Int, val message: String, val data: String?) : Error() {
        /**
         * Invalid JSON was received by the server. An error occurred on the server while parsing the JSON text.
         * */
        val isParseError get() = code == CODE_PARSE_ERROR

        /**
         * The JSON sent is not a valid Request object.
         * */
        val isInvalidRequest get() = code == CODE_INVALID_REQUEST

        /**
         * The method does not exist / is not available.
         * */
        val isMethodNotFound get() = code == CODE_METHOD_NOT_FOUND

        /**
         * Invalid method parameter(s).
         * */
        val isInvalidParams get() = code == CODE_INVALID_PARAMS

        /**
         * Internal JSON-RPC error.
         * */
        val isInternalError get() = code == CODE_INTERNAL_ERROR

        /**
         * Server error. Contains implementation-defined server-errors.
         * */
        val isServerError get() = code in CODE_SERVER_ERROR

        /**
         * Action is not authorized, e.g. sending from a locked account.
         * */
        val isUnauthorized get() = code == CODE_UNAUTHORIZED

        /**
         * Action is not allowed, e.g. preventing an action, while another dependant action is being processed.
         * */
        val isActionNotAllowed get() = code == CODE_ACTION_NOT_ALLOWED

        /**
         * Will contain a subset of custom errors in the data field. See below.
         * */
        val isExecutionError get() = code == CODE_EXECUTION_ERROR

        companion object {
            // Standard JSON-RPC errors
            const val CODE_PARSE_ERROR = -32700
            const val CODE_INVALID_REQUEST = -32600
            const val CODE_METHOD_NOT_FOUND = -32601
            const val CODE_INVALID_PARAMS = -32602
            const val CODE_INTERNAL_ERROR = -32603

            @JvmField
            val CODE_SERVER_ERROR = -32099..-32000

            // Custom errors
            const val CODE_UNAUTHORIZED = 1
            const val CODE_ACTION_NOT_ALLOWED = 2
            const val CODE_EXECUTION_ERROR = 3
        }
    }

    companion object {
        @JvmStatic
        fun <T> result(result: T): RpcResponse<T> = RpcResponse(result, null)

        @JvmStatic
        fun <T> error(error: Error): RpcResponse<T> = RpcResponse(null, error)
    }
}
