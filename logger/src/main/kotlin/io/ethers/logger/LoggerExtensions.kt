package io.ethers.logger

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * NOTE: If logging inside a class, use [getLogger] function instead to get class name automatically.
 *
 * Usage:
 *
 * ```kotlin
 * val LOG = getLogger<ExampleClass>()
 * ```
 * */
inline fun <reified T : Any> getLogger(): Logger {
    return LoggerFactory.getLogger(T::class.java)
}

inline fun <T : Any> T.getLogger(): Logger {
    return LoggerFactory.getLogger(javaClass)
}

/**
 * Logs with level TRACE if enabled.
 * */
inline fun Logger.trc(supplier: () -> String) {
    if (isTraceEnabled) {
        trace(supplier.invoke())
    }
}

/**
 * Logs with level DEBUG if enabled.
 * */
inline fun Logger.dbg(supplier: () -> String) {
    if (isDebugEnabled) {
        debug(supplier.invoke())
    }
}

/**
 * Logs with level INFO if enabled.
 * */
inline fun Logger.inf(supplier: () -> String) {
    if (isInfoEnabled) {
        info(supplier.invoke())
    }
}

/**
 * Logs with level WARN if enabled.
 * */
inline fun Logger.wrn(supplier: () -> String) {
    if (isWarnEnabled) {
        warn(supplier.invoke())
    }
}

/**
 * Logs with level ERROR if enabled.
 * */
inline fun Logger.err(supplier: () -> String) {
    if (isErrorEnabled) {
        error(supplier.invoke())
    }
}

/**
 * Logs [throwable] with level ERROR if enabled.
 * */
inline fun Logger.err(throwable: Throwable, supplier: () -> String) {
    if (isErrorEnabled) {
        error(supplier.invoke(), throwable)
    }
}
