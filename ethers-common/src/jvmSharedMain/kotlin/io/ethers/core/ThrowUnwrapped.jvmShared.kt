package io.ethers.core

// the JVM does not check declared exceptions at runtime, so the error is thrown as is
internal actual fun throwUnwrapped(error: Throwable): Nothing = throw error
