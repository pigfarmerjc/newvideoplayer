package com.pigfarmerjc.galleryplayer

import kotlin.math.abs

sealed interface PlaybackEndAction {
    data object Stop : PlaybackEndAction
    data object Replay : PlaybackEndAction
    data class Advance(val nextIndex: Int?) : PlaybackEndAction
}

data class PipAspectRatio(val width: Int, val height: Int)

fun playbackEndAction(
    repeatMode: PlaybackRepeatMode,
    currentIndex: Int,
    itemCount: Int
): PlaybackEndAction = when (repeatMode) {
    PlaybackRepeatMode.NONE -> PlaybackEndAction.Stop
    PlaybackRepeatMode.ONE -> PlaybackEndAction.Replay
    PlaybackRepeatMode.ALL -> PlaybackEndAction.Advance(
        if (itemCount > 0) (currentIndex + 1).mod(itemCount) else null
    )
}

fun adjacentVideoIndex(currentIndex: Int, itemCount: Int, direction: Int): Int? {
    if (itemCount <= 0 || direction == 0) return null
    return (currentIndex + direction).mod(itemCount)
}

fun pipAspectRatio(width: Int, height: Int): PipAspectRatio? {
    if (width <= 0 || height <= 0) return null
    val divisor = greatestCommonDivisor(width, height)
    return PipAspectRatio(width / divisor, height / divisor)
}

fun formatPlayerTime(ms: Long): String {
    val totalSecs = ms.coerceAtLeast(0L) / 1000
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private tailrec fun greatestCommonDivisor(a: Int, b: Int): Int {
    val positiveA = abs(a)
    val positiveB = abs(b)
    return if (positiveB == 0) positiveA.coerceAtLeast(1)
    else greatestCommonDivisor(positiveB, positiveA % positiveB)
}
