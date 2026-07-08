package com.pigfarmerjc.galleryplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.pigfarmerjc.galleryplayer.core.database.entity.*
import kotlinx.coroutines.flow.Flow

data class PlaybackHistoryWithMediaItem(
    @Embedded val history: PlaybackHistoryEntity,
    @Relation(
        parentColumn = "content_uri",
        entityColumn = "content_uri"
    )
    val mediaItem: MediaItemEntity?
)

@Dao
interface MediaItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(mediaItem: MediaItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceAll(mediaItems: List<MediaItemEntity>)

    @Transaction
    suspend fun upsertAll(mediaItems: List<MediaItemEntity>) {
        insertOrReplaceAll(mediaItems)
    }

    @Query("DELETE FROM media_items WHERE content_uri = :contentUri")
    suspend fun deleteByUri(contentUri: String)

    @Query("SELECT * FROM media_items")
    fun getAll(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE volume_name = :volumeName")
    fun getByVolume(volumeName: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE relative_path = :relativePath")
    fun getByFolder(relativePath: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE content_uri = :contentUri LIMIT 1")
    suspend fun getByUri(contentUri: String): MediaItemEntity?
}

@Dao
interface FolderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceAll(folders: List<FolderEntity>)

    @Transaction
    suspend fun upsertAll(folders: List<FolderEntity>) {
        insertOrReplaceAll(folders)
    }

    @Query("SELECT * FROM folders")
    fun getAll(): Flow<List<FolderEntity>>

    @Query("DELETE FROM folders")
    suspend fun deleteAll()
}

@Dao
interface PlaybackHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(history: PlaybackHistoryEntity)

    @Query("SELECT * FROM playback_history ORDER BY last_played_time DESC")
    fun getAll(): Flow<List<PlaybackHistoryEntity>>

    @Transaction
    @Query("SELECT * FROM playback_history ORDER BY last_played_time DESC")
    fun getHistoryWithMediaItem(): Flow<List<PlaybackHistoryWithMediaItem>>

    @Query("DELETE FROM playback_history WHERE content_uri = :contentUri")
    suspend fun deleteByUri(contentUri: String)
}

@Dao
interface ThumbnailCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(cache: ThumbnailCacheEntity)

    @Query("SELECT * FROM thumbnail_cache WHERE content_uri = :contentUri LIMIT 1")
    suspend fun getByUri(contentUri: String): ThumbnailCacheEntity?

    @Query("DELETE FROM thumbnail_cache WHERE content_uri = :contentUri")
    suspend fun deleteByUri(contentUri: String)

    @Query("DELETE FROM thumbnail_cache WHERE content_uri IN (SELECT content_uri FROM thumbnail_cache ORDER BY last_accessed ASC LIMIT :limit)")
    suspend fun deleteOldest(limit: Int)
}

@Dao
interface StorageVolumeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceAll(volumes: List<StorageVolumeEntity>)

    @Transaction
    suspend fun upsertAll(volumes: List<StorageVolumeEntity>) {
        insertOrReplaceAll(volumes)
    }

    @Query("SELECT * FROM storage_volumes")
    fun getAll(): Flow<List<StorageVolumeEntity>>
}
