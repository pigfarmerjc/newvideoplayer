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
}
