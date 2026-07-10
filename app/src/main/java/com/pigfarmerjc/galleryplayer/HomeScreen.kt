package com.pigfarmerjc.galleryplayer

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    videos: List<LocalMediaItem>,
    images: List<LocalMediaItem>,
    folders: List<FolderItem>,
    onVideoClick: (LocalMediaItem, List<LocalMediaItem>) -> Unit,
    onFolderClick: (FolderItem) -> Unit,
    onImageClick: (LocalMediaItem, List<LocalMediaItem>) -> Unit,
    onReload: () -> Unit,
    mediaRepositoryCount: Int,
    playbackEngine: com.pigfarmerjc.galleryplayer.core.player.api.PlaybackEngine
) {
    var activeTab by remember { mutableStateOf(HomeTab.VIDEOS) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.fillMaxWidth()
            ) {
                NavigationBarItem(
                    selected = activeTab == HomeTab.VIDEOS,
                    onClick = { activeTab = HomeTab.VIDEOS },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Videos") },
                    label = { Text("Videos") }
                )
                NavigationBarItem(
                    selected = activeTab == HomeTab.FOLDERS,
                    onClick = { activeTab = HomeTab.FOLDERS },
                    icon = { Icon(Icons.Default.List, contentDescription = "Folders") },
                    label = { Text("Folders") }
                )
                NavigationBarItem(
                    selected = activeTab == HomeTab.IMAGES,
                    onClick = { activeTab = HomeTab.IMAGES },
                    icon = { Icon(Icons.Default.Face, contentDescription = "Images") },
                    label = { Text("Images") }
                )
                NavigationBarItem(
                    selected = activeTab == HomeTab.SETTINGS,
                    onClick = { activeTab = HomeTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                HomeTab.VIDEOS -> {
                    VideoGridScreen(
                        videos = videos,
                        onVideoClick = onVideoClick,
                        onRefresh = onReload
                    )
                }
                HomeTab.FOLDERS -> {
                    FolderScreen(
                        folders = folders,
                        onFolderClick = onFolderClick,
                        onRefresh = onReload
                    )
                }
                HomeTab.IMAGES -> {
                    ImageGridScreen(
                        images = images,
                        onImageClick = onImageClick,
                        onRefresh = onReload
                    )
                }
                HomeTab.SETTINGS -> {
                    SettingsScreen(
                        onReload = onReload,
                        mediaRepositoryCount = mediaRepositoryCount,
                        playbackEngine = playbackEngine
                    )
                }
            }
        }
    }
}
