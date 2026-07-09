package com.pigfarmerjc.galleryplayer.core.model

enum class MediaType {
    VIDEO,
    IMAGE,
    GIF,
    AUDIO
}

enum class ScanState {
    PENDING,
    SCANNED,
    FAILED
}

data class MediaItem(
    val databaseId: Long,
    val contentUri: String,
    val mediaType: MediaType,
    val volumeName: String,
    val mediaStoreId: Long?,
    val relativePath: String?,
    val displayName: String,
    val mimeType: String?,
    val fileSize: Long,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val rotationDegrees: Int?,
    val dateAddedEpochSeconds: Long?,
    val dateModifiedEpochSeconds: Long?,
    val dateTakenEpochMillis: Long?,
    val videoCodec: String?,
    val audioCodec: String?,
    val audioSampleFormat: String?,
    val audioSampleRate: Int?,
    val audioChannels: Int?,
    val frameRate: Double?,
    val bitrate: Long?,
    val isHdr: Boolean,
    val isGif: Boolean,
    val isFavorite: Boolean,
    val isHidden: Boolean,
    val scanState: ScanState,
    val lastError: String?
)
