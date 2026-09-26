package io.ethers.solana.program

import io.ethers.core.ThrowableError
import io.ethers.providers.RpcError
import io.ethers.solana.codec.SolanaDecodeError

/** A reason fetching an account failed. */
sealed class SolanaAccountError : ThrowableError {
    /** The RPC request itself failed. */
    data class Rpc(val rpcError: RpcError) : SolanaAccountError() {
        override val message get() = "RPC error while fetching an account"
        override val cause get() = rpcError.toException()
    }

    /** The account exists, but is owned by another program or its data does not decode. */
    data class Decode(val error: SolanaDecodeError) : SolanaAccountError() {
        override val message get() = error.message
        override val cause get() = error.toException()
    }
}
