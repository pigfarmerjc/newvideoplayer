package com.pigfarmerjc.galleryplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSeekThrottleUnitTest {

    @Test
    fun testClampPositionWithinValidBounds() {
        val duration = 60_000L
        assertEquals(0L, PlayerSeekThrottle.clampPosition(-100L, duration))
        assertEquals(0L, PlayerSeekThrottle.clampPosition(0L, duration))
        assertEquals(15_000L, PlayerSeekThrottle.clampPosition(15_000L, duration))
        assertEquals(60_000L, PlayerSeekThrottle.clampPosition(60_000L, duration))
        assertEquals(60_000L, PlayerSeekThrottle.clampPosition(90_000L, duration))
    }

    @Test
    fun testClampPositionWithZeroOrNegativeDuration() {
        assertEquals(0L, PlayerSeekThrottle.clampPosition(-500L, 0L))
        assertEquals(1_000L, PlayerSeekThrottle.clampPosition(1_000L, 0L))
        assertEquals(0L, PlayerSeekThrottle.clampPosition(-10L, -100L))
    }

    @Test
    fun testShouldDispatchIgnoresDuplicateAndAcceptsChangedPositions() {
        val duration = 100_000L

        // Initial target
        assertTrue(PlayerSeekThrottle.shouldDispatch(10_000L, -1L, duration))

        // Same position should not dispatch
        assertFalse(PlayerSeekThrottle.shouldDispatch(10_000L, 10_000L, duration))

        // Different position should dispatch
        assertTrue(PlayerSeekThrottle.shouldDispatch(12_000L, 10_000L, duration))

        // Over-boundary position that clamps to same value should not dispatch duplicate
        assertFalse(PlayerSeekThrottle.shouldDispatch(120_000L, 100_000L, duration))
    }

    @Test
    fun testContinuousDraggingThrottlesLatestValuesDuringMovement() {
        val seeks = mutableListOf<Long>()
        val controller = PlayerSeekThrottleController { pos ->
            seeks.add(pos)
        }

        val duration = 100_000L

        // Simulate 60fps drag input updates (every ~16ms for 160ms)
        // User moves thumb from 0ms to 50000ms:
        var currentFingerPosition = 0L

        // Simulation clock:
        var timeMs = 0L
        var lastThrottleSampleTime = 0L
        val throttleInterval = PlayerSeekThrottle.THROTTLE_INTERVAL_MS // 45ms

        for (frame in 1..10) {
            timeMs += 16L
            currentFingerPosition += 5_000L // Finger is continuously moving

            // Throttle sampling loop checks every 45ms
            if (timeMs - lastThrottleSampleTime >= throttleInterval) {
                controller.onSample(currentFingerPosition, duration)
                lastThrottleSampleTime = timeMs
            }
        }

        // Throttle should have dispatched intermediate seeks during the drag
        assertTrue("Throttle must dispatch multiple seeks while finger is moving", seeks.size >= 3)
        assertEquals(listOf(15_000L, 30_000L, 45_000L), seeks)

        // On drag finished, exact final seek is performed
        controller.onDragFinished(currentFingerPosition, duration)
        assertEquals(listOf(15_000L, 30_000L, 45_000L, 50_000L), seeks)
    }

    @Test
    fun testDebounceFailureSimulationDuringContinuousDragging() {
        // This test simulates the OLD debounce approach where each touch event resets a 55ms timer.
        // During 160ms of continuous finger movement with 16ms touch intervals, 0 seeks occur until after drag stops!
        val debounceSeeks = mutableListOf<Long>()
        var pendingDebounceTarget: Long? = null
        var debounceScheduledAt = 0L
        val debounceDelay = 55L

        var currentFingerPosition = 0L
        var timeMs = 0L

        for (frame in 1..10) {
            timeMs += 16L
            currentFingerPosition += 5_000L

            // Old debounce: cancels previous timer and restarts a 55ms delay
            pendingDebounceTarget = currentFingerPosition
            debounceScheduledAt = timeMs

            val target = pendingDebounceTarget
            if (timeMs - debounceScheduledAt >= debounceDelay && target != null) {
                debounceSeeks.add(target)
                pendingDebounceTarget = null
            }
        }

        // Under old debounce logic, zero seeks were dispatched during the entire drag gesture
        assertEquals("Old debounce produced 0 seeks during continuous drag", 0, debounceSeeks.size)

        // Only after finger stopped for > 55ms would old debounce finally fire
        timeMs += 60L
        if (timeMs - debounceScheduledAt >= debounceDelay && pendingDebounceTarget != null) {
            debounceSeeks.add(pendingDebounceTarget)
        }
        assertEquals(listOf(50_000L), debounceSeeks)
    }
}
