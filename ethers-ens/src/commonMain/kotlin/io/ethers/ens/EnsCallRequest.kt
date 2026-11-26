package io.ethers.ens

import io.ethers.core.types.Address
import io.ethers.core.types.CallRequest
import io.ethers.core.types.IntoCallRequest
import kotlin.jvm.JvmSynthetic

/**
 * An immutable call request whose recipient is an ENS name that has not been resolved yet.
 *
 * ENS resolution needs network access and can fail, but [IntoCallRequest.toCallRequest] is synchronous and
 * infallible. Resolution therefore happens one level up, in [EnsMiddleware], and this type exists only to carry
 * the unresolved name to it:
 *
 * ```kotlin
 * val provider = EnsMiddleware(Provider.builder(url).buildAwait().unwrap())
 *
 * // build it with a builder lambda...
 * provider.call(EnsCallRequest("vitalik.eth") { data = callData }, BlockId.LATEST).sendAwait()
 *
 * // ...or wrap a CallRequest you already have
 * provider.call(request.toEns("vitalik.eth"), BlockId.LATEST).sendAwait()
 * ```
 *
 * The recipient is always the ENS name: [request]'s own `to` is replaced by the resolved address.
 *
 * Passing one to a plain [io.ethers.providers.Provider] throws from [toCallRequest] rather than silently
 * producing a call with no recipient.
 * */
class EnsCallRequest(
    val toEns: String,
    /**
     * The fields of the call, other than the recipient.
     * */
    val request: CallRequest,
) : IntoCallRequest {
    constructor(toEns: String) : this(toEns, EMPTY_REQUEST)

    override fun toCallRequest(): CallRequest {
        throw IllegalStateException(
            "Call to ENS name '$toEns' reached a provider that cannot resolve it. " +
                "Wrap your provider in EnsMiddleware: EnsMiddleware(provider).",
        )
    }

    /**
     * Return a copy of **this** request with the changes applied by [builder] to the underlying [request]. **This**
     * request is not modified.
     * */
    @JvmSynthetic
    inline fun copy(builder: CallRequest.Builder.() -> Unit): EnsCallRequest = EnsCallRequest(toEns, request.copy(builder))

    /**
     * Get a plain [CallRequest] for this request, with [address] as the recipient.
     * */
    internal fun resolveTo(address: Address): CallRequest = request.copy { to = address }

    override fun toString(): String = "EnsCallRequest(toEns=$toEns, request=$request)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as EnsCallRequest

        return toEns == other.toEns && request == other.request
    }

    override fun hashCode(): Int = 31 * toEns.hashCode() + request.hashCode()

    companion object {
        private val EMPTY_REQUEST = CallRequest {}

        /**
         * Build an [EnsCallRequest] for [ensName], configuring the underlying request with [builder].
         * */
        @JvmSynthetic
        inline operator fun invoke(ensName: String, builder: CallRequest.Builder.() -> Unit): EnsCallRequest {
            return EnsCallRequest(ensName, CallRequest(builder))
        }
    }
}

/**
 * Wrap `this` call request so that it is sent to [ensName] instead of a raw address, resolved by [EnsMiddleware].
 * */
fun CallRequest.toEns(ensName: String): EnsCallRequest = EnsCallRequest(ensName, this)
