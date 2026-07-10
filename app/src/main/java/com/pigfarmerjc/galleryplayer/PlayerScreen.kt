package com.pigfarmerjc.galleryplayer

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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

    var controlsVisible by remember { mutableStateOf(true) }
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
                // Apply translation downward displacement for reactive swipe down interaction
                if (dragDirection == DragDirection.Vertical && dragOffsetY > 0f) {
                    translationY = dragOffsetY
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
                    detectDragGestures(
                        onDragStart = {
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                            dragDirection = DragDirection.Undecided
                        },
                        onDrag = { change, dragAmount ->
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
                            val action = PlayerGestureState.determineAction(
                                dragOffsetX = dragOffsetX,
                                dragOffsetY = dragOffsetY,
                                horizontalThresholdPx = horizontalThresholdPx,
                                verticalThresholdPx = verticalThresholdPx,
                                currentIndex = currentIndex,
                                lastIndex = videoList.size - 1
                            )

                            when (action) {
                                PlayerDragAction.Previous -> {
                                    saveProgressAndStop()
                                    onChangeVideo(currentIndex - 1)
                                }
                                PlayerDragAction.Next -> {
                                    saveProgressAndStop()
                                    onChangeVideo(currentIndex + 1)
                                }
                                PlayerDragAction.Dismiss -> {
                                    saveProgressAndStop()
                                    onBack()
                                }
                                PlayerDragAction.None -> {
                                    if (dragDirection == DragDirection.Vertical) {
                                        // Snap back to original position
                                        coroutineScope.launch {
                                            val anim = Animatable(dragOffsetY)
                                            anim.animateTo(0f, animationSpec = spring()) {
                                                dragOffsetY = value
                                            }
                                        }
                                    }
                                }
                            }

                            dragOffsetX = 0f
                            dragOffsetY = 0f
                            dragDirection = DragDirection.Undecided
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                val anim = Animatable(dragOffsetY)
                                anim.animateTo(0f) {
                                    dragOffsetY = value
                                }
                            }
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                            dragDirection = DragDirection.Undecided
                        }
                    )
                }
        )

        if (controlsVisible) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(8.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    saveProgressAndStop()
                    onBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = videoTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
            }

            // Bottom controls panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Seekbar and time layout
                val sliderValue = if (isDragging) dragPosition else position.toFloat()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            isDragging = true
                            dragPosition = it
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            playbackEngine.seekTo(dragPosition.toLong())
                        },
                        valueRange = 0f..maxOf(duration.toFloat(), 1f),
                        enabled = isSeekable && duration > 0,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${formatTime(sliderValue.toLong())} / ${formatTime(duration)}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Control buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Button
                    val hasPrev = currentIndex > 0
                    IconButton(
                        onClick = {
                            saveProgressAndStop()
                            onChangeVideo(currentIndex - 1)
                        },
                        enabled = hasPrev
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous",
                            tint = if (hasPrev) Color.White else Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Seek Backward Custom Seconds
                    TextButton(onClick = {
                        playbackEngine.seekTo(maxOf(position - skipSeconds * 1000L, 0L))
                    }) {
                        Text(
                            text = "-${skipSeconds}s",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    // Play / Pause Toggle (Enlarged)
                    IconButton(
                        onClick = {
                            if (state == PlaybackState.Playing) playbackEngine.pause() else playbackEngine.play()
                        },
                        modifier = Modifier.size(64.dp)
                    ) {
                        val icon = if (state == PlaybackState.Playing) Icons.Filled.Pause else Icons.Filled.PlayArrow
                        Icon(
                            imageVector = icon,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    // Seek Forward Custom Seconds
                    TextButton(onClick = {
                        playbackEngine.seekTo(minOf(position + skipSeconds * 1000L, duration))
                    }) {
                        Text(
                            text = "+${skipSeconds}s",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    // Next Button
                    val hasNext = currentIndex < videoList.size - 1
                    IconButton(
                        onClick = {
                            saveProgressAndStop()
                            onChangeVideo(currentIndex + 1)
                        },
                        enabled = hasNext
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next",
                            tint = if (hasNext) Color.White else Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Playback Repeat Mode Cycle Button
                    IconButton(onClick = {
                        val nextMode = when (repeatMode) {
                            PlaybackRepeatMode.NONE -> PlaybackRepeatMode.ONE
                            PlaybackRepeatMode.ONE -> PlaybackRepeatMode.ALL
                            PlaybackRepeatMode.ALL -> PlaybackRepeatMode.NONE
                        }
                        onRepeatModeChange(nextMode)
                    }) {
                        val (icon, desc) = when (repeatMode) {
                            PlaybackRepeatMode.NONE -> Icons.Filled.TrendingFlat to "Play Once"
                            PlaybackRepeatMode.ONE -> Icons.Filled.RepeatOne to "Repeat One"
                            PlaybackRepeatMode.ALL -> Icons.Filled.Repeat to "Repeat All"
                        }
                        Icon(icon, contentDescription = desc, tint = Color.White)
                    }

                    // Speed selector
                    var speedExpanded by remember { mutableStateOf(false) }
                    Box {
                        Button(onClick = { speedExpanded = true }) {
                            Text("${currentSpeed}x")
                        }
                        DropdownMenu(
                            expanded = speedExpanded,
                            onDismissRequest = { speedExpanded = false }
                        ) {
                            listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("${s}x") },
                                    onClick = {
                                        currentSpeed = s
                                        playbackEngine.setSpeed(s)
                                        speedExpanded = false
                                    }
                                )
                            }
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
