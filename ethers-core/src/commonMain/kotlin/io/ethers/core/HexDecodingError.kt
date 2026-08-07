package io.ethers.core

/**
 * Error returned when there is an error during hex decoding of various types.
 * */
class HexDecodingError(override val message: String) : ThrowableError
