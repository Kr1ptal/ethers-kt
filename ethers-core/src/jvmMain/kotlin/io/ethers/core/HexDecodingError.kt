package io.ethers.core

/**
 * Error returned when there is an error during hex decoding of various types.
 * */
class HexDecodingError(val msg: String) : Result.Error {
    override fun doThrow(): Nothing {
        throw RuntimeException(msg)
    }
}
