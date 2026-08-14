package com.pigfarmerjc.galleryplayer

/**
 * Controller and utilities for throttled preview seeking during video scrubber drag gestures.
 * Ensures continuous seek dispatch (every ~45ms) without waiting for finger release,
 * and executes a final exact seek when dragging completes.
 */
object PlayerSeekThrottle {
    const val THROTTLE_INTERVAL_MS = 45L

    fun clampPosition(positionMs: Long, durationMs: Long): Long {
        return if (durationMs > 0L) {
            positionMs.coerceIn(0L, durationMs)
        } else {
            positionMs.coerceAtLeast(0L)
        }
    }

    fun shouldDispatch(
        targetMs: Long,
        lastDispatchedMs: Long,
        durationMs: Long
    ): Boolean {
        val clamped = clampPosition(targetMs, durationMs)
        return clamped != lastDispatchedMs
    }
}

class PlayerSeekThrottleController(
    private val onSeek: (Long) -> Unit
) {
    var lastDispatchedPositionMs: Long = -1L
        private set

    fun onSample(currentPositionMs: Long, durationMs: Long, force: Boolean = false): Boolean {
        val clamped = PlayerSeekThrottle.clampPosition(currentPositionMs, durationMs)
        if (force || clamped != lastDispatchedPositionMs) {
            lastDispatchedPositionMs = clamped
            onSeek(clamped)
            return true
        }
        return false
    }

    fun onDragFinished(finalPositionMs: Long, durationMs: Long) {
        onSample(finalPositionMs, durationMs, force = true)
    }

    fun reset() {
        lastDispatchedPositionMs = -1L
    }
}
