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
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.jvm.JvmField
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

public class EntryPointV07(
    provider: Middleware,
    address: Address,
) : AbiContract(provider, address) {
    public fun receive(`value`: BigInteger): ReceiveFunctionCall = Companion.receive(this.provider, this.address, value)

    public fun addStake(unstakeDelaySec: BigInteger): PayableFunctionCall<Unit> = Companion.addStake(this.provider, this.address, unstakeDelaySec)

    public fun balanceOf(account: Address): ReadFunctionCall<BigInteger> = Companion.balanceOf(this.provider, this.address, account)

    public fun delegateAndRevert(target: Address, `data`: Bytes): FunctionCall<Unit> = Companion.delegateAndRevert(this.provider, this.address, target,`data`)

    public fun depositTo(account: Address): PayableFunctionCall<Unit> = Companion.depositTo(this.provider, this.address, account)

    public fun deposits(arg0: Address): ReadFunctionCall<EntryPointV07.DepositsResult> = Companion.deposits(this.provider, this.address, arg0)

    public fun getDepositInfo(account: Address): ReadFunctionCall<EntryPointV07.DepositInfo> = Companion.getDepositInfo(this.provider, this.address, account)

    public fun getNonce(sender: Address, key: BigInteger): ReadFunctionCall<BigInteger> = Companion.getNonce(this.provider, this.address, sender,key)

    public fun getSenderAddress(initCode: Bytes): FunctionCall<Unit> = Companion.getSenderAddress(this.provider, this.address, initCode)

    public fun getUserOpHash(userOp: EntryPointV07.PackedUserOperation): ReadFunctionCall<Bytes> = Companion.getUserOpHash(this.provider, this.address, userOp)

    public fun handleAggregatedOps(opsPerAggregator: List<EntryPointV07.UserOpsPerAggregator>, beneficiary: Address): FunctionCall<Unit> = Companion.handleAggregatedOps(this.provider, this.address, opsPerAggregator,beneficiary)

    public fun handleOps(ops: List<EntryPointV07.PackedUserOperation>, beneficiary: Address): FunctionCall<Unit> = Companion.handleOps(this.provider, this.address, ops,beneficiary)

    public fun incrementNonce(key: BigInteger): FunctionCall<Unit> = Companion.incrementNonce(this.provider, this.address, key)

    public fun innerHandleOp(
        callData: Bytes,
        opInfo: EntryPointV07.UserOpInfo,
        context: Bytes,
    ): FunctionCall<BigInteger> = Companion.innerHandleOp(this.provider, this.address, callData,opInfo,context)

    public fun nonceSequenceNumber(arg0: Address, arg1: BigInteger): ReadFunctionCall<BigInteger> = Companion.nonceSequenceNumber(this.provider, this.address, arg0,arg1)

    public fun supportsInterface(interfaceId: Bytes): ReadFunctionCall<Boolean> = Companion.supportsInterface(this.provider, this.address, interfaceId)

    public fun unlockStake(): FunctionCall<Unit> = Companion.unlockStake(this.provider, this.address)

    public fun withdrawStake(withdrawAddress: Address): FunctionCall<Unit> = Companion.withdrawStake(this.provider, this.address, withdrawAddress)

    public fun withdrawTo(withdrawAddress: Address, withdrawAmount: BigInteger): FunctionCall<Unit> = Companion.withdrawTo(this.provider, this.address, withdrawAddress,withdrawAmount)

    public sealed class Error : CustomContractError()

    public sealed class Event : ContractEvent

    /**
     * Contract error
     *     
     * Selector: `0x99410554`
     *
     * Signature:
     * ```solidity
     *     error DelegateAndRevert(bool success, bytes ret);
     * ```
     */
    public data class DelegateAndRevert(
        public val success: Boolean,
        public val ret: Bytes,
    ) : EntryPointV07.Error() {
        public companion object : CustomErrorFactory<DelegateAndRevert> {
            @JvmStatic
            override val abi: AbiFunction =
                    AbiFunction("DelegateAndRevert", listOf(AbiType.Bool, AbiType.Bytes), emptyList())

            @JvmStatic
            override fun decodeOrNull(`data`: Bytes): DelegateAndRevert? = super.decodeOrNull(data)

            @JvmStatic
            override fun tryDecode(`data`: Bytes): Result<DelegateAndRevert, ContractErrorDecodingError> = super.tryDecode(data)

            @JvmStatic
            override fun decode(`data`: List<Any>): DelegateAndRevert = DelegateAndRevert(data[0] as kotlin.Boolean,data[1] as io.ethers.core.types.Bytes)
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
    ) : EntryPointV07.Error() {
        public companion object : CustomErrorFactory<FailedOp> {
            @JvmStatic
            override val abi: AbiFunction =
                    AbiFunction("FailedOp", listOf(AbiType.UInt(256), AbiType.String), emptyList())

            @JvmStatic
            override fun decodeOrNull(`data`: Bytes): FailedOp? = super.decodeOrNull(data)

            @JvmStatic
            override fun tryDecode(`data`: Bytes): Result<FailedOp, ContractErrorDecodingError> = super.tryDecode(data)

            @JvmStatic
            override fun decode(`data`: List<Any>): FailedOp = FailedOp(data[0] as io.github.artificialpb.bignum.BigInteger,data[1] as kotlin.String)
        }
    }

    /**
     * Contract error
     *     
     * Selector: `0x65c8fd4d`
     *
     * Signature:
     * ```solidity
     *     error FailedOpWithRevert(uint256 opIndex, string reason, bytes inner);
     * ```
     */
    public data class FailedOpWithRevert(
        public val opIndex: BigInteger,
        public val reason: String,
        public val `inner`: Bytes,
    ) : EntryPointV07.Error() {
        public companion object : CustomErrorFactory<FailedOpWithRevert> {
            @JvmStatic
            override val abi: AbiFunction =
                    AbiFunction("FailedOpWithRevert", listOf(AbiType.UInt(256), AbiType.String, AbiType.Bytes), emptyList())

            @JvmStatic
            override fun decodeOrNull(`data`: Bytes): FailedOpWithRevert? = super.decodeOrNull(data)

            @JvmStatic
            override fun tryDecode(`data`: Bytes): Result<FailedOpWithRevert, ContractErrorDecodingError> = super.tryDecode(data)

            @JvmStatic
            override fun decode(`data`: List<Any>): FailedOpWithRevert = FailedOpWithRevert(data[0] as io.github.artificialpb.bignum.BigInteger,data[1] as kotlin.String,data[2] as io.ethers.core.types.Bytes)
        }
    }

    /**
     * Contract error
     *     
     * Selector: `0xad7954bc`
     *
     * Signature:
     * ```solidity
     *     error PostOpReverted(bytes returnData);
     * ```
     */
    public data class PostOpReverted(
        public val returnData: Bytes,
    ) : EntryPointV07.Error() {
        public companion object : CustomErrorFactory<PostOpReverted> {
            @JvmStatic
            override val abi: AbiFunction =
                    AbiFunction("PostOpReverted", listOf(AbiType.Bytes), emptyList())

            @JvmStatic
            override fun decodeOrNull(`data`: Bytes): PostOpReverted? = super.decodeOrNull(data)

            @JvmStatic
            override fun tryDecode(`data`: Bytes): Result<PostOpReverted, ContractErrorDecodingError> = super.tryDecode(data)

            @JvmStatic
            override fun decode(`data`: List<Any>): PostOpReverted = PostOpReverted(data[0] as io.ethers.core.types.Bytes)
        }
    }

    /**
     * Contract error
     *     
     * Selector: `0x3ee5aeb5`
     *
     * Signature:
     * ```solidity
     *     error ReentrancyGuardReentrantCall();
     * ```
     */
    public class ReentrancyGuardReentrantCall() : EntryPointV07.Error() {
        override fun toString(): String = "ReentrancyGuardReentrantCall"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            return true
        }

        override fun hashCode(): Int = this::class.hashCode()

        public companion object : CustomErrorFactory<ReentrancyGuardReentrantCall> {
            @JvmStatic
            override val abi: AbiFunction =
                    AbiFunction("ReentrancyGuardReentrantCall", listOf(), emptyList())

            @JvmField
            public val INSTANCE: ReentrancyGuardReentrantCall = ReentrancyGuardReentrantCall()

            @JvmStatic
            override fun decodeOrNull(`data`: Bytes): ReentrancyGuardReentrantCall? = super.decodeOrNull(data)

            @JvmStatic
            override fun tryDecode(`data`: Bytes): Result<ReentrancyGuardReentrantCall, ContractErrorDecodingError> = super.tryDecode(data)

            @JvmStatic
            override fun decode(`data`: List<Any>): ReentrancyGuardReentrantCall = INSTANCE
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
    ) : EntryPointV07.Error() {
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
    ) : EntryPointV07.Error() {
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
    ) : EntryPointV07.Event() {
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
            override fun decode(log: Log, `data`: List<Any>): AccountDeployed = AccountDeployed(data[0] as io.ethers.core.types.Bytes,data[1] as io.ethers.core.types.Address,data[2] as io.ethers.core.types.Address,data[3] as io.ethers.core.types.Address,log)
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
    ) : EntryPointV07.Event() {
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
    ) : EntryPointV07.Event() {
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
            override fun decode(log: Log, `data`: List<Any>): Deposited = Deposited(data[0] as io.ethers.core.types.Address,data[1] as io.github.artificialpb.bignum.BigInteger,log)
        }
    }

    /**
     * Contract event (indexed dynamic types are replaced with `bytes32`)
     *
     * Topic ID: `0xf62676f440ff169a3a9afdbf812e89e7f95975ee8e5c31214ffdef631c5f4792`
     *
     * Anonymous: `false`
     *
     * Signature:
     * ```solidity
     *     event PostOpRevertReason(bytes32 indexed userOpHash, address indexed sender, uint256 nonce, bytes revertReason);
     * ```
     */
    public data class PostOpRevertReason(
        public val userOpHash: Bytes,
        public val sender: Address,
        public val nonce: BigInteger,
        public val revertReason: Bytes,
        override val log: Log,
    ) : EntryPointV07.Event() {
        public class Filter(
            provider: Middleware,
            factory: EventFactory<PostOpRevertReason>,
        ) : EventFilterBase<PostOpRevertReason, PostOpRevertReason.Filter>(provider, factory) {
            override val self: PostOpRevertReason.Filter = this

            init {
                filter.topic0(factory.abi.topicId)
            }

            public fun userOpHash(userOpHash: Bytes): PostOpRevertReason.Filter = topic1(userOpHash)

            public fun userOpHash(vararg userOpHash: Bytes): PostOpRevertReason.Filter = topic1(*userOpHash)

            public fun sender(sender: Address): PostOpRevertReason.Filter = topic2(sender)

            public fun sender(vararg sender: Address): PostOpRevertReason.Filter = topic2(*sender)
        }

        public companion object : EventFactory<PostOpRevertReason> {
            @JvmStatic
            override val abi: AbiEvent =
                    AbiEvent("PostOpRevertReason", listOf(AbiEvent.Token(AbiType.FixedBytes(32), true), AbiEvent.Token(AbiType.Address, true), AbiEvent.Token(AbiType.UInt(256), false), AbiEvent.Token(AbiType.Bytes, false)), false)

            @JvmStatic
            override fun filter(provider: Middleware): PostOpRevertReason.Filter = PostOpRevertReason.Filter(provider, this)

            @JvmStatic
            override fun isLogValid(log: Log): Boolean = super.isLogValid(log)

            @JvmStatic
            override fun decodeOrNull(log: Log): PostOpRevertReason? = super.decodeOrNull(log)

            @JvmStatic
            override fun tryDecode(log: Log): Result<PostOpRevertReason, EventDecodingError> = super.tryDecode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): PostOpRevertReason = PostOpRevertReason(data[0] as io.ethers.core.types.Bytes,data[1] as io.ethers.core.types.Address,data[2] as io.github.artificialpb.bignum.BigInteger,data[3] as io.ethers.core.types.Bytes,log)
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
    ) : EntryPointV07.Event() {
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
            override fun decode(log: Log, `data`: List<Any>): SignatureAggregatorChanged = SignatureAggregatorChanged(data[0] as io.ethers.core.types.Address,log)
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
    ) : EntryPointV07.Event() {
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
            override fun decode(log: Log, `data`: List<Any>): StakeLocked = StakeLocked(data[0] as io.ethers.core.types.Address,data[1] as io.github.artificialpb.bignum.BigInteger,data[2] as io.github.artificialpb.bignum.BigInteger,log)
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
    ) : EntryPointV07.Event() {
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
            override fun decode(log: Log, `data`: List<Any>): StakeUnlocked = StakeUnlocked(data[0] as io.ethers.core.types.Address,data[1] as io.github.artificialpb.bignum.BigInteger,log)
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
    ) : EntryPointV07.Event() {
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
            override fun decode(log: Log, `data`: List<Any>): StakeWithdrawn = StakeWithdrawn(data[0] as io.ethers.core.types.Address,data[1] as io.ethers.core.types.Address,data[2] as io.github.artificialpb.bignum.BigInteger,log)
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
    ) : EntryPointV07.Event() {
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
            override fun decode(log: Log, `data`: List<Any>): UserOperationEvent = UserOperationEvent(data[0] as io.ethers.core.types.Bytes,data[1] as io.ethers.core.types.Address,data[2] as io.ethers.core.types.Address,data[3] as io.github.artificialpb.bignum.BigInteger,data[4] as kotlin.Boolean,data[5] as io.github.artificialpb.bignum.BigInteger,data[6] as io.github.artificialpb.bignum.BigInteger,log)
        }
    }

    /**
     * Contract event (indexed dynamic types are replaced with `bytes32`)
     *
     * Topic ID: `0x67b4fa9642f42120bf031f3051d1824b0fe25627945b27b8a6a65d5761d5482e`
     *
     * Anonymous: `false`
     *
     * Signature:
     * ```solidity
     *     event UserOperationPrefundTooLow(bytes32 indexed userOpHash, address indexed sender, uint256 nonce);
     * ```
     */
    public data class UserOperationPrefundTooLow(
        public val userOpHash: Bytes,
        public val sender: Address,
        public val nonce: BigInteger,
        override val log: Log,
    ) : EntryPointV07.Event() {
        public class Filter(
            provider: Middleware,
            factory: EventFactory<UserOperationPrefundTooLow>,
        ) : EventFilterBase<UserOperationPrefundTooLow, UserOperationPrefundTooLow.Filter>(provider, factory) {
            override val self: UserOperationPrefundTooLow.Filter = this

            init {
                filter.topic0(factory.abi.topicId)
            }

            public fun userOpHash(userOpHash: Bytes): UserOperationPrefundTooLow.Filter = topic1(userOpHash)

            public fun userOpHash(vararg userOpHash: Bytes): UserOperationPrefundTooLow.Filter = topic1(*userOpHash)

            public fun sender(sender: Address): UserOperationPrefundTooLow.Filter = topic2(sender)

            public fun sender(vararg sender: Address): UserOperationPrefundTooLow.Filter = topic2(*sender)
        }

        public companion object : EventFactory<UserOperationPrefundTooLow> {
            @JvmStatic
            override val abi: AbiEvent =
                    AbiEvent("UserOperationPrefundTooLow", listOf(AbiEvent.Token(AbiType.FixedBytes(32), true), AbiEvent.Token(AbiType.Address, true), AbiEvent.Token(AbiType.UInt(256), false)), false)

            @JvmStatic
            override fun filter(provider: Middleware): UserOperationPrefundTooLow.Filter = UserOperationPrefundTooLow.Filter(provider, this)

            @JvmStatic
            override fun isLogValid(log: Log): Boolean = super.isLogValid(log)

            @JvmStatic
            override fun decodeOrNull(log: Log): UserOperationPrefundTooLow? = super.decodeOrNull(log)

            @JvmStatic
            override fun tryDecode(log: Log): Result<UserOperationPrefundTooLow, EventDecodingError> = super.tryDecode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): UserOperationPrefundTooLow = UserOperationPrefundTooLow(data[0] as io.ethers.core.types.Bytes,data[1] as io.ethers.core.types.Address,data[2] as io.github.artificialpb.bignum.BigInteger,log)
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
    ) : EntryPointV07.Event() {
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
            override fun decode(log: Log, `data`: List<Any>): UserOperationRevertReason = UserOperationRevertReason(data[0] as io.ethers.core.types.Bytes,data[1] as io.ethers.core.types.Address,data[2] as io.github.artificialpb.bignum.BigInteger,data[3] as io.ethers.core.types.Bytes,log)
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
    ) : EntryPointV07.Event() {
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
            override fun decode(log: Log, `data`: List<Any>): Withdrawn = Withdrawn(data[0] as io.ethers.core.types.Address,data[1] as io.ethers.core.types.Address,data[2] as io.github.artificialpb.bignum.BigInteger,log)
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
     *     struct UserOpsPerAggregator {
     *         PackedUserOperation[] userOps;
     *         address aggregator;
     *         bytes signature;
     *     }
     * ```
     */
    public data class UserOpsPerAggregator(
        public val userOps: List<EntryPointV07.PackedUserOperation>,
        public val aggregator: Address,
        public val signature: Bytes,
    ) : ContractStruct {
        override val tuple: List<Any> = listOf(userOps, aggregator, signature)

        override val abiType: AbiType.Struct<EntryPointV07.UserOpsPerAggregator>
            get() = abi

        public companion object : StructFactory<EntryPointV07.UserOpsPerAggregator> {
            @JvmStatic
            override val abi: AbiType.Struct<EntryPointV07.UserOpsPerAggregator> =
                    AbiType.Struct(EntryPointV07.UserOpsPerAggregator::class, ::fromTuple, AbiType.Struct.Field("userOps", AbiType.Array(PackedUserOperation.abi)),
                    AbiType.Struct.Field("aggregator", AbiType.Address),
                    AbiType.Struct.Field("signature", AbiType.Bytes))

            @JvmStatic
            override fun fromTuple(`data`: List<Any>): EntryPointV07.UserOpsPerAggregator = EntryPointV07.UserOpsPerAggregator(data[0] as kotlin.collections.List<EntryPointV07.PackedUserOperation>, data[1] as io.ethers.core.types.Address, data[2] as io.ethers.core.types.Bytes)
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
        public val mUserOp: EntryPointV07.MemoryUserOp,
        public val userOpHash: Bytes,
        public val prefund: BigInteger,
        public val contextOffset: BigInteger,
        public val preOpGas: BigInteger,
    ) : ContractStruct {
        override val tuple: List<Any> =
                listOf(mUserOp, userOpHash, prefund, contextOffset, preOpGas)

        override val abiType: AbiType.Struct<EntryPointV07.UserOpInfo>
            get() = abi

        public companion object : StructFactory<EntryPointV07.UserOpInfo> {
            @JvmStatic
            override val abi: AbiType.Struct<EntryPointV07.UserOpInfo> =
                    AbiType.Struct(EntryPointV07.UserOpInfo::class, ::fromTuple, AbiType.Struct.Field("mUserOp", MemoryUserOp.abi),
                    AbiType.Struct.Field("userOpHash", AbiType.FixedBytes(32)),
                    AbiType.Struct.Field("prefund", AbiType.UInt(256)),
                    AbiType.Struct.Field("contextOffset", AbiType.UInt(256)),
                    AbiType.Struct.Field("preOpGas", AbiType.UInt(256)))

            @JvmStatic
            override fun fromTuple(`data`: List<Any>): EntryPointV07.UserOpInfo = EntryPointV07.UserOpInfo(data[0] as EntryPointV07.MemoryUserOp, data[1] as io.ethers.core.types.Bytes, data[2] as io.github.artificialpb.bignum.BigInteger, data[3] as io.github.artificialpb.bignum.BigInteger, data[4] as io.github.artificialpb.bignum.BigInteger)
        }
    }

    /**
     * Contract struct
     *
     * Signature:
     * ```solidity
     *     struct DepositInfo {
     *         uint256 deposit;
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

        override val abiType: AbiType.Struct<EntryPointV07.DepositInfo>
            get() = abi

        public companion object : StructFactory<EntryPointV07.DepositInfo> {
            @JvmStatic
            override val abi: AbiType.Struct<EntryPointV07.DepositInfo> =
                    AbiType.Struct(EntryPointV07.DepositInfo::class, ::fromTuple, AbiType.Struct.Field("deposit", AbiType.UInt(256)),
                    AbiType.Struct.Field("staked", AbiType.Bool),
                    AbiType.Struct.Field("stake", AbiType.UInt(112)),
                    AbiType.Struct.Field("unstakeDelaySec", AbiType.UInt(32)),
                    AbiType.Struct.Field("withdrawTime", AbiType.UInt(48)))

            @JvmStatic
            override fun fromTuple(`data`: List<Any>): EntryPointV07.DepositInfo = EntryPointV07.DepositInfo(data[0] as io.github.artificialpb.bignum.BigInteger, data[1] as kotlin.Boolean, data[2] as io.github.artificialpb.bignum.BigInteger, data[3] as io.github.artificialpb.bignum.BigInteger, data[4] as io.github.artificialpb.bignum.BigInteger)
        }
    }

    /**
     * Contract struct
     *
     * Signature:
     * ```solidity
     *     struct PackedUserOperation {
     *         address sender;
     *         uint256 nonce;
     *         bytes initCode;
     *         bytes callData;
     *         bytes32 accountGasLimits;
     *         uint256 preVerificationGas;
     *         bytes32 gasFees;
     *         bytes paymasterAndData;
     *         bytes signature;
     *     }
     * ```
     */
    public data class PackedUserOperation(
        public val sender: Address,
        public val nonce: BigInteger,
        public val initCode: Bytes,
        public val callData: Bytes,
        public val accountGasLimits: Bytes,
        public val preVerificationGas: BigInteger,
        public val gasFees: Bytes,
        public val paymasterAndData: Bytes,
        public val signature: Bytes,
    ) : ContractStruct {
        override val tuple: List<Any> =
                listOf(sender, nonce, initCode, callData, accountGasLimits, preVerificationGas, gasFees, paymasterAndData, signature)

        override val abiType: AbiType.Struct<EntryPointV07.PackedUserOperation>
            get() = abi

        public companion object : StructFactory<EntryPointV07.PackedUserOperation> {
            @JvmStatic
            override val abi: AbiType.Struct<EntryPointV07.PackedUserOperation> =
                    AbiType.Struct(EntryPointV07.PackedUserOperation::class, ::fromTuple, AbiType.Struct.Field("sender", AbiType.Address),
                    AbiType.Struct.Field("nonce", AbiType.UInt(256)),
                    AbiType.Struct.Field("initCode", AbiType.Bytes),
                    AbiType.Struct.Field("callData", AbiType.Bytes),
                    AbiType.Struct.Field("accountGasLimits", AbiType.FixedBytes(32)),
                    AbiType.Struct.Field("preVerificationGas", AbiType.UInt(256)),
                    AbiType.Struct.Field("gasFees", AbiType.FixedBytes(32)),
                    AbiType.Struct.Field("paymasterAndData", AbiType.Bytes),
                    AbiType.Struct.Field("signature", AbiType.Bytes))

            @JvmStatic
            override fun fromTuple(`data`: List<Any>): EntryPointV07.PackedUserOperation = EntryPointV07.PackedUserOperation(data[0] as io.ethers.core.types.Address, data[1] as io.github.artificialpb.bignum.BigInteger, data[2] as io.ethers.core.types.Bytes, data[3] as io.ethers.core.types.Bytes, data[4] as io.ethers.core.types.Bytes, data[5] as io.github.artificialpb.bignum.BigInteger, data[6] as io.ethers.core.types.Bytes, data[7] as io.ethers.core.types.Bytes, data[8] as io.ethers.core.types.Bytes)
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
     *         uint256 verificationGasLimit;
     *         uint256 callGasLimit;
     *         uint256 paymasterVerificationGasLimit;
     *         uint256 paymasterPostOpGasLimit;
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
        public val verificationGasLimit: BigInteger,
        public val callGasLimit: BigInteger,
        public val paymasterVerificationGasLimit: BigInteger,
        public val paymasterPostOpGasLimit: BigInteger,
        public val preVerificationGas: BigInteger,
        public val paymaster: Address,
        public val maxFeePerGas: BigInteger,
        public val maxPriorityFeePerGas: BigInteger,
    ) : ContractStruct {
        override val tuple: List<Any> =
                listOf(sender, nonce, verificationGasLimit, callGasLimit, paymasterVerificationGasLimit, paymasterPostOpGasLimit, preVerificationGas, paymaster, maxFeePerGas, maxPriorityFeePerGas)

        override val abiType: AbiType.Struct<EntryPointV07.MemoryUserOp>
            get() = abi

        public companion object : StructFactory<EntryPointV07.MemoryUserOp> {
            @JvmStatic
            override val abi: AbiType.Struct<EntryPointV07.MemoryUserOp> =
                    AbiType.Struct(EntryPointV07.MemoryUserOp::class, ::fromTuple, AbiType.Struct.Field("sender", AbiType.Address),
                    AbiType.Struct.Field("nonce", AbiType.UInt(256)),
                    AbiType.Struct.Field("verificationGasLimit", AbiType.UInt(256)),
                    AbiType.Struct.Field("callGasLimit", AbiType.UInt(256)),
                    AbiType.Struct.Field("paymasterVerificationGasLimit", AbiType.UInt(256)),
                    AbiType.Struct.Field("paymasterPostOpGasLimit", AbiType.UInt(256)),
                    AbiType.Struct.Field("preVerificationGas", AbiType.UInt(256)),
                    AbiType.Struct.Field("paymaster", AbiType.Address),
                    AbiType.Struct.Field("maxFeePerGas", AbiType.UInt(256)),
                    AbiType.Struct.Field("maxPriorityFeePerGas", AbiType.UInt(256)))

            @JvmStatic
            override fun fromTuple(`data`: List<Any>): EntryPointV07.MemoryUserOp = EntryPointV07.MemoryUserOp(data[0] as io.ethers.core.types.Address, data[1] as io.github.artificialpb.bignum.BigInteger, data[2] as io.github.artificialpb.bignum.BigInteger, data[3] as io.github.artificialpb.bignum.BigInteger, data[4] as io.github.artificialpb.bignum.BigInteger, data[5] as io.github.artificialpb.bignum.BigInteger, data[6] as io.github.artificialpb.bignum.BigInteger, data[7] as io.ethers.core.types.Address, data[8] as io.github.artificialpb.bignum.BigInteger, data[9] as io.github.artificialpb.bignum.BigInteger)
        }
    }

    public companion object {
        @JvmField
        public val ERRORS: List<CustomErrorFactory<out EntryPointV07.Error>> =
                listOf(DelegateAndRevert,FailedOp,FailedOpWithRevert,PostOpReverted,ReentrancyGuardReentrantCall,SenderAddressResult,SignatureValidationFailed)

        @JvmField
        public val EVENTS: List<EventFactory<out EntryPointV07.Event>> =
                listOf(AccountDeployed,BeforeExecution,Deposited,PostOpRevertReason,SignatureAggregatorChanged,StakeLocked,StakeUnlocked,StakeWithdrawn,UserOperationEvent,UserOperationPrefundTooLow,UserOperationRevertReason,Withdrawn)

        @JvmField
        public val FUNCTION_GET_DEPOSIT_INFO: AbiFunction =
                AbiFunction("getDepositInfo", listOf(AbiType.Address), listOf(DepositInfo.abi))

        @JvmField
        public val FUNCTION_INCREMENT_NONCE: AbiFunction =
                AbiFunction("incrementNonce", listOf(AbiType.UInt(192)), listOf())

        @JvmField
        public val FUNCTION_GET_USER_OP_HASH: AbiFunction =
                AbiFunction("getUserOpHash", listOf(PackedUserOperation.abi), listOf(AbiType.FixedBytes(32)))

        @JvmField
        public val FUNCTION_NONCE_SEQUENCE_NUMBER: AbiFunction =
                AbiFunction("nonceSequenceNumber", listOf(AbiType.Address, AbiType.UInt(192)), listOf(AbiType.UInt(256)))

        @JvmField
        public val FUNCTION_BALANCE_OF: AbiFunction =
                AbiFunction("balanceOf", listOf(AbiType.Address), listOf(AbiType.UInt(256)))

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
                AbiFunction("deposits", listOf(AbiType.Address), listOf(AbiType.UInt(256), AbiType.Bool, AbiType.UInt(112), AbiType.UInt(32), AbiType.UInt(48)))

        @JvmField
        public val FUNCTION_UNLOCK_STAKE: AbiFunction =
                AbiFunction("unlockStake", listOf(), listOf())

        @JvmField
        public val FUNCTION_HANDLE_OPS: AbiFunction =
                AbiFunction("handleOps", listOf(AbiType.Array(PackedUserOperation.abi), AbiType.Address), listOf())

        @JvmField
        public val FUNCTION_GET_SENDER_ADDRESS: AbiFunction =
                AbiFunction("getSenderAddress", listOf(AbiType.Bytes), listOf())

        @JvmField
        public val FUNCTION_WITHDRAW_TO: AbiFunction =
                AbiFunction("withdrawTo", listOf(AbiType.Address, AbiType.UInt(256)), listOf())

        @JvmField
        public val FUNCTION_DELEGATE_AND_REVERT: AbiFunction =
                AbiFunction("delegateAndRevert", listOf(AbiType.Address, AbiType.Bytes), listOf())

        @JvmField
        public val FUNCTION_SUPPORTS_INTERFACE: AbiFunction =
                AbiFunction("supportsInterface", listOf(AbiType.FixedBytes(4)), listOf(AbiType.Bool))

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
         * Selector: `0x850aaf62`
         *
         * Signature:
         * ```solidity
         *     function delegateAndRevert(address target, bytes data) external;
         * ```
         */
        @JvmStatic
        public fun delegateAndRevert(
            provider: Middleware,
            address: Address,
            target: Address,
            `data`: Bytes,
        ): FunctionCall<Unit> = FunctionCall(provider, address, FUNCTION_DELEGATE_AND_REVERT.encodeCall(listOf<Any>(target,`data`))) {
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
         *     function deposits(address) external returns (uint256 deposit, bool staked, uint112 stake, uint32 unstakeDelaySec, uint48 withdrawTime);
         * ```
         */
        @JvmStatic
        public fun deposits(
            provider: Middleware,
            address: Address,
            arg2: Address,
        ): ReadFunctionCall<EntryPointV07.DepositsResult> = ReadFunctionCall(provider, address, FUNCTION_DEPOSITS.encodeCall(listOf<Any>(arg2))) {
            val data = FUNCTION_DEPOSITS.decodeResponse(it)
            EntryPointV07.DepositsResult(data[0] as io.github.artificialpb.bignum.BigInteger,data[1] as kotlin.Boolean,data[2] as io.github.artificialpb.bignum.BigInteger,data[3] as io.github.artificialpb.bignum.BigInteger,data[4] as io.github.artificialpb.bignum.BigInteger)
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
        ): ReadFunctionCall<EntryPointV07.DepositInfo> = ReadFunctionCall(provider, address, FUNCTION_GET_DEPOSIT_INFO.encodeCall(listOf<Any>(account))) {
            val data = FUNCTION_GET_DEPOSIT_INFO.decodeResponse(it)
            data[0] as EntryPointV07.DepositInfo
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
        ): ReadFunctionCall<BigInteger> = ReadFunctionCall(provider, address, FUNCTION_GET_NONCE.encodeCall(listOf<Any>(sender,key))) {
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
         * Selector: `0x22cdde4c`
         *
         * Signature:
         * ```solidity
         *     function getUserOpHash(PackedUserOperation userOp) external returns (bytes32);
         * ```
         */
        @JvmStatic
        public fun getUserOpHash(
            provider: Middleware,
            address: Address,
            userOp: EntryPointV07.PackedUserOperation,
        ): ReadFunctionCall<Bytes> = ReadFunctionCall(provider, address, FUNCTION_GET_USER_OP_HASH.encodeCall(listOf<Any>(userOp))) {
            val data = FUNCTION_GET_USER_OP_HASH.decodeResponse(it)
            data[0] as io.ethers.core.types.Bytes
        }

        /**
         * Call contract function
         *
         * Selector: `0xdbed18e0`
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
            opsPerAggregator: List<EntryPointV07.UserOpsPerAggregator>,
            beneficiary: Address,
        ): FunctionCall<Unit> = FunctionCall(provider, address, FUNCTION_HANDLE_AGGREGATED_OPS.encodeCall(listOf<Any>(opsPerAggregator,beneficiary))) {
        }

        /**
         * Call contract function
         *
         * Selector: `0x765e827f`
         *
         * Signature:
         * ```solidity
         *     function handleOps(PackedUserOperation[] ops, address beneficiary) external;
         * ```
         */
        @JvmStatic
        public fun handleOps(
            provider: Middleware,
            address: Address,
            ops: List<EntryPointV07.PackedUserOperation>,
            beneficiary: Address,
        ): FunctionCall<Unit> = FunctionCall(provider, address, FUNCTION_HANDLE_OPS.encodeCall(listOf<Any>(ops,beneficiary))) {
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
         * Selector: `0x0042dc53`
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
            opInfo: EntryPointV07.UserOpInfo,
            context: Bytes,
        ): FunctionCall<BigInteger> = FunctionCall(provider, address, FUNCTION_INNER_HANDLE_OP.encodeCall(listOf<Any>(callData,opInfo,context))) {
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
        ): ReadFunctionCall<BigInteger> = ReadFunctionCall(provider, address, FUNCTION_NONCE_SEQUENCE_NUMBER.encodeCall(listOf<Any>(arg2,arg3))) {
            val data = FUNCTION_NONCE_SEQUENCE_NUMBER.decodeResponse(it)
            data[0] as io.github.artificialpb.bignum.BigInteger
        }

        /**
         * Call contract function
         *
         * Selector: `0x01ffc9a7`
         *
         * Signature:
         * ```solidity
         *     function supportsInterface(bytes4 interfaceId) external returns (bool);
         * ```
         */
        @JvmStatic
        public fun supportsInterface(
            provider: Middleware,
            address: Address,
            interfaceId: Bytes,
        ): ReadFunctionCall<Boolean> = ReadFunctionCall(provider, address, FUNCTION_SUPPORTS_INTERFACE.encodeCall(listOf<Any>(interfaceId))) {
            val data = FUNCTION_SUPPORTS_INTERFACE.decodeResponse(it)
            data[0] as kotlin.Boolean
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
        ): FunctionCall<Unit> = FunctionCall(provider, address, FUNCTION_WITHDRAW_TO.encodeCall(listOf<Any>(withdrawAddress,withdrawAmount))) {
        }

        @JvmStatic
        public fun decodeErrorOrNull(error: Bytes): EntryPointV07.Error? {
            for (err in ERRORS) {
                return err.decodeOrNull(error) ?: continue
            }
            return null
        }

        @JvmStatic
        public fun tryDecodeError(error: Bytes): Result<EntryPointV07.Error, ContractErrorDecodingError> {
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
        public fun decodeEventOrNull(log: Log): EntryPointV07.Event? {
            for (event in EVENTS) {
                return event.decodeOrNull(log) ?: continue
            }
            return null
        }

        @JvmStatic
        public fun tryDecodeEvent(log: Log): Result<EntryPointV07.Event, EventDecodingError> {
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
public value class EntryPointV07Scope(
    private val provider: Middleware,
) {
    public fun receive(address: Address, `value`: BigInteger): ReceiveFunctionCall = EntryPointV07.receive(provider, address, value)

    public fun addStake(address: Address, unstakeDelaySec: BigInteger): PayableFunctionCall<Unit> = EntryPointV07.addStake(provider, address, unstakeDelaySec)

    public fun balanceOf(address: Address, account: Address): ReadFunctionCall<BigInteger> = EntryPointV07.balanceOf(provider, address, account)

    public fun delegateAndRevert(
        address: Address,
        target: Address,
        `data`: Bytes,
    ): FunctionCall<Unit> = EntryPointV07.delegateAndRevert(provider, address, target,`data`)

    public fun depositTo(address: Address, account: Address): PayableFunctionCall<Unit> = EntryPointV07.depositTo(provider, address, account)

    public fun deposits(address: Address, arg1: Address): ReadFunctionCall<EntryPointV07.DepositsResult> = EntryPointV07.deposits(provider, address, arg1)

    public fun getDepositInfo(address: Address, account: Address): ReadFunctionCall<EntryPointV07.DepositInfo> = EntryPointV07.getDepositInfo(provider, address, account)

    public fun getNonce(
        address: Address,
        sender: Address,
        key: BigInteger,
    ): ReadFunctionCall<BigInteger> = EntryPointV07.getNonce(provider, address, sender,key)

    public fun getSenderAddress(address: Address, initCode: Bytes): FunctionCall<Unit> = EntryPointV07.getSenderAddress(provider, address, initCode)

    public fun getUserOpHash(address: Address, userOp: EntryPointV07.PackedUserOperation): ReadFunctionCall<Bytes> = EntryPointV07.getUserOpHash(provider, address, userOp)

    public fun handleAggregatedOps(
        address: Address,
        opsPerAggregator: List<EntryPointV07.UserOpsPerAggregator>,
        beneficiary: Address,
    ): FunctionCall<Unit> = EntryPointV07.handleAggregatedOps(provider, address, opsPerAggregator,beneficiary)

    public fun handleOps(
        address: Address,
        ops: List<EntryPointV07.PackedUserOperation>,
        beneficiary: Address,
    ): FunctionCall<Unit> = EntryPointV07.handleOps(provider, address, ops,beneficiary)

    public fun incrementNonce(address: Address, key: BigInteger): FunctionCall<Unit> = EntryPointV07.incrementNonce(provider, address, key)

    public fun innerHandleOp(
        address: Address,
        callData: Bytes,
        opInfo: EntryPointV07.UserOpInfo,
        context: Bytes,
    ): FunctionCall<BigInteger> = EntryPointV07.innerHandleOp(provider, address, callData,opInfo,context)

    public fun nonceSequenceNumber(
        address: Address,
        arg1: Address,
        arg2: BigInteger,
    ): ReadFunctionCall<BigInteger> = EntryPointV07.nonceSequenceNumber(provider, address, arg1,arg2)

    public fun supportsInterface(address: Address, interfaceId: Bytes): ReadFunctionCall<Boolean> = EntryPointV07.supportsInterface(provider, address, interfaceId)

    public fun unlockStake(address: Address): FunctionCall<Unit> = EntryPointV07.unlockStake(provider, address)

    public fun withdrawStake(address: Address, withdrawAddress: Address): FunctionCall<Unit> = EntryPointV07.withdrawStake(provider, address, withdrawAddress)

    public fun withdrawTo(
        address: Address,
        withdrawAddress: Address,
        withdrawAmount: BigInteger,
    ): FunctionCall<Unit> = EntryPointV07.withdrawTo(provider, address, withdrawAddress,withdrawAmount)
}

public val Middleware.entryPointV07: EntryPointV07Scope
    get() = EntryPointV07Scope(this)
