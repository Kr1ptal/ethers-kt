package io.ethers.aa.errors

import io.ethers.core.ThrowableError
import io.ethers.providers.RpcError
import kotlin.jvm.JvmStatic

/**
 * EntryPoint failure codes, the `AAxx` prefix of `FailedOp` revert reasons. Bundlers include them in the message
 * of a rejected `eth_sendUserOperation` or `eth_estimateUserOperationGas`.
 *
 * The first digit tells which entity failed: `AA1x` account creation, `AA2x` the account, `AA3x` the paymaster,
 * `AA4x` verification gas, `AA5x` post-operation, `AA9x` the bundle itself.
 */
enum class EntryPointErrorCode(val code: String, val description: String) {
    SENDER_ALREADY_CONSTRUCTED("AA10", "sender already constructed"),
    INIT_CODE_FAILED("AA13", "initCode failed or ran out of gas"),
    INIT_CODE_MUST_RETURN_SENDER("AA14", "initCode must return sender"),
    INIT_CODE_MUST_CREATE_SENDER("AA15", "initCode must create sender"),
    ACCOUNT_NOT_DEPLOYED("AA20", "account not deployed"),
    INSUFFICIENT_PREFUND("AA21", "account didn't pay prefund"),
    EXPIRED_OR_NOT_DUE("AA22", "account signature expired or not due"),
    ACCOUNT_VALIDATION_REVERTED("AA23", "account validation reverted"),
    SIGNATURE_ERROR("AA24", "account signature error"),
    INVALID_NONCE("AA25", "invalid account nonce"),
    OVER_VERIFICATION_GAS_LIMIT("AA26", "over verificationGasLimit"),
    OUTSIDE_VALID_BLOCK_RANGE("AA27", "account outside valid block range"),
    PAYMASTER_NOT_DEPLOYED("AA30", "paymaster not deployed"),
    PAYMASTER_DEPOSIT_TOO_LOW("AA31", "paymaster deposit too low"),
    PAYMASTER_EXPIRED_OR_NOT_DUE("AA32", "paymaster expired or not due"),
    PAYMASTER_VALIDATION_REVERTED("AA33", "paymaster validation reverted"),
    PAYMASTER_SIGNATURE_ERROR("AA34", "paymaster signature error"),
    MALFORMED_PAYMASTER_DATA("AA35", "malformed paymaster data"),
    OVER_PAYMASTER_VERIFICATION_GAS_LIMIT("AA36", "over paymasterVerificationGasLimit"),
    PAYMASTER_OUTSIDE_VALID_BLOCK_RANGE("AA37", "paymaster outside valid block range"),
    VERIFICATION_GAS_LIMIT_EXCEEDED("AA40", "over verificationGasLimit"),
    VERIFICATION_GAS_LIMIT_TOO_LOW("AA41", "too little verificationGas"),
    POST_OP_REVERTED("AA50", "paymaster postOp reverted"),
    PREFUND_BELOW_ACTUAL_GAS_COST("AA51", "prefund below actualGasCost"),
    INVALID_BENEFICIARY("AA90", "invalid beneficiary"),
    FAILED_SEND_TO_BENEFICIARY("AA91", "failed send to beneficiary"),
    INTERNAL_CALL_ONLY("AA92", "internal call only"),
    INVALID_PAYMASTER_AND_DATA("AA93", "invalid paymasterAndData"),
    GAS_VALUES_OVERFLOW("AA94", "gas values overflow"),
    OUT_OF_GAS("AA95", "handleOps ran out of gas"),
    INVALID_AGGREGATOR("AA96", "invalid aggregator"),
    SHOULD_CALL_FROM_ENTRY_POINT("AA97", "should call from EntryPoint"),
    INVALID_PAYMASTER("AA98", "invalid paymaster"),
    INIT_CODE_TOO_SMALL("AA99", "initCode too small"),
    ;

    companion object {
        private val CODE_REGEX = Regex("\\bAA\\d{2}\\b")

        /**
         * Return the code with the given `AAxx` [code], or null if unknown.
         */
        @JvmStatic
        fun fromCode(code: String): EntryPointErrorCode? = entries.firstOrNull { it.code == code }

        /**
         * Find the first `AAxx` code in [message], or return null if there is none or it is unknown.
         */
        @JvmStatic
        fun parse(message: String): EntryPointErrorCode? {
            val match = CODE_REGEX.find(message) ?: return null
            return fromCode(match.value)
        }
    }
}

/**
 * JSON-RPC error codes that ERC-7769 assigns to bundler rejections.
 */
enum class BundlerErrorCode(val code: Int, val description: String) {
    INVALID_FIELDS(-32602, "invalid user operation fields"),
    SIMULATE_VALIDATION(-32500, "rejected by the EntryPoint during account creation or validation"),
    SIMULATE_PAYMASTER_VALIDATION(-32501, "rejected by the paymaster's validatePaymasterUserOp"),
    OPCODE_VALIDATION(-32502, "rejected because of a banned opcode or storage access"),
    EXPIRES_SHORTLY(-32503, "outside the valid time range, or expires shortly"),
    REPUTATION(-32504, "paymaster or aggregator is throttled or banned"),
    INSUFFICIENT_STAKE(-32505, "paymaster or aggregator stake or unstake delay too low"),
    UNSUPPORTED_SIGNATURE_AGGREGATOR(-32506, "unsupported signature aggregator"),
    INVALID_SIGNATURE(-32507, "account or paymaster signature validation failed"),
    PAYMASTER_DEPOSIT_TOO_LOW(-32508, "paymaster balance cannot cover all pending operations"),
    EXECUTION_REVERTED(-32521, "execution reverted"),
    ;

    companion object {
        /**
         * Return the code for the JSON-RPC error [code], or null if it is not a bundler error code.
         */
        @JvmStatic
        fun fromCode(code: Int): BundlerErrorCode? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Error of an account abstraction operation: a bundler or paymaster RPC call, preparing, signing or waiting for a
 * user operation.
 */
sealed class UserOperationError : ThrowableError {
    /**
     * A bundler, paymaster or node rejected an RPC call. [entryPointError] and [bundlerError] are parsed from
     * [error] when it carries an `AAxx` code or an ERC-7769 error code.
     */
    data class Rpc(val error: RpcError) : UserOperationError() {
        val entryPointError: EntryPointErrorCode? = EntryPointErrorCode.parse(error.message)
        val bundlerError: BundlerErrorCode? = BundlerErrorCode.fromCode(error.code)

        override val message: String
            get() = error.message

        override fun toException(): RuntimeException = error.toException()
    }

    /**
     * The account could not sign the operation.
     */
    data class Signing(override val message: String, override val cause: Throwable? = null) : UserOperationError()

    /**
     * The operation could not be prepared, e.g. a required field was missing and could not be filled in.
     */
    data class InvalidOperation(override val message: String, override val cause: Throwable? = null) : UserOperationError()
}

/**
 * Wrap this error into [UserOperationError.Rpc], parsing its EntryPoint and bundler error codes.
 */
fun RpcError.toUserOperationError(): UserOperationError.Rpc = UserOperationError.Rpc(this)
