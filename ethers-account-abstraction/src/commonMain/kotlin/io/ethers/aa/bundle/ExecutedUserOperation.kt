package io.ethers.aa.bundle

import io.ethers.aa.EntryPoint
import io.ethers.aa.UserOperation
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.Log
import io.ethers.core.types.tracers.CallTracer.CallFrame
import io.github.artificialpb.bignum.BigInteger

/**
 * One `handleOps` or `handleAggregatedOps` call executed by a transaction, split into the user operations it
 * executed.
 *
 * @property bundler the account that sent the bundle transaction.
 * @property beneficiary the address the bundle's gas refund went to, or null if the call's calldata was not
 * available (the bundle was sent through another contract and no trace was given).
 * @property unattributedLogs logs of the bundle that do not belong to a single operation: the EntryPoint's
 * `BeforeExecution` and `SignatureAggregatorChanged` markers, logs emitted while compensating the beneficiary, and,
 * when no trace was given, validation-phase logs that could not be attributed by user operation hash.
 * @property unattributedCalls with a trace, the EntryPoint's own calls that do not belong to a single operation,
 * such as aggregator signature checks and the beneficiary compensation; otherwise empty.
 */
data class Bundle(
    val transactionHash: Hash,
    val blockHash: Hash,
    val blockNumber: Long,
    val transactionIndex: Int,
    val bundler: Address,
    val entryPoint: EntryPoint,
    val beneficiary: Address?,
    val userOperations: List<ExecutedUserOperation>,
    val unattributedLogs: List<Log>,
    val unattributedCalls: List<CallFrame>,
)

/**
 * A user operation executed on-chain, with everything it did collected in one place.
 *
 * The outcome ([success], [actualGasCost], [actualGasUsed]) comes from its `UserOperationEvent`, so it is always
 * available. The rest depends on what the bundle was decoded from:
 * - [userOperation] and [calls] need the bundle's calldata: the transaction must call the EntryPoint directly, or a
 *   trace must be given.
 * - [validationLogs] are complete only with a trace. Without one, they hold only the EntryPoint events that carry
 *   the operation's hash (`AccountDeployed`, `IgnoredInitCode`, `EIP7702AccountInitialized`).
 * - [postOpLogs] are only split from [executionLogs] with a trace. Without one, they are part of [executionLogs].
 * - [validationFrames] and [executionFrames] need a trace.
 *
 * @property index position of the operation in its bundle.
 * @property paymaster the paymaster that paid for the operation, or null if the account paid itself.
 * @property success whether the operation's execution succeeded. A failed operation still pays for its gas.
 * @property userOperation the operation as submitted in the bundle calldata.
 * @property aggregator the signature aggregator of the operation, if it was bundled through `handleAggregatedOps`.
 * @property factory the factory that deployed the account in this operation, if it did.
 * @property eip7702Delegate the delegate of an EIP-7702 account initialized by this operation (EntryPoint v0.9).
 * @property revertReason the revert data of the operation's failed execution, from `UserOperationRevertReason`.
 * @property postOpRevertReason the revert data of the paymaster's failed `postOp`, from `PostOpRevertReason`.
 * @property prefundTooLow whether the operation's prefund did not cover its actual gas cost, so it failed
 * (`UserOperationPrefundTooLow`, EntryPoint v0.7 and later).
 * @property calls the calls the operation asked its account to make, decoded from its callData with the decoder
 * given to [BundleDecoder].
 * @property validationLogs logs of the operation's validation phase: account deployment, account and paymaster
 * validation.
 * @property executionLogs logs of the operation's execution phase, ending with its `UserOperationEvent`.
 * @property postOpLogs logs of the paymaster's `postOp`.
 * @property validationFrames the EntryPoint's calls that validated this operation: account creation, `validateUserOp`
 * and `validatePaymasterUserOp`.
 * @property executionFrames the EntryPoint's calls that executed this operation, starting with its `innerHandleOp`.
 */
data class ExecutedUserOperation(
    val userOpHash: Hash,
    val entryPoint: EntryPoint,
    val transactionHash: Hash,
    val blockHash: Hash,
    val blockNumber: Long,
    val index: Int,
    val sender: Address,
    val nonce: BigInteger,
    val paymaster: Address?,
    val success: Boolean,
    val actualGasCost: BigInteger,
    val actualGasUsed: BigInteger,
    val userOperation: UserOperation?,
    val aggregator: Address?,
    val factory: Address?,
    val eip7702Delegate: Address?,
    val revertReason: Bytes?,
    val postOpRevertReason: Bytes?,
    val prefundTooLow: Boolean,
    val calls: List<Call>?,
    val validationLogs: List<Log>,
    val executionLogs: List<Log>,
    val postOpLogs: List<Log>,
    val validationFrames: List<CallFrame>?,
    val executionFrames: List<CallFrame>?,
) {
    /**
     * All logs of the operation, in the order they were emitted.
     */
    val logs: List<Log>
        get() = (validationLogs + executionLogs + postOpLogs).sortedBy { it.logIndex }

    /**
     * Whether the operation deployed its account.
     */
    val deployedAccount: Boolean
        get() = factory != null
}

/**
 * Decodes the `callData` of a user operation into the calls the account makes.
 */
fun interface CallDataDecoder {
    /**
     * Return the calls [sender] makes for [callData], or null if the call data is not understood.
     */
    fun decodeCalls(sender: Address, callData: Bytes): List<Call>?
}
