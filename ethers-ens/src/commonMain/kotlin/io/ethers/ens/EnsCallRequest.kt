package io.ethers.ens

import io.ethers.core.types.AccessList
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.core.types.Hash
import io.ethers.core.types.IntoCallRequest
import io.github.artificialpb.bignum.BigInteger
import kotlin.jvm.JvmOverloads

/**
 * A call request whose recipient is an ENS name that has not been resolved yet.
 *
 * ENS resolution needs network access and can fail, but [IntoCallRequest.toCallRequest] is synchronous and
 * infallible. Resolution therefore happens one level up, in [EnsMiddleware], and this type exists only to carry
 * the unresolved name to it:
 *
 * ```kotlin
 * val provider = EnsMiddleware(Provider.builder(url).buildAwait().unwrap())
 *
 * // build it directly...
 * provider.call(EnsCallRequest("vitalik.eth").data(callData), BlockId.LATEST).sendAwait()
 *
 * // ...with a builder lambda...
 * provider.call(EnsCallRequest("vitalik.eth") { data = callData }, BlockId.LATEST).sendAwait()
 *
 * // ...or wrap a CallRequest you already have
 * provider.call(CallRequest().data(callData).toEns("vitalik.eth"), BlockId.LATEST).sendAwait()
 * ```
 *
 * The recipient is always the ENS name, so there is deliberately no `to(Address)` builder - use a plain
 * [CallRequest] to target a raw address.
 *
 * Passing one to a plain [io.ethers.providers.Provider] throws from [toCallRequest] rather than silently
 * producing a call with no recipient.
 * */
class EnsCallRequest @JvmOverloads constructor(
    val toEnsName: String,
    request: CallRequest = CallRequest(),
) : IntoCallRequest {
    // defensive copy - callers must not be able to mutate the request after it has been handed over
    private val request = CallRequest(request)

    override fun toCallRequest(): CallRequest {
        throw IllegalStateException(
            "Call to ENS name '$toEnsName' reached a provider that cannot resolve it. " +
                "Wrap your provider in EnsMiddleware: EnsMiddleware(provider).",
        )
    }

    /**
     * Get a plain [CallRequest] for this request, with [address] as the recipient.
     * */
    internal fun resolveTo(address: Address): CallRequest = CallRequest(request).to(address)

    //-----------------------------------------------------------------------------------------------------------------
    //                                  Builders
    //
    // These mirror CallRequest's chaining builders one-for-one, minus to(Address). EnsCallRequestParityTest
    // fails if CallRequest gains or changes one and this list is not updated to match.
    //-----------------------------------------------------------------------------------------------------------------
    fun from(from: Address?) = apply { request.from = from }
    fun gas(gas: Long) = apply { request.gas = gas }
    fun gasPrice(gasPrice: BigInteger?) = apply { request.gasPrice = gasPrice }
    fun gasFeeCap(gasFeeCap: BigInteger?) = apply { request.gasFeeCap = gasFeeCap }
    fun gasTipCap(gasTipCap: BigInteger?) = apply { request.gasTipCap = gasTipCap }
    fun value(value: BigInteger?) = apply { request.value = value }
    fun nonce(nonce: Long) = apply { request.nonce = nonce }
    fun data(data: Bytes?) = apply { request.data = data }
    fun accessList(accessList: List<AccessList.Item>) = apply { request.accessList = accessList }
    fun chainId(chainId: Long) = apply { request.chainId = chainId }
    fun blobFeeCap(blobFeeCap: BigInteger?) = apply { request.blobFeeCap = blobFeeCap }
    fun blobVersionedHashes(blobVersionedHashes: List<Hash>?) = apply {
        request.blobVersionedHashes = blobVersionedHashes
    }

    override fun toString(): String = "EnsCallRequest(toEnsName=$toEnsName, request=$request)"

    companion object {
        /**
         * Build an [EnsCallRequest] for [ensName], configuring the underlying request with [builder].
         * */
        inline operator fun invoke(ensName: String, builder: CallRequest.() -> Unit): EnsCallRequest {
            return EnsCallRequest(ensName, CallRequest().apply(builder))
        }
    }
}

/**
 * Wrap `this` call request so that it is sent to [ensName] instead of a raw address, resolved by [EnsMiddleware].
 *
 * The request is copied, so later changes to `this` do not affect the returned [EnsCallRequest].
 * */
fun CallRequest.toEns(ensName: String): EnsCallRequest = EnsCallRequest(ensName, this)
