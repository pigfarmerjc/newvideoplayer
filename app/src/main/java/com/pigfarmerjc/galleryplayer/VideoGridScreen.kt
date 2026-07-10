package com.pigfarmerjc.galleryplayer

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pigfarmerjc.galleryplayer.core.model.MediaType

@Composable
fun VideoGridScreen(
    videos: List<LocalMediaItem>,
    onVideoClick: (LocalMediaItem, List<LocalMediaItem>) -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean,
    loadError: String?,
    playbackProgressMap: Map<String, Float> = emptyMap(),
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortMode: VideoSortMode,
    onSortModeChange: (VideoSortMode) -> Unit,
    // Continue Watching removed — use playbackProgressMap only for progress bars
    videoViewMode: VideoViewMode,
    onVideoViewModeChange: (VideoViewMode) -> Unit,
    photosGridColumns: Int,
    onPhotosGridColumnsChange: (Int) -> Unit,
    photosGapDp: Dp = 1.dp,
    // Externally managed grid states for scroll position preservation
    cardGridState: LazyGridState,
    photosGridState: LazyGridState,
    // Transition state for bounds tracking and shared element animation
    transitionState: GalleryTransitionState
) {
    val context = LocalContext.current
    val hasPermission = PermissionState.hasVideoPermission(context)

    if (!hasPermission) {
        InlinePermissionRequest(permissionType = "video", onGranted = onRefresh)
        return
    }

    val filteredVideos = remember(videos, searchQuery, sortMode) {
        VideoFilterAndSort.filterAndSort(videos, searchQuery, sortMode)
    }

    if (isLoading && videos.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.loading_videos), style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    if (loadError != null && videos.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.failed_to_load_videos),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = loadError,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.retry))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.retry))
                }
            }
        }
        return
    }

    if (videoViewMode == VideoViewMode.PHOTOS_GRID) {
        PhotosStyleVideoGridScreen(
            videos = videos,
            filteredVideos = filteredVideos,
            playbackProgressMap = playbackProgressMap,
            columns = photosGridColumns,
            onColumnsChange = onPhotosGridColumnsChange,
            gapDp = photosGapDp,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            sortMode = sortMode,
            onSortModeChange = onSortModeChange,
            onVideoClick = onVideoClick,
            gridState = photosGridState,
            onVideoViewModeChange = onVideoViewModeChange,
            transitionState = transitionState
        )
        return
    }

    // ── Card View ──────────────────────────────────────────────────────────

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenWidth = configuration.screenWidthDp

    val columns = when {
        screenWidth >= 600 -> if (isLandscape) 6 else 4
        else -> if (isLandscape) 3 else 2
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = cardGridState,
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            // Header: Search & Sort
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        placeholder = { Text(stringResource(R.string.search_videos)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_videos)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${stringResource(R.string.tab_videos)} (${filteredVideos.size})",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onVideoViewModeChange(VideoViewMode.PHOTOS_GRID) }) {
                                Icon(Icons.Default.GridView, contentDescription = "View Mode")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.photos_grid))
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Box {
                                var sortExpanded by remember { mutableStateOf(false) }
                                TextButton(onClick = { sortExpanded = true }) {
                                    Icon(Icons.Default.Sort, contentDescription = stringResource(R.string.sort))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    val label = when (sortMode) {
                                        VideoSortMode.DATE_MODIFIED_DESC -> stringResource(R.string.sort_recent)
                                        VideoSortMode.NAME_ASC -> stringResource(R.string.sort_name_asc)
                                        VideoSortMode.NAME_DESC -> stringResource(R.string.sort_name_desc)
                                        VideoSortMode.DURATION_DESC -> stringResource(R.string.sort_duration_desc)
                                        VideoSortMode.DURATION_ASC -> stringResource(R.string.sort_duration_asc)
                                        VideoSortMode.SIZE_DESC -> stringResource(R.string.sort_size_desc)
                                        VideoSortMode.SIZE_ASC -> stringResource(R.string.sort_size_asc)
                                    }
                                    Text(label)
                                }
                                DropdownMenu(
                                    expanded = sortExpanded,
                                    onDismissRequest = { sortExpanded = false }
                                ) {
                                    VideoSortMode.values().forEach { mode ->
                                        DropdownMenuItem(
                                            text = {
                                                val text = when (mode) {
                                                    VideoSortMode.DATE_MODIFIED_DESC -> stringResource(R.string.sort_recent)
                                                    VideoSortMode.NAME_ASC -> stringResource(R.string.sort_name_asc)
                                                    VideoSortMode.NAME_DESC -> stringResource(R.string.sort_name_desc)
                                                    VideoSortMode.DURATION_DESC -> stringResource(R.string.sort_duration_desc)
                                                    VideoSortMode.DURATION_ASC -> stringResource(R.string.sort_duration_asc)
                                                    VideoSortMode.SIZE_DESC -> stringResource(R.string.sort_size_desc)
                                                    VideoSortMode.SIZE_ASC -> stringResource(R.string.sort_size_asc)
                                                }
                                                Text(text)
                                            },
                                            onClick = {
                                                onSortModeChange(mode)
                                                sortExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Empty state
            if (filteredVideos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_local_videos),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(
                    items = filteredVideos,
                    key = { it.contentUri }
                ) { video ->
                    VideoCard(
                        video = video,
                        progressRatio = playbackProgressMap[video.contentUri],
                        onClick = { onVideoClick(video, filteredVideos) }
                    )
                }
            }
        }
    }
}

@Composable
fun VideoCard(
    video: LocalMediaItem,
    progressRatio: Float?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            ) {
                MediaThumbnail(
                    contentUri = video.contentUri,
                    mediaType = MediaType.VIDEO,
                    modifier = Modifier.fillMaxSize(),
                    width = 320,
                    height = 180
                )
                
                if (video.durationMs != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = formatDuration(video.durationMs),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                // Progress bar (kept for resume UX, no Continue Watching section)
                if (progressRatio != null && progressRatio in 0.01f..0.99f) {
                    LinearProgressIndicator(
                        progress = { progressRatio },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = video.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val folderName = video.relativePath.trimEnd('/').split('/').lastOrNull()?.takeIf { it.isNotEmpty() } ?: "Root"
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = folderName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    if (video.width != null && video.height != null) {
                        Text(
                            text = "${video.width}x${video.height}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
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
