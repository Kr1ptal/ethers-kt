package io.ethers.solana.codec

import io.ethers.core.ThrowableError
import io.ethers.solana.types.SolanaBytes

/**
 * A reason program data could not be decoded.
 *
 * [Unrecognized] means the data is not the value asked for, so another decoder may still claim it.
 * [Malformed] means the data was claimed, by its discriminator or by being handed to a codec directly,
 * but its bytes are invalid; no other decoder should be tried. [Unsupported] means the layout cannot be
 * decoded by this library at all. Every case that saw input keeps it in [data].
 */
sealed class SolanaDecodeError : ThrowableError {
    /** Thrown form of this error. Remains an [IllegalArgumentException], as these are argument failures. */
    override fun toException(): SolanaDecodeException = SolanaDecodeException(this)

    /** The data does not start with the expected discriminator. */
    data class Unrecognized(val data: SolanaBytes, override val message: String) : SolanaDecodeError()

    /** The data was recognized, but the bytes at [offset] violate the layout. */
    data class Malformed(
        val reason: Reason,
        override val message: String,
        val offset: Int,
        val data: SolanaBytes,
    ) : SolanaDecodeError()

    /** Which check [Malformed] failed. */
    enum class Reason {
        /** The data ended before the value did. */
        TRUNCATED,

        /** Bytes remain after a value that must span the whole payload. */
        TRAILING_DATA,

        /** A boolean byte other than 0 or 1. */
        INVALID_BOOL,

        /** An option tag other than 0 or 1. */
        INVALID_OPTION_TAG,

        /** An enum variant index the type does not declare. */
        INVALID_VARIANT,

        /** A string that is not valid UTF-8. */
        INVALID_UTF8,

        /** A collection length larger than the remaining data can hold. */
        LENGTH_OUT_OF_RANGE,

        /** A value the layout forbids, such as a NaN float or one rejected by a custom codec. */
        INVALID_VALUE,
    }

    /** The layout cannot be decoded by this library. */
    data class Unsupported(override val message: String) : SolanaDecodeError()
}

/**
 * Carries a [SolanaDecodeError] that was raised rather than returned: by a codec's `read` on invalid
 * input, or by [io.ethers.core.Result.unwrap] on a failed decode.
 */
class SolanaDecodeException(val error: SolanaDecodeError) : IllegalArgumentException(error.message ?: error.toString())
