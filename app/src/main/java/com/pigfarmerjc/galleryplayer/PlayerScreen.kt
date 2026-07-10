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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
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
    onRepeatModeChange: (PlaybackRepeatMode) -> Unit,
    scaleMode: VideoScaleMode,
    onScaleModeChange: (VideoScaleMode) -> Unit
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
    val videoSizeState by playbackEngine.videoSize.collectAsState()

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

    // Transition state driven by parent
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val swipeOffset = remember { Animatable(0f) }
    var isTransitioning by remember { mutableStateOf(false) }
    var transitionDirection by remember { mutableStateOf(1) } // 1: Next (swiped left, new enters from right), -1: Prev (swiped right, new enters from left)
    var lastVideoUri by remember { mutableStateOf(videoUri) }

    // Safe Transition loading and timeout variables
    var isMediaLoading by remember(videoUri) { mutableStateOf(true) }
    var isPlaybackTimeout by remember(videoUri) { mutableStateOf(false) }

    // Real-time View sizing diagnostics variables
    var playerRootSize by remember { mutableStateOf(IntSize.Zero) }
    var androidViewSize by remember { mutableStateOf(IntSize.Zero) }

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

    val triggerVideoChange: (Int) -> Unit = { newIndex ->
        if (!isTransitioning && newIndex in videoList.indices) {
            val direction = if (newIndex > currentIndex) 1 else -1
            transitionDirection = direction
            isTransitioning = true
            isMediaLoading = true
            coroutineScope.launch {
                val targetOffset = if (direction == 1) -screenWidthPx else screenWidthPx
                swipeOffset.animateTo(targetOffset, animationSpec = androidx.compose.animation.core.tween(durationMillis = 200))
                saveProgressAndStop()
                onChangeVideo(newIndex)
            }
        }
    }

    // Hide transition loader when playing starts or size details are available
    LaunchedEffect(state, videoSizeState) {
        if (state == PlaybackState.Playing || (videoSizeState != null && videoSizeState!!.width > 0)) {
            isMediaLoading = false
        }
    }

    // Monitor timeout state (e.g. 7 seconds without video track size or playback starting)
    LaunchedEffect(videoUri) {
        isPlaybackTimeout = false
        delay(7000)
        if (isMediaLoading && state != PlaybackState.Playing) {
            isPlaybackTimeout = true
        }
    }

    // Reset translation when parent updates URI
    LaunchedEffect(videoUri) {
        if (videoUri != lastVideoUri) {
            lastVideoUri = videoUri
            if (isTransitioning) {
                val startOffset = if (transitionDirection == 1) screenWidthPx else -screenWidthPx
                swipeOffset.snapTo(startOffset)
                swipeOffset.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(durationMillis = 200))
                isTransitioning = false
            }
        } else {
            swipeOffset.snapTo(0f)
            isTransitioning = false
        }
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

    // Auto-hide controls after 3 seconds of inactivity
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
            isMediaLoading = true
            playbackEngine.open(android.net.Uri.parse(videoUri))
        }
    }

    // Pass scale mode to videoHost when host changes or scaleMode changes
    LaunchedEffect(videoHost, scaleMode) {
        videoHost?.setVideoScaleMode(scaleMode)
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
                    playbackEngine.seekTo(0L)
                    playbackEngine.play()
                }
                PlaybackRepeatMode.ALL -> {
                    if (videoList.isNotEmpty()) {
                        val nextIndex = (currentIndex + 1) % videoList.size
                        triggerVideoChange(nextIndex)
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
            .onGloballyPositioned { coordinates ->
                playerRootSize = coordinates.size
                playbackEngine.updateViewSizes(
                    playerRootWidth = playerRootSize.width,
                    playerRootHeight = playerRootSize.height,
                    androidViewWidth = androidViewSize.width,
                    androidViewHeight = androidViewSize.height
                )
            }
            .graphicsLayer {
                // Apply translation downward displacement for reactive swipe down interaction
                if (dragDirection == DragDirection.Vertical && dragOffsetY > 0f) {
                    translationY = dragOffsetY
                }
                // Apply horizontal swipe transition offset
                translationX = swipeOffset.value
            }
    ) {
        AndroidView(
            factory = { ctx ->
                val host = videoOutputFactory.create(ctx)
                videoHost = host
                playbackEngine.attachVideoOutput(host)
                host.setVideoScaleMode(scaleMode)
                host.view
            },
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    androidViewSize = coordinates.size
                    playbackEngine.updateViewSizes(
                        playerRootWidth = playerRootSize.width,
                        playerRootHeight = playerRootSize.height,
                        androidViewWidth = androidViewSize.width,
                        androidViewHeight = androidViewSize.height
                    )
                },
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
                            if (!isMediaLoading) {
                                controlsVisible = !controlsVisible
                            }
                        },
                        onDoubleTap = { offset ->
                            if (!isMediaLoading) {
                                val currentPos = playbackEngine.positionMs.value
                                val dur = playbackEngine.durationMs.value
                                val isLeft = offset.x < size.width / 2
                                val skipOffset = skipSeconds * 1000L
                                if (isLeft) {
                                    playbackEngine.seekTo(maxOf(currentPos - skipOffset, 0L))
                                } else {
                                    playbackEngine.seekTo(minOf(currentPos + skipOffset, dur))
                                }
                            }
                        },
                        onPress = {
                            if (!isMediaLoading) {
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
                        }
                    )
                }
                .pointerInput(currentIndex, videoList.size, isTransitioning, isMediaLoading) {
                    detectDragGestures(
                        onDragStart = {
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                            dragDirection = DragDirection.Undecided
                        },
                        onDrag = { change, dragAmount ->
                            if (!isMediaLoading) {
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
                            }
                        },
                        onDragEnd = {
                            if (!isMediaLoading) {
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
                                        triggerVideoChange(currentIndex - 1)
                                    }
                                    PlayerDragAction.Next -> {
                                        triggerVideoChange(currentIndex + 1)
                                    }
                                    PlayerDragAction.Dismiss -> {
                                        saveProgressAndStop()
                                        onBack()
                                    }
                                    PlayerDragAction.None -> {
                                        if (dragDirection == DragDirection.Vertical) {
                                            coroutineScope.launch {
                                                val anim = Animatable(dragOffsetY)
                                                anim.animateTo(0f, animationSpec = spring()) {
                                                    dragOffsetY = value
                                                }
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

        // Top and Bottom Controls Overlays
        if (controlsVisible && !isMediaLoading) {
            val folderName = remember(videoUri) {
                val activeVideoItem = videoList.getOrNull(currentIndex)
                activeVideoItem?.relativePath?.trimEnd('/')?.split('/')?.lastOrNull()?.takeIf { it.isNotEmpty() } ?: "Root"
            }

            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                        )
                    )
                    .padding(top = 16.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = videoTitle,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = folderName,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Bottom controls panel (3 Rows Layout)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
                    .padding(top = 32.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
                    .align(Alignment.BottomCenter),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Row 1: Seekbar and time layout
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

                // Row 2: Playback Action Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Skip Previous
                    val hasPrev = currentIndex > 0
                    IconButton(
                        onClick = { triggerVideoChange(currentIndex - 1) },
                        enabled = hasPrev && !isTransitioning,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous",
                            tint = if (hasPrev) Color.White else Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Seek Backward Custom Seconds
                    TextButton(
                        onClick = { playbackEngine.seekTo(maxOf(position - skipSeconds * 1000L, 0L)) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Text(
                            text = "-${skipSeconds}s",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Play / Pause Toggle (Circular, Enlarged)
                    IconButton(
                        onClick = {
                            if (state == PlaybackState.Playing) playbackEngine.pause() else playbackEngine.play()
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(36.dp))
                    ) {
                        val icon = if (state == PlaybackState.Playing) Icons.Filled.Pause else Icons.Filled.PlayArrow
                        Icon(
                            imageVector = icon,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Seek Forward Custom Seconds
                    TextButton(
                        onClick = { playbackEngine.seekTo(minOf(position + skipSeconds * 1000L, duration)) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Text(
                            text = "+${skipSeconds}s",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Skip Next
                    val hasNext = currentIndex < videoList.size - 1
                    IconButton(
                        onClick = { triggerVideoChange(currentIndex + 1) },
                        enabled = hasNext && !isTransitioning,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next",
                            tint = if (hasNext) Color.White else Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Row 3: Speed, Repeat Mode, Aspect Ratio scaling
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Playback Speed Selector
                    var speedExpanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { speedExpanded = true }) {
                            Icon(Icons.Filled.Speed, contentDescription = "Speed", tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${currentSpeed}x", color = Color.White)
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

                    // Playback Repeat Mode Cycle
                    IconButton(onClick = {
                        val nextMode = when (repeatMode) {
                            PlaybackRepeatMode.NONE -> PlaybackRepeatMode.ONE
                            PlaybackRepeatMode.ONE -> PlaybackRepeatMode.ALL
                            PlaybackRepeatMode.ALL -> PlaybackRepeatMode.NONE
                        }
                        onRepeatModeChange(nextMode)
                    }) {
                        val (icon, desc) = when (repeatMode) {
                            PlaybackRepeatMode.NONE -> Icons.AutoMirrored.Filled.TrendingFlat to "Play Once"
                            PlaybackRepeatMode.ONE -> Icons.Filled.RepeatOne to "Repeat One"
                            PlaybackRepeatMode.ALL -> Icons.Filled.Repeat to "Repeat All"
                        }
                        Icon(icon, contentDescription = desc, tint = Color.White)
                    }

                    // Video Scale Mode Toggle (Locked to Fit Experimental)
                    var scaleExpanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { scaleExpanded = true }) {
                            Icon(Icons.Filled.AspectRatio, contentDescription = "Scale Mode", tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.scale_fit) + " (实验)", color = Color.White)
                        }
                        DropdownMenu(
                            expanded = scaleExpanded,
                            onDismissRequest = { scaleExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.scale_fit) + " (实验)") },
                                onClick = {
                                    onScaleModeChange(VideoScaleMode.FIT)
                                    scaleExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Loading Transition Overlay (Spinner + Title)
        if (isMediaLoading && state != PlaybackState.Error && !isPlaybackTimeout) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text(
                        text = videoTitle,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }

        // Playback Timeout Warning Overlay
        if (isPlaybackTimeout && isMediaLoading && state != PlaybackState.Error) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "视频画面未正常显示",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "可尝试切换解码模式或显示模式\n请复制诊断信息反馈",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val annotatedString = android.text.TextUtils.concat(
                                        "Device: ${android.os.Build.BRAND} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})\n",
                                        "App Version: 1.0 (Debug Build)\n",
                                        "Media Stats: ${videoList.size} videos\n",
                                        "Decoder Mode: ${diagnostics.decoderMode}\n",
                                        "Last Playback Title: $videoTitle\n",
                                        "Last Playback URI: $videoUri\n",
                                        "Playback Error: ${diagnostics.lastError}\n",
                                        "Playback State: ${playbackEngine.playbackState.value}\n",
                                        "Track Resolution: ${diagnostics.width}x${diagnostics.height} (Rot: ${diagnostics.rotation})\n",
                                        "playerRoot Size: ${diagnostics.playerRootWidth}x${diagnostics.playerRootHeight}\n",
                                        "androidView Size: ${diagnostics.androidViewWidth}x${diagnostics.androidViewHeight}\n",
                                        "videoHost Size: ${diagnostics.videoHostWidth}x${diagnostics.videoHostHeight}\n",
                                        "vlcVideoLayout Size: ${diagnostics.vlcVideoLayoutWidth}x${diagnostics.vlcVideoLayoutHeight}\n",
                                        "Surface Attached: ${diagnostics.surfaceAttached}\n",
                                        "Scale Mode: ${diagnostics.scaleMode}\n",
                                        "Audio: ${diagnostics.sampleRate}Hz / ${diagnostics.channels}ch\n"
                                    ).toString()
                                    
                                    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("Diagnostics", annotatedString))
                                    android.widget.Toast.makeText(context, "Diagnostics copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(stringResource(R.string.copy_diagnostics))
                            }
                            TextButton(
                                onClick = {
                                    saveProgressAndStop()
                                    onBack()
                                }
                            ) {
                                Text(stringResource(R.string.close), color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }
        }

        // Playback Error Overlay
        if (state == PlaybackState.Error) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = stringResource(R.string.playback_failed),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = stringResource(R.string.playback_failed_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val annotatedString = android.text.TextUtils.concat(
                                        "Device: ${android.os.Build.BRAND} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})\n",
                                        "App Version: 1.0 (Debug Build)\n",
                                        "Media Stats: ${videoList.size} videos\n",
                                        "Decoder Mode: ${diagnostics.decoderMode}\n",
                                        "Last Playback Title: $videoTitle\n",
                                        "Last Playback URI: $videoUri\n",
                                        "Playback Error: ${diagnostics.lastError}\n",
                                        "Playback State: ${playbackEngine.playbackState.value}\n",
                                        "Track Resolution: ${diagnostics.width}x${diagnostics.height} (Rot: ${diagnostics.rotation})\n",
                                        "playerRoot Size: ${diagnostics.playerRootWidth}x${diagnostics.playerRootHeight}\n",
                                        "androidView Size: ${diagnostics.androidViewWidth}x${diagnostics.androidViewHeight}\n",
                                        "videoHost Size: ${diagnostics.videoHostWidth}x${diagnostics.videoHostHeight}\n",
                                        "vlcVideoLayout Size: ${diagnostics.vlcVideoLayoutWidth}x${diagnostics.vlcVideoLayoutHeight}\n",
                                        "Surface Attached: ${diagnostics.surfaceAttached}\n",
                                        "Scale Mode: ${diagnostics.scaleMode}\n",
                                        "Audio: ${diagnostics.sampleRate}Hz / ${diagnostics.channels}ch\n"
                                    ).toString()
                                    
                                    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("Diagnostics", annotatedString))
                                    android.widget.Toast.makeText(context, "Diagnostics copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(stringResource(R.string.copy_diagnostics))
                            }
                            TextButton(
                                onClick = {
                                    saveProgressAndStop()
                                    onBack()
                                }
                            ) {
                                Text(stringResource(R.string.close), color = MaterialTheme.colorScheme.onErrorContainer)
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
