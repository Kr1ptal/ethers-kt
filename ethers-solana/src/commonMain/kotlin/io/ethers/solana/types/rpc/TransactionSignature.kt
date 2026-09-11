@file:kotlinx.serialization.UseSerializers(io.ethers.solana.types.U64Serializer::class)

package io.ethers.solana.types.rpc

import io.ethers.solana.types.SolanaSignature
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.Serializable

/** One entry of getSignaturesForAddress: the signature and how its transaction ended. */
@Serializable data class TransactionSignature(val signature: SolanaSignature, val slot: BigInteger, val err: TransactionError?, val memo: String? = null, val blockTime: Long? = null, val confirmationStatus: Commitment? = null) {
    val isError: Boolean get() = err != null
}
