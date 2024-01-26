package io.ethers.providers.types

import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.isFailure
import io.ethers.core.success
import io.ethers.core.types.Hash
import io.ethers.core.types.TransactionReceipt
import io.ethers.providers.RpcError
import io.ethers.providers.middleware.Middleware
import java.time.Duration
import kotlin.jvm.optionals.getOrNull

class PendingTransaction(
    val hash: Hash,
    private val provider: Middleware,
) : PendingInclusion<TransactionReceipt> {
    override fun awaitInclusion(
        retries: Int,
        interval: Duration,
        confirmations: Int,
    ): Result<TransactionReceipt, PendingInclusion.Error> {
        val intervalMillis = interval.toMillis()
        var receiptError: RpcError? = null
        var included: TransactionReceipt? = null
        var retriesLeft = retries
        while (retriesLeft-- > 0) {
            val response = provider.getTransactionReceipt(hash).sendAwait()
            if (response.isFailure()) {
                receiptError = response.error
                Thread.sleep(intervalMillis)
                continue
            }

            included = response.unwrap().getOrNull()
            if (included != null) {
                break
            }

            Thread.sleep(intervalMillis)
        }

        if (included == null && receiptError != null) {
            return failure(PendingInclusion.Error.RpcError(hash, receiptError))
        }

        if (included == null) {
            return failure(PendingInclusion.Error.NoInclusion(hash, retries))
        }

        if (!included.isSuccessful) {
            return failure(PendingInclusion.Error.TxFailed(hash, included))
        }

        if (confirmations <= 1) {
            return success(included)
        }

        while (true) {
            val response = provider.getBlockNumber().sendAwait()
            if (response.isFailure()) {
                return failure(PendingInclusion.Error.RpcError(hash, response.error))
            }

            val currentBlock = response.unwrap()
            if ((currentBlock - included.blockNumber) >= (confirmations - 1)) {
                return success(included)
            }

            Thread.sleep(intervalMillis)
        }
    }

    override fun toString(): String {
        return "PendingTransaction(hash=$hash)"
    }
}
