package com.pigfarmerjc.galleryplayer

import com.pigfarmerjc.galleryplayer.core.model.MediaType

sealed class Screen {
    object Home : Screen()
    
    data class FolderVideos(
        val volumeName: String,
        val relativePath: String,
        val folderDisplayName: String
    ) : Screen()
    
    data class Player(
        val videoUri: String,
        val videoTitle: String,
        val videoList: List<LocalMediaItem> = emptyList(),
        val currentIndex: Int = 0,
        val initialPositionMs: Long = 0
    ) : Screen()
    
    data class ImageViewer(
        val images: List<LocalMediaItem>,
        val initialIndex: Int
    ) : Screen()
}

enum class HomeTab {
    VIDEOS,
    FOLDERS,
    IMAGES,
    SETTINGS
}

data class LocalMediaItem(
    val contentUri: String,
    val mediaType: MediaType,
    val volumeName: String,
    val relativePath: String,
    val displayName: String,
    val mimeType: String?,
    val fileSize: Long,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val dateModifiedEpochSeconds: Long?,
    val isGif: Boolean,
    val mediaStoreId: Long?
)

data class FolderItem(
    val volumeName: String,
    val relativePath: String,
    val displayName: String,
    val videoCount: Int,
    val coverUri: String?,
    val totalSize: Long
)
