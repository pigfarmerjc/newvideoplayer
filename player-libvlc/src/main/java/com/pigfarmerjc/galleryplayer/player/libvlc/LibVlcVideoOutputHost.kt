package com.pigfarmerjc.galleryplayer.player.libvlc

import android.content.Context
import android.view.View
import com.pigfarmerjc.galleryplayer.core.player.api.VideoOutputHost
import com.pigfarmerjc.galleryplayer.core.player.api.VideoOutputHostFactory
import org.videolan.libvlc.util.VLCVideoLayout

class LibVlcVideoOutputHost(context: Context) : VideoOutputHost {
    // 1. We keep VLCVideoLayout reference as private
    private var vlcVideoLayout: VLCVideoLayout? = VLCVideoLayout(context.applicationContext)
    
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
        // 3. Prevent duplicate dispose calls
        if (isDisposed) return
        isDisposed = true

        // 2. Remove layout view from its parent to prevent leaks if still attached
        vlcVideoLayout?.let { layout ->
            (layout.parent as? android.view.ViewGroup)?.removeView(layout)
        }
        
        // Clear layout reference to release memory
        vlcVideoLayout = null
    }
}

class LibVlcVideoOutputHostFactory : VideoOutputHostFactory {
    override fun create(context: Context): VideoOutputHost {
        // 4. Do not capture or retain context in factory itself, pass it directly to host
        // Using applicationContext inside the host constructor prevents holding long-lived Activity references.
        return LibVlcVideoOutputHost(context.applicationContext)
    }
}
