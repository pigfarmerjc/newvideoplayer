package com.pigfarmerjc.galleryplayer


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
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Favorite
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pigfarmerjc.galleryplayer.core.player.api.DecoderMode
import com.pigfarmerjc.galleryplayer.core.player.api.PlaybackEngine
import com.pigfarmerjc.galleryplayer.core.player.api.PlaybackState
import com.pigfarmerjc.galleryplayer.core.player.api.VideoOutputHostFactory
import com.pigfarmerjc.galleryplayer.player.libvlc.LibVlcPlaybackEngine
import com.pigfarmerjc.galleryplayer.player.libvlc.LibVlcVideoOutputHostFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var mediaRefreshJob: Job? = null
    private var mediaRefreshGeneration = 0L

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
    var videoSortMode by mutableStateOf(VideoSortMode.DATE_MODIFIED_DESC)
    var folderSortMode by mutableStateOf(FolderSortMode.VIDEO_COUNT_DESC)
    var repeatModeState by mutableStateOf(PlaybackRepeatMode.NONE)
    // Persistent navigation & scroll states
    var activeHomeTab by mutableStateOf(HomeTab.VIDEOS)
    var videoGridColumnCount by mutableIntStateOf(0)
    val videoGridState = androidx.compose.foundation.lazy.grid.LazyGridState()
    val folderGridState = androidx.compose.foundation.lazy.grid.LazyGridState()
    val imageGridState = androidx.compose.foundation.lazy.grid.LazyGridState()
    val folderVideosGridState = androidx.compose.foundation.lazy.grid.LazyGridState()

    // Favorites persistence & state
    var favoriteUris by mutableStateOf<Set<String>>(emptySet())
    val favoriteVideosList = derivedStateOf {
        val favs = favoriteUris
        videosList.filter { favs.contains(it.contentUri) }
    }

    val historyListState = mutableStateOf<List<com.pigfarmerjc.galleryplayer.core.database.repository.PlaybackHistoryItem>>(emptyList())

    val continueWatchingList = derivedStateOf {
        val videosByUri = videosList.associateBy(LocalMediaItem::contentUri)
        historyListState.value
            .asSequence()
            .filter { it.contentUri.isNotBlank() && !it.finished && it.durationMs > 0L }
            .filter { (it.playbackPositionMs.toDouble() / it.durationMs.toDouble()) in 0.01..0.90 }
            .sortedByDescending { it.lastPlayedTime }
            .mapNotNull { videosByUri[it.contentUri] }
            .take(6)
            .toList()
    }

    init {
        val sharedPrefs = application.getSharedPreferences("player_settings", android.content.Context.MODE_PRIVATE)
        defaultSpeed = sharedPrefs.getFloat("default_speed", 1.0f)
        skipSeconds = sharedPrefs.getInt("skip_seconds", 10)
        videoGridColumnCount = sharedPrefs.getInt("video_grid_column_count", 0)

        val favPrefs = application.getSharedPreferences("favorites_settings", android.content.Context.MODE_PRIVATE)
        favoriteUris = favPrefs.getStringSet("favorite_uris", emptySet())?.toSet() ?: emptySet()

        val sortPrefs = application.getSharedPreferences("library_sort_settings", android.content.Context.MODE_PRIVATE)
        videoSortMode = VideoSortMode.entries.find { it.name == sortPrefs.getString("video_sort_mode", null) } ?: VideoSortMode.DATE_MODIFIED_DESC
        folderSortMode = FolderSortMode.entries.find { it.name == sortPrefs.getString("folder_sort_mode", null) } ?: FolderSortMode.VIDEO_COUNT_DESC

        val playbackPrefs = application.getSharedPreferences("playback_settings", android.content.Context.MODE_PRIVATE)
        repeatModeState = PlaybackRepeatMode.entries.find { it.name == playbackPrefs.getString("repeat_mode", null) } ?: PlaybackRepeatMode.NONE
        decoderModeState = DecoderMode.entries.find { it.name == playbackPrefs.getString("decoder_mode", null) } ?: DecoderMode.AUTO
        playbackEngine.setDecoderMode(decoderModeState)

        val safPrefs = application.getSharedPreferences("saf_settings", android.content.Context.MODE_PRIVATE)
        safAuthorizedFolders = safPrefs.getStringSet("authorized_folders", emptySet())?.toList() ?: emptyList()

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

    fun updateVideoGridColumnCount(columns: Int) {
        videoGridColumnCount = columns
        getApplication<Application>().getSharedPreferences("player_settings", android.content.Context.MODE_PRIVATE)
            .edit().putInt("video_grid_column_count", columns).apply()
    }

    fun isFavorite(uri: String): Boolean = favoriteUris.contains(uri)

    fun toggleFavorite(uri: String) {
        val current = favoriteUris.toMutableSet()
        val willBeFavorite = !current.contains(uri)
        if (willBeFavorite) {
            current.add(uri)
        } else {
            current.remove(uri)
        }
        favoriteUris = current
        val favPrefs = getApplication<Application>().getSharedPreferences("favorites_settings", android.content.Context.MODE_PRIVATE)
        favPrefs.edit().putStringSet("favorite_uris", current).apply()

        viewModelScope.launch(Dispatchers.IO) {
            database.mediaItemDao().updateFavorite(uri, willBeFavorite)
        }
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
        val generation = ++mediaRefreshGeneration
        mediaRefreshJob?.cancel()
        mediaRefreshJob = viewModelScope.launch {
            if (!PermissionState.hasAnyStoragePermission(context)) {
                permissionsGranted = false
                return@launch
            }
            permissionsGranted = true
            isLoadingMedia = true
            mediaLoadError = null

            try {
                val startTime = System.currentTimeMillis()
                val authorizedSafFolders = safAuthorizedFolders.toList()
                val snapshot = withContext(Dispatchers.IO) {
                    val volumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        runCatching { MediaStore.getExternalVolumeNames(context).toList() }
                            .getOrDefault(listOf("external"))
                    } else {
                        listOf("external")
                    }

                    val videos = buildList {
                        volumes.forEach { volume ->
                            runCatching {
                                addAll(
                                    MediaStoreHelper.queryVideosForUri(
                                        context,
                                        MediaStore.Video.Media.getContentUri(volume),
                                        volume
                                    )
                                )
                            }
                        }
                        authorizedSafFolders.forEach { safUri ->
                            runCatching { addAll(MediaStoreHelper.querySafVideos(context, safUri)) }
                        }
                    }
                    val images = buildList {
                        volumes.forEach { volume ->
                            runCatching {
                                addAll(
                                    MediaStoreHelper.queryImagesForUri(
                                        context,
                                        MediaStore.Images.Media.getContentUri(volume),
                                        volume
                                    )
                                )
                            }
                        }
                    }
                    val folders = videos
                        .groupBy { it.volumeName to it.relativePath }
                        .map { (key, items) ->
                            val path = key.second
                            val folderName = path.trimEnd('/').substringAfterLast('/').ifBlank { "根目录" }
                            FolderItem(
                                volumeName = key.first,
                                relativePath = path,
                                displayName = folderName,
                                videoCount = items.size,
                                coverUri = items.maxByOrNull { it.dateModifiedEpochSeconds ?: 0L }?.contentUri,
                                totalSize = items.sumOf { it.fileSize }
                            )
                        }
                    Triple(volumes, videos, images) to folders
                }
                val (mediaData, folderData) = snapshot
                if (generation != mediaRefreshGeneration) return@launch
                val (volumes, v, img) = mediaData
                mediaStoreVolumes = volumes
                videosList.clear()
                videosList.addAll(v)
                imagesList.clear()
                imagesList.addAll(img)
                foldersList.clear()
                foldersList.addAll(folderData)

                mediaRepositoryCount = v.size + img.size
                lastRefreshDurationMs = System.currentTimeMillis() - startTime

            } catch (e: Exception) {
                if (generation != mediaRefreshGeneration) return@launch
                mediaLoadError = e.localizedMessage ?: "Failed to read storage"
                android.widget.Toast.makeText(context, "刷新失败: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                if (generation == mediaRefreshGeneration) isLoadingMedia = false
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.navigationBars())

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
                        val inPiP = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            this@MainActivity.isInPictureInPictureMode
                        } else false
                        if (!inPiP) {
                            viewModel.wasPlayingBeforeBackground = (viewModel.playbackEngine.playbackState.value == PlaybackState.Playing)
                            viewModel.playbackEngine.pause()
                        }
                    } else if (event == Lifecycle.Event.ON_RESUME) {
                        val currentlyGranted = PermissionState.hasAnyStoragePermission(context)
                        if (currentlyGranted != viewModel.permissionsGranted) {
                            viewModel.refreshLocalMedia(context)
                        }
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

            GalleryPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!viewModel.permissionsGranted) {
                        PermissionScreen(context = context) {
                            viewModel.refreshLocalMedia(context)
                        }
                    } else {
                        // Layered Overlay Navigation Architecture
                        val screenStack = remember { mutableStateListOf<Screen>(Screen.Home) }

                        Box(modifier = Modifier.fillMaxSize()) {
                            // Layer 1: Base Screen (HomeScreen, FolderVideos, or Favorites)
                            val baseScreen = screenStack.lastOrNull { it is Screen.Home || it is Screen.FolderVideos || it is Screen.Favorites } ?: Screen.Home
                            when (baseScreen) {
                                is Screen.Home -> {
                                    HomeScreen(
                                        videos = viewModel.videosList,
                                        images = viewModel.imagesList,
                                        folders = viewModel.foldersList,
                                        activeTab = viewModel.activeHomeTab,
                                        onActiveTabChange = { viewModel.activeHomeTab = it },
                                        videoGridState = viewModel.videoGridState,
                                        folderGridState = viewModel.folderGridState,
                                        imageGridState = viewModel.imageGridState,
                                        videoGridColumnCount = viewModel.videoGridColumnCount,
                                        onVideoGridColumnCountChange = { viewModel.updateVideoGridColumnCount(it) },
                                        favoriteVideos = viewModel.favoriteVideosList.value,
                                        onFavoriteFolderClick = { screenStack.add(Screen.Favorites) },
                                        onVideoClick = { video, list ->
                                            scope.launch {
                                                val resumePos = viewModel.getResumePlaybackPosition(video.contentUri)
                                                if (resumePos > 0L) {
                                                    android.widget.Toast.makeText(context, "已从上次位置继续播放", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                                screenStack.add(
                                                    Screen.Player(
                                                        videoUri = video.contentUri,
                                                        videoTitle = video.displayName,
                                                        videoList = list,
                                                        currentIndex = list.indexOf(video),
                                                        initialPositionMs = resumePos
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
                                        videoSortMode = viewModel.videoSortMode,
                                        onVideoSortModeChange = { viewModel.updateVideoSortMode(it) },
                                        folderSortMode = viewModel.folderSortMode,
                                        onFolderSortModeChange = { viewModel.updateFolderSortMode(it) },
                                        continueWatchingVideos = viewModel.continueWatchingList.value,
                                        lastRefreshDurationMs = viewModel.lastRefreshDurationMs,
                                        mediaStoreVolumes = viewModel.mediaStoreVolumes,
                                        safAuthorizedFolders = viewModel.safAuthorizedFolders,
                                        lastPlayedUri = viewModel.lastPlayedUri,
                                        lastPlayedTitle = viewModel.lastPlayedTitle,
                                        lastPlayedSize = viewModel.lastPlayedSize,
                                        decoderModeState = viewModel.decoderModeState,
                                        onDecoderModeChange = { viewModel.updateDecoderMode(it) },
                                        onAddSafFolder = { viewModel.addSafFolder(it, context) },
                                        onRemoveSafFolder = { viewModel.removeSafFolder(it, context) }
                                    )
                                }
                                is Screen.Favorites -> {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .statusBarsPadding()
                                                .height(56.dp)
                                                .background(MaterialTheme.colorScheme.surface)
                                                .padding(horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(onClick = {
                                                val favIdx = screenStack.indexOfLast { it is Screen.Favorites }
                                                if (favIdx >= 0) screenStack.removeAt(favIdx)
                                            }) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.Favorite,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFF3B30),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = "我的收藏 (${viewModel.favoriteVideosList.value.size})",
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                            }
                                        }
                                        Box(modifier = Modifier.weight(1f)) {
                                            VideoGridScreen(
                                                videos = viewModel.favoriteVideosList.value,
                                                gridState = viewModel.folderVideosGridState,
                                                onVideoClick = { video, list ->
                                                    scope.launch {
                                                        val resumePos = viewModel.getResumePlaybackPosition(video.contentUri)
                                                        if (resumePos > 0L) {
                                                            android.widget.Toast.makeText(context, "已从上次位置继续播放", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                        screenStack.add(
                                                            Screen.Player(
                                                                videoUri = video.contentUri,
                                                                videoTitle = video.displayName,
                                                                videoList = list,
                                                                currentIndex = list.indexOf(video),
                                                                initialPositionMs = resumePos
                                                            )
                                                        )
                                                    }
                                                },
                                                onRefresh = { viewModel.refreshLocalMedia(context) },
                                                isLoading = viewModel.isLoadingMedia,
                                                loadError = viewModel.mediaLoadError,
                                                playbackProgressMap = viewModel.playbackProgressMap.value,
                                                sortMode = viewModel.videoSortMode,
                                                onSortModeChange = { viewModel.updateVideoSortMode(it) },
                                                persistedColumnCount = viewModel.videoGridColumnCount,
                                                onColumnCountChange = { viewModel.updateVideoGridColumnCount(it) }
                                            )
                                        }
                                    }
                                    BackHandler {
                                        val favIdx = screenStack.indexOfLast { it is Screen.Favorites }
                                        if (favIdx >= 0) screenStack.removeAt(favIdx)
                                    }
                                }
                                is Screen.FolderVideos -> {
                                    val folderVideos = FolderSort.videosInFolder(
                                        videos = viewModel.videosList,
                                        volumeName = baseScreen.volumeName,
                                        relativePath = baseScreen.relativePath
                                    )
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(56.dp)
                                                .background(MaterialTheme.colorScheme.surface)
                                                .padding(horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(onClick = {
                                                val folderIndex = screenStack.indexOfLast { it is Screen.FolderVideos }
                                                if (folderIndex >= 0) {
                                                    screenStack.removeAt(folderIndex)
                                                }
                                            }) {
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
                                                gridState = viewModel.folderVideosGridState,
                                                onVideoClick = { video, list ->
                                                    scope.launch {
                                                        val resumePos = viewModel.getResumePlaybackPosition(video.contentUri)
                                                        if (resumePos > 0L) {
                                                            android.widget.Toast.makeText(context, "已从上次位置继续播放", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                        screenStack.add(
                                                            Screen.Player(
                                                                videoUri = video.contentUri,
                                                                videoTitle = video.displayName,
                                                                videoList = list,
                                                                currentIndex = list.indexOf(video),
                                                                initialPositionMs = resumePos
                                                            )
                                                        )
                                                    }
                                                },
                                                onRefresh = { viewModel.refreshLocalMedia(context) },
                                                isLoading = viewModel.isLoadingMedia,
                                                loadError = viewModel.mediaLoadError,
                                                playbackProgressMap = viewModel.playbackProgressMap.value,
                                                sortMode = viewModel.videoSortMode,
                                                onSortModeChange = { viewModel.updateVideoSortMode(it) }
                                            )
                                        }
                                    }
                                    BackHandler {
                                        val folderIndex = screenStack.indexOfLast { it is Screen.FolderVideos }
                                        if (folderIndex >= 0) {
                                            screenStack.removeAt(folderIndex)
                                        }
                                    }
                                }
                                else -> Unit
                            }

                            // Layer 2: Overlay ImageViewer (if active)
                            val activeImageViewer = screenStack.lastOrNull { it is Screen.ImageViewer } as? Screen.ImageViewer
                            if (activeImageViewer != null) {
                                ImageViewerScreen(
                                    images = activeImageViewer.images,
                                    initialIndex = activeImageViewer.initialIndex,
                                    onBack = {
                                        val idx = screenStack.indexOfLast { it is Screen.ImageViewer }
                                        if (idx >= 0) screenStack.removeAt(idx)
                                    },
                                    isFavorite = { viewModel.isFavorite(it) },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) }
                                )
                            }

                            // Layer 3: Overlay Player (if active)
                            val activePlayer = screenStack.lastOrNull { it is Screen.Player } as? Screen.Player
                            if (activePlayer != null) {
                                PlayerScreen(
                                    videoUri = activePlayer.videoUri,
                                    videoTitle = activePlayer.videoTitle,
                                    videoList = activePlayer.videoList,
                                    currentIndex = activePlayer.currentIndex,
                                    playbackEngine = viewModel.playbackEngine,
                                    videoOutputFactory = viewModel.videoOutputFactory,
                                    onChangeVideo = { newIndex ->
                                        val newItem = activePlayer.videoList[newIndex]
                                        val pIdx = screenStack.indexOfLast { it is Screen.Player }
                                        if (pIdx >= 0) {
                                            screenStack[pIdx] = Screen.Player(
                                                videoUri = newItem.contentUri,
                                                videoTitle = newItem.displayName,
                                                videoList = activePlayer.videoList,
                                                currentIndex = newIndex,
                                                initialPositionMs = 0L
                                            )
                                        }
                                        scope.launch {
                                            val resumePos = viewModel.getResumePlaybackPosition(newItem.contentUri)
                                            if (resumePos > 0L) {
                                                viewModel.playbackEngine.seekTo(resumePos)
                                                android.widget.Toast.makeText(context, "已从上次位置继续播放", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onBack = {
                                        val pIdx = screenStack.indexOfLast { it is Screen.Player }
                                        if (pIdx >= 0) screenStack.removeAt(pIdx)
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
                                    isFavorite = viewModel.isFavorite(activePlayer.videoUri),
                                    onToggleFavorite = { viewModel.toggleFavorite(activePlayer.videoUri) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
