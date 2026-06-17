package io.ethers.abi.error

import io.ethers.abi.AbiCodec
import io.ethers.abi.AbiFunction
import io.ethers.abi.AbiType
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.types.Bytes
import io.ethers.providers.RpcError
import io.github.artificialpb.bignum.BigInteger

/**
 * Error returned from a contract call.
 * */
sealed class ContractError : Result.Error {
    companion object {
        /**
         * Try to get either [PanicError], [RevertError], or [CustomContractError] from provided [data].
         *
         * @return the decoded error, or null if [data] cannot be decoded into any of the supported errors.
         * */
        @JvmStatic
        fun getOrNull(data: Bytes): ContractError? {
            PanicError.getOrNull(data)?.let { return it }
            RevertError.getOrNull(data)?.let { return it }
            CustomErrorRegistry.getOrNull(data)?.let { return it }
            return null
        }

        /**
         * Try to get either [PanicError], [RevertError], or [CustomContractError] from provided [data].
         *
         * @return the decoded error, or a [ContractErrorDecodingError] if [data] cannot be decoded.
         * */
        @JvmStatic
        fun tryGet(data: Bytes): Result<ContractError, ContractErrorDecodingError> {
            when (val panic = PanicError.tryGet(data)) {
                is Result.Success -> return success(panic.value)
                is Result.Failure -> {
                    if (panic.error !is ContractErrorDecodingError.NoMatchingError) {
                        return failure(panic.error)
                    }
                }
            }

            when (val revert = RevertError.tryGet(data)) {
                is Result.Success -> return success(revert.value)
                is Result.Failure -> {
                    if (revert.error !is ContractErrorDecodingError.NoMatchingError) {
                        return failure(revert.error)
                    }
                }
            }

            when (val customError = CustomErrorRegistry.tryGet(data)) {
                is Result.Success -> return success(customError.value)
                is Result.Failure -> {
                    if (customError.error !is ContractErrorDecodingError.NoMatchingError) {
                        return failure(customError.error)
                    }
                }
            }

            return failure(ContractErrorDecodingError.NoMatchingError(data, emptyList()))
        }
    }
}

sealed class ContractErrorDecodingError(
    open val data: Bytes,
    open val msg: String,
    open val cause: Exception? = null,
) : Result.Error {
    data class NoMatchingError(
        override val data: Bytes,
        val expectedErrors: List<AbiFunction>,
    ) : ContractErrorDecodingError(data, "Data does not match any expected contract error")

    data class MalformedError(
        override val data: Bytes,
        val expectedError: AbiFunction?,
        override val cause: Exception,
    ) : ContractErrorDecodingError(data, "Unable to decode contract error", cause)

    override fun doThrow(): Nothing {
        throw RuntimeException(msg, cause)
    }
}

/**
 * Error returned when a contract call reverts with panic error. This happens if `assert` call fails, or can be raised
 * by compiler in certain cases (e.g. division by zero, see [PanicError.Kind] for more info).
 *
 * See: [docs](https://docs.soliditylang.org/en/latest/control-structures.html#panic-via-assert-and-error-via-require)
 * */
data class PanicError(val kind: Kind) : ContractError() {
    enum class Kind(code: Int) {
        /**
         * Generic compiler inserted panic.
         * */
        GENERIC(0x0),

        /**
         * `assert` call evaluated to `false`
         * */
        ASSERT_FAILED(0x1),

        /**
         * Arithmetic operation resulted in underflow or overflow outside an `unchecked` block.
         * */
        UNDERFLOW_OVERFLOW(0x11),

        /**
         * Division or modulo by zero.
         * */
        DIVIDE_BY_ZERO(0x12),

        /**
         * Value that is too big or negative is converted into an enum type.
         * */
        ENUM_CONVERSION_FAILED(0x21),

        /**
         * Access of incorrectly encoded storage byte array.
         * */
        STORAGE_ENCODING_ERROR(0x22),

        /**
         * Calling `pop()` on an empty array.
         * */
        POP_ON_EMPTY_ARRAY(0x31),

        /**
         * Accessing an array, bytesN or an array slice at an out-of-bounds or negative index.
         * */
        INDEX_OUT_OF_BOUNDS(0x32),

        /**
         * Allocated too much memory or created an array that is too large.
         * */
        OUT_OF_MEMORY(0x41),

        /**
         * Calling a zero-initialized variable of internal function type.
         * */
        INVALID_INTERNAL_FUNCTION(0x51),
        ;

        val code = code.toBigInteger()
    }

    companion object {
        @JvmField
        val FUNCTION = AbiFunction("Panic", listOf(AbiType.UInt(256)), emptyList())

        /**
         * Try to decode [PanicError] from [data].
         *
         * @return the decoded [PanicError], or null if [data] cannot be decoded into [PanicError].
         * */
        @JvmStatic
        fun getOrNull(data: Bytes): PanicError? {
            if (data.size < 4) return null
            if (!data.startsWith(FUNCTION.selector)) return null

            val decoded = try {
                AbiCodec.decodeWithPrefix(FUNCTION.selector.size, FUNCTION.inputs, data.asByteArray())
            } catch (_: Exception) {
                return null
            }
            val errorCode = decoded[0] as BigInteger

            for (i in Kind.entries.indices) {
                val kind = Kind.entries[i]
                if (kind.code == errorCode) {
                    return PanicError(kind)
                }
            }

            return null
        }

        /**
         * Try to decode [PanicError] from [data].
         *
         * @return the decoded [PanicError], or a [ContractErrorDecodingError] if [data] cannot be decoded into [PanicError].
         * */
        @JvmStatic
        fun tryGet(data: Bytes): Result<PanicError, ContractErrorDecodingError> {
            if (data.size < 4) return failure(ContractErrorDecodingError.NoMatchingError(data, listOf(FUNCTION)))
            if (!data.startsWith(FUNCTION.selector)) return failure(ContractErrorDecodingError.NoMatchingError(data, listOf(FUNCTION)))

            val decoded = try {
                AbiCodec.decodeWithPrefix(FUNCTION.selector.size, FUNCTION.inputs, data.asByteArray())
            } catch (e: Exception) {
                return failure(ContractErrorDecodingError.MalformedError(data, FUNCTION, e))
            }
            val errorCode = decoded[0] as BigInteger

            for (i in Kind.entries.indices) {
                val kind = Kind.entries[i]
                if (kind.code == errorCode) {
                    return success(PanicError(kind))
                }
            }

            return failure(ContractErrorDecodingError.NoMatchingError(data, listOf(FUNCTION)))
        }
    }
}

/**
 * Error returned when a contract call reverts with revert message.
 * */
data class RevertError(val reason: String) : ContractError() {
    companion object {
        @JvmField
        val FUNCTION = AbiFunction("Error", listOf(AbiType.String), emptyList())

        /**
         * Try to decode [RevertError] from [data].
         *
         * @return the decoded [RevertError], or null if [data] cannot be decoded into [RevertError].
         * */
        @JvmStatic
        fun getOrNull(data: Bytes): RevertError? {
            if (data.size < 4) return null
            if (!data.startsWith(FUNCTION.selector)) return null

            val decoded = try {
                AbiCodec.decodeWithPrefix(FUNCTION.selector.size, FUNCTION.inputs, data.asByteArray())
            } catch (_: Exception) {
                return null
            }
            return RevertError(decoded[0] as String)
        }

        /**
         * Try to decode [RevertError] from [data].
         *
         * @return the decoded [RevertError], or a [ContractErrorDecodingError] if [data] cannot be decoded into [RevertError].
         * */
        @JvmStatic
        fun tryGet(data: Bytes): Result<RevertError, ContractErrorDecodingError> {
            if (data.size < 4) return failure(ContractErrorDecodingError.NoMatchingError(data, listOf(FUNCTION)))
            if (!data.startsWith(FUNCTION.selector)) return failure(ContractErrorDecodingError.NoMatchingError(data, listOf(FUNCTION)))
            val decoded = try {
                AbiCodec.decodeWithPrefix(FUNCTION.selector.size, FUNCTION.inputs, data.asByteArray())
            } catch (e: Exception) {
                return failure(ContractErrorDecodingError.MalformedError(data, FUNCTION, e))
            }
            return success(RevertError(decoded[0] as String))
        }
    }
}

/**
 * Error returned when a contract call reverts due to:
 * - calling a function that does not exist,
 * - the function is called with incorrect arguments,
 * - the function returns unexpected/incorrect response.
 *
 * Use ```traceCall``` with ```CallTracer(onlyTopCall = false)``` to debug which call fails.
 * */
data object ExecutionRevertedError : ContractError() {
    private val MSG = """
        Execution reverted. This happens when calling a function that does not exist, the function is called
        with incorrect arguments, or the function returns unexpected/incorrect response. Use "traceCall" with
        "CallTracer(onlyTopCall = false)" to debug which call fails.
    """.trimIndent().replace("\n", " ")

    override fun doThrow(): Nothing {
        throw RuntimeException(MSG)
    }
}

/**
 * Error returned when a contract call reverts with custom error.
 * */
abstract class CustomContractError : ContractError()

/**
 * Class for decoding an instance of [CustomContractError].
 * */
interface CustomErrorFactory<T : CustomContractError> {
    val abi: AbiFunction

    fun decode(data: Bytes): T? = decodeOrNull(data)

    fun decodeOrNull(data: Bytes): T? {
        if (data.size < 4) return null
        if (!data.startsWith(abi.selector)) return null

        return try {
            decode(abi.decodeCall(data))
        } catch (_: Exception) {
            null
        }
    }

    fun tryDecode(data: Bytes): Result<T, ContractErrorDecodingError> {
        if (data.size < 4) return failure(ContractErrorDecodingError.NoMatchingError(data, listOf(abi)))
        if (!data.startsWith(abi.selector)) return failure(ContractErrorDecodingError.NoMatchingError(data, listOf(abi)))

        return try {
            success(decode(abi.decodeCall(data)))
        } catch (e: Exception) {
            failure(ContractErrorDecodingError.MalformedError(data, abi, e))
        }
    }

    fun decode(data: List<Any>): T
}

fun <T : CustomContractError> List<CustomErrorFactory<out T>>.decode(error: Bytes): T? = decodeOrNull(error)

fun <T : CustomContractError> List<CustomErrorFactory<out T>>.decodeOrNull(error: Bytes): T? {
    for (i in indices) {
        return this[i].decodeOrNull(error) ?: continue
    }
    return null
}

fun <T : CustomContractError> List<CustomErrorFactory<out T>>.tryDecode(error: Bytes): Result<T, ContractErrorDecodingError> {
    for (i in indices) {
        when (val decoded = this[i].tryDecode(error)) {
            is Result.Success -> return success(decoded.value)
            is Result.Failure -> {
                if (decoded.error !is ContractErrorDecodingError.NoMatchingError) {
                    return failure(decoded.error)
                }
            }
        }
    }

    return failure(ContractErrorDecodingError.NoMatchingError(error, map { it.abi }))
}

/**
 * Error returned when decoding the result of a contract call fails.
 * */
data class DecodingError(
    val result: Bytes,
    val message: String,
    val exception: Exception?,
) : ContractError() {
    override fun doThrow(): Nothing {
        throw RuntimeException(message, exception)
    }
}

/**
 * Error returned when a contract deploy fails. This happens when no response is returned by the RPC when deploying a
 * contract.
 * */
sealed class DeployError(val msg: String) : ContractError() {
    data object NoBytecode : DeployError("No bytecode returned by the RPC")
}

/**
 * Error returned when a contract RPC call fails.
 * */
data class ContractRpcError(val cause: RpcError) : ContractError() {
    override fun doThrow(): Nothing {
        throw RuntimeException("RPC error while calling contract function: $cause")
    }
}
