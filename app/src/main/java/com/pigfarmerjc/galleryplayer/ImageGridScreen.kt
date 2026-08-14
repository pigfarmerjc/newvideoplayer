package com.pigfarmerjc.galleryplayer

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pigfarmerjc.galleryplayer.core.model.MediaType

@Composable
fun ImageGridScreen(
    images: List<LocalMediaItem>,
    onImageClick: (LocalMediaItem, List<LocalMediaItem>) -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean,
    loadError: String?,
    gridState: LazyGridState = rememberLazyGridState()
) {
    val context = LocalContext.current
    if (!PermissionState.hasImagesPermission(context)) {
        InlinePermissionRequest(permissionType = "image", onGranted = onRefresh)
        return
    }

    if (isLoading && images.isEmpty()) {
        PhotoLoadingState()
        return
    }
    if (loadError != null && images.isEmpty()) {
        PhotoErrorState(message = loadError, onRetry = onRefresh)
        return
    }

    val configuration = LocalConfiguration.current
    val columns = (GalleryLayout.columnsForWidth(
        configuration.screenWidthDp,
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    ) + 1).coerceAtMost(6)

    Column(modifier = Modifier.fillMaxSize()) {
        if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(start = 8.dp, top = 10.dp, end = 8.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
                    Text("图片", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "${images.size} 张本地图片",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (images.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PhotoEmptyState(onRefresh)
                }
            } else {
                items(images, key = { it.contentUri }, contentType = { "photo-tile" }) { image ->
                    ImageCard(image = image, onClick = { onImageClick(image, images) })
                }
            }
        }
    }
}

@Composable
fun ImageCard(image: LocalMediaItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
    ) {
        MediaThumbnail(
            contentUri = image.contentUri,
            mediaType = if (image.isGif) MediaType.GIF else MediaType.IMAGE,
            modifier = Modifier.fillMaxSize(),
            width = 256,
            height = 256
        )
        if (image.isGif) {
            Surface(
                color = Color.Black.copy(alpha = 0.68f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
            ) {
                Text(
                    "GIF",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun PhotoLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("正在整理图片…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PhotoErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("图片暂时无法读取", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
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
private fun PhotoEmptyState(onRefresh: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 72.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
            Text("还没有找到图片", style = MaterialTheme.typography.titleMedium)
            Text("图片会显示在独立入口中", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRefresh) { Text("重新扫描") }
        }
    }
}
