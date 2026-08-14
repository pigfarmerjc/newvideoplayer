package com.pigfarmerjc.galleryplayer

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
    onRepeatModeChange: (PlaybackRepeatMode) -> Unit
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

    var controlsVisible by remember { mutableStateOf(true) }
    var speedExpanded by remember { mutableStateOf(false) }
    var tracksExpanded by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf(0f) }

    // Multi-speed level separation
    var currentSpeed by remember(videoUri) { mutableStateOf(defaultSpeed) }
    var hasAppliedDefaultSpeed by remember(videoUri) { mutableStateOf(false) }

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

    // Safe save helper
    val saveProgressAndStop = {
        val currentPos = playbackEngine.positionMs.value
        val dur = playbackEngine.durationMs.value
        if (dur > 0) {
            val isFinished = (currentPos.toDouble() / dur.toDouble()) >= 0.90
            onPlaybackProgress(currentPos, dur, isFinished)
        }
        playbackEngine.stop()
    }

    // Full-screen immersive window setup
    DisposableEffect(videoUri) {
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
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
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
                    // Repeat current video: seek to 0 and play again.
                    // This does not change the videoUri, so hasStartedSession remains true
                    // and duplicate playCount additions are blocked.
                    playbackEngine.seekTo(0L)
                    playbackEngine.play()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer {
                when (dragDirection) {
                    DragDirection.Horizontal -> {
                        translationX = dragOffsetX
                        if (size.width > 0f) {
                            alpha = 1f - (abs(dragOffsetX) / size.width).coerceIn(0f, 1f) * 0.18f
                        }
                    }
                    DragDirection.Vertical -> {
                        val progress = if (size.height > 0f) {
                            (dragOffsetY.coerceAtLeast(0f) / size.height).coerceIn(0f, 1f)
                        } else 0f
                        translationY = dragOffsetY.coerceAtLeast(0f)
                        scaleX = 1f - progress * 0.08f
                        scaleY = 1f - progress * 0.08f
                        clip = progress > 0f
                        shape = RoundedCornerShape((24f * progress).dp)
                    }
                    DragDirection.Undecided -> Unit
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                val host = videoOutputFactory.create(ctx)
                videoHost = host
                playbackEngine.attachVideoOutput(host)
                host.view
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = {
                playbackEngine.detachVideoOutput()
                videoHost?.dispose()
                videoHost = null
            }
        )

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
                            val isLeft = offset.x < size.width / 2
                            val skipOffset = skipSeconds * 1000L
                            if (isLeft) {
                                playbackEngine.seekTo(maxOf(currentPos - skipOffset, 0L))
                            } else {
                                playbackEngine.seekTo(minOf(currentPos + skipOffset, dur))
                            }
                        },
                        onPress = {
                            var isLongPress = false
                            val job = coroutineScope.launch {
                                delay(500)
                                isLongPress = true
                                playbackEngine.setSpeed(2.0f)
                            }
                            tryAwaitRelease()
                            job.cancel()
                            if (isLongPress) {
                                playbackEngine.setSpeed(currentSpeed)
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
                            // Avoid registering drag if we're touching active seekbar or buttons (handled by click checks)
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
                                        val anim = Animatable(dragOffsetX)
                                        anim.animateTo(size.width.toFloat(), tween(160)) { dragOffsetX = value }
                                        saveProgressAndStop()
                                        onChangeVideo(currentIndex - 1)
                                        dragOffsetX = 0f
                                        dragDirection = DragDirection.Undecided
                                        gestureSettling = false
                                        settleJob = null
                                    }
                                }
                                PlayerDragAction.Next -> {
                                    gestureSettling = true
                                    settleJob = coroutineScope.launch {
                                        val anim = Animatable(dragOffsetX)
                                        anim.animateTo(-size.width.toFloat(), tween(160)) { dragOffsetX = value }
                                        saveProgressAndStop()
                                        onChangeVideo(currentIndex + 1)
                                        dragOffsetX = 0f
                                        dragDirection = DragDirection.Undecided
                                        gestureSettling = false
                                        settleJob = null
                                    }
                                }
                                PlayerDragAction.Dismiss -> {
                                    gestureSettling = true
                                    settleJob = coroutineScope.launch {
                                        val anim = Animatable(dragOffsetY)
                                        anim.animateTo(size.height.toFloat(), tween(180)) { dragOffsetY = value }
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
                                            dampingRatio = 0.86f,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                        if (settledDirection == DragDirection.Vertical) {
                                            Animatable(dragOffsetY).animateTo(0f, animation) { dragOffsetY = value }
                                        } else if (settledDirection == DragDirection.Horizontal) {
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
                                val animation = spring<Float>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
                                if (settledDirection == DragDirection.Vertical) {
                                    Animatable(dragOffsetY).animateTo(0f, animation) { dragOffsetY = value }
                                } else if (settledDirection == DragDirection.Horizontal) {
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

        if (state == PlaybackState.Opening || state == PlaybackState.Buffering) {
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
                    IconButton(onClick = {
                        val nextMode = when (repeatMode) {
                            PlaybackRepeatMode.NONE -> PlaybackRepeatMode.ONE
                            PlaybackRepeatMode.ONE -> PlaybackRepeatMode.ALL
                            PlaybackRepeatMode.ALL -> PlaybackRepeatMode.NONE
                        }
                        onRepeatModeChange(nextMode)
                    }) {
                        val (icon, desc) = when (repeatMode) {
                            PlaybackRepeatMode.NONE -> Icons.Filled.TrendingFlat to "播放一次"
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
