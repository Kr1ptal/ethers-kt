@file:Suppress("UNCHECKED_CAST", "FunctionName", "PropertyName", "RedundantVisibilityModifier", "RemoveRedundantQualifierName", "LocalVariableName", "unused")

package io.ethers.aa.contracts

import io.ethers.abi.AbiContract
import io.ethers.abi.AbiEvent
import io.ethers.abi.AbiFunction
import io.ethers.abi.AbiType
import io.ethers.abi.ContractEvent
import io.ethers.abi.ContractStruct
import io.ethers.abi.EventDecodingError
import io.ethers.abi.EventFactory
import io.ethers.abi.EventFilter
import io.ethers.abi.EventFilterBase
import io.ethers.abi.StructFactory
import io.ethers.abi.call.FunctionCall
import io.ethers.abi.call.PayableFunctionCall
import io.ethers.abi.call.ReadFunctionCall
import io.ethers.abi.call.ReceiveFunctionCall
import io.ethers.abi.error.ContractErrorDecodingError
import io.ethers.abi.error.CustomContractError
import io.ethers.abi.error.CustomErrorFactory
import io.ethers.core.Result
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Log
import io.ethers.providers.middleware.Middleware
import io.github.artificialpb.bignum.BigInteger
import kotlin.Any
import kotlin.Boolean
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.jvm.JvmField
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

public class EntryPointV06(
    provider: Middleware,
    address: Address,
) : AbiContract(provider, address) {
    public fun receive(`value`: BigInteger): ReceiveFunctionCall = Companion.receive(this.provider, this.address, value)

    public fun SIG_VALIDATION_FAILED(): ReadFunctionCall<BigInteger> = Companion.SIG_VALIDATION_FAILED(this.provider, this.address)

    public fun _validateSenderAndPaymaster(
        initCode: Bytes,
        sender: Address,
        paymasterAndData: Bytes,
    ): ReadFunctionCall<Unit> = Companion._validateSenderAndPaymaster(this.provider, this.address, initCode, sender, paymasterAndData)

    public fun addStake(unstakeDelaySec: BigInteger): PayableFunctionCall<Unit> = Companion.addStake(this.provider, this.address, unstakeDelaySec)

    public fun balanceOf(account: Address): ReadFunctionCall<BigInteger> = Companion.balanceOf(this.provider, this.address, account)

    public fun depositTo(account: Address): PayableFunctionCall<Unit> = Companion.depositTo(this.provider, this.address, account)

    public fun deposits(arg0: Address): ReadFunctionCall<EntryPointV06.DepositsResult> = Companion.deposits(this.provider, this.address, arg0)

    public fun getDepositInfo(account: Address): ReadFunctionCall<EntryPointV06.DepositInfo> = Companion.getDepositInfo(this.provider, this.address, account)

    public fun getNonce(sender: Address, key: BigInteger): ReadFunctionCall<BigInteger> = Companion.getNonce(this.provider, this.address, sender, key)

    public fun getSenderAddress(initCode: Bytes): FunctionCall<Unit> = Companion.getSenderAddress(this.provider, this.address, initCode)

    public fun getUserOpHash(userOp: EntryPointV06.UserOperation): ReadFunctionCall<Bytes> = Companion.getUserOpHash(this.provider, this.address, userOp)

    public fun handleAggregatedOps(opsPerAggregator: List<EntryPointV06.UserOpsPerAggregator>, beneficiary: Address): FunctionCall<Unit> = Companion.handleAggregatedOps(this.provider, this.address, opsPerAggregator, beneficiary)

    public fun handleOps(ops: List<EntryPointV06.UserOperation>, beneficiary: Address): FunctionCall<Unit> = Companion.handleOps(this.provider, this.address, ops, beneficiary)

    public fun incrementNonce(key: BigInteger): FunctionCall<Unit> = Companion.incrementNonce(this.provider, this.address, key)

    public fun innerHandleOp(
        callData: Bytes,
        opInfo: EntryPointV06.UserOpInfo,
        context: Bytes,
    ): FunctionCall<BigInteger> = Companion.innerHandleOp(this.provider, this.address, callData, opInfo, context)

    public fun nonceSequenceNumber(arg0: Address, arg1: BigInteger): ReadFunctionCall<BigInteger> = Companion.nonceSequenceNumber(this.provider, this.address, arg0, arg1)

    public fun simulateHandleOp(
        op: EntryPointV06.UserOperation,
        target: Address,
        targetCallData: Bytes,
    ): FunctionCall<Unit> = Companion.simulateHandleOp(this.provider, this.address, op, target, targetCallData)

    public fun simulateValidation(userOp: EntryPointV06.UserOperation): FunctionCall<Unit> = Companion.simulateValidation(this.provider, this.address, userOp)

    public fun unlockStake(): FunctionCall<Unit> = Companion.unlockStake(this.provider, this.address)

    public fun withdrawStake(withdrawAddress: Address): FunctionCall<Unit> = Companion.withdrawStake(this.provider, this.address, withdrawAddress)

    public fun withdrawTo(withdrawAddress: Address, withdrawAmount: BigInteger): FunctionCall<Unit> = Companion.withdrawTo(this.provider, this.address, withdrawAddress, withdrawAmount)

    public sealed class Error : CustomContractError()

    public sealed class Event : ContractEvent

    /**
     * Contract error
     *
     * Selector: `0x8b7ac980`
     *
     * Signature:
     * ```solidity
     *     error ExecutionResult(uint256 preOpGas, uint256 paid, uint48 validAfter, uint48 validUntil, bool targetSuccess, bytes targetResult);
     * ```
     */
    public data class ExecutionResult(
        public val preOpGas: BigInteger,
        public val paid: BigInteger,
        public val validAfter: BigInteger,
        public val validUntil: BigInteger,
        public val targetSuccess: Boolean,
        public val targetResult: Bytes,
    ) : EntryPointV06.Error() {
        public companion object : CustomErrorFactory<ExecutionResult> {
            @JvmStatic
            override val abi: AbiFunction =
                AbiFunction("ExecutionResult", listOf(AbiType.UInt(256), AbiType.UInt(256), AbiType.UInt(48), AbiType.UInt(48), AbiType.Bool, AbiType.Bytes), emptyList())

            @JvmStatic
            override fun decodeOrNull(`data`: Bytes): ExecutionResult? = super.decodeOrNull(data)

            @JvmStatic
            override fun tryDecode(`data`: Bytes): Result<ExecutionResult, ContractErrorDecodingError> = super.tryDecode(data)

            @JvmStatic
            override fun decode(`data`: List<Any>): ExecutionResult = ExecutionResult(data[0] as io.github.artificialpb.bignum.BigInteger, data[1] as io.github.artificialpb.bignum.BigInteger, data[2] as io.github.artificialpb.bignum.BigInteger, data[3] as io.github.artificialpb.bignum.BigInteger, data[4] as kotlin.Boolean, data[5] as io.ethers.core.types.Bytes)
        }
    }

    /**
     * Contract error
     *
     * Selector: `0x220266b6`
     *
     * Signature:
     * ```solidity
     *     error FailedOp(uint256 opIndex, string reason);
     * ```
     */
    public data class FailedOp(
        public val opIndex: BigInteger,
        public val reason: String,
    ) : EntryPointV06.Error() {
        public companion object : CustomErrorFactory<FailedOp> {
            @JvmStatic
            override val abi: AbiFunction =
                AbiFunction("FailedOp", listOf(AbiType.UInt(256), AbiType.String), emptyList())

            @JvmStatic
            override fun decodeOrNull(`data`: Bytes): FailedOp? = super.decodeOrNull(data)

            @JvmStatic
            override fun tryDecode(`data`: Bytes): Result<FailedOp, ContractErrorDecodingError> = super.tryDecode(data)

            @JvmStatic
            override fun decode(`data`: List<Any>): FailedOp = FailedOp(data[0] as io.github.artificialpb.bignum.BigInteger, data[1] as kotlin.String)
        }
    }

    /**
     * Contract error
     *
     * Selector: `0x6ca7b806`
     *
     * Signature:
     * ```solidity
     *     error SenderAddressResult(address sender);
     * ```
     */
    public data class SenderAddressResult(
        public val sender: Address,
    ) : EntryPointV06.Error() {
        public companion object : CustomErrorFactory<SenderAddressResult> {
            @JvmStatic
            override val abi: AbiFunction =
                AbiFunction("SenderAddressResult", listOf(AbiType.Address), emptyList())

            @JvmStatic
            override fun decodeOrNull(`data`: Bytes): SenderAddressResult? = super.decodeOrNull(data)

            @JvmStatic
            override fun tryDecode(`data`: Bytes): Result<SenderAddressResult, ContractErrorDecodingError> = super.tryDecode(data)

            @JvmStatic
            override fun decode(`data`: List<Any>): SenderAddressResult = SenderAddressResult(data[0] as io.ethers.core.types.Address)
        }
    }

    /**
     * Contract error
     *
     * Selector: `0x86a9f750`
     *
     * Signature:
     * ```solidity
     *     error SignatureValidationFailed(address aggregator);
     * ```
     */
    public data class SignatureValidationFailed(
        public val aggregator: Address,
    ) : EntryPointV06.Error() {
        public companion object : CustomErrorFactory<SignatureValidationFailed> {
            @JvmStatic
            override val abi: AbiFunction =
                AbiFunction("SignatureValidationFailed", listOf(AbiType.Address), emptyList())

            @JvmStatic
            override fun decodeOrNull(`data`: Bytes): SignatureValidationFailed? = super.decodeOrNull(data)

            @JvmStatic
            override fun tryDecode(`data`: Bytes): Result<SignatureValidationFailed, ContractErrorDecodingError> = super.tryDecode(data)

            @JvmStatic
            override fun decode(`data`: List<Any>): SignatureValidationFailed = SignatureValidationFailed(data[0] as io.ethers.core.types.Address)
        }
    }

    /**
     * Contract error
     *
     * Selector: `0xe0cff05f`
     *
     * Signature:
     * ```solidity
     *     error ValidationResult(ReturnInfo returnInfo, StakeInfo senderInfo, StakeInfo factoryInfo, StakeInfo paymasterInfo);
     * ```
     */
    public data class ValidationResult(
        public val returnInfo: EntryPointV06.ReturnInfo,
        public val senderInfo: EntryPointV06.StakeInfo,
        public val factoryInfo: EntryPointV06.StakeInfo,
        public val paymasterInfo: EntryPointV06.StakeInfo,
    ) : EntryPointV06.Error() {
        public companion object : CustomErrorFactory<ValidationResult> {
            @JvmStatic
            override val abi: AbiFunction =
                AbiFunction("ValidationResult", listOf(ReturnInfo.abi, StakeInfo.abi, StakeInfo.abi, StakeInfo.abi), emptyList())

            @JvmStatic
            override fun decodeOrNull(`data`: Bytes): ValidationResult? = super.decodeOrNull(data)

            @JvmStatic
            override fun tryDecode(`data`: Bytes): Result<ValidationResult, ContractErrorDecodingError> = super.tryDecode(data)

            @JvmStatic
            override fun decode(`data`: List<Any>): ValidationResult = ValidationResult(data[0] as EntryPointV06.ReturnInfo, data[1] as EntryPointV06.StakeInfo, data[2] as EntryPointV06.StakeInfo, data[3] as EntryPointV06.StakeInfo)
        }
    }

    /**
     * Contract error
     *
     * Selector: `0xfaecb4e4`
     *
     * Signature:
     * ```solidity
     *     error ValidationResultWithAggregation(ReturnInfo returnInfo, StakeInfo senderInfo, StakeInfo factoryInfo, StakeInfo paymasterInfo, AggregatorStakeInfo aggregatorInfo);
     * ```
     */
    public data class ValidationResultWithAggregation(
        public val returnInfo: EntryPointV06.ReturnInfo,
        public val senderInfo: EntryPointV06.StakeInfo,
        public val factoryInfo: EntryPointV06.StakeInfo,
        public val paymasterInfo: EntryPointV06.StakeInfo,
        public val aggregatorInfo: EntryPointV06.AggregatorStakeInfo,
    ) : EntryPointV06.Error() {
        public companion object : CustomErrorFactory<ValidationResultWithAggregation> {
            @JvmStatic
            override val abi: AbiFunction =
                AbiFunction("ValidationResultWithAggregation", listOf(ReturnInfo.abi, StakeInfo.abi, StakeInfo.abi, StakeInfo.abi, AggregatorStakeInfo.abi), emptyList())

            @JvmStatic
            override fun decodeOrNull(`data`: Bytes): ValidationResultWithAggregation? = super.decodeOrNull(data)

            @JvmStatic
            override fun tryDecode(`data`: Bytes): Result<ValidationResultWithAggregation, ContractErrorDecodingError> = super.tryDecode(data)

            @JvmStatic
            override fun decode(`data`: List<Any>): ValidationResultWithAggregation = ValidationResultWithAggregation(data[0] as EntryPointV06.ReturnInfo, data[1] as EntryPointV06.StakeInfo, data[2] as EntryPointV06.StakeInfo, data[3] as EntryPointV06.StakeInfo, data[4] as EntryPointV06.AggregatorStakeInfo)
        }
    }

    /**
     * Contract event (indexed dynamic types are replaced with `bytes32`)
     *
     * Topic ID: `0xd51a9c61267aa6196961883ecf5ff2da6619c37dac0fa92122513fb32c032d2d`
     *
     * Anonymous: `false`
     *
     * Signature:
     * ```solidity
     *     event AccountDeployed(bytes32 indexed userOpHash, address indexed sender, address factory, address paymaster);
     * ```
     */
    public data class AccountDeployed(
        public val userOpHash: Bytes,
        public val sender: Address,
        public val factory: Address,
        public val paymaster: Address,
        override val log: Log,
    ) : EntryPointV06.Event() {
        public class Filter(
            provider: Middleware,
            factory: EventFactory<AccountDeployed>,
        ) : EventFilterBase<AccountDeployed, AccountDeployed.Filter>(provider, factory) {
            override val self: AccountDeployed.Filter = this

            init {
                filter.topic0(factory.abi.topicId)
            }

            public fun userOpHash(userOpHash: Bytes): AccountDeployed.Filter = topic1(userOpHash)

            public fun userOpHash(vararg userOpHash: Bytes): AccountDeployed.Filter = topic1(*userOpHash)

            public fun sender(sender: Address): AccountDeployed.Filter = topic2(sender)

            public fun sender(vararg sender: Address): AccountDeployed.Filter = topic2(*sender)
        }

        public companion object : EventFactory<AccountDeployed> {
            @JvmStatic
            override val abi: AbiEvent =
                AbiEvent("AccountDeployed", listOf(AbiEvent.Token(AbiType.FixedBytes(32), true), AbiEvent.Token(AbiType.Address, true), AbiEvent.Token(AbiType.Address, false), AbiEvent.Token(AbiType.Address, false)), false)

            @JvmStatic
            override fun filter(provider: Middleware): AccountDeployed.Filter = AccountDeployed.Filter(provider, this)

            @JvmStatic
            override fun isLogValid(log: Log): Boolean = super.isLogValid(log)

            @JvmStatic
            override fun decodeOrNull(log: Log): AccountDeployed? = super.decodeOrNull(log)

            @JvmStatic
            override fun tryDecode(log: Log): Result<AccountDeployed, EventDecodingError> = super.tryDecode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): AccountDeployed = AccountDeployed(data[0] as io.ethers.core.types.Bytes, data[1] as io.ethers.core.types.Address, data[2] as io.ethers.core.types.Address, data[3] as io.ethers.core.types.Address, log)
        }
    }

    /**
     * Contract event (indexed dynamic types are replaced with `bytes32`)
     *
     * Topic ID: `0xbb47ee3e183a558b1a2ff0874b079f3fc5478b7454eacf2bfc5af2ff5878f972`
     *
     * Anonymous: `false`
     *
     * Signature:
     * ```solidity
     *     event BeforeExecution();
     * ```
     */
    public data class BeforeExecution(
        override val log: Log,
    ) : EntryPointV06.Event() {
        public companion object : EventFactory<BeforeExecution> {
            @JvmStatic
            override val abi: AbiEvent = AbiEvent("BeforeExecution", listOf(), false)

            @JvmStatic
            override fun filter(provider: Middleware): EventFilter<BeforeExecution> = EventFilter(provider, this)

            @JvmStatic
            override fun isLogValid(log: Log): Boolean = super.isLogValid(log)

            @JvmStatic
            override fun decodeOrNull(log: Log): BeforeExecution? = super.decodeOrNull(log)

            @JvmStatic
            override fun tryDecode(log: Log): Result<BeforeExecution, EventDecodingError> = super.tryDecode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): BeforeExecution = BeforeExecution(log)
        }
    }

    /**
     * Contract event (indexed dynamic types are replaced with `bytes32`)
     *
     * Topic ID: `0x2da466a7b24304f47e87fa2e1e5a81b9831ce54fec19055ce277ca2f39ba42c4`
     *
     * Anonymous: `false`
     *
     * Signature:
     * ```solidity
     *     event Deposited(address indexed account, uint256 totalDeposit);
     * ```
     */
    public data class Deposited(
        public val account: Address,
        public val totalDeposit: BigInteger,
        override val log: Log,
    ) : EntryPointV06.Event() {
        public class Filter(
            provider: Middleware,
            factory: EventFactory<Deposited>,
        ) : EventFilterBase<Deposited, Deposited.Filter>(provider, factory) {
            override val self: Deposited.Filter = this

            init {
                filter.topic0(factory.abi.topicId)
            }

            public fun account(account: Address): Deposited.Filter = topic1(account)

            public fun account(vararg account: Address): Deposited.Filter = topic1(*account)
        }

        public companion object : EventFactory<Deposited> {
            @JvmStatic
            override val abi: AbiEvent =
                AbiEvent("Deposited", listOf(AbiEvent.Token(AbiType.Address, true), AbiEvent.Token(AbiType.UInt(256), false)), false)

            @JvmStatic
            override fun filter(provider: Middleware): Deposited.Filter = Deposited.Filter(provider, this)

            @JvmStatic
            override fun isLogValid(log: Log): Boolean = super.isLogValid(log)

            @JvmStatic
            override fun decodeOrNull(log: Log): Deposited? = super.decodeOrNull(log)

            @JvmStatic
            override fun tryDecode(log: Log): Result<Deposited, EventDecodingError> = super.tryDecode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): Deposited = Deposited(data[0] as io.ethers.core.types.Address, data[1] as io.github.artificialpb.bignum.BigInteger, log)
        }
    }

    /**
     * Contract event (indexed dynamic types are replaced with `bytes32`)
     *
     * Topic ID: `0x575ff3acadd5ab348fe1855e217e0f3678f8d767d7494c9f9fefbee2e17cca4d`
     *
     * Anonymous: `false`
     *
     * Signature:
     * ```solidity
     *     event SignatureAggregatorChanged(address indexed aggregator);
     * ```
     */
    public data class SignatureAggregatorChanged(
        public val aggregator: Address,
        override val log: Log,
    ) : EntryPointV06.Event() {
        public class Filter(
            provider: Middleware,
            factory: EventFactory<SignatureAggregatorChanged>,
        ) : EventFilterBase<SignatureAggregatorChanged, SignatureAggregatorChanged.Filter>(provider, factory) {
            override val self: SignatureAggregatorChanged.Filter = this

            init {
                filter.topic0(factory.abi.topicId)
            }

            public fun aggregator(aggregator: Address): SignatureAggregatorChanged.Filter = topic1(aggregator)

            public fun aggregator(vararg aggregator: Address): SignatureAggregatorChanged.Filter = topic1(*aggregator)
        }

        public companion object : EventFactory<SignatureAggregatorChanged> {
            @JvmStatic
            override val abi: AbiEvent =
                AbiEvent("SignatureAggregatorChanged", listOf(AbiEvent.Token(AbiType.Address, true)), false)

            @JvmStatic
            override fun filter(provider: Middleware): SignatureAggregatorChanged.Filter = SignatureAggregatorChanged.Filter(provider, this)

            @JvmStatic
            override fun isLogValid(log: Log): Boolean = super.isLogValid(log)

            @JvmStatic
            override fun decodeOrNull(log: Log): SignatureAggregatorChanged? = super.decodeOrNull(log)

            @JvmStatic
            override fun tryDecode(log: Log): Result<SignatureAggregatorChanged, EventDecodingError> = super.tryDecode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): SignatureAggregatorChanged = SignatureAggregatorChanged(data[0] as io.ethers.core.types.Address, log)
        }
    }

    /**
     * Contract event (indexed dynamic types are replaced with `bytes32`)
     *
     * Topic ID: `0xa5ae833d0bb1dcd632d98a8b70973e8516812898e19bf27b70071ebc8dc52c01`
     *
     * Anonymous: `false`
     *
     * Signature:
     * ```solidity
     *     event StakeLocked(address indexed account, uint256 totalStaked, uint256 unstakeDelaySec);
     * ```
     */
    public data class StakeLocked(
        public val account: Address,
        public val totalStaked: BigInteger,
        public val unstakeDelaySec: BigInteger,
        override val log: Log,
    ) : EntryPointV06.Event() {
        public class Filter(
            provider: Middleware,
            factory: EventFactory<StakeLocked>,
        ) : EventFilterBase<StakeLocked, StakeLocked.Filter>(provider, factory) {
            override val self: StakeLocked.Filter = this

            init {
                filter.topic0(factory.abi.topicId)
            }

            public fun account(account: Address): StakeLocked.Filter = topic1(account)

            public fun account(vararg account: Address): StakeLocked.Filter = topic1(*account)
        }

        public companion object : EventFactory<StakeLocked> {
            @JvmStatic
            override val abi: AbiEvent =
                AbiEvent("StakeLocked", listOf(AbiEvent.Token(AbiType.Address, true), AbiEvent.Token(AbiType.UInt(256), false), AbiEvent.Token(AbiType.UInt(256), false)), false)

            @JvmStatic
            override fun filter(provider: Middleware): StakeLocked.Filter = StakeLocked.Filter(provider, this)

            @JvmStatic
            override fun isLogValid(log: Log): Boolean = super.isLogValid(log)

            @JvmStatic
            override fun decodeOrNull(log: Log): StakeLocked? = super.decodeOrNull(log)

            @JvmStatic
            override fun tryDecode(log: Log): Result<StakeLocked, EventDecodingError> = super.tryDecode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): StakeLocked = StakeLocked(data[0] as io.ethers.core.types.Address, data[1] as io.github.artificialpb.bignum.BigInteger, data[2] as io.github.artificialpb.bignum.BigInteger, log)
        }
    }

    /**
     * Contract event (indexed dynamic types are replaced with `bytes32`)
     *
     * Topic ID: `0xfa9b3c14cc825c412c9ed81b3ba365a5b459439403f18829e572ed53a4180f0a`
     *
     * Anonymous: `false`
     *
     * Signature:
     * ```solidity
     *     event StakeUnlocked(address indexed account, uint256 withdrawTime);
     * ```
     */
    public data class StakeUnlocked(
        public val account: Address,
        public val withdrawTime: BigInteger,
        override val log: Log,
    ) : EntryPointV06.Event() {
        public class Filter(
            provider: Middleware,
            factory: EventFactory<StakeUnlocked>,
        ) : EventFilterBase<StakeUnlocked, StakeUnlocked.Filter>(provider, factory) {
            override val self: StakeUnlocked.Filter = this

            init {
                filter.topic0(factory.abi.topicId)
            }

            public fun account(account: Address): StakeUnlocked.Filter = topic1(account)

            public fun account(vararg account: Address): StakeUnlocked.Filter = topic1(*account)
        }

        public companion object : EventFactory<StakeUnlocked> {
            @JvmStatic
            override val abi: AbiEvent =
                AbiEvent("StakeUnlocked", listOf(AbiEvent.Token(AbiType.Address, true), AbiEvent.Token(AbiType.UInt(256), false)), false)

            @JvmStatic
            override fun filter(provider: Middleware): StakeUnlocked.Filter = StakeUnlocked.Filter(provider, this)

            @JvmStatic
            override fun isLogValid(log: Log): Boolean = super.isLogValid(log)

            @JvmStatic
            override fun decodeOrNull(log: Log): StakeUnlocked? = super.decodeOrNull(log)

            @JvmStatic
            override fun tryDecode(log: Log): Result<StakeUnlocked, EventDecodingError> = super.tryDecode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): StakeUnlocked = StakeUnlocked(data[0] as io.ethers.core.types.Address, data[1] as io.github.artificialpb.bignum.BigInteger, log)
        }
    }

    /**
     * Contract event (indexed dynamic types are replaced with `bytes32`)
     *
     * Topic ID: `0xb7c918e0e249f999e965cafeb6c664271b3f4317d296461500e71da39f0cbda3`
     *
     * Anonymous: `false`
     *
     * Signature:
     * ```solidity
     *     event StakeWithdrawn(address indexed account, address withdrawAddress, uint256 amount);
     * ```
     */
    public data class StakeWithdrawn(
        public val account: Address,
        public val withdrawAddress: Address,
        public val amount: BigInteger,
        override val log: Log,
    ) : EntryPointV06.Event() {
        public class Filter(
            provider: Middleware,
            factory: EventFactory<StakeWithdrawn>,
        ) : EventFilterBase<StakeWithdrawn, StakeWithdrawn.Filter>(provider, factory) {
            override val self: StakeWithdrawn.Filter = this

            init {
                filter.topic0(factory.abi.topicId)
            }

            public fun account(account: Address): StakeWithdrawn.Filter = topic1(account)

            public fun account(vararg account: Address): StakeWithdrawn.Filter = topic1(*account)
        }

        public companion object : EventFactory<StakeWithdrawn> {
            @JvmStatic
            override val abi: AbiEvent =
                AbiEvent("StakeWithdrawn", listOf(AbiEvent.Token(AbiType.Address, true), AbiEvent.Token(AbiType.Address, false), AbiEvent.Token(AbiType.UInt(256), false)), false)

            @JvmStatic
            override fun filter(provider: Middleware): StakeWithdrawn.Filter = StakeWithdrawn.Filter(provider, this)

            @JvmStatic
            override fun isLogValid(log: Log): Boolean = super.isLogValid(log)

            @JvmStatic
            override fun decodeOrNull(log: Log): StakeWithdrawn? = super.decodeOrNull(log)

            @JvmStatic
            override fun tryDecode(log: Log): Result<StakeWithdrawn, EventDecodingError> = super.tryDecode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): StakeWithdrawn = StakeWithdrawn(data[0] as io.ethers.core.types.Address, data[1] as io.ethers.core.types.Address, data[2] as io.github.artificialpb.bignum.BigInteger, log)
        }
    }

    /**
     * Contract event (indexed dynamic types are replaced with `bytes32`)
     *
     * Topic ID: `0x49628fd1471006c1482da88028e9ce4dbb080b815c9b0344d39e5a8e6ec1419f`
     *
     * Anonymous: `false`
     *
     * Signature:
     * ```solidity
     *     event UserOperationEvent(bytes32 indexed userOpHash, address indexed sender, address indexed paymaster, uint256 nonce, bool success, uint256 actualGasCost, uint256 actualGasUsed);
     * ```
     */
    public data class UserOperationEvent(
        public val userOpHash: Bytes,
        public val sender: Address,
        public val paymaster: Address,
        public val nonce: BigInteger,
        public val success: Boolean,
        public val actualGasCost: BigInteger,
        public val actualGasUsed: BigInteger,
        override val log: Log,
    ) : EntryPointV06.Event() {
        public class Filter(
            provider: Middleware,
            factory: EventFactory<UserOperationEvent>,
        ) : EventFilterBase<UserOperationEvent, UserOperationEvent.Filter>(provider, factory) {
            override val self: UserOperationEvent.Filter = this

            init {
                filter.topic0(factory.abi.topicId)
            }

            public fun userOpHash(userOpHash: Bytes): UserOperationEvent.Filter = topic1(userOpHash)

            public fun userOpHash(vararg userOpHash: Bytes): UserOperationEvent.Filter = topic1(*userOpHash)

            public fun sender(sender: Address): UserOperationEvent.Filter = topic2(sender)

            public fun sender(vararg sender: Address): UserOperationEvent.Filter = topic2(*sender)

            public fun paymaster(paymaster: Address): UserOperationEvent.Filter = topic3(paymaster)

            public fun paymaster(vararg paymaster: Address): UserOperationEvent.Filter = topic3(*paymaster)
        }

        public companion object : EventFactory<UserOperationEvent> {
            @JvmStatic
            override val abi: AbiEvent =
                AbiEvent("UserOperationEvent", listOf(AbiEvent.Token(AbiType.FixedBytes(32), true), AbiEvent.Token(AbiType.Address, true), AbiEvent.Token(AbiType.Address, true), AbiEvent.Token(AbiType.UInt(256), false), AbiEvent.Token(AbiType.Bool, false), AbiEvent.Token(AbiType.UInt(256), false), AbiEvent.Token(AbiType.UInt(256), false)), false)

            @JvmStatic
            override fun filter(provider: Middleware): UserOperationEvent.Filter = UserOperationEvent.Filter(provider, this)

            @JvmStatic
            override fun isLogValid(log: Log): Boolean = super.isLogValid(log)

            @JvmStatic
            override fun decodeOrNull(log: Log): UserOperationEvent? = super.decodeOrNull(log)

            @JvmStatic
            override fun tryDecode(log: Log): Result<UserOperationEvent, EventDecodingError> = super.tryDecode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): UserOperationEvent = UserOperationEvent(data[0] as io.ethers.core.types.Bytes, data[1] as io.ethers.core.types.Address, data[2] as io.ethers.core.types.Address, data[3] as io.github.artificialpb.bignum.BigInteger, data[4] as kotlin.Boolean, data[5] as io.github.artificialpb.bignum.BigInteger, data[6] as io.github.artificialpb.bignum.BigInteger, log)
        }
    }

    /**
     * Contract event (indexed dynamic types are replaced with `bytes32`)
     *
     * Topic ID: `0x1c4fada7374c0a9ee8841fc38afe82932dc0f8e69012e927f061a8bae611a201`
     *
     * Anonymous: `false`
     *
     * Signature:
     * ```solidity
     *     event UserOperationRevertReason(bytes32 indexed userOpHash, address indexed sender, uint256 nonce, bytes revertReason);
     * ```
     */
    public data class UserOperationRevertReason(
        public val userOpHash: Bytes,
        public val sender: Address,
        public val nonce: BigInteger,
        public val revertReason: Bytes,
        override val log: Log,
    ) : EntryPointV06.Event() {
        public class Filter(
            provider: Middleware,
            factory: EventFactory<UserOperationRevertReason>,
        ) : EventFilterBase<UserOperationRevertReason, UserOperationRevertReason.Filter>(provider, factory) {
            override val self: UserOperationRevertReason.Filter = this

            init {
                filter.topic0(factory.abi.topicId)
            }

            public fun userOpHash(userOpHash: Bytes): UserOperationRevertReason.Filter = topic1(userOpHash)

            public fun userOpHash(vararg userOpHash: Bytes): UserOperationRevertReason.Filter = topic1(*userOpHash)

            public fun sender(sender: Address): UserOperationRevertReason.Filter = topic2(sender)

            public fun sender(vararg sender: Address): UserOperationRevertReason.Filter = topic2(*sender)
        }

        public companion object : EventFactory<UserOperationRevertReason> {
            @JvmStatic
            override val abi: AbiEvent =
                AbiEvent("UserOperationRevertReason", listOf(AbiEvent.Token(AbiType.FixedBytes(32), true), AbiEvent.Token(AbiType.Address, true), AbiEvent.Token(AbiType.UInt(256), false), AbiEvent.Token(AbiType.Bytes, false)), false)

            @JvmStatic
            override fun filter(provider: Middleware): UserOperationRevertReason.Filter = UserOperationRevertReason.Filter(provider, this)

            @JvmStatic
            override fun isLogValid(log: Log): Boolean = super.isLogValid(log)

            @JvmStatic
            override fun decodeOrNull(log: Log): UserOperationRevertReason? = super.decodeOrNull(log)

            @JvmStatic
            override fun tryDecode(log: Log): Result<UserOperationRevertReason, EventDecodingError> = super.tryDecode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): UserOperationRevertReason = UserOperationRevertReason(data[0] as io.ethers.core.types.Bytes, data[1] as io.ethers.core.types.Address, data[2] as io.github.artificialpb.bignum.BigInteger, data[3] as io.ethers.core.types.Bytes, log)
        }
    }

    /**
     * Contract event (indexed dynamic types are replaced with `bytes32`)
     *
     * Topic ID: `0xd1c19fbcd4551a5edfb66d43d2e337c04837afda3482b42bdf569a8fccdae5fb`
     *
     * Anonymous: `false`
     *
     * Signature:
     * ```solidity
     *     event Withdrawn(address indexed account, address withdrawAddress, uint256 amount);
     * ```
     */
    public data class Withdrawn(
        public val account: Address,
        public val withdrawAddress: Address,
        public val amount: BigInteger,
        override val log: Log,
    ) : EntryPointV06.Event() {
        public class Filter(
            provider: Middleware,
            factory: EventFactory<Withdrawn>,
        ) : EventFilterBase<Withdrawn, Withdrawn.Filter>(provider, factory) {
            override val self: Withdrawn.Filter = this

            init {
                filter.topic0(factory.abi.topicId)
            }

            public fun account(account: Address): Withdrawn.Filter = topic1(account)

            public fun account(vararg account: Address): Withdrawn.Filter = topic1(*account)
        }

        public companion object : EventFactory<Withdrawn> {
            @JvmStatic
            override val abi: AbiEvent =
                AbiEvent("Withdrawn", listOf(AbiEvent.Token(AbiType.Address, true), AbiEvent.Token(AbiType.Address, false), AbiEvent.Token(AbiType.UInt(256), false)), false)

            @JvmStatic
            override fun filter(provider: Middleware): Withdrawn.Filter = Withdrawn.Filter(provider, this)

            @JvmStatic
            override fun isLogValid(log: Log): Boolean = super.isLogValid(log)

            @JvmStatic
            override fun decodeOrNull(log: Log): Withdrawn? = super.decodeOrNull(log)

            @JvmStatic
            override fun tryDecode(log: Log): Result<Withdrawn, EventDecodingError> = super.tryDecode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): Withdrawn = Withdrawn(data[0] as io.ethers.core.types.Address, data[1] as io.ethers.core.types.Address, data[2] as io.github.artificialpb.bignum.BigInteger, log)
        }
    }

    public data class DepositsResult(
        public val deposit: BigInteger,
        public val staked: Boolean,
        public val stake: BigInteger,
        public val unstakeDelaySec: BigInteger,
        public val withdrawTime: BigInteger,
    )

    /**
     * Contract struct
     *
     * Signature:
     * ```solidity
     *     struct AggregatorStakeInfo {
     *         address aggregator;
     *         StakeInfo stakeInfo;
     *     }
     * ```
     */
    public data class AggregatorStakeInfo(
        public val aggregator: Address,
        public val stakeInfo: EntryPointV06.StakeInfo,
    ) : ContractStruct {
        override val tuple: List<Any> = listOf(aggregator, stakeInfo)

        override val abiType: AbiType.Struct<EntryPointV06.AggregatorStakeInfo>
            get() = abi

        public companion object : StructFactory<EntryPointV06.AggregatorStakeInfo> {
            @JvmStatic
            override val abi: AbiType.Struct<EntryPointV06.AggregatorStakeInfo> =
                AbiType.Struct(
                    EntryPointV06.AggregatorStakeInfo::class,
                    ::fromTuple,
                    AbiType.Struct.Field("aggregator", AbiType.Address),
                    AbiType.Struct.Field("stakeInfo", StakeInfo.abi),
                )

            @JvmStatic
            override fun fromTuple(`data`: List<Any>): EntryPointV06.AggregatorStakeInfo = EntryPointV06.AggregatorStakeInfo(data[0] as io.ethers.core.types.Address, data[1] as EntryPointV06.StakeInfo)
        }
    }

    /**
     * Contract struct
     *
     * Signature:
     * ```solidity
     *     struct UserOpsPerAggregator {
     *         UserOperation[] userOps;
     *         address aggregator;
     *         bytes signature;
     *     }
     * ```
     */
    public data class UserOpsPerAggregator(
        public val userOps: List<EntryPointV06.UserOperation>,
        public val aggregator: Address,
        public val signature: Bytes,
    ) : ContractStruct {
        override val tuple: List<Any> = listOf(userOps, aggregator, signature)

        override val abiType: AbiType.Struct<EntryPointV06.UserOpsPerAggregator>
            get() = abi

        public companion object : StructFactory<EntryPointV06.UserOpsPerAggregator> {
            @JvmStatic
            override val abi: AbiType.Struct<EntryPointV06.UserOpsPerAggregator> =
                AbiType.Struct(
                    EntryPointV06.UserOpsPerAggregator::class,
                    ::fromTuple,
                    AbiType.Struct.Field("userOps", AbiType.Array(UserOperation.abi)),
                    AbiType.Struct.Field("aggregator", AbiType.Address),
                    AbiType.Struct.Field("signature", AbiType.Bytes),
                )

            @JvmStatic
            override fun fromTuple(`data`: List<Any>): EntryPointV06.UserOpsPerAggregator = EntryPointV06.UserOpsPerAggregator(data[0] as kotlin.collections.List<EntryPointV06.UserOperation>, data[1] as io.ethers.core.types.Address, data[2] as io.ethers.core.types.Bytes)
        }
    }

    /**
     * Contract struct
     *
     * Signature:
     * ```solidity
     *     struct UserOpInfo {
     *         MemoryUserOp mUserOp;
     *         bytes32 userOpHash;
     *         uint256 prefund;
     *         uint256 contextOffset;
     *         uint256 preOpGas;
     *     }
     * ```
     */
    public data class UserOpInfo(
        public val mUserOp: EntryPointV06.MemoryUserOp,
        public val userOpHash: Bytes,
        public val prefund: BigInteger,
        public val contextOffset: BigInteger,
        public val preOpGas: BigInteger,
    ) : ContractStruct {
        override val tuple: List<Any> =
            listOf(mUserOp, userOpHash, prefund, contextOffset, preOpGas)

        override val abiType: AbiType.Struct<EntryPointV06.UserOpInfo>
            get() = abi

        public companion object : StructFactory<EntryPointV06.UserOpInfo> {
            @JvmStatic
            override val abi: AbiType.Struct<EntryPointV06.UserOpInfo> =
                AbiType.Struct(
                    EntryPointV06.UserOpInfo::class,
                    ::fromTuple,
                    AbiType.Struct.Field("mUserOp", MemoryUserOp.abi),
                    AbiType.Struct.Field("userOpHash", AbiType.FixedBytes(32)),
                    AbiType.Struct.Field("prefund", AbiType.UInt(256)),
                    AbiType.Struct.Field("contextOffset", AbiType.UInt(256)),
                    AbiType.Struct.Field("preOpGas", AbiType.UInt(256)),
                )

            @JvmStatic
            override fun fromTuple(`data`: List<Any>): EntryPointV06.UserOpInfo = EntryPointV06.UserOpInfo(data[0] as EntryPointV06.MemoryUserOp, data[1] as io.ethers.core.types.Bytes, data[2] as io.github.artificialpb.bignum.BigInteger, data[3] as io.github.artificialpb.bignum.BigInteger, data[4] as io.github.artificialpb.bignum.BigInteger)
        }
    }

    /**
     * Contract struct
     *
     * Signature:
     * ```solidity
     *     struct StakeInfo {
     *         uint256 stake;
     *         uint256 unstakeDelaySec;
     *     }
     * ```
     */
    public data class StakeInfo(
        public val stake: BigInteger,
        public val unstakeDelaySec: BigInteger,
    ) : ContractStruct {
        override val tuple: List<Any> = listOf(stake, unstakeDelaySec)

        override val abiType: AbiType.Struct<EntryPointV06.StakeInfo>
            get() = abi

        public companion object : StructFactory<EntryPointV06.StakeInfo> {
            @JvmStatic
            override val abi: AbiType.Struct<EntryPointV06.StakeInfo> =
                AbiType.Struct(
                    EntryPointV06.StakeInfo::class,
                    ::fromTuple,
                    AbiType.Struct.Field("stake", AbiType.UInt(256)),
                    AbiType.Struct.Field("unstakeDelaySec", AbiType.UInt(256)),
                )

            @JvmStatic
            override fun fromTuple(`data`: List<Any>): EntryPointV06.StakeInfo = EntryPointV06.StakeInfo(data[0] as io.github.artificialpb.bignum.BigInteger, data[1] as io.github.artificialpb.bignum.BigInteger)
        }
    }

    /**
     * Contract struct
     *
     * Signature:
     * ```solidity
     *     struct UserOperation {
     *         address sender;
     *         uint256 nonce;
     *         bytes initCode;
     *         bytes callData;
     *         uint256 callGasLimit;
     *         uint256 verificationGasLimit;
     *         uint256 preVerificationGas;
     *         uint256 maxFeePerGas;
     *         uint256 maxPriorityFeePerGas;
     *         bytes paymasterAndData;
     *         bytes signature;
     *     }
     * ```
     */
    public data class UserOperation(
        public val sender: Address,
        public val nonce: BigInteger,
        public val initCode: Bytes,
        public val callData: Bytes,
        public val callGasLimit: BigInteger,
        public val verificationGasLimit: BigInteger,
        public val preVerificationGas: BigInteger,
        public val maxFeePerGas: BigInteger,
        public val maxPriorityFeePerGas: BigInteger,
        public val paymasterAndData: Bytes,
        public val signature: Bytes,
    ) : ContractStruct {
        override val tuple: List<Any> =
            listOf(sender, nonce, initCode, callData, callGasLimit, verificationGasLimit, preVerificationGas, maxFeePerGas, maxPriorityFeePerGas, paymasterAndData, signature)

        override val abiType: AbiType.Struct<EntryPointV06.UserOperation>
            get() = abi

        public companion object : StructFactory<EntryPointV06.UserOperation> {
            @JvmStatic
            override val abi: AbiType.Struct<EntryPointV06.UserOperation> =
                AbiType.Struct(
                    EntryPointV06.UserOperation::class, ::fromTuple, AbiType.Struct.Field("sender", AbiType.Address),
                    AbiType.Struct.Field("nonce", AbiType.UInt(256)),
                    AbiType.Struct.Field("initCode", AbiType.Bytes),
                    AbiType.Struct.Field("callData", AbiType.Bytes),
                    AbiType.Struct.Field("callGasLimit", AbiType.UInt(256)),
                    AbiType.Struct.Field("verificationGasLimit", AbiType.UInt(256)),
                    AbiType.Struct.Field("preVerificationGas", AbiType.UInt(256)),
                    AbiType.Struct.Field("maxFeePerGas", AbiType.UInt(256)),
                    AbiType.Struct.Field("maxPriorityFeePerGas", AbiType.UInt(256)),
                    AbiType.Struct.Field("paymasterAndData", AbiType.Bytes),
                    AbiType.Struct.Field("signature", AbiType.Bytes),
                )

            @JvmStatic
            override fun fromTuple(`data`: List<Any>): EntryPointV06.UserOperation = EntryPointV06.UserOperation(data[0] as io.ethers.core.types.Address, data[1] as io.github.artificialpb.bignum.BigInteger, data[2] as io.ethers.core.types.Bytes, data[3] as io.ethers.core.types.Bytes, data[4] as io.github.artificialpb.bignum.BigInteger, data[5] as io.github.artificialpb.bignum.BigInteger, data[6] as io.github.artificialpb.bignum.BigInteger, data[7] as io.github.artificialpb.bignum.BigInteger, data[8] as io.github.artificialpb.bignum.BigInteger, data[9] as io.ethers.core.types.Bytes, data[10] as io.ethers.core.types.Bytes)
        }
    }

    /**
     * Contract struct
     *
     * Signature:
     * ```solidity
     *     struct DepositInfo {
     *         uint112 deposit;
     *         bool staked;
     *         uint112 stake;
     *         uint32 unstakeDelaySec;
     *         uint48 withdrawTime;
     *     }
     * ```
     */
    public data class DepositInfo(
        public val deposit: BigInteger,
        public val staked: Boolean,
        public val stake: BigInteger,
        public val unstakeDelaySec: BigInteger,
        public val withdrawTime: BigInteger,
    ) : ContractStruct {
        override val tuple: List<Any> =
            listOf(deposit, staked, stake, unstakeDelaySec, withdrawTime)

        override val abiType: AbiType.Struct<EntryPointV06.DepositInfo>
            get() = abi

        public companion object : StructFactory<EntryPointV06.DepositInfo> {
            @JvmStatic
            override val abi: AbiType.Struct<EntryPointV06.DepositInfo> =
                AbiType.Struct(
                    EntryPointV06.DepositInfo::class,
                    ::fromTuple,
                    AbiType.Struct.Field("deposit", AbiType.UInt(112)),
                    AbiType.Struct.Field("staked", AbiType.Bool),
                    AbiType.Struct.Field("stake", AbiType.UInt(112)),
                    AbiType.Struct.Field("unstakeDelaySec", AbiType.UInt(32)),
                    AbiType.Struct.Field("withdrawTime", AbiType.UInt(48)),
                )

            @JvmStatic
            override fun fromTuple(`data`: List<Any>): EntryPointV06.DepositInfo = EntryPointV06.DepositInfo(data[0] as io.github.artificialpb.bignum.BigInteger, data[1] as kotlin.Boolean, data[2] as io.github.artificialpb.bignum.BigInteger, data[3] as io.github.artificialpb.bignum.BigInteger, data[4] as io.github.artificialpb.bignum.BigInteger)
        }
    }

    /**
     * Contract struct
     *
     * Signature:
     * ```solidity
     *     struct ReturnInfo {
     *         uint256 preOpGas;
     *         uint256 prefund;
     *         bool sigFailed;
     *         uint48 validAfter;
     *         uint48 validUntil;
     *         bytes paymasterContext;
     *     }
     * ```
     */
    public data class ReturnInfo(
        public val preOpGas: BigInteger,
        public val prefund: BigInteger,
        public val sigFailed: Boolean,
        public val validAfter: BigInteger,
        public val validUntil: BigInteger,
        public val paymasterContext: Bytes,
    ) : ContractStruct {
        override val tuple: List<Any> =
            listOf(preOpGas, prefund, sigFailed, validAfter, validUntil, paymasterContext)

        override val abiType: AbiType.Struct<EntryPointV06.ReturnInfo>
            get() = abi

        public companion object : StructFactory<EntryPointV06.ReturnInfo> {
            @JvmStatic
            override val abi: AbiType.Struct<EntryPointV06.ReturnInfo> =
                AbiType.Struct(
                    EntryPointV06.ReturnInfo::class,
                    ::fromTuple,
                    AbiType.Struct.Field("preOpGas", AbiType.UInt(256)),
                    AbiType.Struct.Field("prefund", AbiType.UInt(256)),
                    AbiType.Struct.Field("sigFailed", AbiType.Bool),
                    AbiType.Struct.Field("validAfter", AbiType.UInt(48)),
                    AbiType.Struct.Field("validUntil", AbiType.UInt(48)),
                    AbiType.Struct.Field("paymasterContext", AbiType.Bytes),
                )

            @JvmStatic
            override fun fromTuple(`data`: List<Any>): EntryPointV06.ReturnInfo = EntryPointV06.ReturnInfo(data[0] as io.github.artificialpb.bignum.BigInteger, data[1] as io.github.artificialpb.bignum.BigInteger, data[2] as kotlin.Boolean, data[3] as io.github.artificialpb.bignum.BigInteger, data[4] as io.github.artificialpb.bignum.BigInteger, data[5] as io.ethers.core.types.Bytes)
        }
    }

    /**
     * Contract struct
     *
     * Signature:
     * ```solidity
     *     struct MemoryUserOp {
     *         address sender;
     *         uint256 nonce;
     *         uint256 callGasLimit;
     *         uint256 verificationGasLimit;
     *         uint256 preVerificationGas;
     *         address paymaster;
     *         uint256 maxFeePerGas;
     *         uint256 maxPriorityFeePerGas;
     *     }
     * ```
     */
    public data class MemoryUserOp(
        public val sender: Address,
        public val nonce: BigInteger,
        public val callGasLimit: BigInteger,
        public val verificationGasLimit: BigInteger,
        public val preVerificationGas: BigInteger,
        public val paymaster: Address,
        public val maxFeePerGas: BigInteger,
        public val maxPriorityFeePerGas: BigInteger,
    ) : ContractStruct {
        override val tuple: List<Any> =
            listOf(sender, nonce, callGasLimit, verificationGasLimit, preVerificationGas, paymaster, maxFeePerGas, maxPriorityFeePerGas)

        override val abiType: AbiType.Struct<EntryPointV06.MemoryUserOp>
            get() = abi

        public companion object : StructFactory<EntryPointV06.MemoryUserOp> {
            @JvmStatic
            override val abi: AbiType.Struct<EntryPointV06.MemoryUserOp> =
                AbiType.Struct(
                    EntryPointV06.MemoryUserOp::class, ::fromTuple, AbiType.Struct.Field("sender", AbiType.Address),
                    AbiType.Struct.Field("nonce", AbiType.UInt(256)),
                    AbiType.Struct.Field("callGasLimit", AbiType.UInt(256)),
                    AbiType.Struct.Field("verificationGasLimit", AbiType.UInt(256)),
                    AbiType.Struct.Field("preVerificationGas", AbiType.UInt(256)),
                    AbiType.Struct.Field("paymaster", AbiType.Address),
                    AbiType.Struct.Field("maxFeePerGas", AbiType.UInt(256)),
                    AbiType.Struct.Field("maxPriorityFeePerGas", AbiType.UInt(256)),
                )

            @JvmStatic
            override fun fromTuple(`data`: List<Any>): EntryPointV06.MemoryUserOp = EntryPointV06.MemoryUserOp(data[0] as io.ethers.core.types.Address, data[1] as io.github.artificialpb.bignum.BigInteger, data[2] as io.github.artificialpb.bignum.BigInteger, data[3] as io.github.artificialpb.bignum.BigInteger, data[4] as io.github.artificialpb.bignum.BigInteger, data[5] as io.ethers.core.types.Address, data[6] as io.github.artificialpb.bignum.BigInteger, data[7] as io.github.artificialpb.bignum.BigInteger)
        }
    }

    public companion object {
        @JvmField
        public val ERRORS: List<CustomErrorFactory<out EntryPointV06.Error>> =
            listOf(ExecutionResult, FailedOp, SenderAddressResult, SignatureValidationFailed, ValidationResult, ValidationResultWithAggregation)

        @JvmField
        public val EVENTS: List<EventFactory<out EntryPointV06.Event>> =
            listOf(AccountDeployed, BeforeExecution, Deposited, SignatureAggregatorChanged, StakeLocked, StakeUnlocked, StakeWithdrawn, UserOperationEvent, UserOperationRevertReason, Withdrawn)

        @JvmField
        public val FUNCTION__VALIDATE_SENDER_AND_PAYMASTER: AbiFunction =
            AbiFunction("_validateSenderAndPaymaster", listOf(AbiType.Bytes, AbiType.Address, AbiType.Bytes), listOf())

        @JvmField
        public val FUNCTION_GET_DEPOSIT_INFO: AbiFunction =
            AbiFunction("getDepositInfo", listOf(AbiType.Address), listOf(DepositInfo.abi))

        @JvmField
        public val FUNCTION_SIMULATE_HANDLE_OP: AbiFunction =
            AbiFunction("simulateHandleOp", listOf(UserOperation.abi, AbiType.Address, AbiType.Bytes), listOf())

        @JvmField
        public val FUNCTION_INCREMENT_NONCE: AbiFunction =
            AbiFunction("incrementNonce", listOf(AbiType.UInt(192)), listOf())

        @JvmField
        public val FUNCTION_SIG_VALIDATION_FAILED: AbiFunction =
            AbiFunction("SIG_VALIDATION_FAILED", listOf(), listOf(AbiType.UInt(256)))

        @JvmField
        public val FUNCTION_GET_USER_OP_HASH: AbiFunction =
            AbiFunction("getUserOpHash", listOf(UserOperation.abi), listOf(AbiType.FixedBytes(32)))

        @JvmField
        public val FUNCTION_NONCE_SEQUENCE_NUMBER: AbiFunction =
            AbiFunction("nonceSequenceNumber", listOf(AbiType.Address, AbiType.UInt(192)), listOf(AbiType.UInt(256)))

        @JvmField
        public val FUNCTION_BALANCE_OF: AbiFunction =
            AbiFunction("balanceOf", listOf(AbiType.Address), listOf(AbiType.UInt(256)))

        @JvmField
        public val FUNCTION_SIMULATE_VALIDATION: AbiFunction =
            AbiFunction("simulateValidation", listOf(UserOperation.abi), listOf())

        @JvmField
        public val FUNCTION_INNER_HANDLE_OP: AbiFunction =
            AbiFunction("innerHandleOp", listOf(AbiType.Bytes, UserOpInfo.abi, AbiType.Bytes), listOf(AbiType.UInt(256)))

        @JvmField
        public val FUNCTION_DEPOSIT_TO: AbiFunction =
            AbiFunction("depositTo", listOf(AbiType.Address), listOf())

        @JvmField
        public val FUNCTION_GET_NONCE: AbiFunction =
            AbiFunction("getNonce", listOf(AbiType.Address, AbiType.UInt(192)), listOf(AbiType.UInt(256)))

        @JvmField
        public val FUNCTION_ADD_STAKE: AbiFunction =
            AbiFunction("addStake", listOf(AbiType.UInt(32)), listOf())

        @JvmField
        public val FUNCTION_HANDLE_AGGREGATED_OPS: AbiFunction =
            AbiFunction("handleAggregatedOps", listOf(AbiType.Array(UserOpsPerAggregator.abi), AbiType.Address), listOf())

        @JvmField
        public val FUNCTION_WITHDRAW_STAKE: AbiFunction =
            AbiFunction("withdrawStake", listOf(AbiType.Address), listOf())

        @JvmField
        public val FUNCTION_DEPOSITS: AbiFunction =
            AbiFunction("deposits", listOf(AbiType.Address), listOf(AbiType.UInt(112), AbiType.Bool, AbiType.UInt(112), AbiType.UInt(32), AbiType.UInt(48)))

        @JvmField
        public val FUNCTION_UNLOCK_STAKE: AbiFunction =
            AbiFunction("unlockStake", listOf(), listOf())

        @JvmField
        public val FUNCTION_HANDLE_OPS: AbiFunction =
            AbiFunction("handleOps", listOf(AbiType.Array(UserOperation.abi), AbiType.Address), listOf())

        @JvmField
        public val FUNCTION_GET_SENDER_ADDRESS: AbiFunction =
            AbiFunction("getSenderAddress", listOf(AbiType.Bytes), listOf())

        @JvmField
        public val FUNCTION_WITHDRAW_TO: AbiFunction =
            AbiFunction("withdrawTo", listOf(AbiType.Address, AbiType.UInt(256)), listOf())

        init {
            io.ethers.abi.error.CustomErrorFactoryResolver.addFactories(ERRORS)
            io.ethers.abigen.loaders.AccountAbstractionAbigenCustomErrorLoader.load()
        }

        /**
         * Receive function with signature:
         * ```solidity
         *     receive() external payable;
         * ```
         */
        @JvmStatic
        public fun receive(
            provider: Middleware,
            address: Address,
            `value`: BigInteger,
        ): ReceiveFunctionCall = ReceiveFunctionCall(provider, address, `value`)

        /**
         * Call contract function
         *
         * Selector: `0x8f41ec5a`
         *
         * Signature:
         * ```solidity
         *     function SIG_VALIDATION_FAILED() external returns (uint256);
         * ```
         */
        @JvmStatic
        public fun SIG_VALIDATION_FAILED(provider: Middleware, address: Address): ReadFunctionCall<BigInteger> = ReadFunctionCall(provider, address, FUNCTION_SIG_VALIDATION_FAILED.encodeCall(emptyList())) {
            val data = FUNCTION_SIG_VALIDATION_FAILED.decodeResponse(it)
            data[0] as io.github.artificialpb.bignum.BigInteger
        }

        /**
         * Call contract function
         *
         * Selector: `0x957122ab`
         *
         * Signature:
         * ```solidity
         *     function _validateSenderAndPaymaster(bytes initCode, address sender, bytes paymasterAndData) external;
         * ```
         */
        @JvmStatic
        public fun _validateSenderAndPaymaster(
            provider: Middleware,
            address: Address,
            initCode: Bytes,
            sender: Address,
            paymasterAndData: Bytes,
        ): ReadFunctionCall<Unit> = ReadFunctionCall(provider, address, FUNCTION__VALIDATE_SENDER_AND_PAYMASTER.encodeCall(listOf<Any>(initCode, sender, paymasterAndData))) {
        }

        /**
         * Call contract function
         *
         * Selector: `0x0396cb60`
         *
         * Signature:
         * ```solidity
         *     function addStake(uint32 unstakeDelaySec) external payable;
         * ```
         */
        @JvmStatic
        public fun addStake(
            provider: Middleware,
            address: Address,
            unstakeDelaySec: BigInteger,
        ): PayableFunctionCall<Unit> = PayableFunctionCall(provider, address, FUNCTION_ADD_STAKE.encodeCall(listOf<Any>(unstakeDelaySec))) {
        }

        /**
         * Call contract function
         *
         * Selector: `0x70a08231`
         *
         * Signature:
         * ```solidity
         *     function balanceOf(address account) external returns (uint256);
         * ```
         */
        @JvmStatic
        public fun balanceOf(
            provider: Middleware,
            address: Address,
            account: Address,
        ): ReadFunctionCall<BigInteger> = ReadFunctionCall(provider, address, FUNCTION_BALANCE_OF.encodeCall(listOf<Any>(account))) {
            val data = FUNCTION_BALANCE_OF.decodeResponse(it)
            data[0] as io.github.artificialpb.bignum.BigInteger
        }

        /**
         * Call contract function
         *
         * Selector: `0xb760faf9`
         *
         * Signature:
         * ```solidity
         *     function depositTo(address account) external payable;
         * ```
         */
        @JvmStatic
        public fun depositTo(
            provider: Middleware,
            address: Address,
            account: Address,
        ): PayableFunctionCall<Unit> = PayableFunctionCall(provider, address, FUNCTION_DEPOSIT_TO.encodeCall(listOf<Any>(account))) {
        }

        /**
         * Call contract function
         *
         * Selector: `0xfc7e286d`
         *
         * Signature:
         * ```solidity
         *     function deposits(address) external returns (uint112 deposit, bool staked, uint112 stake, uint32 unstakeDelaySec, uint48 withdrawTime);
         * ```
         */
        @JvmStatic
        public fun deposits(
            provider: Middleware,
            address: Address,
            arg2: Address,
        ): ReadFunctionCall<EntryPointV06.DepositsResult> = ReadFunctionCall(provider, address, FUNCTION_DEPOSITS.encodeCall(listOf<Any>(arg2))) {
            val data = FUNCTION_DEPOSITS.decodeResponse(it)
            EntryPointV06.DepositsResult(data[0] as io.github.artificialpb.bignum.BigInteger, data[1] as kotlin.Boolean, data[2] as io.github.artificialpb.bignum.BigInteger, data[3] as io.github.artificialpb.bignum.BigInteger, data[4] as io.github.artificialpb.bignum.BigInteger)
        }

        /**
         * Call contract function
         *
         * Selector: `0x5287ce12`
         *
         * Signature:
         * ```solidity
         *     function getDepositInfo(address account) external returns (DepositInfo info);
         * ```
         */
        @JvmStatic
        public fun getDepositInfo(
            provider: Middleware,
            address: Address,
            account: Address,
        ): ReadFunctionCall<EntryPointV06.DepositInfo> = ReadFunctionCall(provider, address, FUNCTION_GET_DEPOSIT_INFO.encodeCall(listOf<Any>(account))) {
            val data = FUNCTION_GET_DEPOSIT_INFO.decodeResponse(it)
            data[0] as EntryPointV06.DepositInfo
        }

        /**
         * Call contract function
         *
         * Selector: `0x35567e1a`
         *
         * Signature:
         * ```solidity
         *     function getNonce(address sender, uint192 key) external returns (uint256 nonce);
         * ```
         */
        @JvmStatic
        public fun getNonce(
            provider: Middleware,
            address: Address,
            sender: Address,
            key: BigInteger,
        ): ReadFunctionCall<BigInteger> = ReadFunctionCall(provider, address, FUNCTION_GET_NONCE.encodeCall(listOf<Any>(sender, key))) {
            val data = FUNCTION_GET_NONCE.decodeResponse(it)
            data[0] as io.github.artificialpb.bignum.BigInteger
        }

        /**
         * Call contract function
         *
         * Selector: `0x9b249f69`
         *
         * Signature:
         * ```solidity
         *     function getSenderAddress(bytes initCode) external;
         * ```
         */
        @JvmStatic
        public fun getSenderAddress(
            provider: Middleware,
            address: Address,
            initCode: Bytes,
        ): FunctionCall<Unit> = FunctionCall(provider, address, FUNCTION_GET_SENDER_ADDRESS.encodeCall(listOf<Any>(initCode))) {
        }

        /**
         * Call contract function
         *
         * Selector: `0xa6193531`
         *
         * Signature:
         * ```solidity
         *     function getUserOpHash(UserOperation userOp) external returns (bytes32);
         * ```
         */
        @JvmStatic
        public fun getUserOpHash(
            provider: Middleware,
            address: Address,
            userOp: EntryPointV06.UserOperation,
        ): ReadFunctionCall<Bytes> = ReadFunctionCall(provider, address, FUNCTION_GET_USER_OP_HASH.encodeCall(listOf<Any>(userOp))) {
            val data = FUNCTION_GET_USER_OP_HASH.decodeResponse(it)
            data[0] as io.ethers.core.types.Bytes
        }

        /**
         * Call contract function
         *
         * Selector: `0x4b1d7cf5`
         *
         * Signature:
         * ```solidity
         *     function handleAggregatedOps(UserOpsPerAggregator[] opsPerAggregator, address beneficiary) external;
         * ```
         */
        @JvmStatic
        public fun handleAggregatedOps(
            provider: Middleware,
            address: Address,
            opsPerAggregator: List<EntryPointV06.UserOpsPerAggregator>,
            beneficiary: Address,
        ): FunctionCall<Unit> = FunctionCall(provider, address, FUNCTION_HANDLE_AGGREGATED_OPS.encodeCall(listOf<Any>(opsPerAggregator, beneficiary))) {
        }

        /**
         * Call contract function
         *
         * Selector: `0x1fad948c`
         *
         * Signature:
         * ```solidity
         *     function handleOps(UserOperation[] ops, address beneficiary) external;
         * ```
         */
        @JvmStatic
        public fun handleOps(
            provider: Middleware,
            address: Address,
            ops: List<EntryPointV06.UserOperation>,
            beneficiary: Address,
        ): FunctionCall<Unit> = FunctionCall(provider, address, FUNCTION_HANDLE_OPS.encodeCall(listOf<Any>(ops, beneficiary))) {
        }

        /**
         * Call contract function
         *
         * Selector: `0x0bd28e3b`
         *
         * Signature:
         * ```solidity
         *     function incrementNonce(uint192 key) external;
         * ```
         */
        @JvmStatic
        public fun incrementNonce(
            provider: Middleware,
            address: Address,
            key: BigInteger,
        ): FunctionCall<Unit> = FunctionCall(provider, address, FUNCTION_INCREMENT_NONCE.encodeCall(listOf<Any>(key))) {
        }

        /**
         * Call contract function
         *
         * Selector: `0x1d732756`
         *
         * Signature:
         * ```solidity
         *     function innerHandleOp(bytes callData, UserOpInfo opInfo, bytes context) external returns (uint256 actualGasCost);
         * ```
         */
        @JvmStatic
        public fun innerHandleOp(
            provider: Middleware,
            address: Address,
            callData: Bytes,
            opInfo: EntryPointV06.UserOpInfo,
            context: Bytes,
        ): FunctionCall<BigInteger> = FunctionCall(provider, address, FUNCTION_INNER_HANDLE_OP.encodeCall(listOf<Any>(callData, opInfo, context))) {
            val data = FUNCTION_INNER_HANDLE_OP.decodeResponse(it)
            data[0] as io.github.artificialpb.bignum.BigInteger
        }

        /**
         * Call contract function
         *
         * Selector: `0x1b2e01b8`
         *
         * Signature:
         * ```solidity
         *     function nonceSequenceNumber(address, uint192) external returns (uint256);
         * ```
         */
        @JvmStatic
        public fun nonceSequenceNumber(
            provider: Middleware,
            address: Address,
            arg2: Address,
            arg3: BigInteger,
        ): ReadFunctionCall<BigInteger> = ReadFunctionCall(provider, address, FUNCTION_NONCE_SEQUENCE_NUMBER.encodeCall(listOf<Any>(arg2, arg3))) {
            val data = FUNCTION_NONCE_SEQUENCE_NUMBER.decodeResponse(it)
            data[0] as io.github.artificialpb.bignum.BigInteger
        }

        /**
         * Call contract function
         *
         * Selector: `0xd6383f94`
         *
         * Signature:
         * ```solidity
         *     function simulateHandleOp(UserOperation op, address target, bytes targetCallData) external;
         * ```
         */
        @JvmStatic
        public fun simulateHandleOp(
            provider: Middleware,
            address: Address,
            op: EntryPointV06.UserOperation,
            target: Address,
            targetCallData: Bytes,
        ): FunctionCall<Unit> = FunctionCall(provider, address, FUNCTION_SIMULATE_HANDLE_OP.encodeCall(listOf<Any>(op, target, targetCallData))) {
        }

        /**
         * Call contract function
         *
         * Selector: `0xee219423`
         *
         * Signature:
         * ```solidity
         *     function simulateValidation(UserOperation userOp) external;
         * ```
         */
        @JvmStatic
        public fun simulateValidation(
            provider: Middleware,
            address: Address,
            userOp: EntryPointV06.UserOperation,
        ): FunctionCall<Unit> = FunctionCall(provider, address, FUNCTION_SIMULATE_VALIDATION.encodeCall(listOf<Any>(userOp))) {
        }

        /**
         * Call contract function
         *
         * Selector: `0xbb9fe6bf`
         *
         * Signature:
         * ```solidity
         *     function unlockStake() external;
         * ```
         */
        @JvmStatic
        public fun unlockStake(provider: Middleware, address: Address): FunctionCall<Unit> = FunctionCall(provider, address, FUNCTION_UNLOCK_STAKE.encodeCall(emptyList())) {
        }

        /**
         * Call contract function
         *
         * Selector: `0xc23a5cea`
         *
         * Signature:
         * ```solidity
         *     function withdrawStake(address withdrawAddress) external;
         * ```
         */
        @JvmStatic
        public fun withdrawStake(
            provider: Middleware,
            address: Address,
            withdrawAddress: Address,
        ): FunctionCall<Unit> = FunctionCall(provider, address, FUNCTION_WITHDRAW_STAKE.encodeCall(listOf<Any>(withdrawAddress))) {
        }

        /**
         * Call contract function
         *
         * Selector: `0x205c2878`
         *
         * Signature:
         * ```solidity
         *     function withdrawTo(address withdrawAddress, uint256 withdrawAmount) external;
         * ```
         */
        @JvmStatic
        public fun withdrawTo(
            provider: Middleware,
            address: Address,
            withdrawAddress: Address,
            withdrawAmount: BigInteger,
        ): FunctionCall<Unit> = FunctionCall(provider, address, FUNCTION_WITHDRAW_TO.encodeCall(listOf<Any>(withdrawAddress, withdrawAmount))) {
        }

        @JvmStatic
        public fun decodeErrorOrNull(error: Bytes): EntryPointV06.Error? {
            for (err in ERRORS) {
                return err.decodeOrNull(error) ?: continue
            }
            return null
        }

        @JvmStatic
        public fun tryDecodeError(error: Bytes): Result<EntryPointV06.Error, ContractErrorDecodingError> {
            for (err in ERRORS) {
                when (val decoded = err.tryDecode(error)) {
                    is Result.Success -> return Result.success(decoded.value)
                    is Result.Failure -> {
                        if (decoded.error !is ContractErrorDecodingError.NoMatchingError) {
                            return Result.failure(decoded.error)
                        }
                    }
                }
            }
            return Result.failure(ContractErrorDecodingError.NoMatchingError(error, ERRORS.map { it.abi }))
        }

        @JvmStatic
        public fun decodeEventOrNull(log: Log): EntryPointV06.Event? {
            for (event in EVENTS) {
                return event.decodeOrNull(log) ?: continue
            }
            return null
        }

        @JvmStatic
        public fun tryDecodeEvent(log: Log): Result<EntryPointV06.Event, EventDecodingError> {
            for (event in EVENTS) {
                when (val decoded = event.tryDecode(log)) {
                    is Result.Success -> return Result.success(decoded.value)
                    is Result.Failure -> {
                        if (decoded.error !is EventDecodingError.NoMatchingEvent) {
                            return Result.failure(decoded.error)
                        }
                    }
                }
            }
            return Result.failure(EventDecodingError.NoMatchingEvent(log, EVENTS.map { it.abi }))
        }
    }
}

@JvmInline
public value class EntryPointV06Scope(
    private val provider: Middleware,
) {
    public fun receive(address: Address, `value`: BigInteger): ReceiveFunctionCall = EntryPointV06.receive(provider, address, value)

    public fun SIG_VALIDATION_FAILED(address: Address): ReadFunctionCall<BigInteger> = EntryPointV06.SIG_VALIDATION_FAILED(provider, address)

    public fun _validateSenderAndPaymaster(
        address: Address,
        initCode: Bytes,
        sender: Address,
        paymasterAndData: Bytes,
    ): ReadFunctionCall<Unit> = EntryPointV06._validateSenderAndPaymaster(provider, address, initCode, sender, paymasterAndData)

    public fun addStake(address: Address, unstakeDelaySec: BigInteger): PayableFunctionCall<Unit> = EntryPointV06.addStake(provider, address, unstakeDelaySec)

    public fun balanceOf(address: Address, account: Address): ReadFunctionCall<BigInteger> = EntryPointV06.balanceOf(provider, address, account)

    public fun depositTo(address: Address, account: Address): PayableFunctionCall<Unit> = EntryPointV06.depositTo(provider, address, account)

    public fun deposits(address: Address, arg1: Address): ReadFunctionCall<EntryPointV06.DepositsResult> = EntryPointV06.deposits(provider, address, arg1)

    public fun getDepositInfo(address: Address, account: Address): ReadFunctionCall<EntryPointV06.DepositInfo> = EntryPointV06.getDepositInfo(provider, address, account)

    public fun getNonce(
        address: Address,
        sender: Address,
        key: BigInteger,
    ): ReadFunctionCall<BigInteger> = EntryPointV06.getNonce(provider, address, sender, key)

    public fun getSenderAddress(address: Address, initCode: Bytes): FunctionCall<Unit> = EntryPointV06.getSenderAddress(provider, address, initCode)

    public fun getUserOpHash(address: Address, userOp: EntryPointV06.UserOperation): ReadFunctionCall<Bytes> = EntryPointV06.getUserOpHash(provider, address, userOp)

    public fun handleAggregatedOps(
        address: Address,
        opsPerAggregator: List<EntryPointV06.UserOpsPerAggregator>,
        beneficiary: Address,
    ): FunctionCall<Unit> = EntryPointV06.handleAggregatedOps(provider, address, opsPerAggregator, beneficiary)

    public fun handleOps(
        address: Address,
        ops: List<EntryPointV06.UserOperation>,
        beneficiary: Address,
    ): FunctionCall<Unit> = EntryPointV06.handleOps(provider, address, ops, beneficiary)

    public fun incrementNonce(address: Address, key: BigInteger): FunctionCall<Unit> = EntryPointV06.incrementNonce(provider, address, key)

    public fun innerHandleOp(
        address: Address,
        callData: Bytes,
        opInfo: EntryPointV06.UserOpInfo,
        context: Bytes,
    ): FunctionCall<BigInteger> = EntryPointV06.innerHandleOp(provider, address, callData, opInfo, context)

    public fun nonceSequenceNumber(
        address: Address,
        arg1: Address,
        arg2: BigInteger,
    ): ReadFunctionCall<BigInteger> = EntryPointV06.nonceSequenceNumber(provider, address, arg1, arg2)

    public fun simulateHandleOp(
        address: Address,
        op: EntryPointV06.UserOperation,
        target: Address,
        targetCallData: Bytes,
    ): FunctionCall<Unit> = EntryPointV06.simulateHandleOp(provider, address, op, target, targetCallData)

    public fun simulateValidation(address: Address, userOp: EntryPointV06.UserOperation): FunctionCall<Unit> = EntryPointV06.simulateValidation(provider, address, userOp)

    public fun unlockStake(address: Address): FunctionCall<Unit> = EntryPointV06.unlockStake(provider, address)

    public fun withdrawStake(address: Address, withdrawAddress: Address): FunctionCall<Unit> = EntryPointV06.withdrawStake(provider, address, withdrawAddress)

    public fun withdrawTo(
        address: Address,
        withdrawAddress: Address,
        withdrawAmount: BigInteger,
    ): FunctionCall<Unit> = EntryPointV06.withdrawTo(provider, address, withdrawAddress, withdrawAmount)
}

public val Middleware.entryPointV06: EntryPointV06Scope
    get() = EntryPointV06Scope(this)
