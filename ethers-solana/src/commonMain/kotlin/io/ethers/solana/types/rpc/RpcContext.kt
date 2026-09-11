@file:kotlinx.serialization.UseSerializers(io.ethers.solana.types.U64Serializer::class)

package io.ethers.solana.types.rpc

import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.Serializable

/** The slot an answer was produced at, which most RPC methods wrap around their value. */
@Serializable data class RpcContext(val slot: BigInteger, val apiVersion: String? = null)

/** A value together with the [RpcContext] the node answered it in. */
@Serializable data class ContextValue<T>(val context: RpcContext, val value: T)
