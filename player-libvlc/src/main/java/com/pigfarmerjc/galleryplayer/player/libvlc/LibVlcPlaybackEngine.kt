package com.pigfarmerjc.galleryplayer.player.libvlc

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.pigfarmerjc.galleryplayer.core.player.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import java.io.IOException

open class LibVlcPlaybackEngine protected constructor(
    private val context: Context,
    protected val isTestNoVideoMode: Boolean
) : PlaybackEngine {

    constructor(context: Context) : this(context, isTestNoVideoMode = false)


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

    private enum class SourceStrategy {
        DIRECT_URI,
        FILE_DESCRIPTOR
    }
    private var currentSourceStrategy = SourceStrategy.DIRECT_URI
    private var hasRetriedForCurrentUri = false

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var isClosingSource = false

    init {
        initializeVlc()
    }

    private fun initializeVlc() {
        val options = VlcOptions.baseOptions
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
                Log.e("LibVlcPlaybackEngine", "Async error encountered. Current strategy: $currentSourceStrategy")
                if (currentSourceStrategy == SourceStrategy.DIRECT_URI && !hasRetriedForCurrentUri) {
                    hasRetriedForCurrentUri = true
                    val uri = currentUri
                    if (uri != null) {
                        Log.i("LibVlcPlaybackEngine", "DIRECT_URI failed asynchronously. Retrying with FILE_DESCRIPTOR.")
                        updateDiagnostics(
                            lastError = "Async DIRECT_URI failed. Retrying with FILE_DESCRIPTOR.",
                            playbackStrategy = "FILE_DESCRIPTOR"
                        )
                        closeCurrentSource()
                        currentUri = uri
                        tryToLoadMedia(uri, SourceStrategy.FILE_DESCRIPTOR)
                        return
                    }
                }
                _playbackState.value = PlaybackState.Error
                updateDiagnostics(lastError = "VLC player error encountered")
                closeCurrentSource()
                "EncounteredError"
            }
            MediaPlayer.Event.TimeChanged -> {
                val time = mediaPlayer?.time ?: 0L
                _positionMs.value = time
                if (_playbackState.value == PlaybackState.Buffering || _playbackState.value == PlaybackState.Opening) {
                    _playbackState.value = PlaybackState.Playing
                    updateMediaDetails()
                }
                "TimeChanged"
            }
            MediaPlayer.Event.PositionChanged -> {
                _isSeekable.value = mediaPlayer?.isSeekable ?: false
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

        // Do not print TimeChanged or PositionChanged in high-frequency logs to avoid spamming
        if (event.type != MediaPlayer.Event.TimeChanged && event.type != MediaPlayer.Event.PositionChanged) {
            Log.i("LibVlcPlaybackEngine", "Received VLC event: $eventName (type: ${event.type}), state: ${_playbackState.value}")
            updateDiagnostics(libvlcEvent = eventName)
        }
    }

    private fun handlePlaybackEnded() {
        when (currentRepeatMode) {
            RepeatMode.ONE -> {
                mediaPlayer?.let { player ->
                    player.time = 0
                    player.play()
                }
            }
            RepeatMode.NONE, RepeatMode.ALL -> {
                // ALL is delegated to future PlaylistController, single looping is disabled here.
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
                            0 -> 0
                            1 -> 0
                            2 -> 180
                            3 -> 180
                            4 -> 90
                            5 -> 90
                            6 -> 270
                            7 -> 270
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
        lastError: String? = null,
        playbackStrategy: String? = null
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
            lastError = lastError ?: currentDiag.lastError,
            playbackStrategy = playbackStrategy ?: currentDiag.playbackStrategy
        )
    }

    private fun closeCurrentSource() {
        if (isClosingSource) return
        isClosingSource = true
        try {
            mediaPlayer?.stop()
            mediaPlayer?.media = null
            currentMedia?.release()
            currentMedia = null
            releaseFileDescriptor()
            currentUri = null
            _playbackState.value = PlaybackState.Idle
            _videoSize.value = null
        } finally {
            isClosingSource = false
        }
    }

    override suspend fun open(uri: Uri) {
        closeCurrentSource()
        currentUri = uri
        _positionMs.value = 0L
        _durationMs.value = 0L
        _videoSize.value = null
        hasRetriedForCurrentUri = false
        tryToLoadMedia(uri, SourceStrategy.DIRECT_URI)
    }

    private fun tryToLoadMedia(uri: Uri, strategy: SourceStrategy) {
        val vlc = libVlc ?: return
        val player = mediaPlayer ?: return
        currentSourceStrategy = strategy

        try {
            val media = when (strategy) {
                SourceStrategy.DIRECT_URI -> {
                    if (uri.scheme == "file") {
                        Media(vlc, uri.path)
                    } else {
                        Media(vlc, uri)
                    }
                }
                SourceStrategy.FILE_DESCRIPTOR -> {
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                        ?: throw IOException("Failed to open file descriptor for $uri")
                    activeParcelFileDescriptor = pfd
                    Media(vlc, pfd.fileDescriptor)
                }
            }
            media.apply {
                configureDecoderMode(this)
            }
            currentMedia = media
            player.media = media
            player.play()
            updateDiagnostics(playbackStrategy = strategy.name)
        } catch (e: Exception) {
            Log.e("LibVlcPlaybackEngine", "Error loading media with strategy $strategy: ${e.message}", e)
            if (strategy == SourceStrategy.DIRECT_URI && !hasRetriedForCurrentUri) {
                hasRetriedForCurrentUri = true
                updateDiagnostics(lastError = "Direct URI failed: ${e.localizedMessage}. Retrying with FD.")
                closeCurrentSource()
                currentUri = uri
                tryToLoadMedia(uri, SourceStrategy.FILE_DESCRIPTOR)
            } else {
                _playbackState.value = PlaybackState.Error
                updateDiagnostics(lastError = "Failed to open media: ${e.localizedMessage}")
                closeCurrentSource()
            }
        }
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
            }
        }
        if (isTestNoVideoMode) {
            media.addOption(":no-video")
        }
    }

    override fun play() {
        mediaPlayer?.play()
    }

    override fun pause() {
        mediaPlayer?.pause()
    }

    override fun stop() {
        closeCurrentSource()
        _playbackState.value = PlaybackState.Stopped
    }

    override fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        if (!_isSeekable.value) return
        val duration = _durationMs.value
        val targetPos = if (duration > 0) {
            positionMs.coerceIn(0L, duration)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        player.time = targetPos
        _positionMs.value = targetPos
    }

    override fun setSpeed(speed: Float) {
        if (speed.isNaN() || speed.isInfinite() || speed < 0.10f || speed > 4.00f) {
            Log.w("LibVlcPlaybackEngine", "Invalid speed value rejected: $speed")
            return
        }
        mediaPlayer?.rate = speed
        _playbackSpeed.value = speed
    }

    override fun setRepeatMode(mode: RepeatMode) {
        currentRepeatMode = mode
    }

    override fun setDecoderMode(mode: DecoderMode) {
        if (currentDecoderMode == mode) return
        currentDecoderMode = mode
        updateDiagnostics()

        val uri = currentUri
        if (uri != null && _playbackState.value != PlaybackState.Idle && _playbackState.value != PlaybackState.Released && _playbackState.value != PlaybackState.Stopped) {
            val wasPlaying = _playbackState.value == PlaybackState.Playing
            val lastPos = mediaPlayer?.time ?: _positionMs.value
            engineScope.launch {
                open(uri)
                if (lastPos > 0) {
                    seekTo(lastPos)
                }
                if (wasPlaying) {
                    play()
                } else {
                    pause()
                }
            }
        }
    }

    override fun attachVideoOutput(output: VideoOutputHost) {
        if (activeVideoOutput != null && activeVideoOutput != output) {
            detachVideoOutput()
        }
        activeVideoOutput = output
        val vlcHost = output as? LibVlcVideoOutputHost ?: return
        val layout = vlcHost.vlcLayout ?: return
        mediaPlayer?.let { player ->
            player.attachViews(layout, null, true, false)
        }
    }

    override fun detachVideoOutput() {
        mediaPlayer?.detachViews()
        activeVideoOutput = null
    }

    override fun release() {
        engineScope.cancel()
        closeCurrentSource()
        detachVideoOutput()
        mediaPlayer?.release()
        mediaPlayer = null
        libVlc?.release()
        libVlc = null
        _playbackState.value = PlaybackState.Released
    }
}
