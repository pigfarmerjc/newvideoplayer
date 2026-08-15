package com.pigfarmerjc.galleryplayer

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pigfarmerjc.galleryplayer.core.model.MediaType
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

sealed class PhotosGridItem {
    data class Header(val label: String, val count: Int) : PhotosGridItem()
    data class Video(val item: LocalMediaItem) : PhotosGridItem()
}

@Composable
fun VideoGridScreen(
    videos: List<LocalMediaItem>,
    onVideoClick: (LocalMediaItem, List<LocalMediaItem>) -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean,
    loadError: String?,
    playbackProgressMap: Map<String, Float> = emptyMap(),
    sortMode: VideoSortMode,
    onSortModeChange: (VideoSortMode) -> Unit,
    continueWatchingVideos: List<LocalMediaItem> = emptyList(),
    gridState: LazyGridState = rememberLazyGridState(),
    persistedColumnCount: Int = 0,
    onColumnCountChange: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    if (!PermissionState.hasVideoPermission(context)) {
        InlinePermissionRequest(permissionType = "video", onGranted = onRefresh)
        return
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredVideos by remember(videos, searchQuery, sortMode) {
        derivedStateOf { VideoFilterAndSort.filterAndSort(videos, searchQuery, sortMode) }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val defaultCols = if (persistedColumnCount in 2..12) persistedColumnCount else if (configuration.screenWidthDp >= 840) 6 else if (isLandscape) 5 else 4

    var baseColumns by rememberSaveable(persistedColumnCount) { mutableIntStateOf(defaultCols) }
    var columnScaleFactor by remember { mutableFloatStateOf(1f) }
    val currentColumns = (baseColumns / columnScaleFactor).roundToInt().coerceIn(2, 12)

    if (isLoading && videos.isEmpty()) {
        GalleryLoadingState()
        return
    }
    if (loadError != null && videos.isEmpty()) {
        GalleryErrorState(message = loadError, onRetry = onRefresh)
        return
    }

    // Date grouping for default chronological sort
    val gridItems by remember(filteredVideos, sortMode) {
        derivedStateOf {
            if (sortMode == VideoSortMode.DATE_MODIFIED_DESC) {
                buildDateGroupedItems(filteredVideos)
            } else {
                filteredVideos.map { PhotosGridItem.Video(it) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // Apple Photos Top Toolbar
        PhotosTopToolbar(
            totalCount = filteredVideos.size,
            columnCount = currentColumns,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            sortMode = sortMode,
            onSortModeChange = onSortModeChange
        )

        // Apple Photos Dense Grid with Pinch-to-Zoom Gesture
        LazyVerticalGrid(
            columns = GridCells.Fixed(currentColumns),
            state = gridState,
            contentPadding = PaddingValues(start = 1.dp, top = 2.dp, end = 1.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(1.5.dp),
            verticalArrangement = Arrangement.spacedBy(1.5.dp),
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        var zoom = 1f
                        var pastTouchSlop = false
                        val touchSlop = viewConfiguration.touchSlop

                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val canceled = event.changes.any { it.isConsumed }
                            if (!canceled && event.changes.size >= 2) {
                                val zoomChange = event.calculateZoom()
                                if (!pastTouchSlop) {
                                    zoom *= zoomChange
                                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                    val zoomMotion = abs(1f - zoom) * centroidSize
                                    if (zoomMotion > touchSlop) {
                                        pastTouchSlop = true
                                    }
                                }
                                if (pastTouchSlop) {
                                    if (zoomChange != 1f) {
                                        columnScaleFactor = (columnScaleFactor * zoomChange).coerceIn(0.25f, 4.0f)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        } while (!canceled && event.changes.any { it.pressed })

                        if (pastTouchSlop) {
                            val targetCols = (baseColumns / columnScaleFactor).roundToInt().coerceIn(2, 12)
                            baseColumns = targetCols
                            columnScaleFactor = 1f
                            onColumnCountChange(targetCols)
                        }
                    }
                }
        ) {
            if (filteredVideos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    GalleryEmptyState(onRefresh = onRefresh)
                }
            } else {
                items(
                    items = gridItems,
                    key = {
                        when (it) {
                            is PhotosGridItem.Header -> "hdr_${it.label}"
                            is PhotosGridItem.Video -> it.item.contentUri
                        }
                    },
                    span = { item ->
                        when (item) {
                            is PhotosGridItem.Header -> GridItemSpan(maxLineSpan)
                            is PhotosGridItem.Video -> GridItemSpan(1)
                        }
                    }
                ) { item ->
                    when (item) {
                        is PhotosGridItem.Header -> {
                            PhotosDateHeader(label = item.label, count = item.count)
                        }
                        is PhotosGridItem.Video -> {
                            PhotosCell(
                                video = item.item,
                                progressRatio = playbackProgressMap[item.item.contentUri],
                                columnCount = currentColumns,
                                onClick = { onVideoClick(item.item, filteredVideos) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotosTopToolbar(
    totalCount: Int,
    columnCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortMode: VideoSortMode,
    onSortModeChange: (VideoSortMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "全部视频 ($totalCount)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${columnCount}列",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                PhotosSortMenu(sortMode = sortMode, onSortModeChange = onSortModeChange)
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            placeholder = { Text("搜索视频", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                focusedBorderColor = MaterialTheme.colorScheme.primary
            ),
            textStyle = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PhotosSortMenu(
    sortMode: VideoSortMode,
    onSortModeChange: (VideoSortMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                contentDescription = "排序",
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(sortLabel(sortMode), style = MaterialTheme.typography.labelMedium)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            VideoSortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(sortLabel(mode)) },
                    onClick = {
                        onSortModeChange(mode)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun sortLabel(mode: VideoSortMode): String = when (mode) {
    VideoSortMode.DATE_MODIFIED_DESC -> "最近更新"
    VideoSortMode.NAME_ASC -> "名称 A–Z"
    VideoSortMode.NAME_DESC -> "名称 Z–A"
    VideoSortMode.DURATION_DESC -> "时长最长"
    VideoSortMode.DURATION_ASC -> "时长最短"
    VideoSortMode.SIZE_DESC -> "文件最大"
    VideoSortMode.SIZE_ASC -> "文件最小"
}

@Composable
private fun PhotosDateHeader(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "$count 个视频",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PhotosCell(
    video: LocalMediaItem,
    progressRatio: Float?,
    columnCount: Int,
    onClick: () -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val density = LocalDensity.current
    val thumbnailSizePx = remember(columnCount, screenWidth) {
        with(density) { (screenWidth / columnCount.coerceAtLeast(1)).toPx().roundToInt() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        MediaThumbnail(
            contentUri = video.contentUri,
            mediaType = MediaType.VIDEO,
            modifier = Modifier.fillMaxSize(),
            width = thumbnailSizePx.coerceAtLeast(60),
            height = thumbnailSizePx.coerceAtLeast(60),
            contentScale = ContentScale.Crop
        )

        // Apple Photos style: NO duration badge, NO filename overlay
        // Only a slim progress indicator if previously watched
        progressRatio?.takeIf { it in 0.01f..0.99f }?.let { progress ->
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Black.copy(alpha = 0.35f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.5.dp)
            )
        }
    }
}

private fun buildDateGroupedItems(videos: List<LocalMediaItem>): List<PhotosGridItem> {
    if (videos.isEmpty()) return emptyList()
    val calendar = Calendar.getInstance()
    val curCalendar = Calendar.getInstance()
    val curYear = curCalendar.get(Calendar.YEAR)
    val curDayOfYear = curCalendar.get(Calendar.DAY_OF_YEAR)

    val grouped = linkedMapOf<String, MutableList<LocalMediaItem>>()

    for (video in videos) {
        val epochSeconds = video.dateModifiedEpochSeconds ?: 0L
        val dateMs = if (epochSeconds > 0L) epochSeconds * 1000L else 0L
        val label = if (dateMs <= 0L) {
            "其他"
        } else {
            calendar.timeInMillis = dateMs
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            if (curYear == year && curDayOfYear == calendar.get(Calendar.DAY_OF_YEAR)) {
                "今天"
            } else if (curYear == year && curDayOfYear - calendar.get(Calendar.DAY_OF_YEAR) == 1) {
                "昨天"
            } else if (curYear == year) {
                "${month}月${day}日"
            } else {
                "${year}年${month}月"
            }
        }
        grouped.getOrPut(label) { mutableListOf() }.add(video)
    }

    val result = mutableListOf<PhotosGridItem>()
    for ((label, list) in grouped) {
        result.add(PhotosGridItem.Header(label, list.size))
        for (video in list) {
            result.add(PhotosGridItem.Video(video))
        }
    }
    return result
}

@Composable
private fun GalleryLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("正在整理视频…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GalleryErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("视频暂时无法读取", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("重试")
            }
        }
    }
}

@Composable
private fun GalleryEmptyState(onRefresh: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 72.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text("还没有找到视频", style = MaterialTheme.typography.titleMedium)
            Text("请确认是否已授权或设备中存在本地视频", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRefresh) { Text("重新扫描") }
        }
    }
}
