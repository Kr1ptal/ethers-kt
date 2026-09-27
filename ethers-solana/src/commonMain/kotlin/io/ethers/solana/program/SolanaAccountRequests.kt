@file:JvmName("SolanaAccountRequests")

package io.ethers.solana.program

import io.ethers.core.Result
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
 * An account that does not exist fails with [SolanaAccountError.Missing]. An account owned by another program, or whose
 * data does not decode, fails the request with [SolanaAccountError.Decode].
 */
@JvmOverloads
fun <T> SolanaAccountFactory<T>.fetch(
    api: SolanaApi,
    address: SolanaAddress,
    owner: SolanaAddress,
    commitment: Commitment = api.defaultCommitment,
): RpcRequest<SolanaAccount<T>, SolanaAccountError> {
    return api.getAccountInfo(address, SolanaAccountConfig(commitment = commitment))
        .mapError<SolanaAccountError> { SolanaAccountError.Rpc(it) }
        .andThen { response ->
            decodeAccount(address, response.context, response.value, owner)
        }
}

/**
 * Fetch the accounts at [addresses] in one request and decode each as this factory's type, as [fetch]
 * does. Results keep the order of [addresses]. Missing and undecodable accounts are individual failures;
 * only RPC errors fail the whole request.
 */
@JvmOverloads
fun <T> SolanaAccountFactory<T>.fetch(
    api: SolanaApi,
    addresses: List<SolanaAddress>,
    owner: SolanaAddress,
    commitment: Commitment = api.defaultCommitment,
): RpcRequest<List<Result<SolanaAccount<T>, SolanaAccountError>>, RpcError> {
    return api.getMultipleAccounts(addresses, SolanaAccountConfig(commitment = commitment)).map { response ->
        addresses.mapIndexed { i, address -> decodeAccount(address, response.context, response.value[i], owner) }
    }
}

/**
 * Subscribe to the account at [address], decoding each notification as this factory's type after checking
 * that [owner] owns it. Missing accounts and decoding failures are emitted as failed results with their
 * notification context. These failures do not end the subscription.
 *
 * Notifications carry the account as of their slot; they are not guaranteed to include every change.
 */
@JvmOverloads
fun <T> SolanaAccountFactory<T>.watch(
    api: SolanaApi,
    address: SolanaAddress,
    owner: SolanaAddress,
    commitment: Commitment = api.defaultCommitment,
): RpcSubscribe<Result<SolanaAccount<T>, SolanaAccountError>, RpcError> {
    return api.subscribeAccount(address, commitment).map { channel ->
        channel.map { decodeAccount(address, it.context, it.value, owner) }
    }
}
