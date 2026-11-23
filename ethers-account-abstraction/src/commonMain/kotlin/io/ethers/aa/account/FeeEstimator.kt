package io.ethers.aa.account

import io.ethers.aa.errors.UserOperationError
import io.ethers.aa.errors.toUserOperationError
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.isFailure
import io.ethers.core.success
import io.ethers.core.types.BlockId
import io.ethers.providers.middleware.Middleware
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.jvm.JvmOverloads

/**
 * Fee caps of a user operation.
 */
data class UserOperationFees(val maxFeePerGas: BigInteger, val maxPriorityFeePerGas: BigInteger)

/**
 * Estimates the fees of user operations. Bundlers often have their own fee requirements, e.g. a minimum priority
 * fee, and expose them through vendor-specific RPC methods such as `rundler_getUserOperationGasPrice` or
 * `pimlico_getUserOperationGasPrice`; implement this interface to use one.
 */
fun interface FeeEstimator {
    suspend fun estimateFees(): Result<UserOperationFees, UserOperationError>
}

/**
 * [FeeEstimator] reading fees from a node: the priority fee is `eth_maxPriorityFeePerGas`, and the fee cap is
 * [baseFeeMultiplier] times the latest base fee, plus the priority fee. The multiplier keeps the operation valid
 * while base fees rise before it is included.
 */
class NodeFeeEstimator @JvmOverloads constructor(
    private val provider: Middleware,
    private val baseFeeMultiplier: Int = 2,
) : FeeEstimator {
    init {
        require(baseFeeMultiplier >= 1) { "baseFeeMultiplier must be at least 1" }
    }

    override suspend fun estimateFees(): Result<UserOperationFees, UserOperationError> = coroutineScope {
        val priorityFut = async { provider.getMaxPriorityFeePerGas().send() }
        val blockFut = async { provider.getBlockWithHashes(BlockId.LATEST).send() }

        val priority = priorityFut.await()
        if (priority.isFailure()) return@coroutineScope failure(priority.error.toUserOperationError())
        val block = blockFut.await()
        if (block.isFailure()) return@coroutineScope failure(block.error.toUserOperationError())

        val baseFee = block.unwrap()?.baseFeePerGas
            ?: return@coroutineScope failure(UserOperationError.InvalidOperation("Latest block has no base fee"))

        val maxPriorityFeePerGas = priority.unwrap()
        val maxFeePerGas = baseFee.multiply(bigIntegerOf(baseFeeMultiplier.toLong())).add(maxPriorityFeePerGas)
        success(UserOperationFees(maxFeePerGas, maxPriorityFeePerGas))
    }
}
