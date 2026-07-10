package com.pigfarmerjc.galleryplayer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Shared state for gallery ↔ player transitions.
 * Held in MainViewModel so it survives screen changes.
 */
class GalleryTransitionState {

    /** Maps contentUri → screen-space bounds of the grid cell. Updated whenever a cell is laid out. */
    val itemBoundsMap = mutableStateMapOf<String, MediaItemBounds>()

    /** URI of the last video the user opened, used to scroll back to its position on return. */
    var lastOpenedVideoUri by mutableStateOf("")

    /** Lookup bounds for a given URI; returns null if not yet measured. */
    fun boundsFor(contentUri: String): MediaItemBounds? = itemBoundsMap[contentUri]

    /** Record or update the screen bounds for a single grid cell. */
    fun updateBounds(bounds: MediaItemBounds) {
        itemBoundsMap[bounds.contentUri] = bounds
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Utility: find the index of a video by its content URI in a list.
// Pure Kotlin, fully testable.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns the index of [contentUri] in [videos], or -1 if not found.
 * Used to restore scroll position after exiting the player.
 */
fun findVideoIndexByUri(videos: List<LocalMediaItem>, contentUri: String): Int {
    if (contentUri.isEmpty()) return -1
    return videos.indexOfFirst { it.contentUri == contentUri }
}

// ─────────────────────────────────────────────────────────────────────────────
// Scrub seek state: throttle logic for live preview during seekbar drag.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Pure Kotlin scrub seek throttle: decides whether a seek call should fire
 * given the time of the last seek and the required minimum interval.
 *
 * @param lastSeekMs Wall-clock time of the last seek (System.currentTimeMillis())
 * @param nowMs      Current wall-clock time
 * @param throttleMs Minimum interval between seeks in milliseconds (default 100ms)
 */
fun shouldFireScrubSeek(lastSeekMs: Long, nowMs: Long, throttleMs: Long = 100L): Boolean {
    return nowMs - lastSeekMs >= throttleMs
}

/**
 * Maps a dismiss drag progress [0..1] to a visual scale factor [1f..targetScale].
 *
 * @param progress    Drag progress (0 = fully expanded, 1 = at dismiss threshold)
 * @param targetScale Minimum scale at full progress (default 0.75f)
 */
fun dismissProgressToScale(progress: Float, targetScale: Float = 0.75f): Float {
    val clamped = progress.coerceIn(0f, 1f)
    return 1f - clamped * (1f - targetScale)
}

/**
 * Maps a dismiss drag progress [0..1] to a background dim alpha [1f..0f].
 */
fun dismissProgressToDimAlpha(progress: Float): Float {
    return (1f - progress.coerceIn(0f, 1f)).coerceIn(0f, 1f)
}

/**
 * Maps a dismiss drag progress [0..1] to a corner radius in dp [0..targetDp].
 */
fun dismissProgressToCornerDp(progress: Float, targetDp: Float = 16f): Float {
    return progress.coerceIn(0f, 1f) * targetDp
}
