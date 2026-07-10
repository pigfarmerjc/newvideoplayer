package com.pigfarmerjc.galleryplayer.player.libvlc

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.pigfarmerjc.galleryplayer.core.player.api.VideoOutputHost
import com.pigfarmerjc.galleryplayer.core.player.api.VideoOutputHostFactory
import com.pigfarmerjc.galleryplayer.core.player.api.VideoScaleMode
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.roundToInt

class VideoScaleFrameLayout(context: Context) : FrameLayout(context) {
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

        if (videoWidth <= 0 || videoHeight <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val rotatedWidth = if (videoRotation == 90 || videoRotation == 270) videoHeight else videoWidth
        val rotatedHeight = if (videoRotation == 90 || videoRotation == 270) videoWidth else videoHeight

        val videoAspectRatio = rotatedWidth.toFloat() / rotatedHeight.toFloat()
        val containerAspectRatio = parentWidth.toFloat() / parentHeight.toFloat()

        var childWidth = parentWidth
        var childHeight = parentHeight

        when (scaleMode) {
            VideoScaleMode.FIT -> {
                if (containerAspectRatio > videoAspectRatio) {
                    childWidth = (parentHeight * videoAspectRatio).roundToInt()
                    childHeight = parentHeight
                } else {
                    childWidth = parentWidth
                    childHeight = (parentWidth / videoAspectRatio).roundToInt()
                }
            }
            VideoScaleMode.FILL -> {
                if (containerAspectRatio > videoAspectRatio) {
                    childWidth = parentWidth
                    childHeight = (parentWidth / videoAspectRatio).roundToInt()
                } else {
                    childWidth = (parentHeight * videoAspectRatio).roundToInt()
                    childHeight = parentHeight
                }
            }
            VideoScaleMode.CENTER -> {
                childWidth = rotatedWidth
                childHeight = rotatedHeight
            }
        }

        val childWidthSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY)
        val childHeightSpec = MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
        vlcVideoLayout.measure(childWidthSpec, childHeightSpec)

        setMeasuredDimension(parentWidth, parentHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val parentWidth = right - left
        val parentHeight = bottom - top

        val childWidth = vlcVideoLayout.measuredWidth
        val childHeight = vlcVideoLayout.measuredHeight

        val childLeft = (parentWidth - childWidth) / 2
        val childTop = (parentHeight - childHeight) / 2

        vlcVideoLayout.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)
    }
}

class LibVlcVideoOutputHost(context: Context) : VideoOutputHost {
    private var containerFrame: VideoScaleFrameLayout? = VideoScaleFrameLayout(context.applicationContext)
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
