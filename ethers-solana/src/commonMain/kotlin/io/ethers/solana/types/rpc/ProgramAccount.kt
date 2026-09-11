package io.ethers.solana.types.rpc

import io.ethers.solana.types.SolanaAddress
import kotlinx.serialization.Serializable

/**
 * An account owned by a program, with the address it lives at.
 *
 * The same shape serves getProgramAccounts, getTokenAccountsByOwner and the programSubscribe stream,
 * which all answer with an account and where to find it.
 */
@Serializable
data class ProgramAccount(val pubkey: SolanaAddress, val account: AccountInfo)
