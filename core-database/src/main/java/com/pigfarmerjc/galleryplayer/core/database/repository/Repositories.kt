package com.pigfarmerjc.galleryplayer.core.database.repository

import com.pigfarmerjc.galleryplayer.core.database.dao.MediaItemDao
import com.pigfarmerjc.galleryplayer.core.database.dao.PlaybackHistoryDao
import com.pigfarmerjc.galleryplayer.core.database.entity.MediaItemEntity
import com.pigfarmerjc.galleryplayer.core.database.entity.PlaybackHistoryEntity
import com.pigfarmerjc.galleryplayer.core.model.MediaItem
import com.pigfarmerjc.galleryplayer.core.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Mappings between Room entities and pure domain models
fun MediaItemEntity.toDomain(): MediaItem = MediaItem(
    contentUri = contentUri,
    mediaStoreId = mediaStoreId,
    volumeName = volumeName,
    relativePath = relativePath,
    displayName = displayName,
    title = title,
    durationMs = durationMs,
    width = width,
    height = height,
    dateModified = dateModified,
    mediaType = mediaType?.let { MediaType.valueOf(it) },
    sizeBytes = sizeBytes
)

fun MediaItem.toEntity(): MediaItemEntity = MediaItemEntity(
    contentUri = contentUri,
    mediaStoreId = mediaStoreId,
    volumeName = volumeName,
    relativePath = relativePath,
    displayName = displayName,
    title = title,
    durationMs = durationMs,
    width = width,
    height = height,
    dateModified = dateModified,
    mediaType = mediaType?.name,
    sizeBytes = sizeBytes
)

// Repository interfaces definitions
interface MediaRepository {
    fun getMediaItems(): Flow<List<MediaItem>>
    fun getMediaItemsInFolder(folderPath: String): Flow<List<MediaItem>>
    fun getMediaItemsOnVolume(volumeName: String): Flow<List<MediaItem>>
    suspend fun saveMediaItems(items: List<MediaItem>)
    suspend fun deleteMediaItem(contentUri: String)
}

data class PlaybackHistoryItem(
    val contentUri: String,
    val lastPlayedTime: Long,
    val playbackPositionMs: Long,
    val finished: Boolean,
    val mediaItem: MediaItem?
)

interface PlaybackHistoryRepository {
    fun getHistory(): Flow<List<PlaybackHistoryItem>>
    suspend fun saveHistory(contentUri: String, positionMs: Long, durationMs: Long, finished: Boolean)
    suspend fun deleteHistory(contentUri: String)
}

// Room database repository implementations
class RoomMediaRepository(
    private val mediaItemDao: MediaItemDao
) : MediaRepository {

    override fun getMediaItems(): Flow<List<MediaItem>> {
        return mediaItemDao.getAll().map { list -> list.map { it.toDomain() } }
    }

    override fun getMediaItemsInFolder(folderPath: String): Flow<List<MediaItem>> {
        return mediaItemDao.getByFolder(folderPath).map { list -> list.map { it.toDomain() } }
    }

    override fun getMediaItemsOnVolume(volumeName: String): Flow<List<MediaItem>> {
        return mediaItemDao.getByVolume(volumeName).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun saveMediaItems(items: List<MediaItem>) {
        mediaItemDao.upsertAll(items.map { it.toEntity() })
    }

    override suspend fun deleteMediaItem(contentUri: String) {
        mediaItemDao.deleteByUri(contentUri)
    }
}

class RoomPlaybackHistoryRepository(
    private val playbackHistoryDao: PlaybackHistoryDao
) : PlaybackHistoryRepository {

    override fun getHistory(): Flow<List<PlaybackHistoryItem>> {
        return playbackHistoryDao.getHistoryWithMediaItem().map { list ->
            list.map { item ->
                PlaybackHistoryItem(
                    contentUri = item.history.contentUri,
                    lastPlayedTime = item.history.lastPlayedTime,
                    playbackPositionMs = item.history.playbackPositionMs,
                    finished = item.history.finished,
                    mediaItem = item.mediaItem?.toDomain()
                )
            }
        }
    }

    override suspend fun saveHistory(contentUri: String, positionMs: Long, durationMs: Long, finished: Boolean) {
        val history = PlaybackHistoryEntity(
            contentUri = contentUri,
            lastPlayedTime = System.currentTimeMillis(),
            playbackPositionMs = positionMs,
            finished = finished
        )
        playbackHistoryDao.insertOrReplace(history)
    }

    override suspend fun deleteHistory(contentUri: String) {
        playbackHistoryDao.deleteByUri(contentUri)
    }
}
