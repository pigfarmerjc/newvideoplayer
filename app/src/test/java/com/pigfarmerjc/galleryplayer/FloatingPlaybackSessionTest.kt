package com.pigfarmerjc.galleryplayer

import android.content.Context
import android.net.Uri
import com.pigfarmerjc.galleryplayer.core.player.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingPlaybackSessionTest {

    @Test
    fun `engine release waits until active floating session closes`() {
        val engine = RecordingPlaybackEngine()
        val configuration = FloatingPlaybackSession.Configuration(
            playbackEngine = engine,
            videoOutputFactory = object : VideoOutputHostFactory {
                override fun create(context: Context): VideoOutputHost = error("unused")
            },
            title = { "title" },
            contentUri = { "content://video" },
            playlistSize = { 1 },
            repeatMode = { PlaybackRepeatMode.NONE },
            onRepeatModeChange = {},
            onPrevious = {},
            onNext = {},
            onClosed = {}
        )

        FloatingPlaybackSession.configure(configuration)
        FloatingPlaybackSession.activate(configuration)
        FloatingPlaybackSession.releaseOrDefer(engine)
        assertFalse(engine.released)

        FloatingPlaybackSession.clear(configuration)
        assertTrue(engine.released)
    }

    @Test
    fun `configured full screen session does not defer engine release`() {
        val engine = RecordingPlaybackEngine()
        val configuration = configuration(engine)

        FloatingPlaybackSession.configure(configuration)
        FloatingPlaybackSession.releaseOrDefer(engine)

        assertTrue(engine.released)
        FloatingPlaybackSession.clear(configuration)
    }

    private fun configuration(engine: PlaybackEngine) = FloatingPlaybackSession.Configuration(
        playbackEngine = engine,
        videoOutputFactory = object : VideoOutputHostFactory {
            override fun create(context: Context): VideoOutputHost = error("unused")
        },
        title = { "title" },
        contentUri = { "content://video" },
        playlistSize = { 1 },
        repeatMode = { PlaybackRepeatMode.NONE },
        onRepeatModeChange = {},
        onPrevious = {},
        onNext = {},
        onClosed = {}
    )
}

private class RecordingPlaybackEngine : PlaybackEngine {
    var released = false
    override val playbackState = MutableStateFlow(PlaybackState.Idle)
    override val positionMs = MutableStateFlow(0L)
    override val durationMs = MutableStateFlow(0L)
    override val isSeekable = MutableStateFlow(false)
    override val playbackSpeed = MutableStateFlow(1f)
    override val videoSize = MutableStateFlow<VideoSize?>(null)
    override val videoOutputRevision = MutableStateFlow(0L)
    override val diagnostics = MutableStateFlow(PlaybackDiagnostics())
    override val audioTracks = MutableStateFlow(emptyList<PlaybackTrack>())
    override val subtitleTracks = MutableStateFlow(emptyList<PlaybackTrack>())
    override suspend fun open(uri: Uri) = Unit
    override fun play() = Unit
    override fun pause() = Unit
    override fun stop() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun setSpeed(speed: Float) = Unit
    override fun setRepeatMode(mode: RepeatMode) = Unit
    override fun setDecoderMode(mode: DecoderMode) = Unit
    override fun setVideoScaleMode(mode: VideoScaleMode) = Unit
    override fun selectAudioTrack(trackId: Int) = false
    override fun selectSubtitleTrack(trackId: Int) = false
    override fun attachVideoOutput(output: VideoOutputHost) = Unit
    override fun detachVideoOutput(output: VideoOutputHost?) = Unit
    override fun release() { released = true }
}
