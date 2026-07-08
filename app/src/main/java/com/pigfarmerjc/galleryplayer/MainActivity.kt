package com.pigfarmerjc.galleryplayer

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pigfarmerjc.galleryplayer.core.player.api.*
import com.pigfarmerjc.galleryplayer.player.libvlc.LibVlcPlaybackEngine
import com.pigfarmerjc.galleryplayer.player.libvlc.LibVlcVideoOutputHostFactory
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val playbackEngine: PlaybackEngine = LibVlcPlaybackEngine(application)
    val videoOutputFactory: VideoOutputHostFactory = LibVlcVideoOutputHostFactory()
    var wasPlayingBeforeBackground: Boolean = false

    override fun onCleared() {
        super.onCleared()
        playbackEngine.release()
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PlayerAppScreen()
                }
            }
        }
    }

    @Composable
    fun PlayerAppScreen(viewModel: MainViewModel = viewModel()) {
        val coroutineScope = rememberCoroutineScope()
        val engine = viewModel.playbackEngine

        // Collect engine states
        val state by engine.playbackState.collectAsStateWithLifecycle()
        val position by engine.positionMs.collectAsStateWithLifecycle()
        val duration by engine.durationMs.collectAsStateWithLifecycle()
        val isSeekable by engine.isSeekable.collectAsStateWithLifecycle()
        val speed by engine.playbackSpeed.collectAsStateWithLifecycle()
        val diagnostics by engine.diagnostics.collectAsStateWithLifecycle()

        // Local UI states
        var isDragging by remember { mutableStateOf(false) }
        var dragPosition by remember { mutableStateOf(0f) }
        var seekSuccessText by remember { mutableStateOf("") }

        // Observe Lifecycle events to pause on background and restore properly
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) {
                    viewModel.wasPlayingBeforeBackground = (state == PlaybackState.Playing)
                    engine.pause()
                } else if (event == Lifecycle.Event.ON_RESUME) {
                    if (viewModel.wasPlayingBeforeBackground) {
                        engine.play()
                        viewModel.wasPlayingBeforeBackground = false
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        val filePicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    Log.w("GalleryPlayer", "Persistable permission not supported for: $uri", e)
                }
                coroutineScope.launch {
                    engine.open(uri)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "GalleryPlayer Phase 0.5 Test Host",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Video Render Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (state == PlaybackState.Idle || state == PlaybackState.Released) {
                    Text(text = "No Media Loaded", color = Color.White)
                } else {
                    AndroidView(
                        factory = { ctx ->
                            val host = viewModel.videoOutputFactory.create(ctx)
                            engine.attachVideoOutput(host)
                            // host.view is returned as a plain android.view.View
                            host.view
                        },
                        modifier = Modifier.fillMaxSize(),
                        onRelease = {
                            engine.detachVideoOutput()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress Slider
            val sliderValue = if (isDragging) dragPosition else position.toFloat()
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = sliderValue,
                    onValueChange = { newValue ->
                        isDragging = true
                        dragPosition = newValue
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        engine.seekTo(dragPosition.toLong())
                        seekSuccessText = "Seeked to ${dragPosition.toLong()} ms"
                    },
                    valueRange = 0f..maxOf(duration.toFloat(), 1f),
                    enabled = isSeekable && duration > 0,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatTime(sliderValue.toLong()))
                    Text(text = formatTime(duration))
                }
                if (seekSuccessText.isNotEmpty()) {
                    Text(
                        text = seekSuccessText,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { filePicker.launch(arrayOf("video/*", "audio/*", "*/*")) }) {
                    Text(text = "Select Document")
                }

                Button(
                    onClick = {
                        if (state == PlaybackState.Playing) engine.pause() else engine.play()
                    },
                    enabled = state == PlaybackState.Playing || state == PlaybackState.Paused
                ) {
                    Text(text = if (state == PlaybackState.Playing) "Pause" else "Play")
                }

                Button(
                    onClick = { engine.stop() },
                    enabled = state == PlaybackState.Playing || state == PlaybackState.Paused || state == PlaybackState.Buffering
                ) {
                    Text(text = "Stop")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Speed & Modes config
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Speed Selector
                var speedExpanded by remember { mutableStateOf(false) }
                Box {
                    Button(onClick = { speedExpanded = true }) {
                        Text(text = "Speed: ${speed}x")
                    }
                    DropdownMenu(
                        expanded = speedExpanded,
                        onDismissRequest = { speedExpanded = false }
                    ) {
                        listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f, 4.0f).forEach { s ->
                            DropdownMenuItem(
                                text = { Text("${s}x") },
                                onClick = {
                                    engine.setSpeed(s)
                                    speedExpanded = false
                                }
                            )
                        }
                    }
                }

                // Decoder Mode Selector
                var decoderExpanded by remember { mutableStateOf(false) }
                Box {
                    Button(onClick = { decoderExpanded = true }) {
                        Text(text = "Decoder: ${diagnostics.decoderMode}")
                    }
                    DropdownMenu(
                        expanded = decoderExpanded,
                        onDismissRequest = { decoderExpanded = false }
                    ) {
                        DecoderMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.name) },
                                onClick = {
                                    engine.setDecoderMode(mode)
                                    decoderExpanded = false
                                }
                            )
                        }
                    }
                }

                // Repeat Mode Selector
                var repeatMode by remember { mutableStateOf(RepeatMode.NONE) }
                var repeatExpanded by remember { mutableStateOf(false) }
                Box {
                    Button(onClick = { repeatExpanded = true }) {
                        Text(text = "Loop: ${repeatMode.name}")
                    }
                    DropdownMenu(
                        expanded = repeatExpanded,
                        onDismissRequest = { repeatExpanded = false }
                    ) {
                        RepeatMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.name) },
                                onClick = {
                                    repeatMode = mode
                                    engine.setRepeatMode(mode)
                                    repeatExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Diagnostics info
            Text(
                text = "Diagnostics Details",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(text = "URI: ${diagnostics.uri}")
                Text(text = "MIME: ${diagnostics.mimeType}")
                Text(text = "Duration: ${diagnostics.durationMs} ms")
                Text(text = "Video Size: ${diagnostics.width} x ${diagnostics.height}")
                Text(text = "Rotation: ${diagnostics.rotation}°")
                Text(text = "Video Tracks: ${diagnostics.videoTracksCount}")
                Text(text = "Audio Tracks: ${diagnostics.audioTracksCount}")
                Text(text = "Sample Rate: ${diagnostics.sampleRate} Hz")
                Text(text = "Channels: ${diagnostics.channels}")
                Text(text = "Last Event: ${diagnostics.libvlcEvent}")
                Text(text = "Last Error: ${diagnostics.lastError}")
                Text(text = "Decoders: best-effort (check VLC logs)")
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
}
