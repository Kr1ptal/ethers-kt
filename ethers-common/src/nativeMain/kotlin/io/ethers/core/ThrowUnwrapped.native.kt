package io.ethers.core

// an exception reaching Swift without being declared aborts the process, so wrap the ones Result.unwrap does not declare
internal actual fun throwUnwrapped(error: Throwable): Nothing = when (error) {
    is RuntimeException, is Error -> throw error
    else -> throw RuntimeException(error.message, error)
}
