package io.ethers.aa.bundler

import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.isFailure
import io.ethers.core.success
import io.ethers.core.types.Hash
import io.ethers.providers.RpcError
import io.ethers.providers.middleware.Middleware
import io.ethers.providers.types.PendingInclusion
import kotlinx.coroutines.delay
import kotlin.time.Duration

/**
 * A user operation submitted to a bundler, pending inclusion in a bundle. The [PendingInclusion.Error] variants
 * carry the [userOpHash] as their `txHash`.
 *
 * @param bundler the bundler to poll for the operation's receipt.
 * @param provider a node of the same chain, needed to wait for more than one confirmation.
 */
class PendingUserOperation(
    val userOpHash: Hash,
    private val bundler: BundlerApi,
    private val provider: Middleware? = null,
) : PendingInclusion<UserOperationReceipt> {
    override suspend fun inclusion(
        retries: Int,
        interval: Duration,
        confirmations: Int,
    ): Result<UserOperationReceipt, PendingInclusion.Error> {
        var receiptError: RpcError? = null
        var included: UserOperationReceipt? = null
        var retriesLeft = retries
        while (retriesLeft-- > 0) {
            val response = bundler.getUserOperationReceipt(userOpHash).send()
            if (response.isFailure()) {
                receiptError = response.error
                delay(interval)
                continue
            }

            included = response.unwrap()
            if (included != null) {
                break
            }

            delay(interval)
        }

        if (included == null && receiptError != null) {
            return failure(PendingInclusion.Error.RpcError(userOpHash, receiptError))
        }

        if (included == null) {
            return failure(PendingInclusion.Error.NoInclusion(userOpHash, retries))
        }

        if (confirmations <= 1) {
            return success(included)
        }

        val node = provider ?: return failure(
            PendingInclusion.Error.RpcError(
                userOpHash,
                RpcError(RpcError.CODE_INVALID_PARAMS, "Waiting for $confirmations confirmations needs a provider"),
            ),
        )

        val includedBlock = included.receipt.blockNumber
        while (true) {
            val response = node.getBlockNumber().send()
            if (response.isFailure()) {
                return failure(PendingInclusion.Error.RpcError(userOpHash, response.error))
            }

            if ((response.unwrap() - includedBlock) >= (confirmations - 1)) {
                return success(included)
            }

            delay(interval)
        }
    }

    override fun toString(): String {
        return "PendingUserOperation(userOpHash=$userOpHash)"
    }
}
