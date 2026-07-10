package com.pigfarmerjc.galleryplayer

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
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
                    icon = { Icon(Icons.Default.Menu, contentDescription = "Folders") },
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
                        onRefresh = onReload,
                        isLoading = isLoadingMedia,
                        loadError = mediaLoadError,
                        playbackProgressMap = playbackProgressMap,
                        searchQuery = searchQuery,
                        onSearchQueryChange = onSearchQueryChange,
                        sortMode = videoSortMode,
                        onSortModeChange = onVideoSortModeChange,
                        continueWatchingVideos = continueWatchingVideos
                    )
                }
                HomeTab.FOLDERS -> {
                    FolderScreen(
                        folders = folders,
                        onFolderClick = onFolderClick,
                        onRefresh = onReload,
                        isLoading = isLoadingMedia,
                        loadError = mediaLoadError,
                        sortMode = folderSortMode,
                        onSortModeChange = onFolderSortModeChange,
                        videos = videos
                    )
                }
                HomeTab.IMAGES -> {
                    ImageGridScreen(
                        images = images,
                        onImageClick = onImageClick,
                        onRefresh = onReload,
                        isLoading = isLoadingMedia,
                        loadError = mediaLoadError
                    )
                }
                HomeTab.SETTINGS -> {
                    SettingsScreen(
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
}
