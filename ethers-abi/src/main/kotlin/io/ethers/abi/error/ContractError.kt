package io.ethers.abi.error

import io.ethers.abi.AbiCodec
import io.ethers.abi.AbiFunction
import io.ethers.abi.AbiType
import io.ethers.core.Result
import io.ethers.core.types.Bytes
import io.ethers.providers.RpcError
import java.math.BigInteger
import java.util.Arrays

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
            val panic = PanicError.getOrNull(data)
            if (panic != null) {
                return panic
            }
            val revert = RevertError.getOrNull(data)
            if (revert != null) {
                return revert
            }
            val customError = CustomErrorRegistry.getOrNull(data)
            if (customError != null) {
                return customError
            }
            return null
        }
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
            if (!Arrays.equals(data.asByteArray(), 0, 4, FUNCTION.selector.asByteArray(), 0, 4)) return null

            val decoded = AbiCodec.decodeWithPrefix(FUNCTION.selector.size, FUNCTION.inputs, data.asByteArray())
            val errorCode = decoded[0] as BigInteger

            for (i in Kind.entries.indices) {
                val kind = Kind.entries[i]
                if (kind.code == errorCode) {
                    return PanicError(kind)
                }
            }

            return null
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
            if (!Arrays.equals(data.asByteArray(), 0, 4, FUNCTION.selector.asByteArray(), 0, 4)) return null
            val decoded = AbiCodec.decodeWithPrefix(FUNCTION.selector.size, FUNCTION.inputs, data.asByteArray())
            return RevertError(decoded[0] as String)
        }
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

    fun decode(data: Bytes): T? {
        if (data.size < 4) return null
        if (!Arrays.equals(data.asByteArray(), 0, 4, abi.selector.asByteArray(), 0, 4)) return null

        return decode(abi.decodeCall(data))
    }

    fun decode(data: Array<out Any>): T
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
