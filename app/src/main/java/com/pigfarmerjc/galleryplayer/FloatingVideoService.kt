package com.pigfarmerjc.galleryplayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pigfarmerjc.galleryplayer.core.player.api.PlaybackState
import com.pigfarmerjc.galleryplayer.core.player.api.VideoOutputHost
import com.pigfarmerjc.galleryplayer.core.player.api.VideoSize
import com.pigfarmerjc.galleryplayer.core.model.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class FloatingVideoService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private var rootView: FrameLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var videoOutputHost: VideoOutputHost? = null
    private var playbackStateJob: Job? = null
    private var videoSizeJob: Job? = null
    private var outputRevisionJob: Job? = null
    private var titleJob: Job? = null
    private var hideControlsJob: Job? = null
    private var returnToAppOnClose = false
    private var currentAspectRatio = 16f / 9f
    private var playPauseButton: ImageButton? = null
    private var repeatButton: ImageButton? = null
    private var titleView: TextView? = null
    private var posterView: ImageView? = null
    private var topControls: View? = null
    private var bottomControls: View? = null
    private var windowUpdatePosted = false
    private var requiredAfterOutputRevision = 0L
    private var displayedUri = ""

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        if (!Settings.canDrawOverlays(this) || FloatingPlaybackSession.configuration == null) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WindowManager::class.java)
        showFloatingWindow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RETURN_TO_APP -> closeFloatingWindow(returnToApp = true)
            ACTION_CLOSE -> closeFloatingWindow(returnToApp = false)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showFloatingWindow() {
        val session = FloatingPlaybackSession.configuration ?: return
        val screen = currentScreenSize()
        val videoSize = session.playbackEngine.videoSize.value
        val initialSize = initialFloatingWindowSize(
            screen.width,
            screen.height,
            videoSize?.width ?: 16,
            videoSize?.height ?: 9
        )
        currentAspectRatio = videoSize?.validAspectRatio() ?: 16f / 9f

        val params = WindowManager.LayoutParams(
            initialSize.width,
            initialSize.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val initialPosition = clampFloatingWindowPosition(
                screen.width - initialSize.width - dp(20),
                screen.height - initialSize.height - dp(48),
                initialSize,
                screen.width,
                screen.height
            )
            x = initialPosition.x
            y = initialPosition.y
        }
        layoutParams = params

        val root = FrameLayout(this).apply {
            clipToOutline = true
            outlineProvider = ViewOutlineProviderCompat.rounded(dp(18).toFloat())
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = dp(18).toFloat()
            }
            elevation = dp(12).toFloat()
        }
        rootView = root

        val videoContainer = FrameLayout(this)
        root.addView(videoContainer, FrameLayout.LayoutParams(MATCH, MATCH))

        val host = session.videoOutputFactory.create(this)
        videoOutputHost = host
        videoContainer.addView(host.view, FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER))
        session.playbackEngine.attachVideoOutput(host)

        posterView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
        }.also { poster ->
            root.addView(poster, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        val gestureLayer = View(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        root.addView(gestureLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        installWindowGestures(gestureLayer)
        requiredAfterOutputRevision = session.playbackEngine.videoOutputRevision.value
        displayedUri = session.contentUri()
        loadPoster(displayedUri)

        addTopControls(root, session)
        addBottomControls(root, session)

        windowManager.addView(root, params)
        observePlayback(session)
        showControlsTemporarily()
    }

    private fun addTopControls(root: FrameLayout, session: FloatingPlaybackSession.Configuration) {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(4), dp(4))
            setBackgroundColor(0x99000000.toInt())
        }
        titleView = TextView(this).apply {
            text = session.title()
            setTextColor(Color.WHITE)
            textSize = 12f
            maxLines = 1
        }
        bar.addView(titleView, LinearLayout.LayoutParams(0, dp(40), 1f))
        bar.addView(iconButton(android.R.drawable.ic_menu_view, "返回全屏") {
            closeFloatingWindow(returnToApp = true)
        })
        bar.addView(iconButton(android.R.drawable.ic_menu_close_clear_cancel, "关闭小窗") {
            closeFloatingWindow(returnToApp = false)
        })
        root.addView(
            bar,
            FrameLayout.LayoutParams(MATCH, dp(48), Gravity.TOP)
        )
        topControls = bar
        installDrag(bar)
    }

    private fun addBottomControls(root: FrameLayout, session: FloatingPlaybackSession.Configuration) {
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(0x99000000.toInt())
        }
        val previous = iconButton(android.R.drawable.ic_media_previous, "上一个") { session.onPrevious() }
        val playPause = iconButton(android.R.drawable.ic_media_pause, "播放或暂停") {
            val engine = session.playbackEngine
            when {
                isActivelyPlaying(engine.playbackState.value) -> engine.pause()
                engine.playbackState.value == PlaybackState.Ended ||
                    engine.playbackState.value == PlaybackState.Stopped -> scope.launch {
                    engine.open(Uri.parse(FloatingPlaybackSession.configuration?.contentUri().orEmpty()))
                }
                else -> engine.play()
            }
        }
        playPauseButton = playPause
        val next = iconButton(android.R.drawable.ic_media_next, "下一个") { session.onNext() }
        val repeat = iconButton(android.R.drawable.ic_menu_more, repeatModeLabel(session.repeatMode())) {
            FloatingPlaybackSession.configuration?.let { current ->
                val nextMode = nextRepeatMode(current.repeatMode())
                current.onRepeatModeChange(nextMode)
                repeatButton?.contentDescription = repeatModeLabel(nextMode)
                Toast.makeText(this, repeatModeLabel(nextMode), Toast.LENGTH_SHORT).show()
            }
        }
        repeatButton = repeat
        controls.addView(previous)
        controls.addView(playPause)
        controls.addView(next)
        controls.addView(repeat)
        val resize = iconButton(android.R.drawable.ic_menu_crop, "拖动缩放") { }
        controls.addView(resize)
        installResizeHandle(resize)
        root.addView(controls, FrameLayout.LayoutParams(MATCH, dp(54), Gravity.BOTTOM))
        bottomControls = controls
    }

    private fun installWindowGestures(target: View) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var gestureStartWidth = 0
        val scaleDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    gestureStartWidth = layoutParams?.width ?: return false
                    moved = true
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    resizeWindow((gestureStartWidth * detector.scaleFactor).roundToInt())
                    gestureStartWidth = layoutParams?.width ?: gestureStartWidth
                    return true
                }
            }
        )
        target.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            val params = layoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    hideControlsJob?.cancel()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                        val deltaX = (event.rawX - downRawX).roundToInt()
                        val deltaY = (event.rawY - downRawY).roundToInt()
                        if (moved || kotlin.math.abs(deltaX) > touchSlop || kotlin.math.abs(deltaY) > touchSlop) {
                            moved = true
                            val screen = currentScreenSize()
                            val position = clampFloatingWindowPosition(
                                startX + deltaX,
                                startY + deltaY,
                                FloatingWindowSize(params.width, params.height),
                                screen.width,
                                screen.height
                            )
                            params.x = position.x
                            params.y = position.y
                            updateWindowLayoutOnNextFrame()
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) toggleControls() else scheduleControlsHide()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    scheduleControlsHide()
                    true
                }

                else -> true
            }
        }
    }

    private fun installDrag(handle: View) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        handle.setOnTouchListener { _, event ->
            val params = layoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val screen = currentScreenSize()
                    val position = clampFloatingWindowPosition(
                        startX + (event.rawX - downRawX).roundToInt(),
                        startY + (event.rawY - downRawY).roundToInt(),
                        FloatingWindowSize(params.width, params.height),
                        screen.width,
                        screen.height
                    )
                    params.x = position.x
                    params.y = position.y
                    updateWindowLayoutOnNextFrame()
                    true
                }
                else -> false
            }
        }
    }

    private fun installResizeHandle(handle: View) {
        var downRawX = 0f
        var startWidth = 0
        handle.setOnTouchListener { _, event ->
            val params = layoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    startWidth = params.width
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    resizeWindow(startWidth + (event.rawX - downRawX).roundToInt())
                    true
                }
                else -> false
            }
        }
    }

    private fun resizeWindow(requestedWidth: Int) {
        val params = layoutParams ?: return
        val screen = currentScreenSize()
        val size = resizeFloatingWindow(requestedWidth, currentAspectRatio, screen.width, screen.height)
        params.width = size.width
        params.height = size.height
        val position = clampFloatingWindowPosition(params.x, params.y, size, screen.width, screen.height)
        params.x = position.x
        params.y = position.y
        updateWindowLayoutOnNextFrame()
    }

    private fun applyVideoSize(videoSize: VideoSize?) {
        val ratio = videoSize?.validAspectRatio() ?: return
        if (kotlin.math.abs(ratio - currentAspectRatio) < 0.01f) return
        currentAspectRatio = ratio
        val params = layoutParams ?: return
        resizeWindow(params.width)
    }

    private fun observePlayback(session: FloatingPlaybackSession.Configuration) {
        playbackStateJob = scope.launch {
            session.playbackEngine.playbackState.collectLatest { state ->
                playPauseButton?.setImageResource(
                    if (isActivelyPlaying(state)) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                )
                when (state) {
                    PlaybackState.Opening, PlaybackState.Buffering -> {
                        requiredAfterOutputRevision = session.playbackEngine.videoOutputRevision.value
                        posterView?.visibility = View.VISIBLE
                    }
                    PlaybackState.Ended, PlaybackState.Stopped -> posterView?.visibility = View.VISIBLE
                    PlaybackState.Playing -> {
                        if (isVideoOutputReady(
                                session.playbackEngine.videoOutputRevision.value,
                                requiredAfterOutputRevision
                            )) {
                            posterView?.visibility = View.GONE
                        }
                    }
                    else -> Unit
                }
            }
        }
        videoSizeJob = scope.launch {
            session.playbackEngine.videoSize.collectLatest(::applyVideoSize)
        }
        outputRevisionJob = scope.launch {
            session.playbackEngine.videoOutputRevision.collectLatest { revision ->
                val state = session.playbackEngine.playbackState.value
                if (isVideoOutputReady(revision, requiredAfterOutputRevision) &&
                    state != PlaybackState.Ended && state != PlaybackState.Stopped
                ) {
                    posterView?.visibility = View.GONE
                }
            }
        }
        titleJob = scope.launch {
            while (true) {
                FloatingPlaybackSession.configuration?.let { current ->
                    titleView?.text = current.title()
                    repeatButton?.contentDescription = repeatModeLabel(current.repeatMode())
                    val uri = current.contentUri()
                    if (uri.isNotBlank() && uri != displayedUri) {
                        displayedUri = uri
                        requiredAfterOutputRevision = current.playbackEngine.videoOutputRevision.value
                        posterView?.visibility = View.VISIBLE
                        loadPoster(uri)
                    }
                }
                delay(400)
            }
        }
    }

    private fun loadPoster(contentUri: String) {
        if (contentUri.isBlank()) return
        scope.launch {
            val bitmap = ThumbnailLoader.loadMediaThumbnail(
                this@FloatingVideoService,
                contentUri,
                MediaType.VIDEO,
                layoutParams?.width ?: 640,
                layoutParams?.height ?: 360,
                768
            )
            if (contentUri == displayedUri) posterView?.setImageBitmap(bitmap)
        }
    }

    private fun closeFloatingWindow(returnToApp: Boolean) {
        returnToAppOnClose = returnToApp
        if (returnToApp) {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
            )
        }
        stopSelf()
    }

    override fun onDestroy() {
        playbackStateJob?.cancel()
        videoSizeJob?.cancel()
        outputRevisionJob?.cancel()
        titleJob?.cancel()
        hideControlsJob?.cancel()
        val session = FloatingPlaybackSession.configuration
        val host = videoOutputHost
        session?.playbackEngine?.detachVideoOutput(host)
        host?.dispose()
        videoOutputHost = null
        rootView?.let { view -> runCatching { windowManager.removeView(view) } }
        rootView = null
        if (!returnToAppOnClose) session?.playbackEngine?.pause()
        session?.onClosed?.invoke(returnToAppOnClose)
        FloatingPlaybackSession.clear(session)
        scope.cancel()
        super.onDestroy()
    }

    private fun updateWindowLayout() {
        val view = rootView ?: return
        val params = layoutParams ?: return
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun updateWindowLayoutOnNextFrame() {
        if (windowUpdatePosted) return
        windowUpdatePosted = true
        rootView?.postOnAnimation {
            windowUpdatePosted = false
            updateWindowLayout()
        }
    }

    private fun toggleControls() {
        if (topControls?.visibility == View.VISIBLE) hideControls() else showControlsTemporarily()
    }

    private fun showControlsTemporarily() {
        topControls?.visibility = View.VISIBLE
        bottomControls?.visibility = View.VISIBLE
        scheduleControlsHide()
    }

    private fun scheduleControlsHide() {
        hideControlsJob?.cancel()
        hideControlsJob = scope.launch {
            delay(CONTROLS_HIDE_DELAY_MS)
            hideControls()
        }
    }

    private fun hideControls() {
        hideControlsJob?.cancel()
        topControls?.visibility = View.GONE
        bottomControls?.visibility = View.GONE
    }

    private fun iconButton(icon: Int, description: String, onClick: () -> Unit): ImageButton =
        ImageButton(this).apply {
            setImageResource(icon)
            contentDescription = description
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                onClick()
                showControlsTemporarily()
            }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }

    private fun createNotification(): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "悬浮播放", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("GalleryPlayer 悬浮播放")
            .setContentText("轻触返回播放器")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun currentScreenSize(): FloatingWindowSize {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            FloatingWindowSize(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            resources.displayMetrics.let { FloatingWindowSize(it.widthPixels, it.heightPixels) }
        }
    }

    private fun VideoSize.validAspectRatio(): Float? =
        if (width > 0 && height > 0) width.toFloat() / height else null

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
        private const val CHANNEL_ID = "floating_playback"
        private const val NOTIFICATION_ID = 4102
        private const val CONTROLS_HIDE_DELAY_MS = 2_800L
        private const val ACTION_RETURN_TO_APP = "com.pigfarmerjc.galleryplayer.action.FLOATING_RETURN"
        private const val ACTION_CLOSE = "com.pigfarmerjc.galleryplayer.action.FLOATING_CLOSE"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, FloatingVideoService::class.java))
        }

        fun returnToApp(context: Context) {
            context.startService(
                Intent(context, FloatingVideoService::class.java).setAction(ACTION_RETURN_TO_APP)
            )
        }
    }
}

private object ViewOutlineProviderCompat {
    fun rounded(radius: Float): android.view.ViewOutlineProvider = object : android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
    }
}
