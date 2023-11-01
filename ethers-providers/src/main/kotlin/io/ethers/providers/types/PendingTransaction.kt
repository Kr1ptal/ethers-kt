package io.ethers.providers.types

import io.ethers.core.types.Hash
import io.ethers.core.types.TransactionReceipt
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
    ): RpcResponse<TransactionReceipt> {
        val intervalMillis = interval.toMillis()
        var retriesLeft = retries
        var included: TransactionReceipt? = null
        while (retriesLeft-- > 0) {
            val response = provider.getTransactionReceipt(hash).sendAwait()
            if (response.isError) {
                return response.propagateError()
            }

            included = response.resultOrThrow().getOrNull()
            if (included != null) {
                break
            }

            Thread.sleep(intervalMillis)
        }

        if (included == null) {
            return RpcResponse.error(InclusionError(hash, "Transaction not included after $retries retries"))
        }

        if (confirmations <= 1) {
            return RpcResponse.result(included)
        }

        while (true) {
            val response = provider.getBlockNumber().sendAwait()
            if (response.isError) {
                return response.propagateError()
            }

            val currentBlock = response.resultOrThrow()
            if ((currentBlock - included.blockNumber) >= (confirmations - 1)) {
                return RpcResponse.result(included)
            }

            Thread.sleep(intervalMillis)
        }
    }

    override fun toString(): String {
        return "PendingTransaction(hash=$hash)"
    }
}

data class InclusionError(val txHash: Hash, val msg: String) : RpcResponse.Error()
