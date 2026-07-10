package com.pigfarmerjc.galleryplayer

import com.pigfarmerjc.galleryplayer.core.model.MediaType
import org.junit.Assert.*
import org.junit.Test

class GalleryTransitionStateTest {

    private fun makeVideo(uri: String) = LocalMediaItem(
        contentUri = uri,
        mediaType = MediaType.VIDEO,
        volumeName = "external",
        relativePath = "DCIM/",
        displayName = uri.substringAfterLast("/"),
        mimeType = "video/mp4",
        fileSize = 1000L,
        durationMs = 30_000L,
        width = 1920,
        height = 1080,
        dateModifiedEpochSeconds = 1_700_000_000L,
        isGif = false,
        mediaStoreId = null
    )

    // ── findVideoIndexByUri ────────────────────────────────────────────────

    @Test
    fun `findVideoIndexByUri returns correct index`() {
        val videos = listOf(
            makeVideo("content://media/a"),
            makeVideo("content://media/b"),
            makeVideo("content://media/c")
        )
        assertEquals(1, findVideoIndexByUri(videos, "content://media/b"))
    }

    @Test
    fun `findVideoIndexByUri returns -1 when not found`() {
        val videos = listOf(makeVideo("content://media/a"))
        assertEquals(-1, findVideoIndexByUri(videos, "content://media/zzz"))
    }

    @Test
    fun `findVideoIndexByUri returns -1 on empty list`() {
        assertEquals(-1, findVideoIndexByUri(emptyList(), "content://media/a"))
    }

    @Test
    fun `findVideoIndexByUri returns -1 on blank uri`() {
        val videos = listOf(makeVideo("content://media/a"))
        assertEquals(-1, findVideoIndexByUri(videos, ""))
    }

    // ── shouldFireScrubSeek ────────────────────────────────────────────────

    @Test
    fun `shouldFireScrubSeek returns true when enough time has passed`() {
        assertTrue(shouldFireScrubSeek(lastSeekMs = 0L, nowMs = 100L, throttleMs = 100L))
    }

    @Test
    fun `shouldFireScrubSeek returns false when not enough time has passed`() {
        assertFalse(shouldFireScrubSeek(lastSeekMs = 0L, nowMs = 50L, throttleMs = 100L))
    }

    @Test
    fun `shouldFireScrubSeek returns true at exact threshold`() {
        assertTrue(shouldFireScrubSeek(lastSeekMs = 1000L, nowMs = 1100L, throttleMs = 100L))
    }

    // ── dismissProgressToScale ─────────────────────────────────────────────

    @Test
    fun `dismissProgressToScale returns 1f at progress 0`() {
        assertEquals(1f, dismissProgressToScale(0f), 0.001f)
    }

    @Test
    fun `dismissProgressToScale returns targetScale at progress 1`() {
        assertEquals(0.75f, dismissProgressToScale(1f, 0.75f), 0.001f)
    }

    @Test
    fun `dismissProgressToScale clamps values above 1`() {
        assertEquals(0.75f, dismissProgressToScale(2f, 0.75f), 0.001f)
    }

    // ── dismissProgressToDimAlpha ──────────────────────────────────────────

    @Test
    fun `dismissProgressToDimAlpha returns 1f at 0 progress`() {
        assertEquals(1f, dismissProgressToDimAlpha(0f), 0.001f)
    }

    @Test
    fun `dismissProgressToDimAlpha returns 0f at 1 progress`() {
        assertEquals(0f, dismissProgressToDimAlpha(1f), 0.001f)
    }

    // ── dismissProgressToCornerDp ──────────────────────────────────────────

    @Test
    fun `dismissProgressToCornerDp returns 0 at progress 0`() {
        assertEquals(0f, dismissProgressToCornerDp(0f, 16f), 0.001f)
    }

    @Test
    fun `dismissProgressToCornerDp returns targetDp at progress 1`() {
        assertEquals(16f, dismissProgressToCornerDp(1f, 16f), 0.001f)
    }

    // ── GalleryTransitionState bounds map ─────────────────────────────────

    @Test
    fun `GalleryTransitionState stores and retrieves bounds`() {
        val state = GalleryTransitionState()
        val bounds = MediaItemBounds("content://media/a", 0f, 100f, 120f, 120f)
        state.updateBounds(bounds)
        assertEquals(bounds, state.boundsFor("content://media/a"))
    }

    @Test
    fun `GalleryTransitionState returns null for unknown uri`() {
        val state = GalleryTransitionState()
        assertNull(state.boundsFor("content://media/unknown"))
    }

    @Test
    fun `GalleryTransitionState updates existing bounds`() {
        val state = GalleryTransitionState()
        val bounds1 = MediaItemBounds("content://media/a", 0f, 0f, 100f, 100f)
        val bounds2 = MediaItemBounds("content://media/a", 50f, 50f, 120f, 120f)
        state.updateBounds(bounds1)
        state.updateBounds(bounds2)
        assertEquals(bounds2, state.boundsFor("content://media/a"))
    }
}
