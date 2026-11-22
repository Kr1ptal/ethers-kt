package io.ethers.solana.types

import io.ethers.core.Result
import io.ethers.core.ThrowableError
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.crypto.Base58
import kotlin.native.ObjCName

/**
 * Returned by the safe factories of Solana values (addresses, signatures, blockhashes, keys and bytes) when their
 * input is malformed: text outside its encoding's alphabet, or bytes of the wrong length.
 */
@ObjCName("SolanaDecodingError", exact = true)
data class SolanaDecodingError(override val message: String) : ThrowableError

/** [create] the value from [bytes] if they are exactly [size] bytes long, naming it [name] otherwise. */
internal inline fun <T> decodeFixedSize(bytes: ByteArray, size: Int, name: String, create: (ByteArray) -> T): Result<T, SolanaDecodingError> {
    if (bytes.size != size) return failure(SolanaDecodingError("$name must contain $size bytes, got ${bytes.size}"))
    return success(create(bytes))
}

/** As [decodeFixedSize], from Base58 [value]. */
internal inline fun <T> decodeFixedSizeBase58(value: String, size: Int, name: String, create: (ByteArray) -> T): Result<T, SolanaDecodingError> {
    val bytes = Base58.decodeOrNull(value) ?: return failure(SolanaDecodingError("$name is not valid Base58: $value"))
    return decodeFixedSize(bytes, size, name, create)
}
