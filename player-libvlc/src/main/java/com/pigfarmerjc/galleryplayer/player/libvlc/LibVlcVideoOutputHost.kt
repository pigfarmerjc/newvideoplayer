package com.pigfarmerjc.galleryplayer.player.libvlc

import android.content.Context
import android.view.View
import com.pigfarmerjc.galleryplayer.core.player.api.VideoOutputHost
import com.pigfarmerjc.galleryplayer.core.player.api.VideoOutputHostFactory
import org.videolan.libvlc.util.VLCVideoLayout

class LibVlcVideoOutputHost(context: Context) : VideoOutputHost {
    // Keep VLCVideoLayout with proper Activity/UI context so it has window metrics for aspect ratio calculation
    private var vlcVideoLayout: VLCVideoLayout? = VLCVideoLayout(context)
    
    // Explicit disposed state tracking
    private var isDisposed = false

    override val view: View
        get() {
            if (isDisposed) {
                throw IllegalStateException("LibVlcVideoOutputHost is already disposed")
            }
            return vlcVideoLayout ?: throw IllegalStateException("LibVlcVideoOutputHost is null")
        }

    val vlcLayout: VLCVideoLayout?
        get() = vlcVideoLayout

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true

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
