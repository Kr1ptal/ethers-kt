package io.ethers.aa.account

import io.ethers.aa.EntryPoint
import io.ethers.aa.UserOperation
import io.ethers.aa.bundler.BundlerApi
import io.ethers.aa.bundler.UserOperationReceipt
import io.ethers.aa.errors.UserOperationError
import io.ethers.aa.errors.toUserOperationError
import io.ethers.aa.internal.WordWriter
import io.ethers.aa.internal.ZERO
import io.ethers.aa.paymaster.PaymasterApi
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.types.Address
import io.ethers.core.types.Authorization
import io.ethers.core.types.BlockId
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.core.types.Hash
import io.ethers.core.unwrapOrReturn
import io.ethers.providers.middleware.Middleware
import io.ethers.providers.types.RpcRequest
import io.ethers.providers.types.SuppliedRpcRequest
import io.github.artificialpb.bignum.BigInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmOverloads
import kotlin.time.Duration

/**
 * Prepares, signs and sends user operations of a [SmartAccount].
 *
 * Preparing fills in everything the request leaves unset, in the order bundlers and ERC-7677 paymasters expect:
 * 1. call data, the nonce, fees, and the deployment of a not yet deployed account, concurrently,
 * 2. a stub signature,
 * 3. stub paymaster data, if the operation is sponsored,
 * 4. gas limits, estimated by the bundler,
 * 5. final paymaster data, unless the stub data already was final.
 *
 * @param account the account to send operations from.
 * @param bundler the bundler to estimate and send operations with.
 * @param provider a node of the same chain, to read the nonce, deployment status and fees from.
 * @param paymaster an optional ERC-7677 paymaster service that sponsors operations.
 * @param feeEstimator how to price operations, defaults to reading fees from [provider].
 */
class SmartAccountClient @JvmOverloads constructor(
    val account: SmartAccount,
    val bundler: BundlerApi,
    val provider: Middleware,
    val paymaster: PaymasterApi? = null,
    private val feeEstimator: FeeEstimator = NodeFeeEstimator(provider),
) {
    val entryPoint: EntryPoint
        get() = account.entryPoint

    val chainId: Long
        get() = provider.chainId

    /**
     * Prepare [calls] into an unsigned user operation.
     */
    fun prepareUserOperation(calls: List<Call>): RpcRequest<UserOperation, UserOperationError> {
        return prepareUserOperation(UserOperationRequest(calls))
    }

    /**
     * Prepare [request] into a user operation that is ready to sign. Its signature is a stub.
     */
    fun prepareUserOperation(request: UserOperationRequest): RpcRequest<UserOperation, UserOperationError> {
        return SuppliedRpcRequest { catching { prepare(request) } }
    }

    /**
     * Sign a prepared [userOperation] with [signer], the account's default validator unless set otherwise.
     */
    @JvmOverloads
    fun signUserOperation(
        userOperation: UserOperation,
        signer: UserOperationSigner = account,
    ): RpcRequest<UserOperation, UserOperationError> {
        return SuppliedRpcRequest { catching { sign(userOperation, signer) } }
    }

    /**
     * Prepare, sign and send [calls], returning the hash of the user operation.
     */
    fun sendUserOperation(calls: List<Call>): RpcRequest<Hash, UserOperationError> {
        return sendUserOperation(UserOperationRequest(calls))
    }

    /**
     * Prepare, sign and send [request], returning the hash of the user operation.
     */
    fun sendUserOperation(request: UserOperationRequest): RpcRequest<Hash, UserOperationError> {
        return SuppliedRpcRequest {
            catching {
                val prepared = prepare(request).unwrapOrReturn { return@catching failure(it) }
                val signed = sign(prepared, request.signer ?: account).unwrapOrReturn { return@catching failure(it) }
                bundler.sendUserOperation(signed, entryPoint).send().mapError { it.toUserOperationError() }
            }
        }
    }

    /**
     * Send an already signed [userOperation], returning its hash.
     */
    fun sendSignedUserOperation(userOperation: UserOperation): RpcRequest<Hash, UserOperationError> {
        return bundler.sendUserOperation(userOperation, entryPoint).mapError { it.toUserOperationError() }
    }

    /**
     * Wait for the receipt of the user operation [userOpHash], see [BundlerApi.waitForUserOperationReceipt].
     */
    fun waitForUserOperationReceipt(userOpHash: Hash): RpcRequest<UserOperationReceipt, UserOperationError> {
        return bundler.waitForUserOperationReceipt(userOpHash)
    }

    /**
     * Wait for the receipt of the user operation [userOpHash], see [BundlerApi.waitForUserOperationReceipt].
     */
    fun waitForUserOperationReceipt(
        userOpHash: Hash,
        timeout: Duration,
        pollingInterval: Duration,
    ): RpcRequest<UserOperationReceipt, UserOperationError> {
        return bundler.waitForUserOperationReceipt(userOpHash, timeout, pollingInterval, false)
    }

    /**
     * Read the next nonce of [key] for the account from the EntryPoint.
     */
    fun getNonce(key: BigInteger): RpcRequest<BigInteger, UserOperationError> {
        val data = WordWriter(2).address(account.address).uint(key).toByteArray()
        val call = CallRequest().to(entryPoint.address).data(Bytes(GET_NONCE_SELECTOR + data))
        return provider.call(call, BlockId.LATEST).map { BigInteger(1, it.asByteArray()) }.mapError { it.toUserOperationError() }
    }

    /**
     * Return whether the account has code, i.e. is deployed or, for an EIP-7702 account, delegated.
     */
    fun isDeployed(): RpcRequest<Boolean, UserOperationError> {
        return provider.getCode(account.address, BlockId.LATEST).map { !it.isEmpty }.mapError { it.toUserOperationError() }
    }

    private suspend fun prepare(request: UserOperationRequest): Result<UserOperation, UserOperationError> = coroutineScope {
        val signer = request.signer ?: account

        val callDataFut = async { callData(request) }
        val nonceFut = async {
            request.nonce?.let { success(it) } ?: getNonce(request.nonceKey ?: signer.nonceKey).send()
        }
        val feesFut = async {
            val maxFee = request.maxFeePerGas
            val maxPriorityFee = request.maxPriorityFeePerGas
            if (maxFee != null && maxPriorityFee != null) {
                success(UserOperationFees(maxFee, maxPriorityFee))
            } else {
                feeEstimator.estimateFees().map { UserOperationFees(maxFee ?: it.maxFeePerGas, maxPriorityFee ?: it.maxPriorityFeePerGas) }
            }
        }
        val deploymentFut = async { deployment(request) }

        val callData = callDataFut.await().unwrapOrReturn { return@coroutineScope failure(it) }
        val nonce = nonceFut.await().unwrapOrReturn { return@coroutineScope failure(it) }
        val fees = feesFut.await().unwrapOrReturn { return@coroutineScope failure(it) }
        val deployment = deploymentFut.await().unwrapOrReturn { return@coroutineScope failure(it) }

        var op = UserOperation(
            sender = account.address,
            nonce = nonce,
            callData = callData,
            callGasLimit = request.callGasLimit ?: ZERO,
            verificationGasLimit = request.verificationGasLimit ?: ZERO,
            preVerificationGas = request.preVerificationGas ?: ZERO,
            maxFeePerGas = fees.maxFeePerGas,
            maxPriorityFeePerGas = fees.maxPriorityFeePerGas,
            signature = Bytes.EMPTY,
            factory = deployment.factory,
            factoryData = deployment.factoryData,
            paymaster = request.paymaster,
            paymasterVerificationGasLimit = request.paymasterVerificationGasLimit,
            paymasterPostOpGasLimit = request.paymasterPostOpGasLimit,
            paymasterData = request.paymasterData,
            eip7702Auth = deployment.authorization,
        )
        op = op.copy(signature = signer.getStubSignature(op))

        val paymaster = this@SmartAccountClient.paymaster
        val sponsored = request.paymaster == null && paymaster != null && (request.sponsored ?: true)
        var paymasterIsFinal = false
        if (sponsored) {
            val stub = paymaster!!.getPaymasterStubData(op, entryPoint, chainId, request.paymasterContext).send()
                .unwrapOrReturn { return@coroutineScope failure(it.toUserOperationError()) }

            paymasterIsFinal = stub.isFinal
            // v0.6 has no paymaster gas limits, its paymaster validation is part of verificationGasLimit
            val packed = entryPoint.version.usesPackedUserOperation
            op = op.copy(
                paymaster = stub.paymaster,
                paymasterData = stub.paymasterData,
                paymasterVerificationGasLimit = (request.paymasterVerificationGasLimit ?: stub.paymasterVerificationGasLimit).takeIf { packed },
                paymasterPostOpGasLimit = (request.paymasterPostOpGasLimit ?: stub.paymasterPostOpGasLimit).takeIf { packed },
            )
        }

        val hasPaymasterGas = !entryPoint.version.usesPackedUserOperation || op.paymaster == null ||
            (op.paymasterVerificationGasLimit != null && op.paymasterPostOpGasLimit != null)
        val needsEstimate = request.callGasLimit == null || request.verificationGasLimit == null ||
            request.preVerificationGas == null || !hasPaymasterGas

        if (needsEstimate) {
            val estimate = bundler.estimateUserOperationGas(op, entryPoint, request.stateOverride).send()
                .unwrapOrReturn { return@coroutineScope failure(it.toUserOperationError()) }

            op = op.copy(
                callGasLimit = request.callGasLimit ?: estimate.callGasLimit,
                verificationGasLimit = request.verificationGasLimit ?: estimate.verificationGasLimit,
                preVerificationGas = request.preVerificationGas ?: estimate.preVerificationGas,
                paymasterVerificationGasLimit = op.paymasterVerificationGasLimit
                    ?: estimate.paymasterVerificationGasLimit.takeIf { op.paymaster != null && entryPoint.version.usesPackedUserOperation },
                paymasterPostOpGasLimit = op.paymasterPostOpGasLimit
                    ?: estimate.paymasterPostOpGasLimit.takeIf { op.paymaster != null && entryPoint.version.usesPackedUserOperation },
            )
        }

        op = account.adjustUserOperation(op)

        if (sponsored && !paymasterIsFinal) {
            val data = paymaster!!.getPaymasterData(op, entryPoint, chainId, request.paymasterContext).send()
                .unwrapOrReturn { return@coroutineScope failure(it.toUserOperationError()) }
            op = op.copy(paymaster = data.paymaster, paymasterData = data.paymasterData)
        }

        success(op)
    }

    private suspend fun sign(op: UserOperation, signer: UserOperationSigner): Result<UserOperation, UserOperationError> {
        val hash = op.hash(entryPoint, chainId)
        val signature = try {
            signer.signUserOperation(op, hash, chainId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return failure(UserOperationError.Signing("Failed to sign user operation $hash", e))
        }
        return success(op.withSignature(signature))
    }

    private suspend fun callData(request: UserOperationRequest): Result<Bytes, UserOperationError> {
        request.callData?.let { return success(it) }
        val calls = request.calls
            ?: return failure(UserOperationError.InvalidOperation("Request has neither calls nor callData"))
        return success(account.encodeCalls(calls))
    }

    private class Deployment(val factory: Address?, val factoryData: Bytes?, val authorization: Authorization?)

    private suspend fun deployment(request: UserOperationRequest): Result<Deployment, UserOperationError> {
        request.factoryArgs?.let { return success(Deployment(it.factory, it.factoryData, request.eip7702Auth)) }

        val deployed = isDeployed().send().unwrapOrReturn { return failure(it) }
        if (deployed) {
            return success(Deployment(null, null, request.eip7702Auth))
        }

        val factoryArgs = account.getFactoryArgs()
        if (factoryArgs != null) {
            return success(Deployment(factoryArgs.factory, factoryArgs.factoryData, request.eip7702Auth))
        }

        // not deployed, no factory: an EIP-7702 account that still has to be delegated
        val authorization = request.eip7702Auth ?: run {
            val eoaNonce = provider.getTransactionCount(account.address, BlockId.PENDING).send()
                .unwrapOrReturn { return failure(it.toUserOperationError()) }
            account.signEip7702Authorization(chainId, eoaNonce)
        }

        if (authorization == null) {
            return failure(
                UserOperationError.InvalidOperation(
                    "Account ${account.address} is not deployed and has neither a factory nor an EIP-7702 authorization",
                ),
            )
        }

        if (!entryPoint.version.supportsEip7702) {
            return failure(UserOperationError.InvalidOperation("EntryPoint ${entryPoint.version} does not support EIP-7702 accounts"))
        }
        return success(Deployment(UserOperation.EIP7702_FACTORY_MARKER, Bytes.EMPTY, authorization))
    }

    private suspend fun <T> catching(block: suspend () -> Result<T, UserOperationError>): Result<T, UserOperationError> {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure(UserOperationError.InvalidOperation(e.message ?: "Invalid user operation", e))
        }
    }

    private companion object {
        // getNonce(address,uint192)
        val GET_NONCE_SELECTOR = byteArrayOf(0x35, 0x56, 0x7e, 0x1a)
    }
}
