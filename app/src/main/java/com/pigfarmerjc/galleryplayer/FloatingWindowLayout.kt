package com.pigfarmerjc.galleryplayer

import kotlin.math.roundToInt

data class FloatingWindowSize(val width: Int, val height: Int)
data class FloatingWindowPosition(val x: Int, val y: Int)
data class FloatingWindowPlacement(val size: FloatingWindowSize, val position: FloatingWindowPosition)

fun initialFloatingWindowSize(
    screenWidth: Int,
    screenHeight: Int,
    videoWidth: Int,
    videoHeight: Int
): FloatingWindowSize {
    val aspectRatio = if (videoWidth > 0 && videoHeight > 0) {
        videoWidth.toFloat() / videoHeight
    } else {
        16f / 9f
    }
    val preferredWidth = if (aspectRatio >= 1f) {
        (screenWidth * 0.48f).roundToInt()
    } else {
        (screenHeight * 0.38f * aspectRatio).roundToInt()
    }
    return resizeFloatingWindow(preferredWidth, aspectRatio, screenWidth, screenHeight)
}

fun resizeFloatingWindow(
    requestedWidth: Int,
    aspectRatio: Float,
    screenWidth: Int,
    screenHeight: Int
): FloatingWindowSize {
    val safeAspect = aspectRatio.takeIf { it.isFinite() && it > 0f } ?: 16f / 9f
    val minWidth = (screenWidth * 0.22f).roundToInt().coerceAtLeast(240)
    val maxWidthByScreen = (screenWidth * 0.90f).roundToInt()
    val maxHeight = (screenHeight * 0.80f).roundToInt()
    val maxWidthByHeight = (maxHeight * safeAspect).roundToInt()
    val maxWidth = minOf(maxWidthByScreen, maxWidthByHeight).coerceAtLeast(minWidth)
    val width = requestedWidth.coerceIn(minWidth, maxWidth)
    return FloatingWindowSize(width, (width / safeAspect).roundToInt().coerceAtLeast(1))
}

fun clampFloatingWindowPosition(
    requestedX: Int,
    requestedY: Int,
    size: FloatingWindowSize,
    screenWidth: Int,
    screenHeight: Int
): FloatingWindowPosition = FloatingWindowPosition(
    x = requestedX.coerceIn(0, (screenWidth - size.width).coerceAtLeast(0)),
    y = requestedY.coerceIn(0, (screenHeight - size.height).coerceAtLeast(0))
)

fun reflowFloatingWindow(
    currentSize: FloatingWindowSize,
    currentPosition: FloatingWindowPosition,
    aspectRatio: Float,
    screenWidth: Int,
    screenHeight: Int
): FloatingWindowPlacement {
    val size = resizeFloatingWindow(currentSize.width, aspectRatio, screenWidth, screenHeight)
    return FloatingWindowPlacement(
        size = size,
        position = clampFloatingWindowPosition(
            currentPosition.x,
            currentPosition.y,
            size,
            screenWidth,
            screenHeight
        )
    )
}
