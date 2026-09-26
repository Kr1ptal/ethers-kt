package io.ethers.solana.codec

import io.ethers.core.Result

/**
 * Decodes the data of accounts owned by one program. A [SolanaAccountFactory] decodes one account type;
 * a generated program's `decodeAccount` decodes any of its account types.
 */
fun interface SolanaAccountDecoder<out T> {
    fun decode(data: ByteArray): Result<T, SolanaDecodeError>
}
