package com.pigfarmerjc.galleryplayer

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
    sortMode: VideoSortMode,
    onSortModeChange: (VideoSortMode) -> Unit,
    continueWatchingVideos: List<LocalMediaItem>,
    gridState: LazyGridState = rememberLazyGridState()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    if (!PermissionState.hasVideoPermission(context)) {
        InlinePermissionRequest(permissionType = "video", onGranted = onRefresh)
        return
    }

    val filteredVideos by remember(videos, sortMode) {
        derivedStateOf { VideoFilterAndSort.filterAndSort(videos, "", sortMode) }
    }
    val configuration = LocalConfiguration.current
    val columns = GalleryLayout.columnsForWidth(
        configuration.screenWidthDp,
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    )
    if (isLoading && videos.isEmpty()) {
        GalleryLoadingState()
        return
    }
    if (loadError != null && videos.isEmpty()) {
        GalleryErrorState(message = loadError, onRetry = onRefresh)
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                VideoLibraryHeader(
                    count = filteredVideos.size,
                    sortMode = sortMode,
                    onSortModeChange = onSortModeChange
                )
            }

            if (continueWatchingVideos.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ContinueWatchingSection(
                        videos = continueWatchingVideos,
                        playbackProgressMap = playbackProgressMap,
                        allVideos = filteredVideos,
                        onVideoClick = onVideoClick
                    )
                }
            }

            if (filteredVideos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    GalleryEmptyState(onRefresh = onRefresh)
                }
            } else {
                items(
                    items = filteredVideos,
                    key = { it.contentUri },
                    contentType = { "video-card" }
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
private fun VideoLibraryHeader(
    count: Int,
    sortMode: VideoSortMode,
    onSortModeChange: (VideoSortMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("视频库", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = if (count == 0) "还没有视频" else "$count 个视频 · 最近更新",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SortMenu(sortMode = sortMode, onSortModeChange = onSortModeChange)
        }
    }
}

@Composable
private fun SortMenu(sortMode: VideoSortMode, onSortModeChange: (VideoSortMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Sort, contentDescription = "排序", modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(sortLabel(sortMode))
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
private fun ContinueWatchingSection(
    videos: List<LocalMediaItem>,
    playbackProgressMap: Map<String, Float>,
    allVideos: List<LocalMediaItem>,
    onVideoClick: (LocalMediaItem, List<LocalMediaItem>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("继续观看", style = MaterialTheme.typography.titleLarge)
            Text("最近播放", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            items(videos, key = { it.contentUri }) { video ->
                ContinueWatchingCard(
                    video = video,
                    progressRatio = playbackProgressMap[video.contentUri],
                    onClick = { onVideoClick(video, allVideos) }
                )
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(video: LocalMediaItem, progressRatio: Float?, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(210.dp)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            MediaThumbnail(video.contentUri, MediaType.VIDEO, Modifier.fillMaxSize(), 420, 236)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                        )
                    )
            )
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "继续播放",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(38.dp)
            )
            Text(
                text = video.displayName,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            )
            if (progressRatio != null) {
                LinearProgressIndicator(
                    progress = { progressRatio.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.28f)
                )
            }
        }
    }
}

@Composable
fun VideoCard(video: LocalMediaItem, progressRatio: Float?, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.Button }
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                MediaThumbnail(video.contentUri, MediaType.VIDEO, Modifier.fillMaxSize(), 420, 236)
                if (video.durationMs != null) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(7.dp)
                    ) {
                        Text(
                            text = formatDuration(video.durationMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
                if (progressRatio != null) {
                    LinearProgressIndicator(
                        progress = { progressRatio.coerceIn(0f, 1f) },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.22f)
                    )
                }
            }
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    video.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = video.relativePath.trimEnd('/').substringAfterLast('/').ifBlank { "根目录" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (video.width != null && video.height != null) {
                        Text("${video.width}×${video.height}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
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
            Text("视频库暂时无法打开", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("重新扫描")
            }
        }
    }
}

@Composable
private fun GalleryEmptyState(onRefresh: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 72.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
            Text("还没有找到视频", style = MaterialTheme.typography.titleMedium)
            Text("把视频放入设备后重新扫描", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRefresh) { Text("重新扫描") }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSecs = ms.coerceAtLeast(0L) / 1000
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return if (hours > 0) "%02d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
