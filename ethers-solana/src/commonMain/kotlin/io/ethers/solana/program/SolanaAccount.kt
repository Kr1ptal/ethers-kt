package io.ethers.solana.program

import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.rpc.AccountInfo
import io.ethers.solana.types.rpc.RpcContext

/**
 * The account at [address] as of [context]: a [SolanaAccount] when one exists there, with [info] as the
 * node returned it and its data decoded as [data], or a [MissingSolanaAccount] when none does.
 */
sealed interface MaybeSolanaAccount<out T> {
    val address: SolanaAddress
    val context: RpcContext
    val info: AccountInfo?
    val data: T?

    val exists: Boolean get() = info != null
}

/** The account at [address] as of [context], with [info] as the node returned it and its data decoded as [data]. */
data class SolanaAccount<out T>(
    override val address: SolanaAddress,
    override val context: RpcContext,
    override val info: AccountInfo,
    override val data: T,
) : MaybeSolanaAccount<T>

/** No account exists at [address] as of [context]. */
data class MissingSolanaAccount(override val address: SolanaAddress, override val context: RpcContext) : MaybeSolanaAccount<Nothing> {
    override val info: AccountInfo? get() = null
    override val data: Nothing? get() = null
}

/**
 * Program accounts matching a query, as of [context]. Iterates, indexes and compares like any other list of
 * the same accounts.
 */
class SolanaAccounts<out T>(val context: RpcContext, private val accounts: List<SolanaAccount<T>>) : List<SolanaAccount<T>> by accounts {
    override fun equals(other: Any?): Boolean = accounts == other
    override fun hashCode(): Int = accounts.hashCode()
    override fun toString(): String = "SolanaAccounts(context=$context, accounts=$accounts)"
}
