package io.ethers.core

/**
 * Throw [error] from [Result.unwrap], converting it to one of the unchecked exceptions [Result.unwrap] declares
 * where the platform needs that.
 * */
internal expect fun throwUnwrapped(error: Throwable): Nothing
