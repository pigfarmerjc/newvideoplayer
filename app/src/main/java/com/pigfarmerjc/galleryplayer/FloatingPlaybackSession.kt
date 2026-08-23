package com.pigfarmerjc.galleryplayer

import com.pigfarmerjc.galleryplayer.core.player.api.PlaybackEngine
import com.pigfarmerjc.galleryplayer.core.player.api.VideoOutputHostFactory

object FloatingPlaybackSession {
    private val lock = Any()
    private var activeConfiguration: Configuration? = null
    private var deferredReleaseEngine: PlaybackEngine? = null

    data class Configuration(
        val playbackEngine: PlaybackEngine,
        val videoOutputFactory: VideoOutputHostFactory,
        val title: () -> String,
        val contentUri: () -> String,
        val playlistSize: () -> Int,
        val repeatMode: () -> PlaybackRepeatMode,
        val onRepeatModeChange: (PlaybackRepeatMode) -> Unit,
        val onPrevious: () -> Unit,
        val onNext: () -> Unit,
        val onClosed: (returningToApp: Boolean) -> Unit
    )

    @Volatile
    var configuration: Configuration? = null
        private set

    fun configure(configuration: Configuration) {
        synchronized(lock) {
            this.configuration = configuration
        }
    }

    fun activate(configuration: Configuration) {
        synchronized(lock) {
            if (this.configuration === configuration) activeConfiguration = configuration
        }
    }

    fun releaseOrDefer(engine: PlaybackEngine) {
        val releaseNow = synchronized(lock) {
            if (activeConfiguration?.playbackEngine === engine) {
                deferredReleaseEngine = engine
                false
            } else {
                true
            }
        }
        if (releaseNow) engine.release()
    }

    fun clear(configuration: Configuration? = null) {
        val releaseAfterClear = synchronized(lock) {
            val active = this.configuration
            if (configuration != null && active !== configuration) return@synchronized null
            this.configuration = null
            activeConfiguration = null
            deferredReleaseEngine?.takeIf { deferred ->
                configuration == null || active?.playbackEngine === deferred
            }.also { deferred ->
                if (deferred != null) deferredReleaseEngine = null
            }
        }
        releaseAfterClear?.release()
    }
}
