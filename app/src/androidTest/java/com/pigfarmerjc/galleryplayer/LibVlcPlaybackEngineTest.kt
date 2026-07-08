package com.pigfarmerjc.galleryplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pigfarmerjc.galleryplayer.core.player.api.DecoderMode
import com.pigfarmerjc.galleryplayer.core.player.api.PlaybackState
import com.pigfarmerjc.galleryplayer.player.libvlc.LibVlcPlaybackEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibVlcPlaybackEngineTest {

    @Test
    fun testEngineInitializationAndRelease() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = LibVlcPlaybackEngine(appContext)

        // Verify initial state is Idle
        assertEquals(PlaybackState.Idle, engine.playbackState.value)

        // Set decoder mode and verify diagnostics
        engine.setDecoderMode(DecoderMode.HARDWARE_FORCED)
        assertEquals(DecoderMode.HARDWARE_FORCED, engine.diagnostics.value.decoderMode)

        // Release engine
        engine.release()
        assertEquals(PlaybackState.Released, engine.playbackState.value)
    }
}
