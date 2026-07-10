package com.pigfarmerjc.galleryplayer.player.libvlc

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.pigfarmerjc.galleryplayer.core.player.api.VideoOutputHost
import com.pigfarmerjc.galleryplayer.core.player.api.VideoOutputHostFactory
import com.pigfarmerjc.galleryplayer.core.player.api.VideoScaleMode
import com.pigfarmerjc.galleryplayer.core.player.api.VideoViewportCalculator
import org.videolan.libvlc.util.VLCVideoLayout

class VideoScaleFrameLayout(
    context: Context,
    private val onViewportChanged: (width: Int, height: Int, isSizeKnown: Boolean, rectStr: String, mode: VideoScaleMode) -> Unit
) : FrameLayout(context) {
    private var videoWidth = 0
    private var videoHeight = 0
    private var videoRotation = 0
    private var scaleMode = VideoScaleMode.FIT
    private val vlcVideoLayout = VLCVideoLayout(context)

    init {
        addView(vlcVideoLayout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
    }

    val vlcLayout: VLCVideoLayout
        get() = vlcVideoLayout

    fun setVideoSize(width: Int, height: Int, rotation: Int) {
        if (videoWidth != width || videoHeight != height || videoRotation != rotation) {
            videoWidth = width
            videoHeight = height
            videoRotation = rotation
            requestLayout()
        }
    }

    fun setVideoScaleMode(mode: VideoScaleMode) {
        if (scaleMode != mode) {
            scaleMode = mode
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)

        val rect = VideoViewportCalculator.calculate(
            containerWidth = parentWidth,
            containerHeight = parentHeight,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            rotation = videoRotation,
            mode = scaleMode
        )

        val childWidthSpec = MeasureSpec.makeMeasureSpec(rect.width, MeasureSpec.EXACTLY)
        val childHeightSpec = MeasureSpec.makeMeasureSpec(rect.height, MeasureSpec.EXACTLY)
        vlcVideoLayout.measure(childWidthSpec, childHeightSpec)

        setMeasuredDimension(parentWidth, parentHeight)
        
        onViewportChanged(parentWidth, parentHeight, videoWidth > 0 && videoHeight > 0, rect.toString(), scaleMode)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val parentWidth = right - left
        val parentHeight = bottom - top

        val rect = VideoViewportCalculator.calculate(
            containerWidth = parentWidth,
            containerHeight = parentHeight,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            rotation = videoRotation,
            mode = scaleMode
        )

        vlcVideoLayout.layout(rect.left, rect.top, rect.right, rect.bottom)
        
        onViewportChanged(parentWidth, parentHeight, videoWidth > 0 && videoHeight > 0, rect.toString(), scaleMode)
    }
}

class LibVlcVideoOutputHost(context: Context) : VideoOutputHost {
    var onViewportChanged: ((containerWidth: Int, containerHeight: Int, isVideoSizeKnown: Boolean, lastViewportRect: String, scaleMode: VideoScaleMode) -> Unit)? = null

    private var containerFrame: VideoScaleFrameLayout? = VideoScaleFrameLayout(context.applicationContext) { w, h, isKnown, rect, mode ->
        onViewportChanged?.invoke(w, h, isKnown, rect, mode)
    }
    private var isDisposed = false

    override val view: View
        get() {
            if (isDisposed) {
                throw IllegalStateException("LibVlcVideoOutputHost is already disposed")
            }
            return containerFrame ?: throw IllegalStateException("LibVlcVideoOutputHost is null")
        }

    val vlcLayout: VLCVideoLayout?
        get() = containerFrame?.vlcLayout

    override fun setVideoScaleMode(mode: VideoScaleMode) {
        containerFrame?.setVideoScaleMode(mode)
    }

    override fun setVideoSize(width: Int, height: Int, rotation: Int) {
        containerFrame?.setVideoSize(width, height, rotation)
    }

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true

        containerFrame?.let { frame ->
            (frame.parent as? android.view.ViewGroup)?.removeView(frame)
            frame.vlcLayout.let { layout ->
                (layout.parent as? android.view.ViewGroup)?.removeView(layout)
            }
        }
        containerFrame = null
    }
}

class LibVlcVideoOutputHostFactory : VideoOutputHostFactory {
    override fun create(context: Context): VideoOutputHost {
        return LibVlcVideoOutputHost(context.applicationContext)
    }
}
