package com.pigfarmerjc.galleryplayer

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pigfarmerjc.galleryplayer.core.player.api.PlaybackEngine
import com.pigfarmerjc.galleryplayer.core.player.api.PlaybackState
import com.pigfarmerjc.galleryplayer.core.player.api.VideoOutputHostFactory
import com.pigfarmerjc.galleryplayer.player.libvlc.LibVlcPlaybackEngine
import com.pigfarmerjc.galleryplayer.player.libvlc.LibVlcVideoOutputHostFactory
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val playbackEngine: PlaybackEngine = LibVlcPlaybackEngine(application)
    val videoOutputFactory: VideoOutputHostFactory = LibVlcVideoOutputHostFactory()
    var wasPlayingBeforeBackground: Boolean = false

    // Media states
    val videosList = mutableStateListOf<LocalMediaItem>()
    val imagesList = mutableStateListOf<LocalMediaItem>()
    val foldersList = mutableStateListOf<FolderItem>()
    
    // Hardening Sprint loader states
    var permissionsGranted by mutableStateOf(false)
    var isLoadingMedia by mutableStateOf(false)
    var mediaLoadError by mutableStateOf<String?>(null)
    var mediaRepositoryCount by mutableStateOf(0)

    fun refreshLocalMedia(context: android.content.Context) {
        viewModelScope.launch {
            if (!PermissionState.hasAnyStoragePermission(context)) {
                permissionsGranted = false
                return@launch
            }
            permissionsGranted = true
            isLoadingMedia = true
            mediaLoadError = null

            try {
                // If only video permission is granted, query videos
                val hasVideo = PermissionState.hasVideoPermission(context)
                val v = if (hasVideo) MediaStoreHelper.queryLocalVideos(context) else emptyList()

                // If only image permission is granted, query images
                val hasImages = PermissionState.hasImagesPermission(context)
                val img = if (hasImages) MediaStoreHelper.queryLocalImages(context) else emptyList()

                videosList.clear()
                videosList.addAll(v)

                imagesList.clear()
                imagesList.addAll(img)

                // Recompute folders aggregate
                val f = v.groupBy { it.relativePath }.map { (path, items) ->
                    val folderName = path.trimEnd('/').split('/').lastOrNull()?.takeIf { it.isNotEmpty() } ?: "Root"
                    FolderItem(
                        volumeName = "external",
                        relativePath = path,
                        displayName = folderName,
                        videoCount = items.size,
                        coverUri = items.firstOrNull()?.contentUri,
                        totalSize = items.sumOf { it.fileSize }
                    )
                }
                foldersList.clear()
                foldersList.addAll(f)

                mediaRepositoryCount = v.size + img.size
            } catch (e: Exception) {
                mediaLoadError = e.localizedMessage ?: "Failed to read local storage"
            } finally {
                isLoadingMedia = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackEngine.release()
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MainViewModel = viewModel()
            val context = LocalContext.current

            // Check permissions and load media on startup
            LaunchedEffect(Unit) {
                viewModel.refreshLocalMedia(context)
            }

            // Lifecycle Observer to handle application background/foreground states
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_PAUSE) {
                        viewModel.wasPlayingBeforeBackground = (viewModel.playbackEngine.playbackState.value == PlaybackState.Playing)
                        viewModel.playbackEngine.pause()
                    } else if (event == Lifecycle.Event.ON_RESUME) {
                        if (viewModel.wasPlayingBeforeBackground) {
                            viewModel.playbackEngine.play()
                            viewModel.wasPlayingBeforeBackground = false
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!viewModel.permissionsGranted) {
                        PermissionScreen(context = context) {
                            viewModel.refreshLocalMedia(context)
                        }
                    } else {
                        // Custom stack-based navigation
                        val screenStack = remember { mutableStateListOf<Screen>(Screen.Home) }

                        when (val currentScreen = screenStack.lastOrNull()) {
                            is Screen.Home -> {
                                HomeScreen(
                                    videos = viewModel.videosList,
                                    images = viewModel.imagesList,
                                    folders = viewModel.foldersList,
                                    onVideoClick = { video, list ->
                                        screenStack.add(
                                            Screen.Player(
                                                videoUri = video.contentUri,
                                                videoTitle = video.displayName,
                                                videoList = list,
                                                currentIndex = list.indexOf(video)
                                            )
                                        )
                                    },
                                    onFolderClick = { folder ->
                                        screenStack.add(
                                            Screen.FolderVideos(
                                                volumeName = folder.volumeName,
                                                relativePath = folder.relativePath,
                                                folderDisplayName = folder.displayName
                                            )
                                        )
                                    },
                                    onImageClick = { image, list ->
                                        screenStack.add(
                                            Screen.ImageViewer(
                                                images = list,
                                                initialIndex = list.indexOf(image)
                                            )
                                        )
                                    },
                                    onReload = { viewModel.refreshLocalMedia(context) },
                                    mediaRepositoryCount = viewModel.mediaRepositoryCount,
                                    playbackEngine = viewModel.playbackEngine,
                                    isLoadingMedia = viewModel.isLoadingMedia,
                                    mediaLoadError = viewModel.mediaLoadError
                                )
                            }
                            is Screen.FolderVideos -> {
                                val folderVideos = viewModel.videosList.filter { it.relativePath == currentScreen.relativePath }
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = { screenStack.removeAt(screenStack.lastIndex) }) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = currentScreen.folderDisplayName,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        VideoGridScreen(
                                            videos = folderVideos,
                                            onVideoClick = { video, list ->
                                                screenStack.add(
                                                    Screen.Player(
                                                        videoUri = video.contentUri,
                                                        videoTitle = video.displayName,
                                                        videoList = list,
                                                        currentIndex = list.indexOf(video)
                                                    )
                                                )
                                            },
                                            onRefresh = { viewModel.refreshLocalMedia(context) },
                                            isLoading = viewModel.isLoadingMedia,
                                            loadError = viewModel.mediaLoadError
                                        )
                                    }
                                }
                                BackHandler {
                                    screenStack.removeAt(screenStack.lastIndex)
                                }
                            }
                            is Screen.Player -> {
                                PlayerScreen(
                                    videoUri = currentScreen.videoUri,
                                    videoTitle = currentScreen.videoTitle,
                                    videoList = currentScreen.videoList,
                                    currentIndex = currentScreen.currentIndex,
                                    playbackEngine = viewModel.playbackEngine,
                                    videoOutputFactory = viewModel.videoOutputFactory,
                                    onChangeVideo = { newIndex ->
                                        // Update parent navigation state stack
                                        val newItem = currentScreen.videoList[newIndex]
                                        screenStack[screenStack.lastIndex] = Screen.Player(
                                            videoUri = newItem.contentUri,
                                            videoTitle = newItem.displayName,
                                            videoList = currentScreen.videoList,
                                            currentIndex = newIndex
                                        )
                                    },
                                    onBack = { screenStack.removeAt(screenStack.lastIndex) }
                                )
                            }
                            is Screen.ImageViewer -> {
                                ImageViewerScreen(
                                    images = currentScreen.images,
                                    initialIndex = currentScreen.initialIndex,
                                    onBack = { screenStack.removeAt(screenStack.lastIndex) }
                                )
                            }
                            null -> {
                                screenStack.add(Screen.Home)
                            }
                        }
                    }
                }
            }
        }
    }
}
