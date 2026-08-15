package com.pigfarmerjc.galleryplayer.player.libvlc

import android.content.Context
import android.view.View
import com.pigfarmerjc.galleryplayer.core.player.api.VideoOutputHost
import com.pigfarmerjc.galleryplayer.core.player.api.VideoOutputHostFactory
import org.videolan.libvlc.util.VLCVideoLayout

class LibVlcVideoOutputHost(context: Context) : VideoOutputHost {
    // Keep VLCVideoLayout with proper Activity/UI context so it has window metrics for aspect ratio calculation
    private var vlcVideoLayout: VLCVideoLayout? = VLCVideoLayout(context)
    
    // Keep a reference even after dispose so Compose can safely finish unbinding the view
    private var lastKnownView: VLCVideoLayout? = null

    override val view: View
        get() {
            // After dispose, return the last known view rather than throwing.
            // Compose (or AccessibilityService) may read .view one final time during teardown;
            // throwing here causes an unhandled crash.
            return vlcVideoLayout ?: lastKnownView
                ?: throw IllegalStateException("LibVlcVideoOutputHost: view was never initialized")
        }

    val vlcLayout: VLCVideoLayout?
        get() = vlcVideoLayout

    override fun dispose() {
        if (vlcVideoLayout == null) return // already disposed

        lastKnownView = vlcVideoLayout

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
