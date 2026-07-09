package com.pigfarmerjc.galleryplayer.core.storage

import com.pigfarmerjc.galleryplayer.core.database.entity.FolderEntity
import com.pigfarmerjc.galleryplayer.core.database.repository.MediaRepository
import com.pigfarmerjc.galleryplayer.core.model.MediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeMediaRepository : MediaRepository {

    val mediaItems = mutableMapOf<String, MediaItem>()
    val folders = mutableMapOf<String, FolderEntity>()
    var nextId = 1L

    override fun getMediaItems(): Flow<List<MediaItem>> = flowOf(mediaItems.values.toList())

    override fun getMediaItemsInFolder(folderPath: String): Flow<List<MediaItem>> {
        return flowOf(mediaItems.values.filter { it.relativePath == folderPath })
    }

    override fun getMediaItemsOnVolume(volumeName: String): Flow<List<MediaItem>> {
        return flowOf(mediaItems.values.filter { it.volumeName == volumeName })
    }

    override suspend fun getMediaItemsOnVolumeSync(volumeName: String): List<MediaItem> {
        return mediaItems.values.filter { it.volumeName == volumeName }
    }

    override suspend fun getFolderMediaItems(volumeName: String, relativePath: String): List<MediaItem> {
        return mediaItems.values.filter { it.volumeName == volumeName && (it.relativePath ?: "") == relativePath }
    }

    override suspend fun getFolderByVolumeAndPath(volumeName: String, relativePath: String): FolderEntity? {
        return folders["$volumeName:$relativePath"]
    }

    override suspend fun saveFolder(folder: FolderEntity) {
        val key = "${folder.volumeName}:${folder.relativePath}"
        val existing = folders[key]
        if (existing != null) {
            folders[key] = folder.copy(folderId = existing.folderId)
        } else {
            folders[key] = folder.copy(folderId = nextId++)
        }
    }

    override suspend fun deleteFolder(volumeName: String, relativePath: String) {
        folders.remove("$volumeName:$relativePath")
    }

    override suspend fun saveMediaItems(items: List<MediaItem>) {
        items.forEach { item ->
            val existing = mediaItems[item.contentUri]
            if (existing != null) {
                mediaItems[item.contentUri] = item.copy(databaseId = existing.databaseId)
            } else {
                mediaItems[item.contentUri] = item.copy(databaseId = nextId++)
            }
        }
    }

    override suspend fun deleteMediaItem(contentUri: String) {
        mediaItems.remove(contentUri)
    }
}
