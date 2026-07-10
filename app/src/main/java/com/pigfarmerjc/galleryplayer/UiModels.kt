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

fun LocalMediaItem.toMediaItem(): com.pigfarmerjc.galleryplayer.core.model.MediaItem {
    return com.pigfarmerjc.galleryplayer.core.model.MediaItem(
        databaseId = 0L,
        contentUri = this.contentUri,
        mediaType = this.mediaType,
        volumeName = this.volumeName,
        mediaStoreId = this.mediaStoreId,
        relativePath = this.relativePath,
        displayName = this.displayName,
        mimeType = this.mimeType,
        fileSize = this.fileSize,
        durationMs = this.durationMs,
        width = this.width,
        height = this.height,
        rotationDegrees = null,
        dateAddedEpochSeconds = this.dateModifiedEpochSeconds ?: 0L,
        dateModifiedEpochSeconds = this.dateModifiedEpochSeconds ?: 0L,
        dateTakenEpochMillis = null,
        videoCodec = null,
        audioCodec = null,
        audioSampleFormat = null,
        audioSampleRate = null,
        audioChannels = null,
        frameRate = null,
        bitrate = null,
        isHdr = false,
        isGif = this.isGif,
        isFavorite = false,
        isHidden = false,
        scanState = com.pigfarmerjc.galleryplayer.core.model.ScanState.SCANNED,
        lastError = null
    )
}


