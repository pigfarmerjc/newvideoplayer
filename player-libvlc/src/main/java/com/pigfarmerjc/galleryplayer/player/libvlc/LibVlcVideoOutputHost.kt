package com.pigfarmerjc.galleryplayer.player.libvlc

import android.content.Context
import android.view.View
import com.pigfarmerjc.galleryplayer.core.player.api.VideoOutputHost
import com.pigfarmerjc.galleryplayer.core.player.api.VideoOutputHostFactory
import org.videolan.libvlc.util.VLCVideoLayout

class LibVlcVideoOutputHost(context: Context) : VideoOutputHost {
    private val vlcVideoLayout = VLCVideoLayout(context)
    override val view: View get() = vlcVideoLayout
    val vlcLayout: VLCVideoLayout get() = vlcVideoLayout

    override fun dispose() {
        // Clean up or release resources if necessary
    }
}

class LibVlcVideoOutputHostFactory : VideoOutputHostFactory {
    override fun create(context: Context): VideoOutputHost {
        return LibVlcVideoOutputHost(context)
    }
}
