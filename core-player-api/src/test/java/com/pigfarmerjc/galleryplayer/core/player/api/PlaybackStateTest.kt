package com.pigfarmerjc.galleryplayer.core.player.api

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackStateTest {
    @Test
    fun testPlaybackStateValues() {
        assertEquals("Idle", PlaybackState.Idle.name)
        assertEquals("Playing", PlaybackState.Playing.name)
        assertEquals("Released", PlaybackState.Released.name)
    }

    @Test
    fun testDecoderModeValues() {
        assertEquals("AUTO", DecoderMode.AUTO.name)
        assertEquals("HARDWARE_FORCED", DecoderMode.HARDWARE_FORCED.name)
        assertEquals("SOFTWARE_ONLY", DecoderMode.SOFTWARE_ONLY.name)
    }

    @Test
    fun playbackTrackCarriesStableSelectionState() {
        val track = PlaybackTrack(id = 7, name = "PCM S24 LE · 48 kHz", selected = true)
        assertEquals(7, track.id)
        assertEquals("PCM S24 LE · 48 kHz", track.name)
        assertEquals(true, track.selected)
    }
}
