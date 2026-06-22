package io.ethers.logger

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * NOTE: If logging inside a class, use [getLogger] function instead to get class name automatically.
 *
 * Usage:
 *
 * ```kotlin
 * val LOG = getLogger<ExampleClass>()
 * ```
 * */
inline fun <reified T : Any> getLogger(): KLogger {
    return KotlinLogging.logger(T::class.java.name)
}

inline fun <T : Any> T.getLogger(): KLogger {
    return KotlinLogging.logger(javaClass.name)
}

/**
 * Logs with level TRACE if enabled.
 * */
inline fun KLogger.trc(supplier: () -> String) {
    if (isTraceEnabled()) {
        val msg = supplier()
        trace { msg }
    }
}

/**
 * Logs with level DEBUG if enabled.
 * */
inline fun KLogger.dbg(supplier: () -> String) {
    if (isDebugEnabled()) {
        val msg = supplier()
        debug { msg }
    }
}

/**
 * Logs with level DEBUG if enabled.
 * */
inline fun KLogger.dbg(throwable: Throwable, supplier: () -> String) {
    if (isDebugEnabled()) {
        val msg = supplier()
        debug(throwable) { msg }
    }
}

/**
 * Logs with level INFO if enabled.
 * */
inline fun KLogger.inf(supplier: () -> String) {
    if (isInfoEnabled()) {
        val msg = supplier()
        info { msg }
    }
}

/**
 * Logs with level INFO if enabled.
 * */
inline fun KLogger.inf(throwable: Throwable, supplier: () -> String) {
    if (isInfoEnabled()) {
        val msg = supplier()
        info(throwable) { msg }
    }
}

/**
 * Logs with level WARN if enabled.
 * */
inline fun KLogger.wrn(supplier: () -> String) {
    if (isWarnEnabled()) {
        val msg = supplier()
        warn { msg }
    }
}

/**
 * Logs with level ERROR if enabled.
 * */
inline fun KLogger.err(supplier: () -> String) {
    if (isErrorEnabled()) {
        val msg = supplier()
        error { msg }
    }
}

/**
 * Logs [throwable] with level ERROR if enabled.
 * */
inline fun KLogger.err(throwable: Throwable, supplier: () -> String) {
    if (isErrorEnabled()) {
        val msg = supplier()
        error(throwable) { msg }
    }
}
