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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pigfarmerjc.galleryplayer.core.model.MediaType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Data classes for grouped grid
// ─────────────────────────────────────────────────────────────────────────────

sealed class PhotosGridItem {
    data class Header(val label: String, val count: Int) : PhotosGridItem()
    data class VideoItem(val video: LocalMediaItem) : PhotosGridItem()
}

// ─────────────────────────────────────────────────────────────────────────────
// Date grouping builder
// ─────────────────────────────────────────────────────────────────────────────

private fun buildGroupedItems(videos: List<LocalMediaItem>): List<PhotosGridItem> {
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val thisWeekStart = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -6) }
    val monthFmt = SimpleDateFormat("yyyy年MM月", Locale.CHINESE)

    data class Group(val key: String, val label: String, val items: MutableList<LocalMediaItem> = mutableListOf())

    val groups = mutableListOf<Group>()
    val groupMap = linkedMapOf<String, Group>()

    for (video in videos) {
        val epochSec = video.dateModifiedEpochSeconds ?: 0L
        val cal = Calendar.getInstance().apply { timeInMillis = epochSec * 1000L }
        val (key, label) = when {
            !cal.before(today) -> "today" to "今天"
            !cal.before(yesterday) -> "yesterday" to "昨天"
            !cal.before(thisWeekStart) -> "thisweek" to "本周"
            else -> {
                val m = monthFmt.format(Date(epochSec * 1000L))
                m to m
            }
        }
        val group = groupMap.getOrPut(key) { Group(key, label).also { groups.add(it) } }
        group.items.add(video)
    }

    return buildList {
        for (group in groups) {
            add(PhotosGridItem.Header(group.label, group.items.size))
            group.items.forEach { add(PhotosGridItem.VideoItem(it)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PhotosStyleVideoGridScreen(
    videos: List<LocalMediaItem>,
    filteredVideos: List<LocalMediaItem>,
    playbackProgressMap: Map<String, Float>,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    gapDp: Dp,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortMode: VideoSortMode,
    onSortModeChange: (VideoSortMode) -> Unit,
    onVideoClick: (LocalMediaItem, List<LocalMediaItem>) -> Unit,
    gridState: LazyGridState,
    onVideoViewModeChange: (VideoViewMode) -> Unit,
    transitionState: GalleryTransitionState,
    modifier: Modifier = Modifier
) {
    var zoomAccumulator by remember { mutableStateOf(1f) }

    val groupedItems = remember(filteredVideos, searchQuery) {
        when {
            filteredVideos.isEmpty() -> emptyList()
            searchQuery.isNotEmpty() ->
                listOf(PhotosGridItem.Header("搜索结果", filteredVideos.size)) +
                    filteredVideos.map { PhotosGridItem.VideoItem(it) }
            else -> buildGroupedItems(filteredVideos)
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
                            if (newCols != columns) { onColumnsChange(newCols); zoomAccumulator = 1f }
                        } else if (zoomAccumulator < 0.92f) {
                            val newCols = PhotosGridState.applyZoomToColumns(columns, 0.90f)
                            if (newCols != columns) { onColumnsChange(newCols); zoomAccumulator = 1f }
                        }
                    }
                }
            }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(gapDp),
            verticalArrangement = Arrangement.spacedBy(gapDp),
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Compact toolbar ──────────────────────────────────────────────
            item(span = { GridItemSpan(maxLineSpan) }) {
                PhotosCompactToolbar(
                    filteredCount = filteredVideos.size,
                    columns = columns,
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    sortMode = sortMode,
                    onSortModeChange = onSortModeChange,
                    onVideoViewModeChange = onVideoViewModeChange
                )
            }

            // ── Empty state ──────────────────────────────────────────────────
            if (groupedItems.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(64.dp),
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

            // ── Grouped items ────────────────────────────────────────────────
            groupedItems.forEach { item ->
                when (item) {
                    is PhotosGridItem.Header -> {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "header_${item.label}") {
                            ApplePhotosDateHeader(label = item.label)
                        }
                    }
                    is PhotosGridItem.VideoItem -> {
                        item(key = item.video.contentUri) {
                            PhotosCell(
                                video = item.video,
                                progressRatio = playbackProgressMap[item.video.contentUri],
                                columnCount = columns,
                                transitionState = transitionState,
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
// Compact toolbar — Apple Photos style (very slim)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PhotosCompactToolbar(
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
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.search_videos), style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            textStyle = MaterialTheme.typography.bodySmall
        )

        // Controls row — very compact
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$filteredCount 个视频 · $columns 列",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onVideoViewModeChange(VideoViewMode.CARD) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(stringResource(R.string.card_view), style = MaterialTheme.typography.labelSmall)
                }

                var sortExpanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(
                        onClick = { sortExpanded = true },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
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
// Date section header — Apple Photos: just a small left-aligned label
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ApplePhotosDateHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 6.dp, bottom = 2.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Single grid cell — Apple Photos: thumbnail only, tiny duration badge, progress bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PhotosCell(
    video: LocalMediaItem,
    progressRatio: Float?,
    columnCount: Int,
    transitionState: GalleryTransitionState,
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
            // Track this cell's screen-space position for transition animation
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                transitionState.updateBounds(
                    MediaItemBounds(
                        contentUri = video.contentUri,
                        left = pos.x,
                        top = pos.y,
                        width = coords.size.width.toFloat(),
                        height = coords.size.height.toFloat()
                    )
                )
            }
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
    ) {
        // Thumbnail fills the entire cell — no margins, no rounded corners
        MediaThumbnail(
            contentUri = video.contentUri,
            mediaType = MediaType.VIDEO,
            modifier = Modifier.fillMaxSize(),
            width = maxOf(targetSizePx, 60),
            height = maxOf(targetSizePx, 60)
        )

        // Duration badge — bottom right, very small
        if (video.durationMs != null && video.durationMs > 0L) {
            Text(
                text = formatCellDuration(video.durationMs),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontSize = if (columnCount >= 9) 7.sp else if (columnCount >= 6) 9.sp else 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .background(Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(2.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            )
        }

        // Progress bar at very bottom edge — 2dp
        if (progressRatio != null && progressRatio in 0.01f..0.99f) {
            LinearProgressIndicator(
                progress = { progressRatio },
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Black.copy(alpha = 0.3f),
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

private fun formatCellDuration(ms: Long): String {
    val totalSecs = ms / 1000
    val h = totalSecs / 3600
    val m = (totalSecs % 3600) / 60
    val s = totalSecs % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%d:%02d", m, s)
}
