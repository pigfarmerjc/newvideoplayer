package com.pigfarmerjc.galleryplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pigfarmerjc.galleryplayer.core.player.api.PlaybackEngine

@Composable
fun SettingsScreen(
    onReload: () -> Unit,
    mediaRepositoryCount: Int,
    playbackEngine: PlaybackEngine
) {
    var showDebugDialog by remember { mutableStateOf(false) }

    // Memory settings states
    var defaultSpeed by remember { mutableStateOf(1.0f) }
    var skipSeconds by remember { mutableStateOf(10) }

    val diagnostics by playbackEngine.diagnostics.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Reload Media
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            ListItem(
                headlineContent = { Text("Re-scan Media") },
                supportingContent = { Text("Triggers local MediaStore and files scanner refresh") },
                trailingContent = {
                    Button(onClick = onReload) {
                        Text("Scan")
                    }
                }
            )
        }

        // Default playback speed configuration
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            var speedExpanded by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text("Default Playback Speed") },
                supportingContent = { Text("Configures initial multiplier speed") },
                trailingContent = {
                    Box {
                        TextButton(onClick = { speedExpanded = true }) {
                            Text("${defaultSpeed}x")
                        }
                        DropdownMenu(
                            expanded = speedExpanded,
                            onDismissRequest = { speedExpanded = false }
                        ) {
                            listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { speedOption ->
                                DropdownMenuItem(
                                    text = { Text("${speedOption}x") },
                                    onClick = {
                                        defaultSpeed = speedOption
                                        playbackEngine.setSpeed(speedOption)
                                        speedExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }

        // Skip duration configuration
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            var skipExpanded by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text("Double-tap Skip Seconds") },
                supportingContent = { Text("Duration for skip gestures seeking") },
                trailingContent = {
                    Box {
                        TextButton(onClick = { skipExpanded = true }) {
                            Text("${skipSeconds}s")
                        }
                        DropdownMenu(
                            expanded = skipExpanded,
                            onDismissRequest = { skipExpanded = false }
                        ) {
                            listOf(5, 10, 15, 30).forEach { skipOption ->
                                DropdownMenuItem(
                                    text = { Text("${skipOption}s") },
                                    onClick = {
                                        skipSeconds = skipOption
                                        skipExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }

        // About block
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            ListItem(
                headlineContent = { Text("About") },
                supportingContent = { Text("GalleryPlayer Local MVP Player v1.0") }
            )
        }

        // Debug Info entry
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDebugDialog = true },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            ListItem(
                headlineContent = { Text("Diagnostics & Debug Info", color = MaterialTheme.colorScheme.onPrimaryContainer) },
                supportingContent = { Text("Inspect engine statuses and media totals", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)) },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Diagnostics",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            )
        }
    }

    // Diagnostics Dialog
    if (showDebugDialog) {
        AlertDialog(
            onDismissRequest = { showDebugDialog = false },
            title = { Text("Diagnostics Console") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "LibVLC Engine: 3.7.4")
                    Text(text = "Decoder Mode: ${diagnostics.decoderMode}")
                    Text(text = "Active URI: ${diagnostics.uri ?: "None Loaded"}")
                    Text(text = "Mime Type: ${diagnostics.mimeType ?: "Unknown"}")
                    Text(text = "Video Dimensions: ${diagnostics.width}x${diagnostics.height}")
                    Text(text = "Video Rotation: ${diagnostics.rotation}°")
                    Text(text = "Audio Channels: ${diagnostics.channels}")
                    Text(text = "Database Scans: $mediaRepositoryCount records")
                    Text(text = "Permission Status: GRANTED")
                }
            },
            confirmButton = {
                TextButton(onClick = { showDebugDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
