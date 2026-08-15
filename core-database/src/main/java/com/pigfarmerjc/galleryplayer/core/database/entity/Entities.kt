package com.pigfarmerjc.galleryplayer.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["content_uri"], unique = true),
        Index(value = ["volume_name", "media_store_id", "media_type"]),
        Index(value = ["relative_path"]),
        Index(value = ["date_modified_epoch_seconds"]),
        Index(value = ["media_type"]),
        Index(value = ["is_favorite"]),
        Index(value = ["is_hidden"])
    ]
)
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "content_uri") val contentUri: String,
    @ColumnInfo(name = "media_type") val mediaType: String,
    @ColumnInfo(name = "volume_name") val volumeName: String,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long?,
    @ColumnInfo(name = "relative_path") val relativePath: String?,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    @ColumnInfo(name = "width") val width: Int?,
    @ColumnInfo(name = "height") val height: Int?,
    @ColumnInfo(name = "rotation_degrees") val rotationDegrees: Int?,
    @ColumnInfo(name = "date_added_epoch_seconds") val dateAddedEpochSeconds: Long?,
    @ColumnInfo(name = "date_modified_epoch_seconds") val dateModifiedEpochSeconds: Long?,
    @ColumnInfo(name = "date_taken_epoch_millis") val dateTakenEpochMillis: Long?,
    @ColumnInfo(name = "video_codec") val videoCodec: String?,
    @ColumnInfo(name = "audio_codec") val audioCodec: String?,
    @ColumnInfo(name = "audio_sample_format") val audioSampleFormat: String?,
    @ColumnInfo(name = "audio_sample_rate") val audioSampleRate: Int?,
    @ColumnInfo(name = "audio_channels") val audioChannels: Int?,
    @ColumnInfo(name = "frame_rate") val frameRate: Double?,
    @ColumnInfo(name = "bitrate") val bitrate: Long?,
    @ColumnInfo(name = "is_hdr") val isHdr: Boolean = false,
    @ColumnInfo(name = "is_gif") val isGif: Boolean = false,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "is_hidden") val isHidden: Boolean = false,
    @ColumnInfo(name = "scan_state") val scanState: String,
    @ColumnInfo(name = "last_error") val lastError: String?
)

@Entity(
    tableName = "folders",
    indices = [
        Index(value = ["volume_name", "relative_path"], unique = true)
    ]
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "folder_id") val folderId: Long = 0L,
    @ColumnInfo(name = "volume_name") val volumeName: String,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "media_count") val mediaCount: Int = 0,
    @ColumnInfo(name = "video_count") val videoCount: Int = 0,
    @ColumnInfo(name = "image_count") val imageCount: Int = 0,
    @ColumnInfo(name = "total_size") val totalSize: Long = 0L,
    @ColumnInfo(name = "primary_cover_media_id") val primaryCoverMediaId: Long? = null,
    @ColumnInfo(name = "latest_modified") val latestModified: Long = 0L,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "is_hidden") val isHidden: Boolean = false
)

@Entity(
    tableName = "thumbnail_cache",
    primaryKeys = ["media_id", "thumbnail_type", "width", "height", "frame_position_ms", "media_modified_time"],
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["media_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["media_id"])
    ]
)
data class ThumbnailCacheEntity(
    @ColumnInfo(name = "media_id") val mediaId: Long,
    @ColumnInfo(name = "thumbnail_type") val thumbnailType: String,
    @ColumnInfo(name = "width") val width: Int,
    @ColumnInfo(name = "height") val height: Int,
    @ColumnInfo(name = "frame_position_ms") val framePositionMs: Long,
    @ColumnInfo(name = "media_modified_time") val mediaModifiedTime: Long,
    @ColumnInfo(name = "cache_path") val cachePath: String,
    @ColumnInfo(name = "generation_state") val generationState: String,
    @ColumnInfo(name = "last_access_time") val lastAccessTime: Long
)

@Entity(
    tableName = "playback_history",
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["media_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["last_played_at"]),
        Index(value = ["completed"])
    ]
)
data class PlaybackHistoryEntity(
    @PrimaryKey @ColumnInfo(name = "media_id") val mediaId: Long,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long,
    @ColumnInfo(name = "completed") val completed: Boolean,
    @ColumnInfo(name = "play_count") val playCount: Int,
    @ColumnInfo(name = "preferred_speed") val preferredSpeed: Float
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
