package com.pigfarmerjc.galleryplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerGestureUnitTest {

    private val horizontalThreshold = 300f  // e.g. 100.dp to Px
    private val verticalThreshold = 420f    // e.g. 140.dp to Px

    @Test
    fun testLeftSwipeOverThresholdExitsNext() {
        // Horizontal left swipe: dragOffsetX is negative, absolute value > threshold
        val action = PlayerGestureState.determineAction(
            dragOffsetX = -350f,
            dragOffsetY = 10f,
            horizontalThresholdPx = horizontalThreshold,
            verticalThresholdPx = verticalThreshold,
            currentIndex = 1,
            lastIndex = 3
        )
        assertEquals(PlayerDragAction.Next, action)
    }

    @Test
    fun testRightSwipeOverThresholdExitsPrev() {
        // Horizontal right swipe: dragOffsetX is positive, absolute value > threshold
        val action = PlayerGestureState.determineAction(
            dragOffsetX = 350f,
            dragOffsetY = -10f,
            horizontalThresholdPx = horizontalThreshold,
            verticalThresholdPx = verticalThreshold,
            currentIndex = 1,
            lastIndex = 3
        )
        assertEquals(PlayerDragAction.Previous, action)
    }

    @Test
    fun testFirstItemRightSwipeExitsNone() {
        // Index is 0, right swipe should not go out of bounds
        val action = PlayerGestureState.determineAction(
            dragOffsetX = 350f,
            dragOffsetY = 0f,
            horizontalThresholdPx = horizontalThreshold,
            verticalThresholdPx = verticalThreshold,
            currentIndex = 0,
            lastIndex = 3
        )
        assertEquals(PlayerDragAction.None, action)
    }

    @Test
    fun testLastItemLeftSwipeExitsNone() {
        // Index is lastIndex, left swipe should not go out of bounds
        val action = PlayerGestureState.determineAction(
            dragOffsetX = -350f,
            dragOffsetY = 0f,
            horizontalThresholdPx = horizontalThreshold,
            verticalThresholdPx = verticalThreshold,
            currentIndex = 3,
            lastIndex = 3
        )
        assertEquals(PlayerDragAction.None, action)
    }

    @Test
    fun testSwipeDownOverThresholdExitsDismiss() {
        // Vertical swipe down: dragOffsetY is positive, absolute value > threshold
        val action = PlayerGestureState.determineAction(
            dragOffsetX = 10f,
            dragOffsetY = 450f,
            horizontalThresholdPx = horizontalThreshold,
            verticalThresholdPx = verticalThreshold,
            currentIndex = 1,
            lastIndex = 3
        )
        assertEquals(PlayerDragAction.Dismiss, action)
    }

    @Test
    fun testSwipeDownUnderThresholdExitsNone() {
        // Vertical swipe down below threshold
        val action = PlayerGestureState.determineAction(
            dragOffsetX = 10f,
            dragOffsetY = 300f,
            horizontalThresholdPx = horizontalThreshold,
            verticalThresholdPx = verticalThreshold,
            currentIndex = 1,
            lastIndex = 3
        )
        assertEquals(PlayerDragAction.None, action)
    }

    @Test
    fun testHorizontalDominantCheck() {
        // Horizontal offset is larger than vertical offset, but not exceeding horizontal threshold
        val action = PlayerGestureState.determineAction(
            dragOffsetX = 200f,
            dragOffsetY = 150f,
            horizontalThresholdPx = horizontalThreshold,
            verticalThresholdPx = verticalThreshold,
            currentIndex = 1,
            lastIndex = 3
        )
        assertEquals(PlayerDragAction.None, action)
    }

    @Test
    fun testVerticalDominantCheck() {
        // Vertical offset is larger than horizontal offset, but not exceeding vertical threshold
        val action = PlayerGestureState.determineAction(
            dragOffsetX = 150f,
            dragOffsetY = 300f,
            horizontalThresholdPx = horizontalThreshold,
            verticalThresholdPx = verticalThreshold,
            currentIndex = 1,
            lastIndex = 3
        )
        assertEquals(PlayerDragAction.None, action)
    }
}
