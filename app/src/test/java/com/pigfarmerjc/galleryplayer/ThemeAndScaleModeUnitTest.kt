package com.pigfarmerjc.galleryplayer

import com.pigfarmerjc.galleryplayer.core.player.api.VideoScaleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeAndScaleModeUnitTest {

    @Test
    fun testAppThemeModeDefaultsAndFallback() {
        val defaultMode = AppThemeMode.SYSTEM
        assertEquals("SYSTEM", defaultMode.name)

        val invalidString = "INVALID_THEME"
        val parsedMode = AppThemeMode.values().firstOrNull { it.name == invalidString } ?: AppThemeMode.SYSTEM
        assertEquals(AppThemeMode.SYSTEM, parsedMode)

        val validString = "DARK"
        val parsedValidMode = AppThemeMode.values().firstOrNull { it.name == validString } ?: AppThemeMode.SYSTEM
        assertEquals(AppThemeMode.DARK, parsedValidMode)
    }

    @Test
    fun testVideoScaleModeDefaultsAndFallback() {
        val defaultMode = VideoScaleMode.FIT
        assertEquals("FIT", defaultMode.name)

        val invalidString = "STRETCH_TO_SCREEN"
        val parsedMode = VideoScaleMode.values().firstOrNull { it.name == invalidString } ?: VideoScaleMode.FIT
        assertEquals(VideoScaleMode.FIT, parsedMode)

        val validString = "FILL"
        val parsedValidMode = VideoScaleMode.values().firstOrNull { it.name == validString } ?: VideoScaleMode.FIT
        assertEquals(VideoScaleMode.FILL, parsedValidMode)
    }

    @Test
    fun testVideoScaleModeEnumPersistence() {
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

        val actionNext = PlayerGestureState.determineAction(
            dragOffsetX = -150f,
            dragOffsetY = 10f,
            horizontalThresholdPx = horizontalThreshold,
            verticalThresholdPx = verticalThreshold,
            currentIndex = 1,
            lastIndex = 3
        )
        assertEquals(PlayerDragAction.Next, actionNext)

        val actionPrev = PlayerGestureState.determineAction(
            dragOffsetX = 150f,
            dragOffsetY = 10f,
            horizontalThresholdPx = horizontalThreshold,
            verticalThresholdPx = verticalThreshold,
            currentIndex = 1,
            lastIndex = 3
        )
        assertEquals(PlayerDragAction.Previous, actionPrev)

        val actionFirstBoundary = PlayerGestureState.determineAction(
            dragOffsetX = 150f,
            dragOffsetY = 0f,
            horizontalThresholdPx = horizontalThreshold,
            verticalThresholdPx = verticalThreshold,
            currentIndex = 0,
            lastIndex = 3
        )
        assertEquals(PlayerDragAction.None, actionFirstBoundary)

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
        var calledNewIndex = -1
        val onChangeVideoMock: (Int) -> Unit = { newIndex ->
            calledNewIndex = newIndex
        }

        val horizontalThreshold = 100f
        val verticalThreshold = 140f
        val currentIndex = 1

        val action = PlayerGestureState.determineAction(
            dragOffsetX = -120f,
            dragOffsetY = 0f,
            horizontalThresholdPx = horizontalThreshold,
            verticalThresholdPx = verticalThreshold,
            currentIndex = currentIndex,
            lastIndex = 3
        )

        assertNotEquals(currentIndex + 1, currentIndex)

        if (action == PlayerDragAction.Next) {
            onChangeVideoMock(currentIndex + 1)
        }

        assertEquals(2, calledNewIndex)
    }

    // --- Viewport Calculator Specific Tests for Sizing Slices ---

    @Test
    fun testViewportUnknownDimensionsFallback() {
        // 1. videoWidth/videoHeight unknown (<= 0) -> fallback to full container
        val rect = VideoViewportCalculator.calculate(
            containerWidth = 1080,
            containerHeight = 1920,
            videoWidth = 0,
            videoHeight = 0,
            rotation = 0,
            mode = VideoScaleMode.FIT
        )
        assertEquals(0, rect.left)
        assertEquals(0, rect.top)
        assertEquals(1080, rect.width)
        assertEquals(1920, rect.height)
    }

    @Test
    fun testViewportRotationSwapping() {
        // 2. rotation 90/270 -> width and height should be swapped in calculations
        // Video: 1920x1080 (landscape), Rotated 90 -> Rotated size is 1080x1920 (portrait)
        // Container: 1080x1920. In FIT mode, it should fit perfectly (0, 0, 1080, 1920)
        val rectRot90 = VideoViewportCalculator.calculate(
            containerWidth = 1080,
            containerHeight = 1920,
            videoWidth = 1920,
            videoHeight = 1080,
            rotation = 90,
            mode = VideoScaleMode.FIT
        )
        assertEquals(0, rectRot90.left)
        assertEquals(0, rectRot90.top)
        assertEquals(1080, rectRot90.width)
        assertEquals(1920, rectRot90.height)

        // Rotation 270 should behave same
        val rectRot270 = VideoViewportCalculator.calculate(
            containerWidth = 1080,
            containerHeight = 1920,
            videoWidth = 1920,
            videoHeight = 1080,
            rotation = 270,
            mode = VideoScaleMode.FIT
        )
        assertEquals(1080, rectRot270.width)
        assertEquals(1920, rectRot270.height)
    }

    @Test
    fun testViewportFitPortraitVideoInLandscapeContainer() {
        // 3. FIT 竖屏视频在横屏 container 内不会返回极小尺寸
        // Video: 1080x1920 (9:16), Container: 1920x1080 (16:9)
        // Should scale height to match container height (1080), width should be (1080 * 9 / 16) = 608
        val rect = VideoViewportCalculator.calculate(
            containerWidth = 1920,
            containerHeight = 1080,
            videoWidth = 1080,
            videoHeight = 1920,
            rotation = 0,
            mode = VideoScaleMode.FIT
        )
        assertEquals(1080, rect.height)
        assertEquals(608, rect.width)
        // Check centering
        assertEquals((1920 - 608) / 2, rect.left)
    }

    @Test
    fun testViewportFitLandscapeVideoInPortraitContainer() {
        // 4. FIT 横屏视频在竖屏 container 内不会返回极小尺寸
        // Video: 1920x1080 (16:9), Container: 1080x1920 (9:16)
        // Should scale width to match container width (1080), height should be (1080 * 9 / 16) = 608
        val rect = VideoViewportCalculator.calculate(
            containerWidth = 1080,
            containerHeight = 1920,
            videoWidth = 1920,
            videoHeight = 1080,
            rotation = 0,
            mode = VideoScaleMode.FIT
        )
        assertEquals(1080, rect.width)
        assertEquals(608, rect.height)
        // Centering check
        assertEquals((1920 - 608) / 2, rect.top)
    }

    @Test
    fun testViewportCenterEqualsFit() {
        // 5. CENTER mode currently behaves exactly like FIT mode
        val rectFit = VideoViewportCalculator.calculate(
            containerWidth = 1080,
            containerHeight = 1920,
            videoWidth = 400,
            videoHeight = 300,
            rotation = 0,
            mode = VideoScaleMode.FIT
        )
        val rectCenter = VideoViewportCalculator.calculate(
            containerWidth = 1080,
            containerHeight = 1920,
            videoWidth = 400,
            videoHeight = 300,
            rotation = 0,
            mode = VideoScaleMode.CENTER
        )
        assertEquals(rectFit.width, rectCenter.width)
        assertEquals(rectFit.height, rectCenter.height)
        assertEquals(rectFit.left, rectCenter.left)
        assertEquals(rectFit.top, rectCenter.top)
    }

    @Test
    fun testViewportFillCoverContainer() {
        // 6. FILL mode returns sizes covering the entire container (no smaller than container boundaries)
        // Video: 1920x1080 (16:9), Container: 1080x1920 (9:16)
        val rect = VideoViewportCalculator.calculate(
            containerWidth = 1080,
            containerHeight = 1920,
            videoWidth = 1920,
            videoHeight = 1080,
            rotation = 0,
            mode = VideoScaleMode.FILL
        )
        assertTrue("FILL width should cover container", rect.width >= 1080)
        assertTrue("FILL height should cover container", rect.height >= 1920)
    }

    @Test
    fun testViewportTinyContainerAndInvalidInputs() {
        // 7. Container very small (e.g. 5x5) does not crash or loop
        val rectTiny = VideoViewportCalculator.calculate(
            containerWidth = 5,
            containerHeight = 5,
            videoWidth = 1080,
            videoHeight = 1920,
            rotation = 0,
            mode = VideoScaleMode.FIT
        )
        assertTrue(rectTiny.width > 0)
        assertTrue(rectTiny.height > 0)

        // 8. Invalid mode or negative dimensions do not crash
        val rectNegative = VideoViewportCalculator.calculate(
            containerWidth = -10,
            containerHeight = 1080,
            videoWidth = 1920,
            videoHeight = -1080,
            rotation = 0,
            mode = VideoScaleMode.FIT
        )
        assertEquals(0, rectNegative.width)
        assertEquals(0, rectNegative.height)
    }
}
