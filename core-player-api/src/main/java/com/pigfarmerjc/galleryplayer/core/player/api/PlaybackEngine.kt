package com.pigfarmerjc.galleryplayer.core.player.api

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

interface PlaybackEngine {
    val playbackState: StateFlow<PlaybackState>
    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val isSeekable: StateFlow<Boolean>
    val playbackSpeed: StateFlow<Float>
    val videoSize: StateFlow<VideoSize?>
    val diagnostics: StateFlow<PlaybackDiagnostics>

    suspend fun open(uri: Uri)
    fun play()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun setRepeatMode(mode: RepeatMode)
    fun setDecoderMode(mode: DecoderMode)
    fun attachVideoOutput(output: VideoOutputHost)
    fun detachVideoOutput()
    fun release()
}

interface VideoOutputHost {
    val containerView: Any
}

enum class PlaybackState {
    Idle,
    Opening,
    Buffering,
    Playing,
    Paused,
    Stopped,
    Ended,
    Error,
    Released
}

enum class RepeatMode {
    NONE,
    ONE,
    ALL
}

enum class DecoderMode {
    AUTO,
    HARDWARE_PREFERRED,
    SOFTWARE_ONLY
}

data class VideoSize(
    val width: Int,
    val height: Int
)

data class PlaybackDiagnostics(
    val uri: String = "",
    val mimeType: String = "",
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val rotation: Int = 0,
    val videoTracksCount: Int = 0,
    val audioTracksCount: Int = 0,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val decoderMode: DecoderMode = DecoderMode.AUTO,
    val libvlcEvent: String = "",
    val lastError: String = ""
)
