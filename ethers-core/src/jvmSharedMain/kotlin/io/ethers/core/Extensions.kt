package io.ethers.core

import io.ethers.core.types.Bytes
import io.ethers.rlp.RlpDecodable

/**
 * Decode RLP-encoded [data] and return instance of [T], or null if decoding fails.
 */
fun <T> RlpDecodable<T>.rlpDecode(data: Bytes): T? {
    return rlpDecode(data.asByteArray())
}
