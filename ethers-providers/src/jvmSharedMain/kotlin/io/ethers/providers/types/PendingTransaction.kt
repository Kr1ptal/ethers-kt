package io.ethers.providers.types

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getError
import io.ethers.core.types.Hash
import io.ethers.core.types.TransactionReceipt
import io.ethers.core.unwrap
import io.ethers.providers.RpcError
import io.ethers.providers.middleware.Middleware
import java.time.Duration
import kotlin.time.toKotlinDuration

class PendingTransaction(
    val hash: Hash,
    private val provider: Middleware,
) : PendingInclusion<TransactionReceipt> {
    override fun awaitInclusion(
        retries: Int,
        interval: Duration,
        confirmations: Int,
    ): Result<TransactionReceipt, PendingInclusion.Error> {
        val intervalMillis = interval.toKotlinDuration().inWholeMilliseconds
        var receiptError: RpcError? = null
        var included: TransactionReceipt? = null
        var retriesLeft = retries
        while (retriesLeft-- > 0) {
            val response = provider.getTransactionReceipt(hash).sendAwait()
            if (response.isErr) {
                receiptError = response.getError()!!
                Thread.sleep(intervalMillis)
                continue
            }

            included = response.unwrap()
            if (included != null) {
                break
            }

            Thread.sleep(intervalMillis)
        }

        if (included == null && receiptError != null) {
            return Err(PendingInclusion.Error.RpcError(hash, receiptError))
        }

        if (included == null) {
            return Err(PendingInclusion.Error.NoInclusion(hash, retries))
        }

        if (confirmations <= 1) {
            return Ok(included)
        }

        while (true) {
            val response = provider.getBlockNumber().sendAwait()
            if (response.isErr) {
                return Err(PendingInclusion.Error.RpcError(hash, response.getError()!!))
            }

            val currentBlock = response.unwrap()
            if ((currentBlock - included.blockNumber) >= (confirmations - 1)) {
                return Ok(included)
            }

            Thread.sleep(intervalMillis)
        }
    }

    override fun toString(): String {
        return "PendingTransaction(hash=$hash)"
    }
}
