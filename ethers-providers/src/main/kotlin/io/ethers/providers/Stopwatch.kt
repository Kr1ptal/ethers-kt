package io.ethers.providers

import java.util.concurrent.TimeUnit

/**
 * Monotonic clock used for measuring elapsed duration. It supports only relative times. In contrast, wall time is a
 * reading of "now" as given by a method like [System.currentTimeMillis]. Such values can be subtracted to obtain a
 * duration, but doing so does not give a reliable measurement of elapsed time, because wall time readings are
 * inherently approximate, routinely affected by periodic clock corrections. Because this class uses [System.nanoTime],
 * it is unaffected by these changes.
 * */
@JvmInline
internal value class Stopwatch private constructor(private val startNano: Long) {

    /**
     * Return elapsed duration in MILLIS.
     */
    fun elapsedMillis(): Long = TimeUnit.NANOSECONDS.toMillis(elapsedNano())

    /**
     * Return elapsed duration in MICROS.
     */
    fun elapsedMicro(): Long = TimeUnit.NANOSECONDS.toMicros(elapsedNano())

    /**
     * Return elapsed duration in NANOS.
     *
     * NOTE: Will return positive number even if it overflows.
     */
    fun elapsedNano(): Long = System.nanoTime() - startNano

    /**
     * Check if it has already elapsed expected time since stopwatch [start].
     *
     * @param duration expected elapsed duration
     * @param unit of elapsed duration
     */
    fun hasElapsed(duration: Long, unit: TimeUnit): Boolean {
        var durationNano = duration
        if (unit != TimeUnit.NANOSECONDS) {
            durationNano = TimeUnit.NANOSECONDS.convert(durationNano, unit)
        }
        return elapsedNano() > durationNano
    }

    companion object {
        val ZERO = Stopwatch(0)

        /**
         * Start stopwatch.
         */
        fun start(): Stopwatch {
            return Stopwatch(System.nanoTime())
        }
    }
}
