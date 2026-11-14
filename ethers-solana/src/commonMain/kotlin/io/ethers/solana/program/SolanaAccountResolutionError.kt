package io.ethers.solana.program

import io.ethers.core.ThrowableError
import io.ethers.providers.RpcError
import io.ethers.solana.codec.SolanaDecodeError
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.rpc.RpcContext

/**
 * A reason [SolanaAccountResolver] could not resolve an instruction's accounts.
 *
 * Accounts are named by their path in the instruction, such as `vault` or `escrow.vault` for an account in a group.
 * Where an account was fetched because another account's derivation needs its data, `requiredBy` names that other
 * account.
 */
sealed class SolanaAccountResolutionError : ThrowableError {
    /** An account fetch failed. */
    data class Rpc(val rpcError: RpcError) : SolanaAccountResolutionError() {
        override val message get() = "RPC error while fetching accounts to resolve: ${rpcError.message}"
        override val cause get() = rpcError.toException()
    }

    /**
     * The required [account] was not provided, and the IDL gives no way to derive it, or every way it gives depends on
     * an optional account that was not provided.
     */
    data class MissingAccount(val account: String, val reason: String) : SolanaAccountResolutionError() {
        override val message get() = "Account '$account' is required: $reason"
    }

    /** No account exists at [address], the [account] whose data [requiredBy]'s derivation reads. */
    data class AccountNotFound(
        val account: String,
        val address: SolanaAddress,
        val context: RpcContext,
        val requiredBy: String,
    ) : SolanaAccountResolutionError() {
        override val message get() = "Account '$account' ($address) does not exist at slot ${context.slot}, but '$requiredBy' is derived from its data"
    }

    /** The [account] at [address], whose data [requiredBy]'s derivation reads, is owned by [owner] rather than [expected]. */
    data class OwnerMismatch(
        val account: String,
        val address: SolanaAddress,
        val owner: SolanaAddress,
        val expected: SolanaAddress,
        val requiredBy: String,
    ) : SolanaAccountResolutionError() {
        override val message get() = "Account '$account' ($address) is owned by $owner, not $expected, so '$requiredBy' cannot be derived from its data"
    }

    /** The data of the [account] at [address] is not of any of the [expected] account types [requiredBy]'s derivation reads. */
    data class AccountTypeMismatch(
        val account: String,
        val address: SolanaAddress,
        val expected: List<String>,
        val requiredBy: String,
    ) : SolanaAccountResolutionError() {
        override val message get() = "Account '$account' ($address) is not ${expected.joinToString(" or ")}, so '$requiredBy' cannot be derived from its data"
    }

    /** The data of the [account] at [address] has the discriminator of [type], but does not decode as one. */
    data class Undecodable(
        val account: String,
        val address: SolanaAddress,
        val type: String,
        val error: SolanaDecodeError,
        val requiredBy: String,
    ) : SolanaAccountResolutionError() {
        override val message get() = "Account '$account' ($address) does not decode as $type: ${error.message}"
        override val cause get() = error.toException()
    }

    /**
     * The relation sources of [account] name different addresses for it. [candidates] maps the path of each source to
     * the address its data names.
     */
    data class AmbiguousRelation(val account: String, val candidates: Map<String, SolanaAddress>) : SolanaAccountResolutionError() {
        override val message get() = "Account '$account' is named differently by its relations: " +
            candidates.entries.joinToString { (source, address) -> "'$source' names $address" }
    }

    /** The seeds of the program derived [account] cannot derive an address, such as a seed longer than 32 bytes. */
    data class InvalidSeeds(val account: String, val reason: String) : SolanaAccountResolutionError() {
        override val message get() = "Account '$account' cannot be derived: $reason"
    }

    /**
     * Accounts whose derivations depend on each other, so none can be resolved without providing one of them. [path]
     * starts and ends with the same account, each depending on the next.
     */
    data class DependencyCycle(val path: List<String>) : SolanaAccountResolutionError() {
        override val message get() = "Accounts depend on each other: ${path.joinToString(" -> ")}; provide one of them"
    }
}
