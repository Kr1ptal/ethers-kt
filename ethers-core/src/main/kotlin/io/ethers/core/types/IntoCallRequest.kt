package io.ethers.core.types

/**
 * Interface to convert a class into a [CallRequest].
 * */
interface IntoCallRequest {
    /**
     * Get the [CallRequest] representation of `this` class.
     * */
    fun toCallRequest(): CallRequest
}