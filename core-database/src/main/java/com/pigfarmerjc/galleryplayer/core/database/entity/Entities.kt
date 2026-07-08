package com.pigfarmerjc.galleryplayer.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["volume_name", "media_store_id"], unique = true),
        Index(value = ["relative_path"]),
        Index(value = ["date_modified"]),
        Index(value = ["media_type"])
    ]
)
data class MediaItemEntity(
    @PrimaryKey @ColumnInfo(name = "content_uri") val contentUri: String,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long?,
    @ColumnInfo(name = "volume_name") val volumeName: String?,
    @ColumnInfo(name = "relative_path") val relativePath: String?,
    @ColumnInfo(name = "display_name") val displayName: String?,
    @ColumnInfo(name = "title") val title: String?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    @ColumnInfo(name = "width") val width: Int?,
    @ColumnInfo(name = "height") val height: Int?,
    @ColumnInfo(name = "date_modified") val dateModified: Long?,
    @ColumnInfo(name = "media_type") val mediaType: String?, // "VIDEO", "AUDIO"
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long?
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "date_modified") val dateModified: Long
)

@Entity(
    tableName = "playback_history",
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["content_uri"],
            childColumns = ["content_uri"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaybackHistoryEntity(
    @PrimaryKey @ColumnInfo(name = "content_uri") val contentUri: String,
    @ColumnInfo(name = "last_played_time") val lastPlayedTime: Long,
    @ColumnInfo(name = "playback_position_ms") val playbackPositionMs: Long,
    @ColumnInfo(name = "finished") val finished: Boolean
)

@Entity(
    tableName = "thumbnail_cache",
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["content_uri"],
            childColumns = ["content_uri"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ThumbnailCacheEntity(
    @PrimaryKey @ColumnInfo(name = "content_uri") val contentUri: String,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "last_accessed") val lastAccessed: Long
)

@Entity(tableName = "storage_volumes")
data class StorageVolumeEntity(
    @PrimaryKey @ColumnInfo(name = "volume_name") val volumeName: String,
    @ColumnInfo(name = "path") val path: String?,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "total_bytes") val totalBytes: Long,
    @ColumnInfo(name = "free_bytes") val freeBytes: Long,
    @ColumnInfo(name = "is_removable") val isRemovable: Boolean
)
