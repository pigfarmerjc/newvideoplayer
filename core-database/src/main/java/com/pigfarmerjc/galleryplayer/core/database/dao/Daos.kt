package com.pigfarmerjc.galleryplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.pigfarmerjc.galleryplayer.core.database.entity.*
import kotlinx.coroutines.flow.Flow

data class PlaybackHistoryWithMediaItem(
    @Embedded val history: PlaybackHistoryEntity,
    @Relation(
        parentColumn = "media_id",
        entityColumn = "id"
    )
    val mediaItem: MediaItemEntity?
)

@Dao
abstract class MediaItemDao {
    @Insert
    abstract suspend fun insert(mediaItem: MediaItemEntity): Long

    @Update
    abstract suspend fun update(mediaItem: MediaItemEntity)

    @Transaction
    open suspend fun upsert(mediaItem: MediaItemEntity): Long {
        val existing = getByUri(mediaItem.contentUri)
        return if (existing != null) {
            update(mediaItem.copy(id = existing.id))
            existing.id
        } else {
            insert(mediaItem)
        }
    }

    @Transaction
    open suspend fun upsertAll(mediaItems: List<MediaItemEntity>) {
        mediaItems.forEach { upsert(it) }
    }

    @Query("DELETE FROM media_items WHERE content_uri = :contentUri")
    abstract suspend fun deleteByUri(contentUri: String)

    @Query("DELETE FROM media_items WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    @Query("SELECT * FROM media_items")
    abstract fun getAll(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE volume_name = :volumeName")
    abstract fun getByVolume(volumeName: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE relative_path = :relativePath")
    abstract fun getByFolder(relativePath: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE volume_name = :volumeName")
    abstract suspend fun getByVolumeSync(volumeName: String): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE volume_name = :volumeName AND relative_path = :relativePath")
    abstract suspend fun getByFolderSync(volumeName: String, relativePath: String): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE content_uri = :contentUri LIMIT 1")
    abstract suspend fun getByUri(contentUri: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: Long): MediaItemEntity?
}

@Dao
abstract class FolderDao {
    @Insert
    abstract suspend fun insert(folder: FolderEntity): Long

    @Update
    abstract suspend fun update(folder: FolderEntity)

    @Query("SELECT * FROM folders WHERE volume_name = :volumeName AND relative_path = :relativePath LIMIT 1")
    abstract suspend fun getByVolumeAndPath(volumeName: String, relativePath: String): FolderEntity?

    @Query("DELETE FROM folders WHERE volume_name = :volumeName AND relative_path = :relativePath")
    abstract suspend fun deleteByVolumeAndPath(volumeName: String, relativePath: String)

    @Transaction
    open suspend fun upsert(folder: FolderEntity): Long {
        val existing = getByVolumeAndPath(folder.volumeName, folder.relativePath)
        return if (existing != null) {
            update(folder.copy(folderId = existing.folderId))
            existing.folderId
        } else {
            insert(folder)
        }
    }

    @Transaction
    open suspend fun upsertAll(folders: List<FolderEntity>) {
        folders.forEach { upsert(it) }
    }

    @Query("SELECT * FROM folders")
    abstract fun getAll(): Flow<List<FolderEntity>>

    @Query("DELETE FROM folders")
    abstract suspend fun deleteAll()
}

@Dao
abstract class PlaybackHistoryDao {
    @Upsert
    abstract suspend fun upsert(history: PlaybackHistoryEntity)

    @Query("SELECT * FROM playback_history ORDER BY last_played_at DESC")
    abstract fun getAll(): Flow<List<PlaybackHistoryEntity>>

    @Transaction
    @Query("SELECT * FROM playback_history ORDER BY last_played_at DESC")
    abstract fun getHistoryWithMediaItem(): Flow<List<PlaybackHistoryWithMediaItem>>

    @Query("SELECT * FROM playback_history WHERE media_id = :mediaId LIMIT 1")
    abstract suspend fun getByMediaId(mediaId: Long): PlaybackHistoryEntity?

    @Query("DELETE FROM playback_history WHERE media_id = (SELECT id FROM media_items WHERE content_uri = :contentUri)")
    abstract suspend fun deleteByUri(contentUri: String)

    @Query("DELETE FROM playback_history WHERE media_id = :mediaId")
    abstract suspend fun deleteByMediaId(mediaId: Long)
}

@Dao
abstract class ThumbnailCacheDao {
    @Upsert
    abstract suspend fun upsert(cache: ThumbnailCacheEntity)

    @Query("SELECT * FROM thumbnail_cache WHERE media_id = :mediaId")
    abstract suspend fun getByMediaId(mediaId: Long): List<ThumbnailCacheEntity>

    @Query("SELECT * FROM thumbnail_cache WHERE media_id = :mediaId AND thumbnail_type = :type AND width = :width AND height = :height AND frame_position_ms = :framePos AND media_modified_time = :modTime LIMIT 1")
    abstract suspend fun getSpecific(
        mediaId: Long,
        type: String,
        width: Int,
        height: Int,
        framePos: Long,
        modTime: Long
    ): ThumbnailCacheEntity?

    @Query("DELETE FROM thumbnail_cache WHERE media_id = (SELECT id FROM media_items WHERE content_uri = :contentUri)")
    abstract suspend fun deleteByUri(contentUri: String)

    @Query("DELETE FROM thumbnail_cache WHERE media_id = :mediaId")
    abstract suspend fun deleteByMediaId(mediaId: Long)

    @Query("DELETE FROM thumbnail_cache WHERE media_id IN (SELECT media_id FROM thumbnail_cache ORDER BY last_access_time ASC LIMIT :limit)")
    abstract suspend fun deleteOldest(limit: Int)
}

@Dao
abstract class StorageVolumeDao {
    @Upsert
    abstract suspend fun upsertAll(volumes: List<StorageVolumeEntity>)

    @Query("SELECT * FROM storage_volumes")
    abstract fun getAll(): Flow<List<StorageVolumeEntity>>
}
