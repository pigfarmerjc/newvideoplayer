package com.pigfarmerjc.galleryplayer

import kotlin.math.abs

enum class DragDirection {
    Undecided,
    Horizontal,
    Vertical
}

enum class PlayerDragAction {
    None,
    Previous,
    Next,
    Dismiss
}

object PlayerGestureState {
    fun determineAction(
        dragOffsetX: Float,
        dragOffsetY: Float,
        horizontalThresholdPx: Float,
        verticalThresholdPx: Float,
        currentIndex: Int,
        lastIndex: Int
    ): PlayerDragAction {
        val absX = abs(dragOffsetX)
        val absY = abs(dragOffsetY)

        // Horizontal dominant
        if (absX > absY) {
            if (absX > horizontalThresholdPx) {
                return if (dragOffsetX > 0f) {
                    if (currentIndex > 0) PlayerDragAction.Previous else PlayerDragAction.None
                } else {
                    if (currentIndex < lastIndex) PlayerDragAction.Next else PlayerDragAction.None
                }
            }
        } else {
            // Vertical dominant and downwards swipe
            if (dragOffsetY > 0f && absY > verticalThresholdPx) {
                return PlayerDragAction.Dismiss
            }
        }
        return PlayerDragAction.None
    }
}
