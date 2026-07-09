package com.pigfarmerjc.galleryplayer.player.libvlc

import android.content.Context
import android.view.View
import com.pigfarmerjc.galleryplayer.core.player.api.VideoOutputHost
import com.pigfarmerjc.galleryplayer.core.player.api.VideoOutputHostFactory
import org.videolan.libvlc.util.VLCVideoLayout

class LibVlcVideoOutputHost(context: Context) : VideoOutputHost {
    private var vlcVideoLayout: VLCVideoLayout? = VLCVideoLayout(context)
    override val view: View get() = vlcVideoLayout ?: throw IllegalStateException("LibVlcVideoOutputHost is already disposed")
    val vlcLayout: VLCVideoLayout? get() = vlcVideoLayout

    override fun dispose() {
        vlcVideoLayout?.let { layout ->
            (layout.parent as? android.view.ViewGroup)?.removeView(layout)
        }
        vlcVideoLayout = null
    }
}

class LibVlcVideoOutputHostFactory : VideoOutputHostFactory {
    override fun create(context: Context): VideoOutputHost {
        return LibVlcVideoOutputHost(context)
    }
}
