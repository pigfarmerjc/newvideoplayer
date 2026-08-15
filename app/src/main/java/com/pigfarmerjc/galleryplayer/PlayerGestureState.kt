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
        lastIndex: Int,
        velocityX: Float = 0f,
        velocityY: Float = 0f
    ): PlayerDragAction {
        val absX = abs(dragOffsetX)
        val absY = abs(dragOffsetY)
        // A flick only overrides position-based direction when velocity and displacement agree on direction.
        // This prevents a brief reverse-velocity finger lift from flipping the navigation intent.
        val horizontalFlick = abs(velocityX) >= 700f && absX >= horizontalThresholdPx * 0.15f &&
                (velocityX * dragOffsetX > 0f) // velocity and displacement must point the same way
        val downwardFlick = velocityY >= 800f && dragOffsetY >= verticalThresholdPx * 0.15f

        // Horizontal dominant
        if (absX > absY || (abs(velocityX) > abs(velocityY) && absX > 20f)) {
            if (absX > horizontalThresholdPx || horizontalFlick) {
                val movesRight = if (horizontalFlick) velocityX > 0f else dragOffsetX > 0f
                return if (movesRight) {
                    if (currentIndex > 0) PlayerDragAction.Previous else PlayerDragAction.None
                } else {
                    if (currentIndex < lastIndex) PlayerDragAction.Next else PlayerDragAction.None
                }
            }
        } else {
            // Vertical dominant and downwards swipe
            if (dragOffsetY > 0f && (absY > verticalThresholdPx || downwardFlick)) {
                return PlayerDragAction.Dismiss
            }
        }
        return PlayerDragAction.None
    }
}
