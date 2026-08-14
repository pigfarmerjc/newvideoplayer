package com.pigfarmerjc.galleryplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pigfarmerjc.galleryplayer.core.player.api.DecoderMode
import com.pigfarmerjc.galleryplayer.core.player.api.PlaybackEngine

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
    playbackEngine: PlaybackEngine,
    isLoadingMedia: Boolean,
    mediaLoadError: String?,
    playbackProgressMap: Map<String, Float>,
    defaultSpeed: Float,
    skipSeconds: Int,
    onDefaultSpeedChange: (Float) -> Unit,
    onSkipSecondsChange: (Int) -> Unit,
    videoSortMode: VideoSortMode,
    onVideoSortModeChange: (VideoSortMode) -> Unit,
    folderSortMode: FolderSortMode,
    onFolderSortModeChange: (FolderSortMode) -> Unit,
    continueWatchingVideos: List<LocalMediaItem>,
    lastRefreshDurationMs: Long,
    mediaStoreVolumes: List<String>,
    safAuthorizedFolders: List<String>,
    lastPlayedUri: String,
    lastPlayedTitle: String,
    lastPlayedSize: Long,
    decoderModeState: DecoderMode,
    onDecoderModeChange: (DecoderMode) -> Unit,
    onAddSafFolder: (String) -> Unit,
    onRemoveSafFolder: (String) -> Unit
) {
    var activeTab by rememberSaveable { mutableStateOf(HomeTab.VIDEOS) }
    val videoGridState = rememberLazyGridState()
    val folderGridState = rememberLazyGridState()
    val imageGridState = rememberLazyGridState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 10.dp
            ) {
                GalleryNavItem(HomeTab.VIDEOS, activeTab, "视频", Icons.Filled.PlayArrow) { activeTab = it }
                GalleryNavItem(HomeTab.FOLDERS, activeTab, "文件夹", Icons.Filled.Folder) { activeTab = it }
                GalleryNavItem(HomeTab.IMAGES, activeTab, "图片", Icons.Filled.Image) { activeTab = it }
                GalleryNavItem(HomeTab.SETTINGS, activeTab, "设置", Icons.Filled.Settings) { activeTab = it }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            when (activeTab) {
                HomeTab.VIDEOS -> VideoGridScreen(
                    videos = videos,
                    onVideoClick = onVideoClick,
                    onRefresh = onReload,
                    isLoading = isLoadingMedia,
                    loadError = mediaLoadError,
                    playbackProgressMap = playbackProgressMap,
                    sortMode = videoSortMode,
                    onSortModeChange = onVideoSortModeChange,
                    continueWatchingVideos = continueWatchingVideos,
                    gridState = videoGridState
                )
                HomeTab.FOLDERS -> FolderScreen(
                    folders = folders,
                    onFolderClick = onFolderClick,
                    onRefresh = onReload,
                    isLoading = isLoadingMedia,
                    loadError = mediaLoadError,
                    sortMode = folderSortMode,
                    onSortModeChange = onFolderSortModeChange,
                    videos = videos,
                    gridState = folderGridState
                )
                HomeTab.IMAGES -> ImageGridScreen(
                    images = images,
                    onImageClick = onImageClick,
                    onRefresh = onReload,
                    isLoading = isLoadingMedia,
                    loadError = mediaLoadError,
                    gridState = imageGridState
                )
                HomeTab.SETTINGS -> SettingsScreen(
                    onReload = onReload,
                    mediaRepositoryCount = mediaRepositoryCount,
                    playbackEngine = playbackEngine,
                    defaultSpeed = defaultSpeed,
                    skipSeconds = skipSeconds,
                    onDefaultSpeedChange = onDefaultSpeedChange,
                    onSkipSecondsChange = onSkipSecondsChange,
                    lastRefreshDurationMs = lastRefreshDurationMs,
                    mediaStoreVolumes = mediaStoreVolumes,
                    safAuthorizedFolders = safAuthorizedFolders,
                    lastPlayedUri = lastPlayedUri,
                    lastPlayedTitle = lastPlayedTitle,
                    lastPlayedSize = lastPlayedSize,
                    decoderModeState = decoderModeState,
                    onDecoderModeChange = onDecoderModeChange,
                    onAddSafFolder = onAddSafFolder,
                    onRemoveSafFolder = onRemoveSafFolder,
                    videosCount = videos.size,
                    imagesCount = images.size,
                    foldersCount = folders.size
                )
            }
        }
    }
}

@Composable
private fun RowScope.GalleryNavItem(
    tab: HomeTab,
    activeTab: HomeTab,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onSelect: (HomeTab) -> Unit
) {
    NavigationBarItem(
        selected = tab == activeTab,
        onClick = { onSelect(tab) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) }
    )
}
