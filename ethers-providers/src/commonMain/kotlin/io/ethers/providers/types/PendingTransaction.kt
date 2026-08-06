package io.ethers.providers.types

import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.isFailure
import io.ethers.core.success
import io.ethers.core.types.Hash
import io.ethers.core.types.TransactionReceipt
import io.ethers.providers.RpcError
import io.ethers.providers.middleware.Middleware
import kotlinx.coroutines.delay
import kotlin.time.Duration

class PendingTransaction(
    val hash: Hash,
    private val provider: Middleware,
) : PendingInclusion<TransactionReceipt> {
    override suspend fun inclusion(
        retries: Int,
        interval: Duration,
        confirmations: Int,
    ): Result<TransactionReceipt, PendingInclusion.Error> {
        var receiptError: RpcError? = null
        var included: TransactionReceipt? = null
        var retriesLeft = retries
        while (retriesLeft-- > 0) {
            val response = provider.getTransactionReceipt(hash).send()
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
            return failure(PendingInclusion.Error.RpcError(hash, receiptError))
        }

        if (included == null) {
            return failure(PendingInclusion.Error.NoInclusion(hash, retries))
        }

        if (confirmations <= 1) {
            return success(included)
        }

        while (true) {
            val response = provider.getBlockNumber().send()
            if (response.isFailure()) {
                return failure(PendingInclusion.Error.RpcError(hash, response.error))
            }

            val currentBlock = response.unwrap()
            if ((currentBlock - included.blockNumber) >= (confirmations - 1)) {
                return success(included)
            }

            delay(interval)
        }
    }

    override fun toString(): String {
        return "PendingTransaction(hash=$hash)"
    }
}
