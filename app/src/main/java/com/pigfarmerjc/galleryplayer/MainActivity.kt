package com.pigfarmerjc.galleryplayer

import com.pigfarmerjc.galleryplayer.core.player.api.VideoScaleMode


import android.app.Application
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.pigfarmerjc.galleryplayer.core.database.GalleryDatabase
import com.pigfarmerjc.galleryplayer.core.database.repository.RoomMediaRepository
import com.pigfarmerjc.galleryplayer.core.database.repository.RoomPlaybackHistoryRepository
import com.pigfarmerjc.galleryplayer.core.model.ScanState
import com.pigfarmerjc.galleryplayer.core.player.api.DecoderMode
import com.pigfarmerjc.galleryplayer.core.player.api.PlaybackEngine
import com.pigfarmerjc.galleryplayer.core.player.api.PlaybackState
import com.pigfarmerjc.galleryplayer.core.player.api.VideoOutputHostFactory
import com.pigfarmerjc.galleryplayer.player.libvlc.LibVlcPlaybackEngine
import com.pigfarmerjc.galleryplayer.player.libvlc.LibVlcVideoOutputHostFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // Diagnostics metrics
    var lastRefreshDurationMs by mutableStateOf(0L)
    var mediaStoreVolumes by mutableStateOf<List<String>>(emptyList())
    var safAuthorizedFolders by mutableStateOf<List<String>>(emptyList())
    
    // Last playback info
    var lastPlayedUri by mutableStateOf("")
    var lastPlayedTitle by mutableStateOf("")
    var lastPlayedSize by mutableStateOf(0L)
    var decoderModeState by mutableStateOf(DecoderMode.AUTO)

    // DB Repositories
    private val database by lazy { GalleryDatabase.getDatabase(application) }
    private val mediaRepository by lazy { RoomMediaRepository(database.mediaItemDao(), database.folderDao()) }
    private val historyRepository by lazy { RoomPlaybackHistoryRepository(database.playbackHistoryDao(), database.mediaItemDao()) }

    // Playback progress map: contentUri -> progressRatio (0.01 to 0.99)
    private val _playbackProgressMap = mutableStateOf<Map<String, Float>>(emptyMap())
    val playbackProgressMap: State<Map<String, Float>> get() = _playbackProgressMap

    // Persistent settings states
    var defaultSpeed by mutableStateOf(1.0f)
    var skipSeconds by mutableStateOf(10)

    // Library search & sort states
    var searchQuery by mutableStateOf("")
    var videoSortMode by mutableStateOf(VideoSortMode.DATE_MODIFIED_DESC)
    var folderSortMode by mutableStateOf(FolderSortMode.VIDEO_COUNT_DESC)
    var repeatModeState by mutableStateOf(PlaybackRepeatMode.NONE)
    var themeModeState by mutableStateOf(AppThemeMode.SYSTEM)
    var scaleModeState by mutableStateOf(VideoScaleMode.FIT)
    var videoViewModeState by mutableStateOf(VideoViewMode.CARD)
    var photosGridColumnsState by mutableStateOf(6)
    var photosGridSpacingState by mutableStateOf(1f) // default 1dp
    val transitionState = GalleryTransitionState()

    val historyListState = mutableStateOf<List<com.pigfarmerjc.galleryplayer.core.database.repository.PlaybackHistoryItem>>(emptyList())

    val continueWatchingList = derivedStateOf {
        val activeUris = playbackProgressMap.value.keys
        historyListState.value
            .filter { it.contentUri in activeUris && !it.finished }
            .mapNotNull { historyItem ->
                videosList.find { it.contentUri == historyItem.contentUri }
            }
            .take(10)
    }

    init {
        val sharedPrefs = application.getSharedPreferences("player_settings", android.content.Context.MODE_PRIVATE)
        defaultSpeed = sharedPrefs.getFloat("default_speed", 1.0f)
        skipSeconds = sharedPrefs.getInt("skip_seconds", 10)

        val sortPrefs = application.getSharedPreferences("library_sort_settings", android.content.Context.MODE_PRIVATE)
        videoSortMode = VideoSortMode.valueOf(sortPrefs.getString("video_sort_mode", VideoSortMode.DATE_MODIFIED_DESC.name) ?: VideoSortMode.DATE_MODIFIED_DESC.name)
        folderSortMode = FolderSortMode.valueOf(sortPrefs.getString("folder_sort_mode", FolderSortMode.VIDEO_COUNT_DESC.name) ?: FolderSortMode.VIDEO_COUNT_DESC.name)

        val playbackPrefs = application.getSharedPreferences("playback_settings", android.content.Context.MODE_PRIVATE)
        repeatModeState = PlaybackRepeatMode.valueOf(playbackPrefs.getString("repeat_mode", PlaybackRepeatMode.NONE.name) ?: PlaybackRepeatMode.NONE.name)
        decoderModeState = DecoderMode.valueOf(playbackPrefs.getString("decoder_mode", DecoderMode.AUTO.name) ?: DecoderMode.AUTO.name)
        playbackEngine.setDecoderMode(decoderModeState)

        val safPrefs = application.getSharedPreferences("saf_settings", android.content.Context.MODE_PRIVATE)
        safAuthorizedFolders = safPrefs.getStringSet("authorized_folders", emptySet())?.toList() ?: emptyList()

        val themePrefs = application.getSharedPreferences("theme_settings", android.content.Context.MODE_PRIVATE)
        themeModeState = AppThemeMode.values().firstOrNull { it.name == themePrefs.getString("theme_mode", AppThemeMode.SYSTEM.name) } ?: AppThemeMode.SYSTEM

        val scaleModeVal = playbackPrefs.getString("scale_mode", VideoScaleMode.FIT.name) ?: VideoScaleMode.FIT.name
        scaleModeState = VideoScaleMode.values().firstOrNull { it.name == scaleModeVal } ?: VideoScaleMode.FIT

        val viewPrefs = application.getSharedPreferences("video_view_settings", android.content.Context.MODE_PRIVATE)
        val modeStr = viewPrefs.getString("video_view_mode", VideoViewMode.CARD.name) ?: VideoViewMode.CARD.name
        videoViewModeState = VideoViewMode.values().firstOrNull { it.name == modeStr } ?: VideoViewMode.CARD
        photosGridColumnsState = viewPrefs.getInt("photos_grid_columns", 6)
        photosGridSpacingState = viewPrefs.getFloat("photos_grid_spacing", 1f)

        // Stream playback history changes to the progress map
        viewModelScope.launch {
            historyRepository.getHistory().collect { list ->
                historyListState.value = list
                val map = mutableMapOf<String, Float>()
                list.forEach { item ->
                    if (item.durationMs > 0 && !item.finished) {
                        val progress = item.playbackPositionMs.toFloat() / item.durationMs.toFloat()
                        if (progress in 0.01f..0.99f) {
                            map[item.contentUri] = progress
                        }
                    }
                }
                _playbackProgressMap.value = map
            }
        }
    }

    fun addSafFolder(uriString: String, context: android.content.Context) {
        try {
            context.contentResolver.takePersistableUriPermission(
                android.net.Uri.parse(uriString),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            Log.e("MainViewModel", "Could not take persistable permission: ${e.message}")
        }
        val currentSet = safAuthorizedFolders.toMutableSet()
        currentSet.add(uriString)
        safAuthorizedFolders = currentSet.toList()
        getApplication<Application>().getSharedPreferences("saf_settings", android.content.Context.MODE_PRIVATE)
            .edit().putStringSet("authorized_folders", currentSet).apply()
    }

    fun removeSafFolder(uriString: String, context: android.content.Context) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                android.net.Uri.parse(uriString),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            Log.e("MainViewModel", "Could not release permission: ${e.message}")
        }
        val currentSet = safAuthorizedFolders.toMutableSet()
        currentSet.remove(uriString)
        safAuthorizedFolders = currentSet.toList()
        getApplication<Application>().getSharedPreferences("saf_settings", android.content.Context.MODE_PRIVATE)
            .edit().putStringSet("authorized_folders", currentSet).apply()
    }

    fun updateDefaultSpeed(speed: Float) {
        defaultSpeed = speed
        getApplication<Application>().getSharedPreferences("player_settings", android.content.Context.MODE_PRIVATE)
            .edit().putFloat("default_speed", speed).apply()
    }

    fun updateSkipSeconds(seconds: Int) {
        skipSeconds = seconds
        getApplication<Application>().getSharedPreferences("player_settings", android.content.Context.MODE_PRIVATE)
            .edit().putInt("skip_seconds", seconds).apply()
    }

    fun updateVideoSortMode(mode: VideoSortMode) {
        videoSortMode = mode
        getApplication<Application>().getSharedPreferences("library_sort_settings", android.content.Context.MODE_PRIVATE)
            .edit().putString("video_sort_mode", mode.name).apply()
    }

    fun updateFolderSortMode(mode: FolderSortMode) {
        folderSortMode = mode
        getApplication<Application>().getSharedPreferences("library_sort_settings", android.content.Context.MODE_PRIVATE)
            .edit().putString("folder_sort_mode", mode.name).apply()
    }

    fun updateRepeatMode(mode: PlaybackRepeatMode) {
        repeatModeState = mode
        getApplication<Application>().getSharedPreferences("playback_settings", android.content.Context.MODE_PRIVATE)
            .edit().putString("repeat_mode", mode.name).apply()
    }

    fun updateDecoderMode(mode: DecoderMode) {
        decoderModeState = mode
        playbackEngine.setDecoderMode(mode)
        getApplication<Application>().getSharedPreferences("playback_settings", android.content.Context.MODE_PRIVATE)
            .edit().putString("decoder_mode", mode.name).apply()
    }

    fun updateThemeMode(mode: AppThemeMode) {
        themeModeState = mode
        getApplication<Application>().getSharedPreferences("theme_settings", android.content.Context.MODE_PRIVATE)
            .edit().putString("theme_mode", mode.name).apply()
    }

    fun updateScaleMode(mode: VideoScaleMode) {
        scaleModeState = mode
        getApplication<Application>().getSharedPreferences("playback_settings", android.content.Context.MODE_PRIVATE)
            .edit().putString("scale_mode", mode.name).apply()
    }

    fun updateVideoViewMode(mode: VideoViewMode) {
        videoViewModeState = mode
        getApplication<Application>().getSharedPreferences("video_view_settings", android.content.Context.MODE_PRIVATE)
            .edit().putString("video_view_mode", mode.name).apply()
    }

    fun updatePhotosGridColumns(columns: Int) {
        photosGridColumnsState = columns.coerceIn(2, 12)
        getApplication<Application>().getSharedPreferences("video_view_settings", android.content.Context.MODE_PRIVATE)
            .edit().putInt("photos_grid_columns", columns.coerceIn(2, 12)).apply()
    }

    fun updatePhotosGridSpacing(spacing: Float) {
        photosGridSpacingState = spacing
        getApplication<Application>().getSharedPreferences("video_view_settings", android.content.Context.MODE_PRIVATE)
            .edit().putFloat("photos_grid_spacing", spacing).apply()
    }

    fun startPlaybackSession(video: LocalMediaItem) {
        lastPlayedUri = video.contentUri
        lastPlayedTitle = video.displayName
        lastPlayedSize = video.fileSize

        viewModelScope.launch(Dispatchers.IO) {
            // First persist media item to prevent FK violation
            val domainItem = video.toMediaItem()
            mediaRepository.saveMediaItems(listOf(domainItem))
            // Start history session
            historyRepository.startPlaybackSession(video.contentUri)
        }
    }

    fun updatePlaybackProgress(video: LocalMediaItem, positionMs: Long, durationMs: Long, speed: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            val domainItem = video.toMediaItem()
            mediaRepository.saveMediaItems(listOf(domainItem))
            historyRepository.updatePlaybackProgress(video.contentUri, positionMs, durationMs, speed)
        }
    }

    fun markPlaybackCompleted(video: LocalMediaItem, durationMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val domainItem = video.toMediaItem()
            mediaRepository.saveMediaItems(listOf(domainItem))
            historyRepository.markPlaybackCompleted(video.contentUri, durationMs)
        }
    }

    suspend fun getResumePlaybackPosition(contentUri: String): Long = withContext(Dispatchers.IO) {
        val item = database.mediaItemDao().getByUri(contentUri) ?: return@withContext 0L
        val history = database.playbackHistoryDao().getByMediaId(item.id)
        if (history != null && !history.completed) {
            val pos = history.positionMs
            val dur = history.durationMs
            if (pos > 3000L && dur > 0L && pos < dur * 0.90) {
                return@withContext pos
            }
        }
        return@withContext 0L
    }

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
                val startTime = System.currentTimeMillis()

                // Query all external volumes
                val volumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        MediaStore.getExternalVolumeNames(context).toList()
                    } catch (e: Exception) {
                        listOf("external")
                    }
                } else {
                    listOf("external")
                }
                mediaStoreVolumes = volumes

                val v = mutableListOf<LocalMediaItem>()
                for (volume in volumes) {
                    try {
                        val uri = MediaStore.Video.Media.getContentUri(volume)
                        v.addAll(MediaStoreHelper.queryVideosForUri(context, uri, volume))
                    } catch (e: Exception) {
                        // ignore
                    }
                }

                val img = mutableListOf<LocalMediaItem>()
                for (volume in volumes) {
                    try {
                        val uri = MediaStore.Images.Media.getContentUri(volume)
                        img.addAll(MediaStoreHelper.queryImagesForUri(context, uri, volume))
                    } catch (e: Exception) {
                        // ignore
                    }
                }

                // Query SAF directories
                for (safUriString in safAuthorizedFolders) {
                    try {
                        v.addAll(MediaStoreHelper.querySafVideos(context, safUriString))
                    } catch (e: Exception) {
                        // ignore
                    }
                }

                videosList.clear()
                videosList.addAll(v)

                imagesList.clear()
                imagesList.addAll(img)

                // Recompute folders aggregate
                val f = v.groupBy { it.relativePath }.map { (path, items) ->
                    val folderName = path.trimEnd('/').split('/').lastOrNull()?.takeIf { it.isNotEmpty() } ?: "Root"
                    FolderItem(
                        volumeName = items.firstOrNull()?.volumeName ?: "external",
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
                lastRefreshDurationMs = System.currentTimeMillis() - startTime

                android.widget.Toast.makeText(context, context.getString(R.string.media_library_refreshed), android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                mediaLoadError = e.localizedMessage ?: "Failed to read storage"
                android.widget.Toast.makeText(context, context.getString(R.string.media_library_refresh_failed, e.localizedMessage ?: ""), android.widget.Toast.LENGTH_LONG).show()
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
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val viewModel: MainViewModel = viewModel()
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

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

            GalleryPlayerTheme(themeMode = viewModel.themeModeState) {
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

                        // Shared grid states to preserve scroll position
                        val cardGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                        val photosGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

                        val currentScreen = screenStack.lastOrNull()
                        val activePlayer = screenStack.lastOrNull { it is Screen.Player } as? Screen.Player
                        val activeImageViewer = screenStack.lastOrNull { it is Screen.ImageViewer } as? Screen.ImageViewer

                        // Base screen is whatever was open below player/imageviewer
                        val baseScreen = screenStack.lastOrNull { it is Screen.Home || it is Screen.FolderVideos } ?: Screen.Home

                        // Scroll position restoration when returning to Home
                        LaunchedEffect(currentScreen, viewModel.transitionState.lastOpenedVideoUri, viewModel.videoViewModeState, viewModel.videosList.size) {
                            if (currentScreen is Screen.Home) {
                                val uri = viewModel.transitionState.lastOpenedVideoUri
                                if (uri.isNotEmpty()) {
                                    val filtered = VideoFilterAndSort.filterAndSort(
                                        viewModel.videosList,
                                        viewModel.searchQuery,
                                        viewModel.videoSortMode
                                    )
                                    val idx = findVideoIndexByUri(filtered, uri)
                                    if (idx >= 0) {
                                        if (viewModel.videoViewModeState == VideoViewMode.PHOTOS_GRID) {
                                            photosGridState.scrollToItem(idx)
                                        } else {
                                            cardGridState.scrollToItem(idx)
                                        }
                                    }
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            // ── Layer 1: Base Screen (Gallery Grid) ──
                            when (baseScreen) {
                                is Screen.Home -> {
                                    HomeScreen(
                                        videos = viewModel.videosList,
                                        images = viewModel.imagesList,
                                        folders = viewModel.foldersList,
                                        onVideoClick = { video, list ->
                                            scope.launch {
                                                viewModel.transitionState.lastOpenedVideoUri = video.contentUri
                                                val bounds = viewModel.transitionState.boundsFor(video.contentUri)
                                                val resumePos = viewModel.getResumePlaybackPosition(video.contentUri)
                                                if (resumePos > 0L) {
                                                    android.widget.Toast.makeText(context, context.getString(R.string.playback_resumed), android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                                screenStack.add(
                                                    Screen.Player(
                                                        videoUri = video.contentUri,
                                                        videoTitle = video.displayName,
                                                        videoList = list,
                                                        currentIndex = list.indexOf(video),
                                                        initialPositionMs = resumePos,
                                                        initialBounds = bounds
                                                    )
                                                )
                                            }
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
                                        mediaLoadError = viewModel.mediaLoadError,
                                        playbackProgressMap = viewModel.playbackProgressMap.value,
                                        defaultSpeed = viewModel.defaultSpeed,
                                        skipSeconds = viewModel.skipSeconds,
                                        onDefaultSpeedChange = { viewModel.updateDefaultSpeed(it) },
                                        onSkipSecondsChange = { viewModel.updateSkipSeconds(it) },
                                        searchQuery = viewModel.searchQuery,
                                        onSearchQueryChange = { viewModel.searchQuery = it },
                                        videoSortMode = viewModel.videoSortMode,
                                        onVideoSortModeChange = { viewModel.updateVideoSortMode(it) },
                                        folderSortMode = viewModel.folderSortMode,
                                        onFolderSortModeChange = { viewModel.updateFolderSortMode(it) },
                                        continueWatchingVideos = emptyList(), // Continue watching UI removed
                                        lastRefreshDurationMs = viewModel.lastRefreshDurationMs,
                                        mediaStoreVolumes = viewModel.mediaStoreVolumes,
                                        safAuthorizedFolders = viewModel.safAuthorizedFolders,
                                        lastPlayedUri = viewModel.lastPlayedUri,
                                        lastPlayedTitle = viewModel.lastPlayedTitle,
                                        lastPlayedSize = viewModel.lastPlayedSize,
                                        decoderModeState = viewModel.decoderModeState,
                                        onDecoderModeChange = { viewModel.updateDecoderMode(it) },
                                        onAddSafFolder = { viewModel.addSafFolder(it, context) },
                                        onRemoveSafFolder = { viewModel.removeSafFolder(it, context) },
                                        themeMode = viewModel.themeModeState,
                                        onThemeModeChange = { viewModel.updateThemeMode(it) },
                                        videoViewMode = viewModel.videoViewModeState,
                                        onVideoViewModeChange = { viewModel.updateVideoViewMode(it) },
                                        photosGridColumns = viewModel.photosGridColumnsState,
                                        onPhotosGridColumnsChange = { viewModel.updatePhotosGridColumns(it) },
                                        photosGridSpacing = viewModel.photosGridSpacingState,
                                        onPhotosGridSpacingChange = { viewModel.updatePhotosGridSpacing(it) },
                                        cardGridState = cardGridState,
                                        photosGridState = photosGridState,
                                        transitionState = viewModel.transitionState
                                    )
                                }
                                is Screen.FolderVideos -> {
                                    val folderVideos = viewModel.videosList.filter { it.relativePath == baseScreen.relativePath }
                                    val folderCardGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                                    val folderPhotosGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

                                    // Scroll position restoration when returning to Folder videos
                                    LaunchedEffect(currentScreen, viewModel.transitionState.lastOpenedVideoUri, viewModel.videoViewModeState, folderVideos.size) {
                                        val uri = viewModel.transitionState.lastOpenedVideoUri
                                        if (uri.isNotEmpty()) {
                                            val index = findVideoIndexByUri(folderVideos, uri)
                                            if (index >= 0) {
                                                if (viewModel.videoViewModeState == VideoViewMode.PHOTOS_GRID) {
                                                    folderPhotosGridState.scrollToItem(index)
                                                } else {
                                                    folderCardGridState.scrollToItem(index)
                                                }
                                            }
                                        }
                                    }

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
                                                text = baseScreen.folderDisplayName,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                        Box(modifier = Modifier.weight(1f)) {
                                            VideoGridScreen(
                                                videos = folderVideos,
                                                onVideoClick = { video, list ->
                                                    scope.launch {
                                                        viewModel.transitionState.lastOpenedVideoUri = video.contentUri
                                                        val bounds = viewModel.transitionState.boundsFor(video.contentUri)
                                                        val resumePos = viewModel.getResumePlaybackPosition(video.contentUri)
                                                        if (resumePos > 0L) {
                                                            android.widget.Toast.makeText(context, context.getString(R.string.playback_resumed), android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                        screenStack.add(
                                                            Screen.Player(
                                                                videoUri = video.contentUri,
                                                                videoTitle = video.displayName,
                                                                videoList = list,
                                                                currentIndex = list.indexOf(video),
                                                                initialPositionMs = resumePos,
                                                                initialBounds = bounds
                                                            )
                                                        )
                                                    }
                                                },
                                                onRefresh = { viewModel.refreshLocalMedia(context) },
                                                isLoading = viewModel.isLoadingMedia,
                                                loadError = viewModel.mediaLoadError,
                                                playbackProgressMap = viewModel.playbackProgressMap.value,
                                                searchQuery = viewModel.searchQuery,
                                                onSearchQueryChange = { viewModel.searchQuery = it },
                                                sortMode = viewModel.videoSortMode,
                                                onSortModeChange = { viewModel.updateVideoSortMode(it) },
                                                videoViewMode = viewModel.videoViewModeState,
                                                onVideoViewModeChange = { viewModel.updateVideoViewMode(it) },
                                                photosGridColumns = viewModel.photosGridColumnsState,
                                                onPhotosGridColumnsChange = { viewModel.updatePhotosGridColumns(it) },
                                                photosGapDp = viewModel.photosGridSpacingState.dp,
                                                cardGridState = folderCardGridState,
                                                photosGridState = folderPhotosGridState,
                                                transitionState = viewModel.transitionState
                                            )
                                        }
                                    }
                                    BackHandler {
                                        screenStack.removeAt(screenStack.lastIndex)
                                    }
                                }
                                else -> {}
                            }

                            // ── Layer 2: Player Screen (Overlay) ──
                            if (activePlayer != null) {
                                PlayerScreen(
                                    videoUri = activePlayer.videoUri,
                                    videoTitle = activePlayer.videoTitle,
                                    videoList = activePlayer.videoList,
                                    currentIndex = activePlayer.currentIndex,
                                    playbackEngine = viewModel.playbackEngine,
                                    videoOutputFactory = viewModel.videoOutputFactory,
                                    initialBounds = activePlayer.initialBounds,
                                    onChangeVideo = { newIndex ->
                                        val newItem = activePlayer.videoList[newIndex]
                                        scope.launch {
                                            viewModel.transitionState.lastOpenedVideoUri = newItem.contentUri
                                            val bounds = viewModel.transitionState.boundsFor(newItem.contentUri)
                                            val resumePos = viewModel.getResumePlaybackPosition(newItem.contentUri)
                                            if (resumePos > 0L) {
                                                android.widget.Toast.makeText(context, context.getString(R.string.playback_resumed), android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                            val idx = screenStack.indexOfFirst { it is Screen.Player }
                                            if (idx >= 0) {
                                                screenStack[idx] = Screen.Player(
                                                    videoUri = newItem.contentUri,
                                                    videoTitle = newItem.displayName,
                                                    videoList = activePlayer.videoList,
                                                    currentIndex = newIndex,
                                                    initialPositionMs = resumePos,
                                                    initialBounds = bounds
                                                )
                                            }
                                        }
                                    },
                                    onBack = {
                                        screenStack.removeAll { it is Screen.Player }
                                    },
                                    initialPositionMs = activePlayer.initialPositionMs,
                                    onPlaybackSessionStart = {
                                        viewModel.startPlaybackSession(activePlayer.videoList[activePlayer.currentIndex])
                                    },
                                    onPlaybackProgress = { pos, dur, completed ->
                                        val currentVideo = activePlayer.videoList[activePlayer.currentIndex]
                                        if (completed) {
                                            viewModel.markPlaybackCompleted(currentVideo, dur)
                                        } else {
                                            viewModel.updatePlaybackProgress(
                                                currentVideo,
                                                pos,
                                                dur,
                                                viewModel.playbackEngine.playbackSpeed.value
                                            )
                                        }
                                    },
                                    defaultSpeed = viewModel.defaultSpeed,
                                    skipSeconds = viewModel.skipSeconds,
                                    repeatMode = viewModel.repeatModeState,
                                    onRepeatModeChange = { viewModel.updateRepeatMode(it) },
                                    scaleMode = viewModel.scaleModeState,
                                    onScaleModeChange = { viewModel.updateScaleMode(it) }
                                )
                            }

                            // ── Layer 3: Image Viewer (Overlay) ──
                            if (activeImageViewer != null) {
                                ImageViewerScreen(
                                    images = activeImageViewer.images,
                                    initialIndex = activeImageViewer.initialIndex,
                                    onBack = {
                                        screenStack.removeAll { it is Screen.ImageViewer }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
