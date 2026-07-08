package com.pigfarmerjc.galleryplayer.core.player.api

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackStateTest {
    @Test
    fun testPlaybackStateValues() {
        assertEquals("Idle", PlaybackState.Idle.name)
        assertEquals("Playing", PlaybackState.Playing.name)
    }
}
