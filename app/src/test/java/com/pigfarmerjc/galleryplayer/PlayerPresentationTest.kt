package com.pigfarmerjc.galleryplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerPresentationTest {

    @Test
    fun `time labels support minutes and hours`() {
        assertEquals("00:00", formatPlayerTime(0L))
        assertEquals("01:05", formatPlayerTime(65_000L))
        assertEquals("01:01:01", formatPlayerTime(3_661_000L))
    }

    @Test
    fun `repeat decisions preserve current playback behavior`() {
        assertEquals(PlaybackEndAction.Stop, playbackEndAction(PlaybackRepeatMode.NONE, 1, 3))
        assertEquals(PlaybackEndAction.Replay, playbackEndAction(PlaybackRepeatMode.ONE, 1, 3))
        assertEquals(PlaybackEndAction.Advance(2), playbackEndAction(PlaybackRepeatMode.ALL, 1, 3))
        assertEquals(PlaybackEndAction.Advance(0), playbackEndAction(PlaybackRepeatMode.ALL, 2, 3))
        assertEquals(PlaybackEndAction.Advance(null), playbackEndAction(PlaybackRepeatMode.ALL, 0, 0))
    }

    @Test
    fun `picture in picture aspect ratio follows landscape and portrait video`() {
        assertEquals(PipAspectRatio(16, 9), pipAspectRatio(1920, 1080))
        assertEquals(PipAspectRatio(9, 16), pipAspectRatio(1080, 1920))
        assertEquals(PipAspectRatio(1, 1), pipAspectRatio(1080, 1080))
        assertNull(pipAspectRatio(0, 1080))
    }

    @Test
    fun `picture in picture navigation wraps across the playlist`() {
        assertEquals(2, adjacentVideoIndex(currentIndex = 0, itemCount = 3, direction = -1))
        assertEquals(0, adjacentVideoIndex(currentIndex = 2, itemCount = 3, direction = 1))
        assertNull(adjacentVideoIndex(currentIndex = 0, itemCount = 0, direction = 1))
    }
}
