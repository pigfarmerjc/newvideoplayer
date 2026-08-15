package com.pigfarmerjc.galleryplayer

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pigfarmerjc.galleryplayer.core.model.MediaType
import com.pigfarmerjc.galleryplayer.core.player.api.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private fun Context.findActivity(): ComponentActivity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is ComponentActivity) return context
        context = context.baseContext
    }
    return null
}

enum class DoubleTapSide { LEFT, RIGHT }

@Composable
fun PlayerScreen(
    videoUri: String,
    videoTitle: String,
    videoList: List<LocalMediaItem>,
    currentIndex: Int,
    playbackEngine: PlaybackEngine,
    videoOutputFactory: VideoOutputHostFactory,
    onChangeVideo: (Int) -> Unit,
    onBack: () -> Unit,
    initialPositionMs: Long,
    onPlaybackProgress: (positionMs: Long, durationMs: Long, completed: Boolean) -> Unit,
    onPlaybackSessionStart: () -> Unit,
    defaultSpeed: Float,
    skipSeconds: Int,
    repeatMode: PlaybackRepeatMode,
    onRepeatModeChange: (PlaybackRepeatMode) -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Collect playback states
    val state by playbackEngine.playbackState.collectAsState()
    val position by playbackEngine.positionMs.collectAsState()
    val duration by playbackEngine.durationMs.collectAsState()
    val isSeekable by playbackEngine.isSeekable.collectAsState()
    val speed by playbackEngine.playbackSpeed.collectAsState()
    val diagnostics by playbackEngine.diagnostics.collectAsState()
    val audioTracks by playbackEngine.audioTracks.collectAsState()
    val subtitleTracks by playbackEngine.subtitleTracks.collectAsState()
    val videoSize by playbackEngine.videoSize.collectAsState()

    var controlsVisible by remember { mutableStateOf(true) }
    var speedExpanded by remember { mutableStateOf(false) }
    var tracksExpanded by remember { mutableStateOf(false) }
    var scaleExpanded by remember { mutableStateOf(false) }
    var videoScaleMode by remember { mutableStateOf(VideoScaleMode.FIT) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf(0f) }

    // Multi-speed level separation
    var currentSpeed by remember(videoUri) { mutableStateOf(defaultSpeed) }
    var hasAppliedDefaultSpeed by remember(videoUri) { mutableStateOf(false) }
    var isHoldingSpeed by remember { mutableStateOf(false) }

    // Double-tap visual feedback state (LEFT / RIGHT)
    var activeDoubleTapSide by remember { mutableStateOf<DoubleTapSide?>(null) }
    var feedbackJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Keep track of the active video output host
    var videoHost by remember { mutableStateOf<VideoOutputHost?>(null) }

    // Prevent multiple initial seeks
    var hasAppliedInitialSeek by remember(videoUri) { mutableStateOf(false) }

    // Protect playCount increment from pausing/resuming repeatedly
    var hasStartedSession by remember(videoUri) { mutableStateOf(false) }

    // Drag gesture tracking states
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var dragDirection by remember { mutableStateOf(DragDirection.Undecided) }
    var gestureSettling by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val density = LocalDensity.current
    val horizontalThresholdPx = remember { with(density) { 100.dp.toPx() } }
    val verticalThresholdPx = remember { with(density) { 140.dp.toPx() } }
    val lockThresholdPx = remember { with(density) { 10.dp.toPx() } }

    // Safe save helpers
    val saveProgress = {
        val currentPos = playbackEngine.positionMs.value
        val dur = playbackEngine.durationMs.value
        if (dur > 0) {
            val isFinished = (currentPos.toDouble() / dur.toDouble()) >= 0.90
            onPlaybackProgress(currentPos, dur, isFinished)
        }
    }

    val saveProgressAndStop = {
        saveProgress()
        playbackEngine.stop()
    }

    // Full-screen immersive window setup (keyed on Unit to avoid insets reset when switching videos)
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    // Intercept hardware back button
    BackHandler {
        saveProgressAndStop()
        onBack()
    }

    // Auto-hide controls after 3 seconds of inactivity (paused when dragging or speed dialog open)
    LaunchedEffect(controlsVisible, isDragging, state) {
        if (controlsVisible && !isDragging && state == PlaybackState.Playing) {
            delay(3000)
            controlsVisible = false
        }
    }

    // Open video only AFTER videoHost is attached
    LaunchedEffect(videoUri, videoHost) {
        val host = videoHost
        if (host != null) {
            playbackEngine.open(android.net.Uri.parse(videoUri))
        }
    }

    // Apply speed once playing starts
    LaunchedEffect(state) {
        if (state == PlaybackState.Playing && !hasAppliedDefaultSpeed) {
            playbackEngine.setSpeed(currentSpeed)
            hasAppliedDefaultSpeed = true
        }
    }

    // Apply initial position seek restoration
    LaunchedEffect(state, duration, isSeekable) {
        if (initialPositionMs > 0 && !hasAppliedInitialSeek && duration > 0 && isSeekable) {
            playbackEngine.seekTo(initialPositionMs)
            hasAppliedInitialSeek = true
        }
    }

    // Throttled preview seeking during continuous drag:
    // Periodically reads latest dragPosition every ~45ms without cancelling on each touch event,
    // so video frames follow the finger continuously during dragging.
    LaunchedEffect(isDragging, isSeekable, duration) {
        if (isDragging && isSeekable && duration > 0L) {
            var lastDispatchedSeek = -1L
            while (isDragging) {
                val targetMs = PlayerSeekThrottle.clampPosition(dragPosition.toLong(), duration)
                if (targetMs != lastDispatchedSeek) {
                    playbackEngine.seekTo(targetMs)
                    lastDispatchedSeek = targetMs
                }
                delay(PlayerSeekThrottle.THROTTLE_INTERVAL_MS)
            }
        }
    }

    // Handle playback ended state based on PlaybackRepeatMode
    LaunchedEffect(state) {
        if (state == PlaybackState.Ended) {
            when (repeatMode) {
                PlaybackRepeatMode.NONE -> {
                    saveProgressAndStop()
                }
                PlaybackRepeatMode.ONE -> {
                    // After Ended, LibVLC marks the media as non-seekable.
                    // Re-open the same URI to restart from the beginning instead of seeking.
                    playbackEngine.open(android.net.Uri.parse(videoUri))
                }
                PlaybackRepeatMode.ALL -> {
                    saveProgressAndStop()
                    if (videoList.isNotEmpty()) {
                        val nextIndex = (currentIndex + 1) % videoList.size
                        onChangeVideo(nextIndex)
                    }
                }
            }
        }
    }

    // Periodically save progress every 5 seconds, and notify on play session start
    LaunchedEffect(videoUri, state) {
        if (state == PlaybackState.Playing) {
            if (!hasStartedSession) {
                onPlaybackSessionStart()
                hasStartedSession = true
            }
            
            while (true) {
                delay(5000)
                val currentPos = playbackEngine.positionMs.value
                val dur = playbackEngine.durationMs.value
                if (dur > 0) {
                    val isFinished = (currentPos.toDouble() / dur.toDouble()) >= 0.90
                    onPlaybackProgress(currentPos, dur, isFinished)
                }
            }
        } else if (state == PlaybackState.Paused) {
            val currentPos = playbackEngine.positionMs.value
            val dur = playbackEngine.durationMs.value
            if (dur > 0) {
                val isFinished = (currentPos.toDouble() / dur.toDouble()) >= 0.90
                onPlaybackProgress(currentPos, dur, isFinished)
            }
        }
    }

    // Save final progress when exit/dispose
    DisposableEffect(videoUri) {
        onDispose {
            val currentPos = playbackEngine.positionMs.value
            val dur = playbackEngine.durationMs.value
            if (dur > 0) {
                val isFinished = (currentPos.toDouble() / dur.toDouble()) >= 0.90
                onPlaybackProgress(currentPos, dur, isFinished)
            }
        }
    }

    // Track when the active video has decoded its first frame and started playing
    var isFirstFrameReady by remember(videoUri) { mutableStateOf(false) }

    LaunchedEffect(videoUri) {
        isFirstFrameReady = false
        dragOffsetX = 0f
        dragOffsetY = 0f
        dragDirection = DragDirection.Undecided
        gestureSettling = false
        settleJob?.cancel()
        settleJob = null
    }

    LaunchedEffect(state, position, diagnostics.uri, videoUri) {
        // Primary trigger: playing with a known position (works for video and audio)
        if (diagnostics.uri == videoUri && state == PlaybackState.Playing && position >= 60L) {
            isFirstFrameReady = true
        }
    }

    // Fallback: if the video never reaches Playing (e.g. starts paused, or pure audio),
    // reveal the surface after a short timeout to avoid permanently hiding the player view.
    LaunchedEffect(videoUri) {
        kotlinx.coroutines.delay(2500L)
        isFirstFrameReady = true
    }

    // Preload next and previous video thumbnails in background for instantaneous zero-latency swipe previews
    LaunchedEffect(currentIndex, videoList) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (currentIndex + 1 < videoList.size) {
                ThumbnailLoader.loadMediaThumbnail(
                    context,
                    videoList[currentIndex + 1].contentUri,
                    MediaType.VIDEO,
                    1920,
                    1080,
                    1024
                )
            }
            if (currentIndex - 1 >= 0) {
                ThumbnailLoader.loadMediaThumbnail(
                    context,
                    videoList[currentIndex - 1].contentUri,
                    MediaType.VIDEO,
                    1920,
                    1080,
                    1024
                )
            }
        }
    }

    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    // Backdrop opacity: when dragging down, alpha scales down smoothly so underlying gallery is visible
    val bgAlpha = remember(dragOffsetY, dragDirection) {
        if (dragDirection == DragDirection.Vertical && dragOffsetY > 0f) {
            (1f - (dragOffsetY / (screenHeightPx * 0.55f))).coerceIn(0f, 1f)
        } else {
            1f
        }
    }

    val isDraggingGesture = dragDirection != DragDirection.Undecided || abs(dragOffsetX) > 0f || abs(dragOffsetY) > 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = bgAlpha))
    ) {
        // 1. Active Video Layer (transforms on drag)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    when (dragDirection) {
                        DragDirection.Horizontal -> {
                            translationX = dragOffsetX
                        }
                        DragDirection.Vertical -> {
                            val progress = if (size.height > 0f) {
                                (dragOffsetY.coerceAtLeast(0f) / size.height).coerceIn(0f, 1f)
                            } else 0f
                            translationY = dragOffsetY.coerceAtLeast(0f)
                            val scale = (1f - progress * 0.22f).coerceIn(0.75f, 1f)
                            scaleX = scale
                            scaleY = scale
                            clip = progress > 0.005f
                            shape = RoundedCornerShape((28f * (progress * 2.5f).coerceIn(0f, 1f)).dp)
                        }
                        DragDirection.Undecided -> {
                            if (dragOffsetX != 0f) translationX = dragOffsetX
                            if (dragOffsetY != 0f) translationY = dragOffsetY.coerceAtLeast(0f)
                        }
                    }
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    val host = videoOutputFactory.create(ctx)
                    playbackEngine.attachVideoOutput(host)
                    host.view.apply {
                        post {
                            videoHost = host
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (isFirstFrameReady) 1f else 0f
                    },
                onRelease = {
                    playbackEngine.detachVideoOutput()
                    videoHost?.dispose()
                    videoHost = null
                }
            )

            // Seamless poster overlay: covers until the first frame is playing to eliminate black screen flicker
            AnimatedVisibility(
                visible = !isFirstFrameReady,
                enter = fadeIn(tween(0)),
                exit = fadeOut(tween(140)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    MediaThumbnail(
                        contentUri = videoUri,
                        mediaType = MediaType.VIDEO,
                        modifier = Modifier.fillMaxSize(),
                        width = 1920,
                        height = 1080,
                        maxDecodeDimension = 1024,
                        contentScale = ContentScale.Fit,
                        placeholderColor = Color.Black,
                        showPlaceholderIcon = false
                    )
                }
            }
        }

        // 2. Next Video Side Preview (attached to the right when swiping left)
        if (dragOffsetX < 0f && currentIndex < videoList.size - 1) {
            val nextVideo = videoList[currentIndex + 1]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = size.width + dragOffsetX + 16.dp.toPx()
                    }
                    .background(Color.Black)
            ) {
                MediaThumbnail(
                    contentUri = nextVideo.contentUri,
                    mediaType = MediaType.VIDEO,
                    modifier = Modifier.fillMaxSize(),
                    width = 1920,
                    height = 1080,
                    maxDecodeDimension = 1024,
                    contentScale = ContentScale.Fit,
                    placeholderColor = Color.Black,
                    showPlaceholderIcon = false
                )
            }
        }

        // 3. Previous Video Side Preview (attached to the left when swiping right)
        if (dragOffsetX > 0f && currentIndex > 0) {
            val prevVideo = videoList[currentIndex - 1]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = -size.width + dragOffsetX - 16.dp.toPx()
                    }
                    .background(Color.Black)
            ) {
                MediaThumbnail(
                    contentUri = prevVideo.contentUri,
                    mediaType = MediaType.VIDEO,
                    modifier = Modifier.fillMaxSize(),
                    width = 1920,
                    height = 1080,
                    maxDecodeDimension = 1024,
                    contentScale = ContentScale.Fit,
                    placeholderColor = Color.Black,
                    showPlaceholderIcon = false
                )
            }
        }

        // Gesture Overlay Detector Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(speed, skipSeconds, currentSpeed) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                        },
                        onDoubleTap = { offset ->
                            val currentPos = playbackEngine.positionMs.value
                            val dur = playbackEngine.durationMs.value
                            // Guard: if duration is unknown (0), seeking is meaningless and would jump to 0:00
                            if (dur <= 0L) return@detectTapGestures
                            val isLeft = offset.x < size.width / 2
                            val skipOffset = skipSeconds * 1000L
                            if (isLeft) {
                                playbackEngine.seekTo(maxOf(currentPos - skipOffset, 0L))
                                activeDoubleTapSide = DoubleTapSide.LEFT
                            } else {
                                playbackEngine.seekTo(minOf(currentPos + skipOffset, dur))
                                activeDoubleTapSide = DoubleTapSide.RIGHT
                            }
                            feedbackJob?.cancel()
                            feedbackJob = coroutineScope.launch {
                                delay(600)
                                activeDoubleTapSide = null
                            }
                        },
                        onPress = {
                            var isLongPress = false
                            val job = coroutineScope.launch {
                                delay(400)
                                isLongPress = true
                                isHoldingSpeed = true
                                playbackEngine.setSpeed(2.0f)
                            }
                            tryAwaitRelease()
                            job.cancel()
                            if (isLongPress) {
                                isHoldingSpeed = false
                                playbackEngine.setSpeed(currentSpeed)
                                // Consume this release so onTap is NOT triggered after a long-press
                                // (otherwise the control bar would toggle unexpectedly)
                            }
                        }
                    )
                }
                .pointerInput(currentIndex, videoList.size) {
                    var velocityTracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = {
                            settleJob?.cancel()
                            settleJob = null
                            gestureSettling = false
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                            dragDirection = DragDirection.Undecided
                            velocityTracker = VelocityTracker()
                        },
                        onDrag = { change, dragAmount ->
                            if (gestureSettling) {
                                settleJob?.cancel()
                                settleJob = null
                                gestureSettling = false
                            }
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            dragOffsetX += dragAmount.x
                            dragOffsetY += dragAmount.y

                            val absX = abs(dragOffsetX)
                            val absY = abs(dragOffsetY)

                            if (dragDirection == DragDirection.Undecided) {
                                if (absX > lockThresholdPx || absY > lockThresholdPx) {
                                    dragDirection = if (absX > absY) {
                                        DragDirection.Horizontal
                                    } else {
                                        DragDirection.Vertical
                                    }
                                }
                            }

                            if (dragDirection == DragDirection.Vertical && dragOffsetY > 0f) {
                                change.consume()
                            } else if (dragDirection == DragDirection.Horizontal) {
                                change.consume()
                            }
                        },
                        onDragEnd = {
                            if (gestureSettling) return@detectDragGestures
                            val velocity = velocityTracker.calculateVelocity()
                            val action = PlayerGestureState.determineAction(
                                dragOffsetX = dragOffsetX,
                                dragOffsetY = dragOffsetY,
                                horizontalThresholdPx = horizontalThresholdPx,
                                verticalThresholdPx = verticalThresholdPx,
                                currentIndex = currentIndex,
                                lastIndex = videoList.size - 1,
                                velocityX = velocity.x,
                                velocityY = velocity.y
                            )

                            when (action) {
                                PlayerDragAction.Previous -> {
                                    gestureSettling = true
                                    settleJob = coroutineScope.launch {
                                        val targetX = size.width.toFloat() + 16.dp.toPx()
                                        val anim = Animatable(dragOffsetX)
                                        anim.animateTo(targetX, tween(180, easing = FastOutSlowInEasing)) { dragOffsetX = value }
                                        saveProgress()
                                        onChangeVideo(currentIndex - 1)
                                    }
                                }
                                PlayerDragAction.Next -> {
                                    gestureSettling = true
                                    settleJob = coroutineScope.launch {
                                        val targetX = -size.width.toFloat() - 16.dp.toPx()
                                        val anim = Animatable(dragOffsetX)
                                        anim.animateTo(targetX, tween(180, easing = FastOutSlowInEasing)) { dragOffsetX = value }
                                        saveProgress()
                                        onChangeVideo(currentIndex + 1)
                                    }
                                }
                                PlayerDragAction.Dismiss -> {
                                    gestureSettling = true
                                    settleJob = coroutineScope.launch {
                                        val anim = Animatable(dragOffsetY)
                                        anim.animateTo(size.height.toFloat(), tween(180, easing = FastOutSlowInEasing)) { dragOffsetY = value }
                                        saveProgressAndStop()
                                        onBack()
                                        gestureSettling = false
                                        settleJob = null
                                    }
                                }
                                PlayerDragAction.None -> {
                                    val settledDirection = dragDirection
                                    gestureSettling = true
                                    settleJob = coroutineScope.launch {
                                        val animation = spring<Float>(
                                            dampingRatio = 0.82f,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                        if (settledDirection == DragDirection.Vertical || dragOffsetY != 0f) {
                                            Animatable(dragOffsetY).animateTo(0f, animation) { dragOffsetY = value }
                                        }
                                        if (settledDirection == DragDirection.Horizontal || dragOffsetX != 0f) {
                                            Animatable(dragOffsetX).animateTo(0f, animation) { dragOffsetX = value }
                                        }
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                        dragDirection = DragDirection.Undecided
                                        gestureSettling = false
                                        settleJob = null
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            val settledDirection = dragDirection
                            gestureSettling = true
                            settleJob = coroutineScope.launch {
                                val animation = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                                if (settledDirection == DragDirection.Vertical || dragOffsetY != 0f) {
                                    Animatable(dragOffsetY).animateTo(0f, animation) { dragOffsetY = value }
                                }
                                if (settledDirection == DragDirection.Horizontal || dragOffsetX != 0f) {
                                    Animatable(dragOffsetX).animateTo(0f, animation) { dragOffsetX = value }
                                }
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                                dragDirection = DragDirection.Undecided
                                gestureSettling = false
                                settleJob = null
                            }
                        }
                    )
                }
        )

        // Double-Tap Left Rewind Indicator (Clean icon with drop shadow, no black card)
        AnimatedVisibility(
            visible = activeDoubleTapSide == DoubleTapSide.LEFT,
            enter = fadeIn(tween(80)) + scaleIn(initialScale = 0.82f),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.82f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 72.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Filled.FastRewind,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(52.dp)
                )
                Text(
                    text = "-${skipSeconds}s",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            blurRadius = 10f
                        )
                    )
                )
            }
        }

        // Double-Tap Right Fast-Forward Indicator (Clean icon with drop shadow, no black card)
        AnimatedVisibility(
            visible = activeDoubleTapSide == DoubleTapSide.RIGHT,
            enter = fadeIn(tween(80)) + scaleIn(initialScale = 0.82f),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.82f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 72.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Filled.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(52.dp)
                )
                Text(
                    text = "+${skipSeconds}s",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            blurRadius = 10f
                        )
                    )
                )
            }
        }

        // Hold-to-Speed 2.0x HUD Indicator
        AnimatedVisibility(
            visible = isHoldingSpeed,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(160)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 18.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.82f),
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.FastForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "2.0× 快进中",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // Debounced buffering indicator (only shows if buffering takes longer than 600ms)
        var showBufferingIndicator by remember(videoUri) { mutableStateOf(false) }
        LaunchedEffect(state, isFirstFrameReady) {
            if ((state == PlaybackState.Opening || state == PlaybackState.Buffering) && !isFirstFrameReady) {
                delay(600)
                showBufferingIndicator = true
            } else {
                showBufferingIndicator = false
            }
        }

        if (showBufferingIndicator) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).size(38.dp)
            )
        } else if (state == PlaybackState.Error) {
            Surface(
                color = Color.Black.copy(alpha = 0.82f),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("无法播放此视频", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(
                        diagnostics.lastError.ifBlank { "请检查文件是否完整，或尝试切换解码模式。" },
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onBack) { Text("返回") }
                        Button(onClick = {
                            coroutineScope.launch { playbackEngine.open(android.net.Uri.parse(videoUri)) }
                        }) { Text("重试") }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(110))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        saveProgressAndStop()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                    Text(
                        text = videoTitle,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isFavorite) "取消收藏" else "加入收藏",
                            tint = if (isFavorite) Color(0xFFFF3B30) else Color.White
                        )
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        IconButton(onClick = {
                            val activity = context.findActivity()
                            if (activity != null) {
                                val params = android.app.PictureInPictureParams.Builder().build()
                                activity.enterPictureInPictureMode(params)
                            }
                        }) {
                            Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "画中画", tint = Color.White)
                        }
                    }
                    IconButton(onClick = {
                        val nextMode = when (repeatMode) {
                            PlaybackRepeatMode.NONE -> PlaybackRepeatMode.ONE
                            PlaybackRepeatMode.ONE -> PlaybackRepeatMode.ALL
                            PlaybackRepeatMode.ALL -> PlaybackRepeatMode.NONE
                        }
                        onRepeatModeChange(nextMode)
                    }) {
                        val (icon, desc) = when (repeatMode) {
                            PlaybackRepeatMode.NONE -> Icons.AutoMirrored.Filled.TrendingFlat to "播放一次"
                            PlaybackRepeatMode.ONE -> Icons.Filled.RepeatOne to "单曲循环"
                            PlaybackRepeatMode.ALL -> Icons.Filled.Repeat to "列表循环"
                        }
                        Icon(icon, contentDescription = desc, tint = Color.White)
                    }
                    if (audioTracks.size > 1 || subtitleTracks.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { tracksExpanded = true }) {
                                Icon(Icons.Filled.Subtitles, contentDescription = "音轨与字幕", tint = Color.White)
                            }
                            DropdownMenu(expanded = tracksExpanded, onDismissRequest = { tracksExpanded = false }) {
                                if (audioTracks.isNotEmpty()) {
                                    DropdownMenuItem(text = { Text("音轨") }, onClick = {}, enabled = false)
                                    audioTracks.forEach { track ->
                                        DropdownMenuItem(
                                            text = { Text(track.name) },
                                            leadingIcon = {
                                                if (track.selected) Icon(Icons.Filled.Check, contentDescription = null)
                                                else Spacer(Modifier.size(24.dp))
                                            },
                                            onClick = {
                                                playbackEngine.selectAudioTrack(track.id)
                                                tracksExpanded = false
                                            }
                                        )
                                    }
                                }
                                if (subtitleTracks.isNotEmpty()) {
                                    DropdownMenuItem(text = { Text("字幕") }, onClick = {}, enabled = false)
                                    // "Disable subtitles" option — always shown when subtitle tracks are present
                                    DropdownMenuItem(
                                        text = { Text("关闭字幕") },
                                        leadingIcon = {
                                            if (subtitleTracks.none { it.selected }) Icon(Icons.Filled.Check, contentDescription = null)
                                            else Spacer(Modifier.size(24.dp))
                                        },
                                        onClick = {
                                            playbackEngine.selectSubtitleTrack(-1)
                                            tracksExpanded = false
                                        }
                                    )
                                    subtitleTracks.forEach { track ->
                                        DropdownMenuItem(
                                            text = { Text(track.name) },
                                            leadingIcon = {
                                                if (track.selected) Icon(Icons.Filled.Check, contentDescription = null)
                                                else Spacer(Modifier.size(24.dp))
                                            },
                                            onClick = {
                                                playbackEngine.selectSubtitleTrack(track.id)
                                                tracksExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { scaleExpanded = true }) {
                            Icon(Icons.Filled.AspectRatio, contentDescription = "画面比例", tint = Color.White)
                        }
                        DropdownMenu(expanded = scaleExpanded, onDismissRequest = { scaleExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("自适应 (默认)") },
                                leadingIcon = {
                                    if (videoScaleMode == VideoScaleMode.FIT) Icon(Icons.Filled.Check, contentDescription = null)
                                    else Spacer(Modifier.size(24.dp))
                                },
                                onClick = {
                                    videoScaleMode = VideoScaleMode.FIT
                                    playbackEngine.setVideoScaleMode(VideoScaleMode.FIT)
                                    scaleExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("铺满全屏 (裁剪)") },
                                leadingIcon = {
                                    if (videoScaleMode == VideoScaleMode.FILL) Icon(Icons.Filled.Check, contentDescription = null)
                                    else Spacer(Modifier.size(24.dp))
                                },
                                onClick = {
                                    videoScaleMode = VideoScaleMode.FILL
                                    playbackEngine.setVideoScaleMode(VideoScaleMode.FILL)
                                    scaleExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("拉伸全屏") },
                                leadingIcon = {
                                    if (videoScaleMode == VideoScaleMode.STRETCH) Icon(Icons.Filled.Check, contentDescription = null)
                                    else Spacer(Modifier.size(24.dp))
                                },
                                onClick = {
                                    videoScaleMode = VideoScaleMode.STRETCH
                                    playbackEngine.setVideoScaleMode(VideoScaleMode.STRETCH)
                                    scaleExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("16:9 比例") },
                                leadingIcon = {
                                    if (videoScaleMode == VideoScaleMode.RATIO_16_9) Icon(Icons.Filled.Check, contentDescription = null)
                                    else Spacer(Modifier.size(24.dp))
                                },
                                onClick = {
                                    videoScaleMode = VideoScaleMode.RATIO_16_9
                                    playbackEngine.setVideoScaleMode(VideoScaleMode.RATIO_16_9)
                                    scaleExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("4:3 比例") },
                                leadingIcon = {
                                    if (videoScaleMode == VideoScaleMode.RATIO_4_3) Icon(Icons.Filled.Check, contentDescription = null)
                                    else Spacer(Modifier.size(24.dp))
                                },
                                onClick = {
                                    videoScaleMode = VideoScaleMode.RATIO_4_3
                                    playbackEngine.setVideoScaleMode(VideoScaleMode.RATIO_4_3)
                                    scaleExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("100% 原始尺寸") },
                                leadingIcon = {
                                    if (videoScaleMode == VideoScaleMode.ORIGINAL) Icon(Icons.Filled.Check, contentDescription = null)
                                    else Spacer(Modifier.size(24.dp))
                                },
                                onClick = {
                                    videoScaleMode = VideoScaleMode.ORIGINAL
                                    playbackEngine.setVideoScaleMode(VideoScaleMode.ORIGINAL)
                                    scaleExpanded = false
                                }
                            )
                        }
                    }
                    Box {
                        TextButton(onClick = { speedExpanded = true }) {
                            Text("${currentSpeed}×", color = Color.White)
                        }
                        DropdownMenu(expanded = speedExpanded, onDismissRequest = { speedExpanded = false }) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { newSpeed ->
                                DropdownMenuItem(
                                    text = { Text("${newSpeed}×") },
                                    onClick = {
                                        currentSpeed = newSpeed
                                        playbackEngine.setSpeed(newSpeed)
                                        speedExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                            )
                        )
                        .navigationBarsPadding()
                        .padding(start = 18.dp, top = 42.dp, end = 18.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val sliderValue = if (isDragging) dragPosition else position.toFloat()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(sliderValue.toLong()), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Text(formatTime(duration), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                    }
                    Slider(
                        value = sliderValue.coerceIn(0f, maxOf(duration.toFloat(), 1f)),
                        onValueChange = {
                            isDragging = true
                            dragPosition = it
                        },
                        onValueChangeFinished = {
                            val finalTarget = PlayerSeekThrottle.clampPosition(dragPosition.toLong(), duration)
                            playbackEngine.seekTo(finalTarget)
                            isDragging = false
                        },
                        valueRange = 0f..maxOf(duration.toFloat(), 1f),
                        enabled = isSeekable && duration > 0,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val hasPrev = currentIndex > 0
                        val hasNext = currentIndex < videoList.size - 1
                        IconButton(
                            onClick = {
                                saveProgressAndStop()
                                onChangeVideo(currentIndex - 1)
                            },
                            enabled = hasPrev
                        ) {
                            Icon(Icons.Filled.SkipPrevious, "上一个", tint = if (hasPrev) Color.White else Color.White.copy(alpha = 0.28f))
                        }
                        TextButton(onClick = { playbackEngine.seekTo(maxOf(position - skipSeconds * 1000L, 0L)) }) {
                            Text("−${skipSeconds}", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        }
                        FilledIconButton(
                            onClick = {
                                if (state == PlaybackState.Playing) playbackEngine.pause() else playbackEngine.play()
                            },
                            modifier = Modifier.size(62.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                if (state == PlaybackState.Playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (state == PlaybackState.Playing) "暂停" else "播放",
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        TextButton(onClick = { playbackEngine.seekTo(minOf(position + skipSeconds * 1000L, duration)) }) {
                            Text("+${skipSeconds}", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(
                            onClick = {
                                saveProgressAndStop()
                                onChangeVideo(currentIndex + 1)
                            },
                            enabled = hasNext
                        ) {
                            Icon(Icons.Filled.SkipNext, "下一个", tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.28f))
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
