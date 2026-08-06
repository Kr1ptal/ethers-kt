package io.ethers.core

/**
 * Error returned when there is an error during hex decoding of various types.
 * */
class HexDecodingError(val msg: String) : ThrowableError {
    override fun toException(): RuntimeException {
        return RuntimeException(msg)
    }
}
