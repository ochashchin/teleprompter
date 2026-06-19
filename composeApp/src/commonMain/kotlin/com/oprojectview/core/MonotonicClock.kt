package com.oprojectview.core

import kotlin.time.TimeSource

/**
 * A shared monotonic clock using a single epoch baseline.
 * Ensures that foreground Compose UI and background PiP frame producer
 * measure elapsed time with microsecond precision against the exact same origin.
 */
object MonotonicClock {
    private val epoch = TimeSource.Monotonic.markNow()

    fun currentTimeUs(): Long {
        return epoch.elapsedNow().inWholeMicroseconds
    }
}
