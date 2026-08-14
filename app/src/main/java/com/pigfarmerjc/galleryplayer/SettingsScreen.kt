package com.pigfarmerjc.galleryplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pigfarmerjc.galleryplayer.core.player.api.DecoderMode
import com.pigfarmerjc.galleryplayer.core.player.api.PlaybackEngine

@Composable
fun SettingsScreen(
    onReload: () -> Unit,
    mediaRepositoryCount: Int,
    playbackEngine: PlaybackEngine,
    defaultSpeed: Float,
    skipSeconds: Int,
    onDefaultSpeedChange: (Float) -> Unit,
    onSkipSecondsChange: (Int) -> Unit,
    lastRefreshDurationMs: Long,
    mediaStoreVolumes: List<String>,
    safAuthorizedFolders: List<String>,
    lastPlayedUri: String,
    lastPlayedTitle: String,
    lastPlayedSize: Long,
    decoderModeState: DecoderMode,
    onDecoderModeChange: (DecoderMode) -> Unit,
    onAddSafFolder: (String) -> Unit,
    onRemoveSafFolder: (String) -> Unit,
    videosCount: Int,
    imagesCount: Int,
    foldersCount: Int
) {
    var showDebugDialog by remember { mutableStateOf(false) }
    val diagnostics by playbackEngine.diagnostics.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val safPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            onAddSafFolder(uri.toString())
            onReload()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Reload Media
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            ListItem(
                headlineContent = { Text("重新扫描媒体") },
                supportingContent = { Text("刷新设备存储和已授权目录中的媒体") },
                trailingContent = {
                    Button(onClick = onReload) {
                        Text("扫描")
                    }
                }
            )
        }

        // Playback Decoder Mode
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            var decExpanded by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text("视频解码模式") },
                supportingContent = { Text("遇到 4K 或特殊编码卡顿时可切换") },
                trailingContent = {
                    Box {
                        TextButton(onClick = { decExpanded = true }) {
                            Text(decoderModeLabel(decoderModeState))
                        }
                        DropdownMenu(
                            expanded = decExpanded,
                            onDismissRequest = { decExpanded = false }
                        ) {
                            DecoderMode.values().forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(decoderModeLabel(mode)) },
                                    onClick = {
                                        onDecoderModeChange(mode)
                                        decExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }

        // SAF authorized folders config
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("外部存储目录", style = MaterialTheme.typography.titleMedium)
                        Text("添加 TF 卡或自定义目录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { safPickerLauncher.launch(null) }) {
                        Text("添加目录")
                    }
                }
                if (safAuthorizedFolders.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    safAuthorizedFolders.forEach { folderUri ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = folderUri,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            IconButton(onClick = { 
                                onRemoveSafFolder(folderUri)
                                onReload()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "移除")
                            }
                        }
                    }
                }
            }
        }

        // Default playback speed configuration
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            var speedExpanded by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text("默认播放速度") },
                supportingContent = { Text("新视频开始播放时使用的速度") },
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
                                        onDefaultSpeedChange(speedOption)
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
                headlineContent = { Text("双击快进与快退") },
                supportingContent = { Text("播放器双击手势的跳转秒数") },
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
                                        onSkipSecondsChange(skipOption)
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
                headlineContent = { Text("关于") },
                supportingContent = { Text("GalleryPlayer 本地视频播放器 1.0") }
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
                headlineContent = { Text("播放诊断", color = MaterialTheme.colorScheme.onPrimaryContainer) },
                supportingContent = { Text("查看解码器、媒体统计和错误信息", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)) },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "播放诊断",
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
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "App version: 1.0 (Debug readiness pack)", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "LibVLC Engine Version: 3.7.4", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Device Model: ${android.os.Build.MODEL} (${android.os.Build.BRAND})", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Android OS: Release ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})", style = MaterialTheme.typography.bodyMedium)
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(text = "Total Videos: $videosCount", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Total Images: $imagesCount", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Total Folders: $foldersCount", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "MediaStore Volumes: ${mediaStoreVolumes.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "SAF Folders: ${safAuthorizedFolders.size} active", style = MaterialTheme.typography.bodyMedium)
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(text = "Last Media Refresh duration: ${lastRefreshDurationMs} ms", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Thumbnail Cache Hits: ${com.pigfarmerjc.galleryplayer.ThumbnailCache.hitCount.get()}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Thumbnail Cache Misses: ${com.pigfarmerjc.galleryplayer.ThumbnailCache.missCount.get()}", style = MaterialTheme.typography.bodyMedium)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(text = "Last Played URI: ${lastPlayedUri.ifEmpty { "None" }}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Last Played Title: ${lastPlayedTitle.ifEmpty { "None" }}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Last Played File Size: ${if (lastPlayedSize > 0) "${lastPlayedSize / (1024 * 1024)} MB" else "0"}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Last Decoder Mode: ${decoderModeState}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Playback State: ${playbackEngine.playbackState.collectAsState().value}", style = MaterialTheme.typography.bodyMedium)
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(text = "Active Track Resolution: ${diagnostics.width}x${diagnostics.height}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Active Audio Sample Rate: ${diagnostics.sampleRate} Hz", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Active Audio Channels: ${diagnostics.channels}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "LibVLC Event: ${diagnostics.libvlcEvent.ifEmpty { "None" }}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Last Playback Error: ${diagnostics.lastError.ifEmpty { "None" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    
                    Text(text = "* PCM S24 LE requires real device verification *", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    Button(
                        onClick = {
                            val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
                                append("Device: ${android.os.Build.BRAND} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})\n")
                                append("App Version: 1.0 (Debug Build)\n")
                                append("Media Stats: $videosCount videos, $imagesCount images, $foldersCount folders\n")
                                append("Volumes: ${mediaStoreVolumes.joinToString(", ")}\n")
                                append("SAF Folders: ${safAuthorizedFolders.joinToString("; ")}\n")
                                append("Cache Hits/Misses: ${com.pigfarmerjc.galleryplayer.ThumbnailCache.hitCount.get()}/${com.pigfarmerjc.galleryplayer.ThumbnailCache.missCount.get()}\n")
                                append("Decoder Mode: $decoderModeState\n")
                                append("Last Playback Title: $lastPlayedTitle\n")
                                append("Last Playback URI: $lastPlayedUri\n")
                                append("Playback Error: ${diagnostics.lastError}\n")
                                append("Playback State: ${playbackEngine.playbackState.value}\n")
                                append("Track Resolution: ${diagnostics.width}x${diagnostics.height}\n")
                                append("Audio: ${diagnostics.sampleRate}Hz / ${diagnostics.channels}ch\n")
                            }
                            clipboardManager.setText(annotatedString)
                            android.widget.Toast.makeText(context, "Diagnostics copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Copy Diagnostics")
                    }
                    TextButton(onClick = { showDebugDialog = false }, modifier = Modifier.weight(1f)) {
                        Text("Close")
                    }
                }
            }
        )
    }
}

private fun decoderModeLabel(mode: DecoderMode): String = when (mode) {
    DecoderMode.AUTO -> "自动"
    DecoderMode.HARDWARE_FORCED -> "强制硬件解码"
    DecoderMode.SOFTWARE_ONLY -> "仅软件解码"
}
