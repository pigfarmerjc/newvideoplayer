package com.pigfarmerjc.galleryplayer.core.model

data class MediaItem(
    val contentUri: String,
    val mediaStoreId: Long?,
    val volumeName: String?,
    val relativePath: String?,
    val displayName: String?,
    val title: String?,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val dateModified: Long?,
    val mediaType: MediaType?,
    val sizeBytes: Long?
)

enum class MediaType {
    VIDEO, AUDIO
}
