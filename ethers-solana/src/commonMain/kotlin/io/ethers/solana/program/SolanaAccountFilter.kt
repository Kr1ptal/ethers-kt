package io.ethers.solana.program

import io.ethers.core.Result
import io.ethers.crypto.Base58
import io.ethers.providers.RpcError
import io.ethers.providers.types.RpcRequest
import io.ethers.providers.types.RpcSubscribe
import io.ethers.solana.codec.BorshWriter
import io.ethers.solana.codec.SolanaAccountFactory
import io.ethers.solana.providers.AccountFilter
import io.ethers.solana.providers.middleware.SolanaApi
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.rpc.Commitment
import io.ethers.solana.types.rpc.SolanaAccountConfig

/** Filter over the accounts of one type that a program owns, selected by the type's discriminator. */
class SolanaAccountFilter<T>(api: SolanaApi, factory: SolanaAccountFactory<T>, program: SolanaAddress) :
    SolanaAccountFilterBase<T, SolanaAccountFilter<T>>(api, factory, program) {
    override val self: SolanaAccountFilter<T> get() = this
}

/**
 * Filter over the accounts of [factory]'s type that a program owns, like `EventFilterBase` for EVM events.
 *
 * Accounts are always selected by the type's discriminator; each call adds another condition. Generated
 * account types subclass this with a method per field at a known offset.
 */
abstract class SolanaAccountFilterBase<T, F : SolanaAccountFilterBase<T, F>>(
    private val api: SolanaApi,
    private val factory: SolanaAccountFactory<T>,
    program: SolanaAddress,
) {
    private var program: SolanaAddress = program
    private var commitment: Commitment = api.defaultCommitment
    private val filters = arrayListOf(factory.discriminatorFilter())

    protected abstract val self: F

    /** The node-side filters this selects accounts with, discriminator first. */
    val accountFilters: List<AccountFilter> get() = filters.toList()

    /** Select accounts owned by [programId] instead, such as an alternate deployment of the program. */
    fun program(programId: SolanaAddress): F {
        program = programId
        return self
    }

    fun commitment(commitment: Commitment): F {
        this.commitment = commitment
        return self
    }

    /** Select accounts whose data holds [bytes] at [offset], counted from the start of the account data. */
    fun memcmp(offset: Long, bytes: ByteArray): F {
        filters.add(AccountFilter.Memcmp(offset, Base58.encode(bytes)))
        return self
    }

    /** Select accounts allocated exactly [bytes] of data. */
    fun dataSize(bytes: Long): F {
        filters.add(AccountFilter.DataSize(bytes))
        return self
    }

    /** Select accounts whose payload holds the bytes [write] produces at [offset], counted from the end of the discriminator. */
    protected fun field(offset: Int, write: BorshWriter.() -> Unit): F {
        filters.add(factory.fieldFilter(offset, write))
        return self
    }

    /** Query the matching accounts. Undecodable accounts are retained as failed results. */
    fun query(): RpcRequest<SolanaAccounts<T>, RpcError> {
        val owner = program
        return api.getProgramAccounts(owner, filters.toList(), SolanaAccountConfig(commitment = commitment)).map { response ->
            val accounts = response.value.map { factory.decodeExisting(it.pubkey, response.context, it.account, owner) }
            SolanaAccounts(response.context, accounts)
        }
    }

    /**
     * Subscribe to changes of the matching accounts. Decoding failures are emitted as failed results without ending the subscription.
     *
     * Notifications carry an account as of their slot; they are not guaranteed to include every change.
     */
    fun subscribe(): RpcSubscribe<Result<SolanaAccount<T>, SolanaAccountError>, RpcError> {
        val owner = program
        return api.subscribeProgram(owner, filters.toList(), commitment).map { channel ->
            channel.map { factory.decodeExisting(it.value.pubkey, it.context, it.value.account, owner) }
        }
    }
}
