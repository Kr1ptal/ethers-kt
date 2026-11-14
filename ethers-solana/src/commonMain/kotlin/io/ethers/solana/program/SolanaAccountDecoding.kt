@file:JvmName("SolanaAccountDecoding")

package io.ethers.solana.program

import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.solana.codec.SolanaAccountDecoder
import io.ethers.solana.codec.SolanaDecodeError
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.rpc.AccountInfo
import io.ethers.solana.types.rpc.ContextValue
import io.ethers.solana.types.rpc.RpcContext
import io.ethers.solana.types.rpc.TransactionSimulation
import kotlin.jvm.JvmName

/**
 * Decode the account at [address], as the node returned it at [context], after checking that [owner] owns
 * it. Missing accounts and decoding failures retain their address and context in [SolanaAccountError].
 */
fun <T> SolanaAccountDecoder<T>.decodeAccount(
    address: SolanaAddress,
    context: RpcContext,
    info: AccountInfo?,
    owner: SolanaAddress,
): Result<SolanaAccount<T>, SolanaAccountError> {
    if (info == null) return failure(SolanaAccountError.Missing(address, context))
    return decodeExisting(address, context, info, owner)
}

internal fun <T> SolanaAccountDecoder<T>.decodeExisting(
    address: SolanaAddress,
    context: RpcContext,
    info: AccountInfo,
    owner: SolanaAddress,
): Result<SolanaAccount<T>, SolanaAccountError> {
    if (info.owner != owner) return failure(SolanaAccountError.Decode(address, context, info, SolanaDecodeError.UnexpectedOwner(address, info.owner, owner)))
    return decode(info.data.asByteArray()).mapError { SolanaAccountError.Decode(address, context, info, it) }.map { SolanaAccount(address, context, info, it) }
}

/**
 * Decode the accounts [simulation] returned for [addresses], the addresses its config requested, in the
 * same order and as of the simulation's context, checking that [owner] owns each. Returns null when the
 * simulation returned no accounts.
 *
 * @throws IllegalArgumentException if the simulation returned a different number of accounts.
 */
fun <T> SolanaAccountDecoder<T>.decodeAccounts(
    simulation: ContextValue<TransactionSimulation>,
    addresses: List<SolanaAddress>,
    owner: SolanaAddress,
): List<Result<SolanaAccount<T>, SolanaAccountError>>? {
    val infos = simulation.value.accounts ?: return null
    require(addresses.size == infos.size) { "Requested ${addresses.size} accounts, the simulation returned ${infos.size}" }
    return addresses.mapIndexed { i, address -> decodeAccount(address, simulation.context, infos[i], owner) }
}
