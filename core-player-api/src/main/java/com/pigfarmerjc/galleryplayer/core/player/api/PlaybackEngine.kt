package com.pigfarmerjc.galleryplayer.core.player.api

import android.content.Context
import android.net.Uri
import android.view.View
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
    
    fun updateViewportDiagnostics(
        containerWidth: Int,
        containerHeight: Int,
        vlcLayoutWidth: Int,
        vlcLayoutHeight: Int,
        isVideoSizeKnown: Boolean,
        lastViewportRect: String,
        scaleMode: VideoScaleMode
    ) {}

    fun updateViewSizes(
        playerRootWidth: Int,
        playerRootHeight: Int,
        androidViewWidth: Int,
        androidViewHeight: Int
    ) {}
}

interface VideoOutputHost {
    val view: View
    fun dispose()
    fun setVideoScaleMode(mode: VideoScaleMode) {}
    fun setVideoSize(width: Int, height: Int, rotation: Int) {}
}

interface VideoOutputHostFactory {
    fun create(context: Context): VideoOutputHost
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
    HARDWARE_FORCED,
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
    val lastError: String = "",
    val playbackStrategy: String = "DIRECT_URI",
    val containerWidth: Int = 0,
    val containerHeight: Int = 0,
    val surfaceAttached: Boolean = false,
    val lastVideoOutputAttachTime: Long = 0L,
    val lastMediaOpenTime: Long = 0L,
    val scaleMode: VideoScaleMode = VideoScaleMode.FIT,
    val isVideoSizeKnown: Boolean = false,
    val lastViewportRect: String = "",
    
    // Real View Sizing dimensions for diagnostics
    val playerRootWidth: Int = 0,
    val playerRootHeight: Int = 0,
    val androidViewWidth: Int = 0,
    val androidViewHeight: Int = 0,
    val videoHostWidth: Int = 0,
    val videoHostHeight: Int = 0,
    val vlcVideoLayoutWidth: Int = 0,
    val vlcVideoLayoutHeight: Int = 0
)
