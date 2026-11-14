package io.ethers.solana.program

import io.ethers.core.ThrowableError
import io.ethers.providers.RpcError
import io.ethers.solana.codec.SolanaDecodeError
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.rpc.AccountInfo
import io.ethers.solana.types.rpc.RpcContext

/** A reason a typed account could not be read. Subscription item failures do not end the stream. */
sealed class SolanaAccountError : ThrowableError {
    /** The RPC request itself failed. */
    data class Rpc(val rpcError: RpcError) : SolanaAccountError() {
        override val message get() = "RPC error while fetching an account"
        override val cause get() = rpcError.toException()
    }

    /** No account exists at [address] as of [context]. */
    data class Missing(val address: SolanaAddress, val context: RpcContext) : SolanaAccountError() {
        override val message get() = "Account $address does not exist at slot ${context.slot}"
    }

    /** The account exists, but is owned by another program or its data does not decode. */
    data class Decode(
        val address: SolanaAddress,
        val context: RpcContext,
        val info: AccountInfo,
        val error: SolanaDecodeError,
    ) : SolanaAccountError() {
        override val message get() = error.message
        override val cause get() = error.toException()
    }
}
