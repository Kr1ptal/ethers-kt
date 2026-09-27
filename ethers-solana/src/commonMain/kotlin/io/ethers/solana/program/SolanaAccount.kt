package io.ethers.solana.program

import io.ethers.core.Result
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.rpc.AccountInfo
import io.ethers.solana.types.rpc.RpcContext

/** An existing account at [address] as of [context], with its raw [info] and successfully decoded [data]. */
data class SolanaAccount<out T>(
    val address: SolanaAddress,
    val context: RpcContext,
    val info: AccountInfo,
    val data: T,
)

/**
 * Results for program accounts matching a query, as of [context]. Undecodable accounts are retained as
 * failures. An empty query has no results but still carries its context.
 */
class SolanaAccounts<out T>(val context: RpcContext, private val accounts: List<Result<SolanaAccount<T>, SolanaAccountError>>) : List<Result<SolanaAccount<T>, SolanaAccountError>> by accounts {
    override fun equals(other: Any?): Boolean = accounts == other
    override fun hashCode(): Int = accounts.hashCode()
    override fun toString(): String = "SolanaAccounts(context=$context, accounts=$accounts)"
}
