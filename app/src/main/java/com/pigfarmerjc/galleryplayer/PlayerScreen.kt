package com.pigfarmerjc.galleryplayer

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pigfarmerjc.galleryplayer.core.player.api.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    videoUri: String,
    videoTitle: String,
    videoList: List<LocalMediaItem>,
    currentIndex: Int,
    playbackEngine: PlaybackEngine,
    videoOutputFactory: VideoOutputHostFactory,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    // Collect playback states
    val state by playbackEngine.playbackState.collectAsState()
    val position by playbackEngine.positionMs.collectAsState()
    val duration by playbackEngine.durationMs.collectAsState()
    val isSeekable by playbackEngine.isSeekable.collectAsState()
    val speed by playbackEngine.playbackSpeed.collectAsState()

    var controlsVisible by remember { mutableStateOf(true) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf(0f) }

    // Gesture states
    var originalSpeed by remember { mutableStateOf(1.0f) }

    // Intercept hardware back button
    BackHandler {
        playbackEngine.stop()
        onBack()
    }

    // Auto-hide controls after 3 seconds of inactivity
    LaunchedEffect(controlsVisible, isDragging, state) {
        if (controlsVisible && !isDragging && state == PlaybackState.Playing) {
            delay(3000)
            controlsVisible = false
        }
    }

    // Open video on startup
    LaunchedEffect(videoUri) {
        playbackEngine.open(android.net.Uri.parse(videoUri))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Video Surface view
        if (state != PlaybackState.Idle && state != PlaybackState.Released) {
            var videoHost by remember { mutableStateOf<VideoOutputHost?>(null) }
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
        }

        // 2. Gesture Overlay Detector
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(speed) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                        },
                        onDoubleTap = { offset ->
                            val currentPos = playbackEngine.positionMs.value
                            val dur = playbackEngine.durationMs.value
                            val isLeft = offset.x < size.width / 2
                            if (isLeft) {
                                playbackEngine.seekTo(maxOf(currentPos - 10000L, 0L))
                            } else {
                                playbackEngine.seekTo(minOf(currentPos + 10000L, dur))
                            }
                        },
                        onPress = {
                            var isLongPress = false
                            val job = coroutineScope.launch {
                                delay(500)
                                isLongPress = true
                                originalSpeed = speed
                                playbackEngine.setSpeed(2.0f)
                            }
                            tryAwaitRelease()
                            job.cancel()
                            if (isLongPress) {
                                playbackEngine.setSpeed(originalSpeed)
                            }
                        }
                    )
                }
        )

        // 3. UI Controls overlay
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
                    playbackEngine.stop()
                    onBack()
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
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
                // Seekbar
                val sliderValue = if (isDragging) dragPosition else position.toFloat()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatTime(sliderValue.toLong()),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
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
                        text = formatTime(duration),
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
                            val prevItem = videoList[currentIndex - 1]
                            playbackEngine.stop()
                            coroutineScope.launch {
                                playbackEngine.open(android.net.Uri.parse(prevItem.contentUri))
                            }
                        },
                        enabled = hasPrev
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Previous", tint = if (hasPrev) Color.White else Color.Gray)
                    }

                    // Seek Backward 10s
                    IconButton(onClick = {
                        playbackEngine.seekTo(maxOf(position - 10000L, 0L))
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rewind 10s", tint = Color.White)
                    }

                    // Play / Pause Toggle
                    IconButton(onClick = {
                        if (state == PlaybackState.Playing) playbackEngine.pause() else playbackEngine.play()
                    }) {
                        val icon = if (state == PlaybackState.Playing) Icons.Default.Close else Icons.Default.PlayArrow
                        Icon(icon, contentDescription = "Play/Pause", tint = Color.White)
                    }

                    // Seek Forward 10s
                    IconButton(onClick = {
                        playbackEngine.seekTo(minOf(position + 10000L, duration))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Forward 10s", tint = Color.White) // fallback icon
                    }

                    // Next Button
                    val hasNext = currentIndex < videoList.size - 1
                    IconButton(
                        onClick = {
                            val nextItem = videoList[currentIndex + 1]
                            playbackEngine.stop()
                            coroutineScope.launch {
                                playbackEngine.open(android.net.Uri.parse(nextItem.contentUri))
                            }
                        },
                        enabled = hasNext
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Next", tint = if (hasNext) Color.White else Color.Gray)
                    }

                    // Speed selector
                    var speedExpanded by remember { mutableStateOf(false) }
                    Box {
                        Button(onClick = { speedExpanded = true }) {
                            Text("Speed: ${speed}x")
                        }
                        DropdownMenu(
                            expanded = speedExpanded,
                            onDismissRequest = { speedExpanded = false }
                        ) {
                            listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("${s}x") },
                                    onClick = {
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
