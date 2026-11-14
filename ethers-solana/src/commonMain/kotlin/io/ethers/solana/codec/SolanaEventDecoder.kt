package io.ethers.solana.codec

import io.ethers.core.Result

/**
 * Decodes the events of one program from their data, discriminator included. A [SolanaEventFactory]
 * decodes one event type; a generated program's `decodeEvent` decodes any of its events.
 */
fun interface SolanaEventDecoder<out T> {
    fun decode(data: ByteArray, context: SolanaEventContext): Result<T, SolanaDecodeError>
}
