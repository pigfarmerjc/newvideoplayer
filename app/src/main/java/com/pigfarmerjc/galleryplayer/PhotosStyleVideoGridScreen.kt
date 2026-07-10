package com.pigfarmerjc.galleryplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pigfarmerjc.galleryplayer.core.model.MediaType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Data classes for grouped list items
// ─────────────────────────────────────────────────────────────────────────────

sealed class PhotosGridItem {
    data class Header(val label: String, val count: Int) : PhotosGridItem()
    data class VideoItem(val video: LocalMediaItem) : PhotosGridItem()
}

// ─────────────────────────────────────────────────────────────────────────────
// Group builder: group videos by relative date ("今天", "昨天", "本周", "YYYY年MM月", etc.)
// ─────────────────────────────────────────────────────────────────────────────

private fun buildGroupedItems(videos: List<LocalMediaItem>): List<PhotosGridItem> {
    val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val thisWeekStart = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -6) }

    val monthFmt = SimpleDateFormat("yyyy年MM月", Locale.CHINESE)

    data class Group(val key: String, val label: String, val items: MutableList<LocalMediaItem> = mutableListOf())

    val groups = mutableListOf<Group>()
    val groupMap = linkedMapOf<String, Group>()

    for (video in videos) {
        val epochSec = video.dateModifiedEpochSeconds ?: 0L
        val cal = Calendar.getInstance().apply { timeInMillis = epochSec * 1000L }

        val key: String
        val label: String
        when {
            !cal.before(today) -> { key = "today"; label = "今天" }
            !cal.before(yesterday) -> { key = "yesterday"; label = "昨天" }
            !cal.before(thisWeekStart) -> { key = "thisweek"; label = "本周" }
            else -> {
                val monthKey = monthFmt.format(Date(epochSec * 1000L))
                key = monthKey; label = monthKey
            }
        }

        val group = groupMap.getOrPut(key) { Group(key, label).also { groups.add(it) } }
        group.items.add(video)
    }

    val result = mutableListOf<PhotosGridItem>()
    for (group in groups) {
        result.add(PhotosGridItem.Header(group.label, group.items.size))
        group.items.forEach { result.add(PhotosGridItem.VideoItem(it)) }
    }
    return result
}

// ─────────────────────────────────────────────────────────────────────────────
// Main screen composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PhotosStyleVideoGridScreen(
    videos: List<LocalMediaItem>,
    filteredVideos: List<LocalMediaItem>,
    continueWatchingVideos: List<LocalMediaItem>,
    playbackProgressMap: Map<String, Float>,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortMode: VideoSortMode,
    onSortModeChange: (VideoSortMode) -> Unit,
    onVideoClick: (LocalMediaItem, List<LocalMediaItem>) -> Unit,
    gridState: LazyGridState,
    onVideoViewModeChange: (VideoViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var zoomAccumulator by remember { mutableStateOf(1f) }

    // Group filtered videos by date when search is empty; otherwise show flat list with single header
    val groupedItems = remember(filteredVideos, searchQuery) {
        if (filteredVideos.isEmpty()) {
            emptyList()
        } else if (searchQuery.isNotEmpty()) {
            // Flat list with a single "搜索结果" header
            listOf(PhotosGridItem.Header("搜索结果", filteredVideos.size)) +
                filteredVideos.map { PhotosGridItem.VideoItem(it) }
        } else {
            buildGroupedItems(filteredVideos)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(columns) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1f) {
                        zoomAccumulator *= zoom
                        if (zoomAccumulator > 1.08f) {
                            val newCols = PhotosGridState.applyZoomToColumns(columns, 1.10f)
                            if (newCols != columns) {
                                onColumnsChange(newCols)
                                zoomAccumulator = 1f
                            }
                        } else if (zoomAccumulator < 0.92f) {
                            val newCols = PhotosGridState.applyZoomToColumns(columns, 0.90f)
                            if (newCols != columns) {
                                onColumnsChange(newCols)
                                zoomAccumulator = 1f
                            }
                        }
                    }
                }
            }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Toolbar row: title, view toggle, sort ──────────────────────
            item(span = { GridItemSpan(maxLineSpan) }) {
                PhotosGridToolbar(
                    filteredCount = filteredVideos.size,
                    columns = columns,
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    sortMode = sortMode,
                    onSortModeChange = onSortModeChange,
                    onVideoViewModeChange = onVideoViewModeChange
                )
            }

            // ── Continue Watching carousel ─────────────────────────────────
            if (continueWatchingVideos.isNotEmpty() && searchQuery.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.continue_watching),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(
                                items = continueWatchingVideos,
                                key = { it.contentUri }
                            ) { video ->
                                ContinueWatchingCard(
                                    video = video,
                                    progressRatio = playbackProgressMap[video.contentUri],
                                    onClick = { onVideoClick(video, videos) }
                                )
                            }
                        }
                    }
                }
            }

            // ── Empty state ────────────────────────────────────────────────
            if (groupedItems.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_local_videos),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                return@LazyVerticalGrid
            }

            // ── Grouped items: headers span full width, video items are 1 cell ──
            groupedItems.forEach { item ->
                when (item) {
                    is PhotosGridItem.Header -> {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "header_${item.label}") {
                            PhotosDateHeader(label = item.label, count = item.count)
                        }
                    }
                    is PhotosGridItem.VideoItem -> {
                        item(key = item.video.contentUri) {
                            PhotosStyleVideoGridItem(
                                video = item.video,
                                progressRatio = playbackProgressMap[item.video.contentUri],
                                columnCount = columns,
                                onClick = { onVideoClick(item.video, filteredVideos) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Toolbar composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PhotosGridToolbar(
    filteredCount: Int,
    columns: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortMode: VideoSortMode,
    onSortModeChange: (VideoSortMode) -> Unit,
    onVideoViewModeChange: (VideoViewMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.search_videos), style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            textStyle = MaterialTheme.typography.bodyMedium
        )

        // Controls row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title + count
            Text(
                text = "${stringResource(R.string.tab_videos)} ($filteredCount)  •  $columns 列",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Switch to card view
                TextButton(onClick = { onVideoViewModeChange(VideoViewMode.CARD) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    Icon(Icons.Default.List, contentDescription = "List View", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.card_view), style = MaterialTheme.typography.labelSmall)
                }

                // Sort menu
                var sortExpanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { sortExpanded = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                        Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
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
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                    DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                        VideoSortMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Text(when (mode) {
                                        VideoSortMode.DATE_MODIFIED_DESC -> stringResource(R.string.sort_recent)
                                        VideoSortMode.NAME_ASC -> stringResource(R.string.sort_name_asc)
                                        VideoSortMode.NAME_DESC -> stringResource(R.string.sort_name_desc)
                                        VideoSortMode.DURATION_DESC -> stringResource(R.string.sort_duration_desc)
                                        VideoSortMode.DURATION_ASC -> stringResource(R.string.sort_duration_asc)
                                        VideoSortMode.SIZE_DESC -> stringResource(R.string.sort_size_desc)
                                        VideoSortMode.SIZE_ASC -> stringResource(R.string.sort_size_asc)
                                    })
                                },
                                onClick = { onSortModeChange(mode); sortExpanded = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Date section header (Apple Photos style)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PhotosDateHeader(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp
        )
        Text(
            text = "$count 个视频",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Single grid cell (Apple Photos style)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PhotosStyleVideoGridItem(
    video: LocalMediaItem,
    progressRatio: Float?,
    columnCount: Int,
    onClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val density = LocalDensity.current
    val targetSizePx = remember(columnCount, screenWidth) {
        with(density) { (screenWidth / columnCount).toPx().roundToInt() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        // Thumbnail fills the entire cell
        MediaThumbnail(
            contentUri = video.contentUri,
            mediaType = MediaType.VIDEO,
            modifier = Modifier.fillMaxSize(),
            width = maxOf(targetSizePx, 80),
            height = maxOf(targetSizePx, 80)
        )

        // Duration badge – bottom right
        if (video.durationMs != null && video.durationMs > 0L) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .background(Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = formatPhotosGridDuration(video.durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = if (columnCount >= 8) 8.sp else 10.sp
                )
            }
        }

        // Resolution badge – top left (only shown when columns <= 6)
        if (columnCount <= 6 && video.width != null && video.height != null) {
            val res = when {
                video.height!! >= 2160 -> "4K"
                video.height!! >= 1080 -> "HD"
                else -> null
            }
            if (res != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), shape = RoundedCornerShape(3.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = res,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Progress bar at bottom edge (2dp height)
        if (progressRatio != null && progressRatio in 0.01f..0.99f) {
            LinearProgressIndicator(
                progress = { progressRatio },
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun formatPhotosGridDuration(ms: Long): String {
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, mins, secs)
    } else {
        String.format("%d:%02d", mins, secs)
    }
}
