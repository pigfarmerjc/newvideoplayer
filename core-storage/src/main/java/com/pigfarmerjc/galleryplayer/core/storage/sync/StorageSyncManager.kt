package com.pigfarmerjc.galleryplayer.core.storage.sync

import android.net.Uri
import android.util.Log
import com.pigfarmerjc.galleryplayer.core.database.entity.FolderEntity
import com.pigfarmerjc.galleryplayer.core.database.repository.MediaRepository
import com.pigfarmerjc.galleryplayer.core.model.*
import com.pigfarmerjc.galleryplayer.core.storage.MediaStoreScanner
import com.pigfarmerjc.galleryplayer.core.storage.SafDirectoryScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class StorageSyncManager(
    private val mediaRepository: MediaRepository,
    private val mediaStoreScanner: MediaStoreScanner,
    private val safDirectoryScanner: SafDirectoryScanner
) {

    suspend fun scanAndSyncMediaStore(
        volumeName: String,
        onProgress: (ScanProgress) -> Unit
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()

        // 1. Run MediaStore Scan
        val scannedItems = mediaStoreScanner.scanVolume(volumeName) { progress ->
            onProgress(progress)
        }

        currentCoroutineContext().ensureActive()

        // 2. Perform Incremental Sync
        syncScannedItems(
            volumeName = volumeName,
            sourceType = ScanSourceType.MEDIASTORE,
            scannedItems = scannedItems,
            onProgress = onProgress
        )

        return@withContext scannedItems
    }

    suspend fun scanAndSyncSaf(
        treeUri: Uri,
        onProgress: (ScanProgress) -> Unit
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()

        val volumeKey = "SAF:${treeUri.toString().hashCode()}"

        // 1. Run SAF Scan
        val scannedItems = safDirectoryScanner.scanTree(treeUri) { progress ->
            onProgress(progress)
        }

        currentCoroutineContext().ensureActive()

        // 2. Perform Incremental Sync
        syncScannedItems(
            volumeName = volumeKey,
            sourceType = ScanSourceType.SAF_TREE,
            scannedItems = scannedItems,
            onProgress = onProgress
        )

        return@withContext scannedItems
    }

    private suspend fun syncScannedItems(
        volumeName: String,
        sourceType: ScanSourceType,
        scannedItems: List<MediaItem>,
        onProgress: (ScanProgress) -> Unit
    ) {
        onProgress(
            ScanProgress(
                phase = ScanPhase.PERSISTING,
                sourceType = sourceType,
                volumeName = volumeName,
                scannedCount = scannedItems.size,
                insertedCount = 0,
                updatedCount = 0,
                deletedCount = 0,
                failedItems = 0,
                lastError = null,
                currentPath = "Comparing with database changes..."
            )
        )

        // Query existing items on the DB for this volume
        val dbItems = mediaRepository.getMediaItemsOnVolumeSync(volumeName)
        val dbMap = dbItems.associateBy { it.contentUri }

        val scannedUris = scannedItems.map { it.contentUri }.toSet()

        val itemsToUpsert = mutableListOf<MediaItem>()
        var insertedCount = 0
        var updatedCount = 0

        for (item in scannedItems) {
            val dbItem = dbMap[item.contentUri]
            if (dbItem == null) {
                // New Item
                itemsToUpsert.add(item)
                insertedCount++
            } else {
                // Modification Check
                val hasChanged = dbItem.fileSize != item.fileSize ||
                        dbItem.dateModifiedEpochSeconds != item.dateModifiedEpochSeconds

                if (hasChanged) {
                    // Retain db primary key to do update
                    itemsToUpsert.add(item.copy(databaseId = dbItem.databaseId))
                    updatedCount++
                }
            }
        }

        // Deletion Scope: ONLY items associated with this volume that are no longer present in the scan
        val itemsToDelete = dbItems.filter { it.contentUri !in scannedUris }
        for (item in itemsToDelete) {
            mediaRepository.deleteMediaItem(item.contentUri)
        }

        // Batch save new and modified items
        if (itemsToUpsert.isNotEmpty()) {
            mediaRepository.saveMediaItems(itemsToUpsert)
        }

        currentCoroutineContext().ensureActive()

        // 3. Folder Aggregates Recalculation
        onProgress(
            ScanProgress(
                phase = ScanPhase.PERSISTING,
                sourceType = sourceType,
                volumeName = volumeName,
                scannedCount = scannedItems.size,
                insertedCount = insertedCount,
                updatedCount = updatedCount,
                deletedCount = itemsToDelete.size,
                failedItems = 0,
                lastError = null,
                currentPath = "Recalculating directory aggregates..."
            )
        )

        recalculateFolders(volumeName, scannedItems, itemsToDelete)

        onProgress(
            ScanProgress(
                phase = ScanPhase.COMPLETED,
                sourceType = sourceType,
                volumeName = volumeName,
                scannedCount = scannedItems.size,
                insertedCount = insertedCount,
                updatedCount = updatedCount,
                deletedCount = itemsToDelete.size,
                failedItems = 0,
                lastError = null,
                currentPath = "Synchronization completed successfully"
            )
        )
    }

    private suspend fun recalculateFolders(
        volumeName: String,
        scannedItems: List<MediaItem>,
        deletedItems: List<MediaItem>
    ) {
        // Group final scanned items by relativePath
        // Fallback relativePath to root as "" if null
        val foldersGrouped = scannedItems.groupBy { it.relativePath ?: "" }

        // 1. Update or create folders that contain items
        for ((relativePath, folderItems) in foldersGrouped) {
            val displayName = relativePath.trimEnd('/').split('/').lastOrNull()?.takeIf { it.isNotEmpty() } ?: volumeName

            val mediaCount = folderItems.size
            val videoCount = folderItems.count { it.mediaType == MediaType.VIDEO }
            val imageCount = folderItems.count { it.mediaType == MediaType.IMAGE || it.mediaType == MediaType.GIF }
            val totalSize = folderItems.sumOf { it.fileSize }
            val latestModified = folderItems.maxOfOrNull { it.dateModifiedEpochSeconds ?: 0L } ?: 0L

            // To get database IDs for cover media, retrieve the items from database in this directory
            val dbFolderItems = mediaRepository.getFolderMediaItems(volumeName, relativePath)
            
            // Cover Media Priority: latest modified VIDEO, fallback to latest modified IMAGE/GIF
            val primaryCoverMediaId = dbFolderItems
                .filter { it.mediaType == MediaType.VIDEO }
                .maxByOrNull { it.dateModifiedEpochSeconds ?: 0L }?.databaseId
                ?: dbFolderItems
                    .filter { it.mediaType == MediaType.IMAGE || it.mediaType == MediaType.GIF }
                    .maxByOrNull { it.dateModifiedEpochSeconds ?: 0L }?.databaseId

            val folderEntity = FolderEntity(
                volumeName = volumeName,
                relativePath = relativePath,
                displayName = displayName,
                mediaCount = mediaCount,
                videoCount = videoCount,
                imageCount = imageCount,
                totalSize = totalSize,
                primaryCoverMediaId = primaryCoverMediaId,
                latestModified = latestModified
            )
            mediaRepository.saveFolder(folderEntity)
        }

        // 2. Identify folders that were deleted (now have 0 items)
        val deletedFolderPaths = deletedItems.map { it.relativePath ?: "" }.toSet()
        for (path in deletedFolderPaths) {
            val activeItems = mediaRepository.getFolderMediaItems(volumeName, path)
            if (activeItems.isEmpty()) {
                mediaRepository.deleteFolder(volumeName, path)
            }
        }
    }
}
