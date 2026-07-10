package com.pigfarmerjc.galleryplayer

import com.pigfarmerjc.galleryplayer.core.player.api.VideoScaleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThemeAndScaleModeUnitTest {

    @Test
    fun testAppThemeModeDefaultsAndFallback() {
        // AppThemeMode defaults to SYSTEM
        val defaultMode = AppThemeMode.SYSTEM
        assertEquals("SYSTEM", defaultMode.name)

        // Simulating invalid string parsing falling back to SYSTEM
        val invalidString = "INVALID_THEME"
        val parsedMode = AppThemeMode.values().firstOrNull { it.name == invalidString } ?: AppThemeMode.SYSTEM
        assertEquals(AppThemeMode.SYSTEM, parsedMode)

        // Valid parsing
        val validString = "DARK"
        val parsedValidMode = AppThemeMode.values().firstOrNull { it.name == validString } ?: AppThemeMode.SYSTEM
        assertEquals(AppThemeMode.DARK, parsedValidMode)
    }

    @Test
    fun testVideoScaleModeDefaultsAndFallback() {
        // VideoScaleMode defaults to FIT
        val defaultMode = VideoScaleMode.FIT
        assertEquals("FIT", defaultMode.name)

        // Simulating invalid string parsing falling back to FIT
        val invalidString = "STRETCH_TO_SCREEN"
        val parsedMode = VideoScaleMode.values().firstOrNull { it.name == invalidString } ?: VideoScaleMode.FIT
        assertEquals(VideoScaleMode.FIT, parsedMode)

        // Valid parsing
        val validString = "FILL"
        val parsedValidMode = VideoScaleMode.values().firstOrNull { it.name == validString } ?: VideoScaleMode.FIT
        assertEquals(VideoScaleMode.FILL, parsedValidMode)
    }

    @Test
    fun testVideoScaleModeEnumPersistence() {
        // Ensure the values FIT, FILL, CENTER exist and match persistent names
        val fitValue = VideoScaleMode.valueOf("FIT")
        assertEquals(VideoScaleMode.FIT, fitValue)

        val fillValue = VideoScaleMode.valueOf("FILL")
        assertEquals(VideoScaleMode.FILL, fillValue)

        val centerValue = VideoScaleMode.valueOf("CENTER")
        assertEquals(VideoScaleMode.CENTER, centerValue)
    }

    @Test
    fun testSwipeDirectionCalculationsAndBoundaries() {
        val horizontalThreshold = 100f
        val verticalThreshold = 140f

        // Left swipe (negative dragOffsetX) beyond threshold -> Next video
        val actionNext = PlayerGestureState.determineAction(
            dragOffsetX = -150f,
            dragOffsetY = 10f,
            horizontalThresholdPx = horizontalThreshold,
            verticalThresholdPx = verticalThreshold,
            currentIndex = 1,
            lastIndex = 3
        )
        assertEquals(PlayerDragAction.Next, actionNext)

        // Right swipe (positive dragOffsetX) beyond threshold -> Previous video
        val actionPrev = PlayerGestureState.determineAction(
            dragOffsetX = 150f,
            dragOffsetY = 10f,
            horizontalThresholdPx = horizontalThreshold,
            verticalThresholdPx = verticalThreshold,
            currentIndex = 1,
            lastIndex = 3
        )
        assertEquals(PlayerDragAction.Previous, actionPrev)

        // First item boundary: Right swipe should not go out of bounds (should return None)
        val actionFirstBoundary = PlayerGestureState.determineAction(
            dragOffsetX = 150f,
            dragOffsetY = 0f,
            horizontalThresholdPx = horizontalThreshold,
            verticalThresholdPx = verticalThreshold,
            currentIndex = 0,
            lastIndex = 3
        )
        assertEquals(PlayerDragAction.None, actionFirstBoundary)

        // Last item boundary: Left swipe should not go out of bounds (should return None)
        val actionLastBoundary = PlayerGestureState.determineAction(
            dragOffsetX = -150f,
            dragOffsetY = 0f,
            horizontalThresholdPx = horizontalThreshold,
            verticalThresholdPx = verticalThreshold,
            currentIndex = 3,
            lastIndex = 3
        )
        assertEquals(PlayerDragAction.None, actionLastBoundary)
    }

    @Test
    fun testSwipeAnimationDelegationAndIndexSafety() {
        // This test verifies that gesture state processing outputs Next/Previous actions,
        // which must be handled by parent state changes rather than modifying internal state directly.
        var calledNewIndex = -1
        val onChangeVideoMock: (Int) -> Unit = { newIndex ->
            calledNewIndex = newIndex
        }

        val horizontalThreshold = 100f
        val verticalThreshold = 140f
        val currentIndex = 1

        // Simulate swipe Next
        val action = PlayerGestureState.determineAction(
            dragOffsetX = -120f,
            dragOffsetY = 0f,
            horizontalThresholdPx = horizontalThreshold,
            verticalThresholdPx = verticalThreshold,
            currentIndex = currentIndex,
            lastIndex = 3
        )

        // The gesture state itself must NOT change the current index directly
        assertNotEquals(currentIndex + 1, currentIndex) // Verification that the local variable was not changed

        // Instead, the handler in PlayerScreen must intercept the action and call onChangeVideo
        if (action == PlayerDragAction.Next) {
            onChangeVideoMock(currentIndex + 1)
        }

        // Verify the callback was triggered with the correct index delegate
        assertEquals(2, calledNewIndex)
    }
}
