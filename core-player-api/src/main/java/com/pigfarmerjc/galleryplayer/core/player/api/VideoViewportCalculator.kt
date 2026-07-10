package com.pigfarmerjc.galleryplayer.core.player.api

import kotlin.math.roundToInt

data class ViewportRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

object VideoViewportCalculator {
    fun calculate(
        containerWidth: Int,
        containerHeight: Int,
        videoWidth: Int,
        videoHeight: Int,
        rotation: Int,
        mode: VideoScaleMode
    ): ViewportRect {
        // 1. If container dimensions are invalid, return an empty rect.
        if (containerWidth <= 0 || containerHeight <= 0) {
            return ViewportRect(0, 0, 0, 0)
        }

        // 2. If video size is unknown or invalid, fallback to full container.
        if (videoWidth <= 0 || videoHeight <= 0) {
            return ViewportRect(0, 0, containerWidth, containerHeight)
        }

        // 3. Swap dimensions if rotation is 90 or 270 degrees.
        val rotatedWidth = if (rotation == 90 || rotation == 270) videoHeight else videoWidth
        val rotatedHeight = if (rotation == 90 || rotation == 270) videoWidth else videoHeight

        val videoAspectRatio = rotatedWidth.toFloat() / rotatedHeight.toFloat()
        val containerAspectRatio = containerWidth.toFloat() / containerHeight.toFloat()

        var targetWidth = containerWidth
        var targetHeight = containerHeight

        // 4. Calculate dimensions based on the requested VideoScaleMode.
        // Note: CENTER defaults to FIT for safety to prevent tiny viewport shrinking (TODO).
        when (mode) {
            VideoScaleMode.FIT, VideoScaleMode.CENTER -> {
                if (containerAspectRatio > videoAspectRatio) {
                    targetWidth = (containerHeight * videoAspectRatio).roundToInt()
                    targetHeight = containerHeight
                } else {
                    targetWidth = containerWidth
                    targetHeight = (containerWidth / videoAspectRatio).roundToInt()
                }
            }
            VideoScaleMode.FILL -> {
                if (containerAspectRatio > videoAspectRatio) {
                    targetWidth = containerWidth
                    targetHeight = (containerWidth / videoAspectRatio).roundToInt()
                } else {
                    targetWidth = (containerHeight * videoAspectRatio).roundToInt()
                    targetHeight = containerHeight
                }
            }
        }

        // 5. Ensure target size never falls below 1px.
        if (targetWidth <= 0) targetWidth = 1
        if (targetHeight <= 0) targetHeight = 1

        // 6. Impose a safety lower limit so small videos do not shrink to tiny blocks (min 100px or full container size).
        val minSafetyLimit = minOf(containerWidth, containerHeight, 100)
        if (targetWidth < minSafetyLimit || targetHeight < minSafetyLimit) {
            if (containerAspectRatio > videoAspectRatio) {
                targetHeight = maxOf(minSafetyLimit, targetHeight)
                targetWidth = (targetHeight * videoAspectRatio).roundToInt()
            } else {
                targetWidth = maxOf(minSafetyLimit, targetWidth)
                targetHeight = (targetWidth / videoAspectRatio).roundToInt()
            }
        }

        // 7. Center within the parent container.
        val left = (containerWidth - targetWidth) / 2
        val top = (containerHeight - targetHeight) / 2
        val right = left + targetWidth
        val bottom = top + targetHeight

        return ViewportRect(left, top, right, bottom)
    }
}
