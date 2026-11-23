package io.ethers.aa.bundle

import io.ethers.aa.EntryPointVersion
import io.ethers.aa.PackedUserOperation
import io.ethers.aa.UserOperation
import io.ethers.aa.contracts.EntryPointV06
import io.ethers.aa.contracts.EntryPointV07
import io.ethers.aa.contracts.EntryPointV08
import io.ethers.aa.contracts.EntryPointV09
import io.ethers.abi.AbiFunction
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.crypto.Hashing
import io.github.artificialpb.bignum.BigInteger

/**
 * A `handleOps` or `handleAggregatedOps` call decoded from its calldata. Operations are in execution order, which
 * for `handleAggregatedOps` is the order of the aggregator groups.
 */
internal class DecodedHandleOps(
    val userOperations: List<UserOperation>,
    val aggregators: List<Address?>,
    val beneficiary: Address,
)

/**
 * Selectors and calldata decoding of one EntryPoint version.
 */
internal class EntryPointCodec private constructor(
    val version: EntryPointVersion,
    private val handleOps: AbiFunction,
    private val handleAggregatedOps: AbiFunction,
    innerHandleOp: AbiFunction,
    validateUserOpSignature: String,
    validatePaymasterUserOpSignature: String,
    postOpSignature: String,
) {
    val handleOpsSelector: Bytes = handleOps.selector
    val handleAggregatedOpsSelector: Bytes = handleAggregatedOps.selector
    val innerHandleOpSelector: Bytes = innerHandleOp.selector
    val validateUserOpSelector: Bytes = selector(validateUserOpSignature)
    val validatePaymasterUserOpSelector: Bytes = selector(validatePaymasterUserOpSignature)
    val postOpSelector: Bytes = selector(postOpSignature)

    fun isBundleCall(input: Bytes): Boolean = input.startsWith(handleOpsSelector) || input.startsWith(handleAggregatedOpsSelector)

    /**
     * Decode the calldata of a `handleOps` or `handleAggregatedOps` call.
     *
     * @throws IllegalArgumentException if [input] is neither, or is malformed.
     */
    fun decode(input: Bytes): DecodedHandleOps {
        return when {
            input.startsWith(handleOpsSelector) -> {
                val args = handleOps.decodeCall(input)
                val ops = (args[0] as List<*>).map { toUserOperation(it!!) }
                DecodedHandleOps(ops, List(ops.size) { null }, args[1] as Address)
            }

            input.startsWith(handleAggregatedOpsSelector) -> {
                val args = handleAggregatedOps.decodeCall(input)
                val ops = ArrayList<UserOperation>()
                val aggregators = ArrayList<Address?>()
                for (group in args[0] as List<*>) {
                    val (userOps, aggregator) = aggregatorGroup(group!!)
                    for (op in userOps) {
                        ops.add(toUserOperation(op!!))
                        aggregators.add(aggregator.takeIf { it != Address.ZERO })
                    }
                }
                DecodedHandleOps(ops, aggregators, args[1] as Address)
            }

            else -> throw IllegalArgumentException("Not a handleOps or handleAggregatedOps call")
        }
    }

    private fun aggregatorGroup(group: Any): Pair<List<*>, Address> = when (group) {
        is EntryPointV06.UserOpsPerAggregator -> group.userOps to group.aggregator
        is EntryPointV07.UserOpsPerAggregator -> group.userOps to group.aggregator
        is EntryPointV08.UserOpsPerAggregator -> group.userOps to group.aggregator
        is EntryPointV09.UserOpsPerAggregator -> group.userOps to group.aggregator
        else -> throw IllegalArgumentException("Unexpected aggregator group type: $group")
    }

    private fun toUserOperation(struct: Any): UserOperation = when (struct) {
        is EntryPointV06.UserOperation -> UserOperation.fromV06(
            sender = struct.sender,
            nonce = struct.nonce,
            initCode = struct.initCode,
            callData = struct.callData,
            callGasLimit = struct.callGasLimit,
            verificationGasLimit = struct.verificationGasLimit,
            preVerificationGas = struct.preVerificationGas,
            maxFeePerGas = struct.maxFeePerGas,
            maxPriorityFeePerGas = struct.maxPriorityFeePerGas,
            paymasterAndData = struct.paymasterAndData,
            signature = struct.signature,
        )

        is EntryPointV07.PackedUserOperation -> packed(
            struct.sender, struct.nonce, struct.initCode, struct.callData, struct.accountGasLimits,
            struct.preVerificationGas, struct.gasFees, struct.paymasterAndData, struct.signature,
        )

        is EntryPointV08.PackedUserOperation -> packed(
            struct.sender, struct.nonce, struct.initCode, struct.callData, struct.accountGasLimits,
            struct.preVerificationGas, struct.gasFees, struct.paymasterAndData, struct.signature,
        )

        is EntryPointV09.PackedUserOperation -> packed(
            struct.sender, struct.nonce, struct.initCode, struct.callData, struct.accountGasLimits,
            struct.preVerificationGas, struct.gasFees, struct.paymasterAndData, struct.signature,
        )

        else -> throw IllegalArgumentException("Unexpected user operation type: $struct")
    }

    private fun packed(
        sender: Address,
        nonce: BigInteger,
        initCode: Bytes,
        callData: Bytes,
        accountGasLimits: Bytes,
        preVerificationGas: BigInteger,
        gasFees: Bytes,
        paymasterAndData: Bytes,
        signature: Bytes,
    ): UserOperation {
        val packed = PackedUserOperation(
            sender, nonce, initCode, callData, accountGasLimits, preVerificationGas, gasFees, paymasterAndData, signature,
        )
        return UserOperation.fromPacked(packed, version)
    }

    companion object {
        private const val V06_USER_OP = "(address,uint256,bytes,bytes,uint256,uint256,uint256,uint256,uint256,bytes,bytes)"
        private const val PACKED_USER_OP = "(address,uint256,bytes,bytes,bytes32,uint256,bytes32,bytes,bytes)"

        private val V06 = EntryPointCodec(
            EntryPointVersion.V0_6,
            EntryPointV06.FUNCTION_HANDLE_OPS,
            EntryPointV06.FUNCTION_HANDLE_AGGREGATED_OPS,
            EntryPointV06.FUNCTION_INNER_HANDLE_OP,
            "validateUserOp($V06_USER_OP,bytes32,uint256)",
            "validatePaymasterUserOp($V06_USER_OP,bytes32,uint256)",
            "postOp(uint8,bytes,uint256)",
        )

        private val V07 = EntryPointCodec(
            EntryPointVersion.V0_7,
            EntryPointV07.FUNCTION_HANDLE_OPS,
            EntryPointV07.FUNCTION_HANDLE_AGGREGATED_OPS,
            EntryPointV07.FUNCTION_INNER_HANDLE_OP,
            "validateUserOp($PACKED_USER_OP,bytes32,uint256)",
            "validatePaymasterUserOp($PACKED_USER_OP,bytes32,uint256)",
            "postOp(uint8,bytes,uint256,uint256)",
        )

        private val V08 = EntryPointCodec(
            EntryPointVersion.V0_8,
            EntryPointV08.FUNCTION_HANDLE_OPS,
            EntryPointV08.FUNCTION_HANDLE_AGGREGATED_OPS,
            EntryPointV08.FUNCTION_INNER_HANDLE_OP,
            "validateUserOp($PACKED_USER_OP,bytes32,uint256)",
            "validatePaymasterUserOp($PACKED_USER_OP,bytes32,uint256)",
            "postOp(uint8,bytes,uint256,uint256)",
        )

        private val V09 = EntryPointCodec(
            EntryPointVersion.V0_9,
            EntryPointV09.FUNCTION_HANDLE_OPS,
            EntryPointV09.FUNCTION_HANDLE_AGGREGATED_OPS,
            EntryPointV09.FUNCTION_INNER_HANDLE_OP,
            "validateUserOp($PACKED_USER_OP,bytes32,uint256)",
            "validatePaymasterUserOp($PACKED_USER_OP,bytes32,uint256)",
            "postOp(uint8,bytes,uint256,uint256)",
        )

        fun of(version: EntryPointVersion): EntryPointCodec = when (version) {
            EntryPointVersion.V0_6 -> V06
            EntryPointVersion.V0_7 -> V07
            EntryPointVersion.V0_8 -> V08
            EntryPointVersion.V0_9 -> V09
        }

        private fun selector(signature: String): Bytes {
            return Bytes(Hashing.keccak256(signature.encodeToByteArray()).copyOfRange(0, 4))
        }

        /**
         * Read the `bytes32 userOpHash` argument of `validateUserOp` and `validatePaymasterUserOp` calls, the second
         * head word after the offset of the dynamic user operation tuple.
         */
        fun userOpHashArgument(input: Bytes): Hash? {
            if (input.size < 68) return null
            return Hash(input.asByteArray().copyOfRange(36, 68))
        }
    }
}
