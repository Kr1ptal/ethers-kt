@file:JvmName("SolanaAccountRequests")

package io.ethers.solana.program

import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.providers.RpcError
import io.ethers.providers.types.RpcRequest
import io.ethers.providers.types.RpcSubscribe
import io.ethers.solana.codec.SolanaAccountFactory
import io.ethers.solana.providers.middleware.SolanaApi
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.rpc.Commitment
import io.ethers.solana.types.rpc.SolanaAccountConfig
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * Fetch the account at [address] and decode it as this factory's type, checking that [owner] owns it.
 *
 * An account that does not exist is a [MissingSolanaAccount]. An account owned by another program, or whose
 * data does not decode, fails the request with [SolanaAccountError.Decode].
 */
@JvmOverloads
fun <T> SolanaAccountFactory<T>.fetch(
    api: SolanaApi,
    address: SolanaAddress,
    owner: SolanaAddress,
    commitment: Commitment = api.defaultCommitment,
): RpcRequest<MaybeSolanaAccount<T>, SolanaAccountError> {
    return api.getAccountInfo(address, SolanaAccountConfig(commitment = commitment))
        .mapError<SolanaAccountError> { SolanaAccountError.Rpc(it) }
        .andThen { response ->
            decodeAccount(address, response.context, response.value, owner).mapError { SolanaAccountError.Decode(it) }
        }
}

/**
 * Fetch the accounts at [addresses] in one request and decode each as this factory's type, as [fetch]
 * does. The accounts keep the order of [addresses], each missing one a [MissingSolanaAccount]; the first
 * account that does not decode fails the request.
 */
@JvmOverloads
fun <T> SolanaAccountFactory<T>.fetch(
    api: SolanaApi,
    addresses: List<SolanaAddress>,
    owner: SolanaAddress,
    commitment: Commitment = api.defaultCommitment,
): RpcRequest<List<MaybeSolanaAccount<T>>, SolanaAccountError> {
    return api.getMultipleAccounts(addresses, SolanaAccountConfig(commitment = commitment))
        .mapError<SolanaAccountError> { SolanaAccountError.Rpc(it) }
        .andThen { response ->
            val accounts = ArrayList<MaybeSolanaAccount<T>>(addresses.size)
            addresses.forEachIndexed { i, address ->
                val decoded = decodeAccount(address, response.context, response.value.getOrNull(i), owner)
                decoded.unwrapErrorOrNull()?.let { return@andThen failure(SolanaAccountError.Decode(it)) }
                accounts.add(decoded.unwrap())
            }
            success(accounts)
        }
}

/**
 * Subscribe to the account at [address], decoding each notification as this factory's type after checking
 * that [owner] owns it. Once the account no longer exists, notifications are a [MissingSolanaAccount];
 * notifications that do not decode are skipped.
 *
 * Notifications carry the account as of their slot; they are not guaranteed to include every change.
 */
@JvmOverloads
fun <T> SolanaAccountFactory<T>.watch(
    api: SolanaApi,
    address: SolanaAddress,
    owner: SolanaAddress,
    commitment: Commitment = api.defaultCommitment,
): RpcSubscribe<MaybeSolanaAccount<T>, RpcError> {
    return api.subscribeAccount(address, commitment).map { channel ->
        channel.mapNotNull { decodeAccount(address, it.context, it.value, owner).unwrapOrNull() }
    }
}
