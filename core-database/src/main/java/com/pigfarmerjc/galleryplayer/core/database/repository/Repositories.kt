package com.pigfarmerjc.galleryplayer.core.database.repository

import com.pigfarmerjc.galleryplayer.core.database.dao.MediaItemDao
import com.pigfarmerjc.galleryplayer.core.database.dao.PlaybackHistoryDao
import com.pigfarmerjc.galleryplayer.core.database.dao.FolderDao
import com.pigfarmerjc.galleryplayer.core.database.entity.MediaItemEntity
import com.pigfarmerjc.galleryplayer.core.database.entity.PlaybackHistoryEntity
import com.pigfarmerjc.galleryplayer.core.database.entity.FolderEntity
import com.pigfarmerjc.galleryplayer.core.model.MediaItem
import com.pigfarmerjc.galleryplayer.core.model.MediaType
import com.pigfarmerjc.galleryplayer.core.model.ScanState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Mappings between Room entities and pure domain models
fun MediaItemEntity.toDomain(): MediaItem = MediaItem(
    databaseId = id,
    contentUri = contentUri,
    mediaType = MediaType.valueOf(mediaType),
    volumeName = volumeName,
    mediaStoreId = mediaStoreId,
    relativePath = relativePath,
    displayName = displayName,
    mimeType = mimeType,
    fileSize = fileSize,
    durationMs = durationMs,
    width = width,
    height = height,
    rotationDegrees = rotationDegrees,
    dateAddedEpochSeconds = dateAddedEpochSeconds,
    dateModifiedEpochSeconds = dateModifiedEpochSeconds,
    dateTakenEpochMillis = dateTakenEpochMillis,
    videoCodec = videoCodec,
    audioCodec = audioCodec,
    audioSampleFormat = audioSampleFormat,
    audioSampleRate = audioSampleRate,
    audioChannels = audioChannels,
    frameRate = frameRate,
    bitrate = bitrate,
    isHdr = isHdr,
    isGif = isGif,
    isFavorite = isFavorite,
    isHidden = isHidden,
    scanState = ScanState.valueOf(scanState),
    lastError = lastError
)

fun MediaItem.toEntity(): MediaItemEntity = MediaItemEntity(
    id = databaseId,
    contentUri = contentUri,
    mediaType = mediaType.name,
    volumeName = volumeName,
    mediaStoreId = mediaStoreId,
    relativePath = relativePath,
    displayName = displayName,
    mimeType = mimeType,
    fileSize = fileSize,
    durationMs = durationMs,
    width = width,
    height = height,
    rotationDegrees = rotationDegrees,
    dateAddedEpochSeconds = dateAddedEpochSeconds,
    dateModifiedEpochSeconds = dateModifiedEpochSeconds,
    dateTakenEpochMillis = dateTakenEpochMillis,
    videoCodec = videoCodec,
    audioCodec = audioCodec,
    audioSampleFormat = audioSampleFormat,
    audioSampleRate = audioSampleRate,
    audioChannels = audioChannels,
    frameRate = frameRate,
    bitrate = bitrate,
    isHdr = isHdr,
    isGif = isGif,
    isFavorite = isFavorite,
    isHidden = isHidden,
    scanState = scanState.name,
    lastError = lastError
)

// Repository interfaces definitions
interface MediaRepository {
    fun getMediaItems(): Flow<List<MediaItem>>
    fun getMediaItemsInFolder(folderPath: String): Flow<List<MediaItem>>
    fun getMediaItemsOnVolume(volumeName: String): Flow<List<MediaItem>>
    suspend fun getMediaItemsOnVolumeSync(volumeName: String): List<MediaItem>
    suspend fun getFolderMediaItems(volumeName: String, relativePath: String): List<MediaItem>
    suspend fun getFolderByVolumeAndPath(volumeName: String, relativePath: String): FolderEntity?
    suspend fun saveFolder(folder: FolderEntity)
    suspend fun deleteFolder(volumeName: String, relativePath: String)
    suspend fun saveMediaItems(items: List<MediaItem>)
    suspend fun deleteMediaItem(contentUri: String)
}

data class PlaybackHistoryItem(
    val mediaId: Long,
    val contentUri: String,
    val lastPlayedTime: Long,
    val playbackPositionMs: Long,
    val durationMs: Long,
    val finished: Boolean,
    val playCount: Int,
    val preferredSpeed: Float,
    val mediaItem: MediaItem?
)

interface PlaybackHistoryRepository {
    fun getHistory(): Flow<List<PlaybackHistoryItem>>
    suspend fun saveHistory(contentUri: String, positionMs: Long, durationMs: Long, finished: Boolean)
    suspend fun deleteHistory(contentUri: String)
}

// Room database repository implementations
class RoomMediaRepository(
    private val mediaItemDao: MediaItemDao,
    private val folderDao: FolderDao
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

    override suspend fun getMediaItemsOnVolumeSync(volumeName: String): List<MediaItem> {
        return mediaItemDao.getByVolumeSync(volumeName).map { it.toDomain() }
    }

    override suspend fun getFolderMediaItems(volumeName: String, relativePath: String): List<MediaItem> {
        return mediaItemDao.getByFolderSync(volumeName, relativePath).map { it.toDomain() }
    }

    override suspend fun getFolderByVolumeAndPath(volumeName: String, relativePath: String): FolderEntity? {
        return folderDao.getByVolumeAndPath(volumeName, relativePath)
    }

    override suspend fun saveFolder(folder: FolderEntity) {
        folderDao.upsert(folder)
    }

    override suspend fun deleteFolder(volumeName: String, relativePath: String) {
        folderDao.deleteByVolumeAndPath(volumeName, relativePath)
    }

    override suspend fun saveMediaItems(items: List<MediaItem>) {
        mediaItemDao.upsertAll(items.map { it.toEntity() })
    }

    override suspend fun deleteMediaItem(contentUri: String) {
        mediaItemDao.deleteByUri(contentUri)
    }
}

class RoomPlaybackHistoryRepository(
    private val playbackHistoryDao: PlaybackHistoryDao,
    private val mediaItemDao: MediaItemDao
) : PlaybackHistoryRepository {

    companion object {
        const val COMPLETION_THRESHOLD = 0.90
    }

    override fun getHistory(): Flow<List<PlaybackHistoryItem>> {
        return playbackHistoryDao.getHistoryWithMediaItem().map { list ->
            list.map { item ->
                PlaybackHistoryItem(
                    mediaId = item.history.mediaId,
                    contentUri = item.mediaItem?.contentUri ?: "",
                    lastPlayedTime = item.history.lastPlayedAt,
                    playbackPositionMs = item.history.positionMs,
                    durationMs = item.history.durationMs,
                    finished = item.history.completed,
                    playCount = item.history.playCount,
                    preferredSpeed = item.history.preferredSpeed,
                    mediaItem = item.mediaItem?.toDomain()
                )
            }
        }
    }

    override suspend fun saveHistory(contentUri: String, positionMs: Long, durationMs: Long, finished: Boolean) {
        val mediaItem = mediaItemDao.getByUri(contentUri) ?: return
        val existing = playbackHistoryDao.getByMediaId(mediaItem.id)

        // Rule: if position / duration >= 0.90, mark as completed
        val calculatedFinished = if (durationMs > 0) {
            (positionMs.toDouble() / durationMs.toDouble()) >= COMPLETION_THRESHOLD
        } else {
            finished
        }

        val newPlayCount = if (existing != null) {
            existing.playCount + 1
        } else {
            1
        }

        val history = PlaybackHistoryEntity(
            mediaId = mediaItem.id,
            positionMs = positionMs,
            durationMs = durationMs,
            lastPlayedAt = System.currentTimeMillis(),
            completed = calculatedFinished,
            playCount = newPlayCount,
            preferredSpeed = existing?.preferredSpeed ?: 1.0f
        )
        playbackHistoryDao.upsert(history)
    }

    override suspend fun deleteHistory(contentUri: String) {
        playbackHistoryDao.deleteByUri(contentUri)
    }
}
