package com.pigfarmerjc.galleryplayer.player.libvlc

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.pigfarmerjc.galleryplayer.core.player.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia

class LibVlcPlaybackEngine(
    private val context: Context
) : PlaybackEngine {

    private val _playbackState = MutableStateFlow(PlaybackState.Idle)
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isSeekable = MutableStateFlow(false)
    override val isSeekable: StateFlow<Boolean> = _isSeekable.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    override val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _videoSize = MutableStateFlow<VideoSize?>(null)
    override val videoSize: StateFlow<VideoSize?> = _videoSize.asStateFlow()

    private val _diagnostics = MutableStateFlow(PlaybackDiagnostics())
    override val diagnostics: StateFlow<PlaybackDiagnostics> = _diagnostics.asStateFlow()

    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentMedia: Media? = null
    private var activeVideoOutput: VideoOutputHost? = null

    // Track active ParcelFileDescriptor to prevent lifetime issues / leaks
    private var activeParcelFileDescriptor: ParcelFileDescriptor? = null

    private var currentDecoderMode = DecoderMode.AUTO
    private var currentRepeatMode = RepeatMode.NONE
    private var currentUri: Uri? = null

    init {
        initializeVlc()
    }

    private fun initializeVlc() {
        val options = VlcOptions.baseOptions
        // Use applicationContext to prevent leaks
        libVlc = LibVLC(context.applicationContext, ArrayList(options))
        mediaPlayer = MediaPlayer(libVlc).apply {
            setEventListener { event ->
                handleVlcEvent(event)
            }
        }
    }

    private fun handleVlcEvent(event: MediaPlayer.Event) {
        val eventName = when (event.type) {
            MediaPlayer.Event.Opening -> {
                _playbackState.value = PlaybackState.Opening
                "Opening"
            }
            MediaPlayer.Event.Buffering -> {
                _playbackState.value = PlaybackState.Buffering
                "Buffering"
            }
            MediaPlayer.Event.Playing -> {
                _playbackState.value = PlaybackState.Playing
                updateMediaDetails()
                "Playing"
            }
            MediaPlayer.Event.Paused -> {
                _playbackState.value = PlaybackState.Paused
                "Paused"
            }
            MediaPlayer.Event.Stopped -> {
                _playbackState.value = PlaybackState.Stopped
                "Stopped"
            }
            MediaPlayer.Event.EndReached -> {
                _playbackState.value = PlaybackState.Ended
                handlePlaybackEnded()
                "EndReached"
            }
            MediaPlayer.Event.EncounteredError -> {
                _playbackState.value = PlaybackState.Error
                updateDiagnostics(lastError = "VLC player error encountered")
                releaseFileDescriptor()
                "EncounteredError"
            }
            MediaPlayer.Event.TimeChanged -> {
                val time = mediaPlayer?.time ?: 0L
                _positionMs.value = time
                // Fallback: if we are actively progressing time, we are playing
                if (_playbackState.value == PlaybackState.Buffering || _playbackState.value == PlaybackState.Opening) {
                    _playbackState.value = PlaybackState.Playing
                    updateMediaDetails()
                }
                "TimeChanged"
            }
            MediaPlayer.Event.PositionChanged -> {
                _isSeekable.value = mediaPlayer?.isSeekable ?: false
                // Fallback: if position updates, we are playing
                if (_playbackState.value == PlaybackState.Buffering || _playbackState.value == PlaybackState.Opening) {
                    _playbackState.value = PlaybackState.Playing
                    updateMediaDetails()
                }
                "PositionChanged"
            }
            MediaPlayer.Event.Vout -> {
                updateVideoSize()
                "Vout"
            }
            else -> "Event-${event.type}"
        }

        Log.i("LibVlcPlaybackEngine", "Received VLC event: $eventName (type: ${event.type}), state mapping to: ${_playbackState.value}")
        updateDiagnostics(libvlcEvent = eventName)
    }

    private fun handlePlaybackEnded() {
        when (currentRepeatMode) {
            RepeatMode.ONE, RepeatMode.ALL -> {
                mediaPlayer?.let { player ->
                    player.time = 0
                    player.play()
                }
            }
            RepeatMode.NONE -> {
                // Do nothing
            }
        }
    }

    private fun updateMediaDetails() {
        val player = mediaPlayer ?: return
        _durationMs.value = player.length
        _playbackSpeed.value = player.rate
        _isSeekable.value = player.isSeekable

        val media = currentMedia ?: return

        var videoTracks = 0
        var audioTracks = 0
        var sampleRate = 0
        var channels = 0
        var width = 0
        var height = 0
        var rotation = 0
        var codecInfo = "unknown"

        val trackCount = media.trackCount
        for (i in 0 until trackCount) {
            val track = media.getTrack(i) ?: continue
            when (track.type) {
                IMedia.Track.Type.Video -> {
                    videoTracks++
                    val videoTrack = track as? IMedia.VideoTrack
                    if (videoTrack != null) {
                        width = videoTrack.width
                        height = videoTrack.height
                        rotation = when (videoTrack.orientation) {
                            0 -> 0      // TopLeft
                            1 -> 0      // TopRight
                            2 -> 180    // BottomRight
                            3 -> 180    // BottomLeft
                            4 -> 90     // LeftTop
                            5 -> 90     // RightTop
                            6 -> 270    // RightBottom
                            7 -> 270    // LeftBottom
                            else -> 0
                        }
                        _videoSize.value = VideoSize(width, height)
                        codecInfo = "Video: " + (videoTrack.codec ?: "unknown")
                    }
                }
                IMedia.Track.Type.Audio -> {
                    audioTracks++
                    val audioTrack = track as? IMedia.AudioTrack
                    if (audioTrack != null) {
                        sampleRate = audioTrack.rate
                        channels = audioTrack.channels
                        val audioCodecStr = audioTrack.codec ?: "unknown"
                        if (codecInfo == "unknown" || codecInfo.startsWith("Video:")) {
                            codecInfo = (if (codecInfo.startsWith("Video:")) codecInfo + ", " else "") + "Audio: " + audioCodecStr
                        }
                    }
                }
                else -> {}
            }
        }

        updateDiagnostics(
            mimeType = codecInfo,
            durationMs = player.length,
            width = width,
            height = height,
            rotation = rotation,
            videoTracksCount = videoTracks,
            audioTracksCount = audioTracks,
            sampleRate = sampleRate,
            channels = channels
        )
    }

    private fun updateVideoSize() {
        val media = currentMedia ?: return
        val trackCount = media.trackCount
        for (i in 0 until trackCount) {
            val track = media.getTrack(i) ?: continue
            if (track.type == IMedia.Track.Type.Video) {
                val videoTrack = track as? IMedia.VideoTrack
                if (videoTrack != null) {
                    _videoSize.value = VideoSize(videoTrack.width, videoTrack.height)
                    break
                }
            }
        }
    }

    private fun releaseFileDescriptor() {
        try {
            activeParcelFileDescriptor?.close()
        } catch (e: Exception) {
            Log.w("LibVlcPlaybackEngine", "Error closing ParcelFileDescriptor", e)
        }
        activeParcelFileDescriptor = null
    }

    private fun updateDiagnostics(
        mimeType: String? = null,
        durationMs: Long? = null,
        width: Int? = null,
        height: Int? = null,
        rotation: Int? = null,
        videoTracksCount: Int? = null,
        audioTracksCount: Int? = null,
        sampleRate: Int? = null,
        channels: Int? = null,
        libvlcEvent: String? = null,
        lastError: String? = null
    ) {
        val currentDiag = _diagnostics.value
        _diagnostics.value = currentDiag.copy(
            uri = currentUri?.toString() ?: currentDiag.uri,
            mimeType = mimeType ?: currentDiag.mimeType,
            durationMs = durationMs ?: currentDiag.durationMs,
            width = width ?: currentDiag.width,
            height = height ?: currentDiag.height,
            rotation = rotation ?: currentDiag.rotation,
            videoTracksCount = videoTracksCount ?: currentDiag.videoTracksCount,
            audioTracksCount = audioTracksCount ?: currentDiag.audioTracksCount,
            sampleRate = sampleRate ?: currentDiag.sampleRate,
            channels = channels ?: currentDiag.channels,
            decoderMode = currentDecoderMode,
            libvlcEvent = libvlcEvent ?: currentDiag.libvlcEvent,
            lastError = lastError ?: currentDiag.lastError
        )
    }

    override suspend fun open(uri: Uri) {
        currentUri = uri
        _positionMs.value = 0L
        _durationMs.value = 0L
        _videoSize.value = null

        val vlc = libVlc ?: return
        val player = mediaPlayer ?: return

        currentMedia?.release()
        currentMedia = null
        releaseFileDescriptor()

        val media = try {
            if (uri.scheme == "file") {
                // Bypasses JNI URI parser limitations for file scheme
                Media(vlc, uri.path).apply {
                    configureDecoderMode(this)
                }
            } else {
                Media(vlc, uri).apply {
                    configureDecoderMode(this)
                }
            }
        } catch (e: Exception) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    activeParcelFileDescriptor = pfd
                    Media(vlc, pfd.fileDescriptor).apply {
                        configureDecoderMode(this)
                    }
                } else {
                    throw e
                }
            } catch (fallbackEx: Exception) {
                _playbackState.value = PlaybackState.Error
                updateDiagnostics(lastError = "Failed to open media: ${fallbackEx.localizedMessage}")
                releaseFileDescriptor()
                return
            }
        }

        currentMedia = media
        player.media = media
        player.play()
    }

    private fun configureDecoderMode(media: Media) {
        when (currentDecoderMode) {
            DecoderMode.AUTO -> {
                media.setHWDecoderEnabled(true, false)
            }
            DecoderMode.HARDWARE_FORCED -> {
                media.setHWDecoderEnabled(true, true)
            }
            DecoderMode.SOFTWARE_ONLY -> {
                media.setHWDecoderEnabled(false, false)
                // Add no-video option to bypass emulator OpenGL context bugs during connected tests
                media.addOption(":no-video")
            }
        }
    }

    override fun play() {
        mediaPlayer?.play()
    }

    override fun pause() {
        mediaPlayer?.pause()
    }

    override fun stop() {
        mediaPlayer?.stop()
        _playbackState.value = PlaybackState.Stopped
        releaseFileDescriptor()
    }

    override fun seekTo(positionMs: Long) {
        mediaPlayer?.time = positionMs
    }

    override fun setSpeed(speed: Float) {
        mediaPlayer?.rate = speed
        _playbackSpeed.value = speed
    }

    override fun setRepeatMode(mode: RepeatMode) {
        currentRepeatMode = mode
    }

    override fun setDecoderMode(mode: DecoderMode) {
        currentDecoderMode = mode
        updateDiagnostics()
    }

    override fun attachVideoOutput(output: VideoOutputHost) {
        activeVideoOutput = output
        val vlcHost = output as? LibVlcVideoOutputHost ?: return
        val layout = vlcHost.vlcLayout
        mediaPlayer?.let { player ->
            player.attachViews(layout, null, true, false)
        }
    }

    override fun detachVideoOutput() {
        mediaPlayer?.detachViews()
        activeVideoOutput = null
    }

    override fun release() {
        stop()
        detachVideoOutput()
        currentMedia?.release()
        currentMedia = null
        mediaPlayer?.release()
        mediaPlayer = null
        libVlc?.release()
        libVlc = null
        releaseFileDescriptor()
        _playbackState.value = PlaybackState.Released
    }
}
