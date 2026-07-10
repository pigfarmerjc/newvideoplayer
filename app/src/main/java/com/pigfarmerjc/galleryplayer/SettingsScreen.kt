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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pigfarmerjc.galleryplayer.core.player.api.DecoderMode
import com.pigfarmerjc.galleryplayer.core.player.api.PlaybackEngine
import kotlin.math.roundToInt

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
    foldersCount: Int,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    videoViewMode: VideoViewMode,
    onVideoViewModeChange: (VideoViewMode) -> Unit,
    photosGridColumns: Int,
    onPhotosGridColumnsChange: (Int) -> Unit,
    photosGridSpacing: Float,
    onPhotosGridSpacingChange: (Float) -> Unit
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
            text = stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Reload Media
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.rescan_media)) },
                supportingContent = { Text(stringResource(R.string.rescan_media_desc)) },
                trailingContent = {
                    Button(onClick = onReload) {
                        Text(stringResource(R.string.scan))
                    }
                }
            )
        }

        // Appearance Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            var themeExpanded by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(stringResource(R.string.appearance)) },
                supportingContent = {
                    val label = when (themeMode) {
                        AppThemeMode.SYSTEM -> stringResource(R.string.follow_system)
                        AppThemeMode.LIGHT -> stringResource(R.string.light_theme)
                        AppThemeMode.DARK -> stringResource(R.string.dark_theme)
                    }
                    Text(label)
                },
                trailingContent = {
                    Box {
                        TextButton(onClick = { themeExpanded = true }) {
                            val label = when (themeMode) {
                                AppThemeMode.SYSTEM -> stringResource(R.string.follow_system)
                                AppThemeMode.LIGHT -> stringResource(R.string.light_theme)
                                AppThemeMode.DARK -> stringResource(R.string.dark_theme)
                            }
                            Text(label)
                        }
                        DropdownMenu(
                            expanded = themeExpanded,
                            onDismissRequest = { themeExpanded = false }
                        ) {
                            AppThemeMode.values().forEach { mode ->
                                val label = when (mode) {
                                    AppThemeMode.SYSTEM -> stringResource(R.string.follow_system)
                                    AppThemeMode.LIGHT -> stringResource(R.string.light_theme)
                                    AppThemeMode.DARK -> stringResource(R.string.dark_theme)
                                }
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        onThemeModeChange(mode)
                                        themeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }

        // Video View Settings Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                // View Mode setting
                var viewModeExpanded by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.default_video_view_mode)) },
                    supportingContent = {
                        val label = when (videoViewMode) {
                            VideoViewMode.CARD -> stringResource(R.string.card_view)
                            VideoViewMode.PHOTOS_GRID -> stringResource(R.string.photos_grid)
                        }
                        Text(label)
                    },
                    trailingContent = {
                        Box {
                            TextButton(onClick = { viewModeExpanded = true }) {
                                val label = when (videoViewMode) {
                                    VideoViewMode.CARD -> stringResource(R.string.card_view)
                                    VideoViewMode.PHOTOS_GRID -> stringResource(R.string.photos_grid)
                                }
                                Text(label)
                            }
                            DropdownMenu(
                                expanded = viewModeExpanded,
                                onDismissRequest = { viewModeExpanded = false }
                            ) {
                                VideoViewMode.values().forEach { mode ->
                                    val label = when (mode) {
                                        VideoViewMode.CARD -> stringResource(R.string.card_view)
                                        VideoViewMode.PHOTOS_GRID -> stringResource(R.string.photos_grid)
                                    }
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            onVideoViewModeChange(mode)
                                            viewModeExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Photos Grid Columns slider setting
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.photos_grid_columns_setting),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "$photosGridColumns",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = stringResource(R.string.photos_grid_columns_setting_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = photosGridColumns.toFloat(),
                        onValueChange = { onPhotosGridColumnsChange(it.roundToInt()) },
                        valueRange = 2f..12f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    var spacingExpanded by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.photos_grid_spacing)) },
                        supportingContent = { Text(stringResource(R.string.photos_grid_spacing_desc)) },
                        trailingContent = {
                            Box {
                                TextButton(onClick = { spacingExpanded = true }) {
                                    val label = when (photosGridSpacing) {
                                        1f -> stringResource(R.string.spacing_1dp)
                                        2f -> stringResource(R.string.spacing_2dp)
                                        4f -> stringResource(R.string.spacing_4dp)
                                        else -> "${photosGridSpacing.toInt()}dp"
                                    }
                                    Text(label)
                                }
                                DropdownMenu(
                                    expanded = spacingExpanded,
                                    onDismissRequest = { spacingExpanded = false }
                                ) {
                                    listOf(1f, 2f, 4f).forEach { value ->
                                        val label = when (value) {
                                            1f -> stringResource(R.string.spacing_1dp)
                                            2f -> stringResource(R.string.spacing_2dp)
                                            4f -> stringResource(R.string.spacing_4dp)
                                            else -> ""
                                        }
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                onPhotosGridSpacingChange(value)
                                                spacingExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }

        // Playback Decoder Mode
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            var decExpanded by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(stringResource(R.string.playback_decoder_mode)) },
                supportingContent = { Text(stringResource(R.string.playback_decoder_mode_desc)) },
                trailingContent = {
                    Box {
                        TextButton(onClick = { decExpanded = true }) {
                            Text(decoderModeState.name)
                        }
                        DropdownMenu(
                            expanded = decExpanded,
                            onDismissRequest = { decExpanded = false }
                        ) {
                            DecoderMode.values().forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.name) },
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.external_saf_folders), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.add_tf_card_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { safPickerLauncher.launch(null) }) {
                        Text(stringResource(R.string.add_folder))
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
                                Icon(Icons.Default.Delete, contentDescription = "Remove")
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
                headlineContent = { Text(stringResource(R.string.default_speed)) },
                supportingContent = { Text(stringResource(R.string.default_speed_desc)) },
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
                headlineContent = { Text(stringResource(R.string.skip_seconds)) },
                supportingContent = { Text(stringResource(R.string.skip_seconds_desc)) },
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
                headlineContent = { Text(stringResource(R.string.about)) },
                supportingContent = { Text(stringResource(R.string.about_desc)) }
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
                headlineContent = { Text(stringResource(R.string.diagnostics), color = MaterialTheme.colorScheme.onPrimaryContainer) },
                supportingContent = { Text(stringResource(R.string.diagnostics_desc), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)) },
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
            title = { Text(stringResource(R.string.diagnostics_console)) },
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
                    Text(text = "Active Track Resolution: ${diagnostics.width}x${diagnostics.height} (Rot: ${diagnostics.rotation})", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "playerRoot Size: ${diagnostics.playerRootWidth}x${diagnostics.playerRootHeight}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "androidView Size: ${diagnostics.androidViewWidth}x${diagnostics.androidViewHeight}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "videoHost Size: ${diagnostics.videoHostWidth}x${diagnostics.videoHostHeight}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "vlcVideoLayout Size: ${diagnostics.vlcVideoLayoutWidth}x${diagnostics.vlcVideoLayoutHeight}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Surface Attached: ${diagnostics.surfaceAttached}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Scale Mode: ${diagnostics.scaleMode}", style = MaterialTheme.typography.bodyMedium)
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
                                append("Track Resolution: ${diagnostics.width}x${diagnostics.height} (Rot: ${diagnostics.rotation})\n")
                                append("playerRoot Size: ${diagnostics.playerRootWidth}x${diagnostics.playerRootHeight}\n")
                                append("androidView Size: ${diagnostics.androidViewWidth}x${diagnostics.androidViewHeight}\n")
                                append("videoHost Size: ${diagnostics.videoHostWidth}x${diagnostics.videoHostHeight}\n")
                                append("vlcVideoLayout Size: ${diagnostics.vlcVideoLayoutWidth}x${diagnostics.vlcVideoLayoutHeight}\n")
                                append("Surface Attached: ${diagnostics.surfaceAttached}\n")
                                append("Scale Mode: ${diagnostics.scaleMode}\n")
                                append("Audio: ${diagnostics.sampleRate}Hz / ${diagnostics.channels}ch\n")
                            }
                            clipboardManager.setText(annotatedString)
                            android.widget.Toast.makeText(context, "Diagnostics copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.copy_diagnostics))
                    }
                    TextButton(onClick = { showDebugDialog = false }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        )
    }
}
