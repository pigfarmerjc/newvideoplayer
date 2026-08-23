package com.pigfarmerjc.galleryplayer

import com.pigfarmerjc.galleryplayer.core.player.api.PlaybackEngine
import com.pigfarmerjc.galleryplayer.core.player.api.VideoOutputHostFactory

object FloatingPlaybackSession {
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
        this.configuration = configuration
    }

    fun clear(configuration: Configuration? = null) {
        if (configuration == null || this.configuration === configuration) {
            this.configuration = null
        }
    }
}
