package com.pigfarmerjc.galleryplayer

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pigfarmerjc.galleryplayer.core.model.MediaType

@Composable
fun FolderScreen(
    folders: List<FolderItem>,
    onFolderClick: (FolderItem) -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean,
    loadError: String?,
    sortMode: FolderSortMode,
    onSortModeChange: (FolderSortMode) -> Unit,
    videos: List<LocalMediaItem>
) {
    val context = LocalContext.current
    val hasPermission = PermissionState.hasVideoPermission(context)

    if (!hasPermission) {
        InlinePermissionRequest(permissionType = "video", onGranted = onRefresh)
        return
    }

    val sortedFolders = remember(folders, sortMode, videos) {
        FolderSort.sort(folders, sortMode, videos)
    }

    if (isLoading && folders.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator()
                Text("Loading local folders...", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    if (loadError != null && folders.isEmpty()) {
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
                    text = "Failed to load folders",
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
                    Icon(Icons.Default.Refresh, contentDescription = "Retry")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Retry")
                }
            }
        }
        return
    }

    if (folders.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "No folders found",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Add videos in subdirectories and refresh",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Refresh")
                }
            }
        }
    } else {
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

            // Sort Selector Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Folders (${sortedFolders.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Box {
                    var sortExpanded by remember { mutableStateOf(false) }
                    TextButton(onClick = { sortExpanded = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort")
                        Spacer(modifier = Modifier.width(4.dp))
                        val label = when (sortMode) {
                            FolderSortMode.NAME_ASC -> "Name A-Z"
                            FolderSortMode.VIDEO_COUNT_DESC -> "Video Count"
                            FolderSortMode.TOTAL_SIZE_DESC -> "Total Size"
                            FolderSortMode.DATE_MODIFIED_DESC -> "Recent Modified"
                        }
                        Text(label)
                    }
                    DropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = { sortExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Name A-Z") },
                            onClick = {
                                onSortModeChange(FolderSortMode.NAME_ASC)
                                sortExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Video Count") },
                            onClick = {
                                onSortModeChange(FolderSortMode.VIDEO_COUNT_DESC)
                                sortExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Total Size") },
                            onClick = {
                                onSortModeChange(FolderSortMode.TOTAL_SIZE_DESC)
                                sortExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Recent Modified") },
                            onClick = {
                                onSortModeChange(FolderSortMode.DATE_MODIFIED_DESC)
                                sortExpanded = false
                            }
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                items(
                    items = sortedFolders,
                    key = { it.relativePath }
                ) { folder ->
                    FolderCard(
                        folder = folder,
                        onClick = { onFolderClick(folder) }
                    )
                }
            }
        }
    }
}

@Composable
fun FolderCard(
    folder: FolderItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            ) {
                if (folder.coverUri != null) {
                    MediaThumbnail(
                        contentUri = folder.coverUri,
                        mediaType = MediaType.VIDEO,
                        modifier = Modifier.fillMaxSize(),
                        width = 320,
                        height = 200
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = folder.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${folder.videoCount} videos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    val sizeMb = folder.totalSize / (1024L * 1024L)
                    Text(
                        text = "${sizeMb} MB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
