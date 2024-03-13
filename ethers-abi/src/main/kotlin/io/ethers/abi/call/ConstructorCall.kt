package io.ethers.abi.call

import io.ethers.abi.AbiContract
import io.ethers.abi.error.ContractError
import io.ethers.abi.error.DeployError
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.types.AccountOverride
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.BlockOverride
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.TransactionReceipt
import io.ethers.core.types.tracers.PrestateTracer
import io.ethers.core.types.tracers.TracerConfig
import io.ethers.providers.middleware.Middleware
import io.ethers.providers.types.PendingInclusion
import io.ethers.providers.types.PendingTransaction
import io.ethers.providers.types.RpcRequest
import java.math.BigInteger
import java.time.Duration
import java.util.function.BiFunction

private val PRESTATE_DIFF_TRACER = PrestateTracer(diffMode = true)
private val TRACER_CONFIG_NO_OVERRIDES = TracerConfig(PRESTATE_DIFF_TRACER)

class ConstructorCall<T : AbiContract>(
    provider: Middleware,
    bytecode: Bytes,
    private val constructor: BiFunction<Middleware, Address, T>,
) : ReadWriteContractCall<CallDeploy<T>, PendingContractDeploy<T>, ConstructorCall<T>>(provider) {

    init {
        // create a random address for each ConstructorCall instance, so multiple deploys can be made via "call",
        // all on a different address, so they don't override each other in state overrides.
        call.from = Address.random()
        call.to = null
        call.data = bytecode
    }

    override val self: ConstructorCall<T>
        get() = this

    override fun doCall(
        blockId: BlockId,
        stateOverride: Map<Address, AccountOverride>?,
        blockOverride: BlockOverride?,
    ): RpcRequest<CallDeploy<T>, ContractError> {
        val config = when {
            stateOverride == null && blockOverride == null -> TRACER_CONFIG_NO_OVERRIDES
            else -> TracerConfig(PRESTATE_DIFF_TRACER, stateOverrides = stateOverride, blockOverrides = blockOverride)
        }

        val sender = call.from ?: Address.ZERO
        val nonce = if (call.nonce == -1L) 0L else call.nonce
        val deployAddress: Address = Address.computeCreate(sender, nonce)

        // deploy via traceCall to get the full state diff, which includes:
        // - the deployed contract bytecode,
        // - contracts that might have been created in the constructor,
        // - storage slots that might have been written in the constructor
        return provider.traceCall(call, blockId, config)
            .mapError(::tryDecodingContractRevert)
            .andThen {
                val overrides = it.toStateOverride()
                val deployedBytecode = overrides[deployAddress]?.code ?: return@andThen failure(DeployError.NoBytecode)

                return@andThen success(
                    CallDeploy(
                        constructor.apply(provider, deployAddress),
                        overrides,
                        deployedBytecode,
                    ),
                )
            }
    }

    override fun handleSendResult(result: PendingTransaction): PendingContractDeploy<T> {
        return PendingContractDeploy(provider, result, constructor)
    }
}

class PayableConstructorCall<T : AbiContract>(
    provider: Middleware,
    bytecode: Bytes,
    private val constructor: BiFunction<Middleware, Address, T>,
) : ReadWriteContractCall<CallDeploy<T>, PendingContractDeploy<T>, PayableConstructorCall<T>>(provider) {

    init {
        // create a random address for each ConstructorCall instance, so multiple deploys can be made via "call",
        // all on a different address, so they don't override each other in state overrides.
        call.from = Address.random()
        call.to = null
        call.data = bytecode
    }

    override val self: PayableConstructorCall<T>
        get() = this

    override fun doCall(
        blockId: BlockId,
        stateOverride: Map<Address, AccountOverride>?,
        blockOverride: BlockOverride?,
    ): RpcRequest<CallDeploy<T>, ContractError> {
        val config = when {
            stateOverride == null && blockOverride == null -> TRACER_CONFIG_NO_OVERRIDES
            else -> TracerConfig(PRESTATE_DIFF_TRACER, stateOverrides = stateOverride, blockOverrides = blockOverride)
        }

        val sender = call.from ?: Address.ZERO
        val nonce = if (call.nonce == -1L) 0L else call.nonce
        val deployAddress: Address = Address.computeCreate(sender, nonce)

        // deploy via traceCall to get the full state diff, which includes:
        // - the deployed contract bytecode,
        // - contracts that might have been created in the constructor,
        // - storage slots that might have been written in the constructor
        return provider.traceCall(call, blockId, config)
            .mapError(::tryDecodingContractRevert)
            .andThen {
                val overrides = it.toStateOverride()
                val deployedBytecode = overrides[deployAddress]?.code ?: return@andThen failure(DeployError.NoBytecode)

                return@andThen success(
                    CallDeploy(
                        constructor.apply(provider, deployAddress),
                        overrides,
                        deployedBytecode,
                    ),
                )
            }
    }

    override fun handleSendResult(result: PendingTransaction): PendingContractDeploy<T> {
        return PendingContractDeploy(provider, result, constructor)
    }

    override var value: BigInteger?
        get() = call.value
        @JvmSynthetic set(value) {
            call.value = value
        }

    fun value(value: BigInteger?): PayableConstructorCall<T> {
        call.value = value
        return this
    }
}

/**
 * Container for the result of a contract deploy via `eth_call`. Contains the wrapper pointing to deployed address
 * and the state overrides produced by deploying the contract. The overrides should be set for subsequent calls to the
 * [contract] so that the state at the time of the call contains the deployment.
 *
 * Example usage:
 * ```kotlin
 *     // deploy a contract via call
 *     val (contract, overrides) = ERC20.deploy(provider, "TEST TOKEN", "TEST", 18)
 *         .call(BlockId.LATEST)
 *         .sendAwait()
 *         .unwrap()
 *
 *     // set the state overrides for subsequent calls to the contract
 *     contract.transfer(Address.ZERO, EthUnit.ETHER.toWei("1").toBigInteger())
 *         .call(BlockId.LATEST, overrides)
 * ```
 *
 * @param contract the wrapper pointing to the deployed contract address
 * @param stateOverrides state overrides which include the changed produced by deploying the contract. Should be set
 * for subsequent calls to the contract.
 * @param deployedBytecode the bytecode of the deployed contract
 * */
class CallDeploy<T : AbiContract>(
    val contract: T,
    val stateOverrides: Map<Address, AccountOverride>,
    val deployedBytecode: Bytes,
) {
    val address: Address
        get() = contract.address

    @JvmSynthetic
    operator fun component1(): T = contract

    @JvmSynthetic
    operator fun component2(): Map<Address, AccountOverride> = stateOverrides
}

/**
 * Contract deploy that is pending inclusion in a block.
 * */
class PendingContractDeploy<T : AbiContract>(
    private val provider: Middleware,
    private val result: PendingTransaction,
    private val constructor: BiFunction<Middleware, Address, T>,
) : PendingInclusion<ContractDeployment<T>> {
    val hash: Hash
        get() = result.hash

    override fun awaitInclusion(
        retries: Int,
        interval: Duration,
        confirmations: Int,
    ): Result<ContractDeployment<T>, PendingInclusion.Error> {
        return result.awaitInclusion(retries, interval, confirmations).andThen {
            when {
                !it.isSuccessful || it.contractAddress == null -> success(ContractDeployment(null, it))
                else -> success(ContractDeployment(constructor.apply(provider, it.contractAddress!!), it))
            }
        }
    }

    override fun toString(): String {
        return "PendingContractDeploy(hash=$hash)"
    }
}

class ContractDeployment<T : AbiContract>(
    val contract: T?,
    val receipt: TransactionReceipt,
) {
    val isSuccessful: Boolean
        get() = receipt.isSuccessful && contract != null

    @JvmSynthetic
    operator fun component1(): T? = contract

    @JvmSynthetic
    operator fun component2(): TransactionReceipt = receipt
}
