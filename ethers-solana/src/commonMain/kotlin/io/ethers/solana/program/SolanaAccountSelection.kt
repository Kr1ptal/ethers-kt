package io.ethers.solana.program

import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.providers.RpcError
import io.ethers.providers.types.RpcRequest
import io.ethers.providers.types.RpcSubscribe
import io.ethers.solana.codec.BorshReader
import io.ethers.solana.codec.SolanaAccountFactory
import io.ethers.solana.codec.SolanaAccountField
import io.ethers.solana.codec.SolanaDecodeError
import io.ethers.solana.providers.middleware.SolanaApi
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.rpc.AccountInfo
import io.ethers.solana.types.rpc.Commitment
import io.ethers.solana.types.rpc.DataSlice
import io.ethers.solana.types.rpc.RpcContext
import io.ethers.solana.types.rpc.SolanaAccountConfig
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * [fields] of the accounts of [factory]'s type that [program] owns, read as one [dataSlice] of their data: the node
 * returns only the bytes from the first field to the end of the last.
 *
 * A slice holds no discriminator to identify the account type by, so fetching by address checks only the owner. A
 * selection that starts with the account's first field includes the discriminator, and checks it too. Filter queries
 * and subscriptions select accounts by discriminator on the node, before slicing.
 */
class SolanaAccountSelection<T, R>(
    @JvmField val factory: SolanaAccountFactory<T>,
    @JvmField val program: SolanaAddress,
    private val projection: SolanaAccountProjection<T, R>,
) {
    val fields: List<SolanaAccountField<T, *>> = projection.fields

    /** Whether the slice includes the discriminator, which it does when the first field directly follows it. */
    @JvmField
    val checksDiscriminator: Boolean

    @JvmField
    val dataSlice: DataSlice

    init {
        require(this.fields.isNotEmpty()) { "Select at least one field" }
        val first = this.fields.minOf { it.offset }
        val end = factory.discriminator.size + this.fields.maxOf { it.offset + it.size }
        checksDiscriminator = first == 0
        val start = if (checksDiscriminator) 0 else factory.discriminator.size + first
        dataSlice = DataSlice(start, end - start)
    }

    /** These fields of accounts owned by [programId] instead, such as an alternate deployment of the program. */
    fun program(programId: SolanaAddress): SolanaAccountSelection<T, R> = SolanaAccountSelection(factory, programId, projection)

    /**
     * Fetch the selected fields of the account at [address]. An account that does not exist fails with
     * [SolanaAccountError.Missing]; one owned by another program, of another type where the discriminator is selected,
     * or too short for the selection fails with [SolanaAccountError.Decode].
     */
    @JvmOverloads
    fun fetch(api: SolanaApi, address: SolanaAddress, commitment: Commitment = api.defaultCommitment): RpcRequest<R, SolanaAccountError> {
        return api.getAccountInfo(address, config(commitment))
            .mapError<SolanaAccountError> { SolanaAccountError.Rpc(it) }
            .andThen { response -> slice(address, response.context, response.value, program) }
    }

    /**
     * Fetch the selected fields of the accounts at [addresses] in one request, as [fetch] does. Results keep the order
     * of [addresses]; only RPC errors fail the whole request.
     */
    @JvmOverloads
    fun fetch(
        api: SolanaApi,
        addresses: List<SolanaAddress>,
        commitment: Commitment = api.defaultCommitment,
    ): RpcRequest<List<Result<R, SolanaAccountError>>, RpcError> {
        return api.getMultipleAccounts(addresses, config(commitment)).map { response ->
            addresses.mapIndexed { i, address -> slice(address, response.context, response.value[i], program) }
        }
    }

    /**
     * Subscribe to the selected fields of the account at [address], as [fetch] reads them. Failures are emitted as
     * failed results without ending the subscription.
     */
    @JvmOverloads
    fun watch(
        api: SolanaApi,
        address: SolanaAddress,
        commitment: Commitment = api.defaultCommitment,
    ): RpcSubscribe<Result<R, SolanaAccountError>, RpcError> {
        return api.subscribeAccount(address, config(commitment)).map { channel ->
            channel.map { slice(address, it.context, it.value, program) }
        }
    }

    internal fun config(commitment: Commitment) = SolanaAccountConfig(commitment = commitment, dataSlice = dataSlice)

    /** The slice of the account at [address] as returned at [context], after checking that [owner] owns it. */
    internal fun slice(address: SolanaAddress, context: RpcContext, info: AccountInfo?, owner: SolanaAddress): Result<R, SolanaAccountError> {
        if (info == null) return failure(SolanaAccountError.Missing(address, context))
        fun invalid(error: SolanaDecodeError) = failure(SolanaAccountError.Decode(address, context, info, error))
        if (info.owner != owner) return invalid(SolanaDecodeError.UnexpectedOwner(address, info.owner, owner))
        if (info.data.size < dataSlice.length) {
            return invalid(
                SolanaDecodeError.Malformed(
                    SolanaDecodeError.Reason.TRUNCATED,
                    "Expected ${dataSlice.length} bytes of a ${factory.name} account from offset ${dataSlice.offset}, got ${info.data.size}",
                    0,
                    info.data,
                ),
            )
        }
        if (checksDiscriminator && !factory.matches(info.data)) return invalid(SolanaDecodeError.Unrecognized(info.data, "Not a ${factory.name} account"))
        return projection.decode(SolanaAccountSlice(address, context, info, this))
            .mapError { SolanaAccountError.Decode(address, context, info, it) }
    }

    override fun toString(): String = "SolanaAccountSelection(${factory.name}, ${fields.map { it.name }}, $dataSlice)"
}

/**
 * The selected fields of the account at [address] as of [context]: [info] holds the slice of its data that
 * [selection] read.
 */
data class SolanaAccountSlice<T>(
    val address: SolanaAddress,
    val context: RpcContext,
    val info: AccountInfo,
    val selection: SolanaAccountSelection<T, *>,
) {
    /**
     * The value of [field], decoded from its bytes in the slice. [field] is either selected or lies within a selected
     * field, such as a field of a selected struct or an element of a selected array.
     *
     * @throws IllegalArgumentException if [selection] does not include [field].
     * @throws io.ethers.core.ThrowableError.Exception if the field's bytes do not decode, such as an invalid bool.
     */
    operator fun <V> get(field: SolanaAccountField<T, V>): V = decode(field).unwrap()

    /**
     * The value of [field], decoded from its bytes in the slice, or why they do not decode. [field] is either selected
     * or lies within a selected field.
     *
     * @throws IllegalArgumentException if [selection] does not include [field].
     */
    fun <V> decode(field: SolanaAccountField<T, V>): Result<V, SolanaDecodeError> {
        require(selection.fields.any { field.isWithin(it) }) { "$field is not in $selection" }
        val offset = selection.factory.discriminator.size + field.offset - selection.dataSlice.offset
        return BorshReader.decode(info.data.asByteArray(), offset, false, field::read)
    }
}
