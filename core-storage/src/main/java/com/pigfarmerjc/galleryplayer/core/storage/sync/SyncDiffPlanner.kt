package com.pigfarmerjc.galleryplayer.core.storage.sync

import com.pigfarmerjc.galleryplayer.core.model.MediaItem

data class SyncDiff(
    val upserts: List<MediaItem>,
    val deletedUris: List<String>,
    val insertedCount: Int,
    val updatedCount: Int
)

object SyncDiffPlanner {
    fun plan(existing: List<MediaItem>, scanned: List<MediaItem>): SyncDiff {
        val existingByUri = existing.associateBy(MediaItem::contentUri)
        val scannedUris = HashSet<String>(scanned.size)
        val upserts = ArrayList<MediaItem>()
        var insertedCount = 0
        var updatedCount = 0

        for (scannedItem in scanned) {
            scannedUris += scannedItem.contentUri
            val existingItem = existingByUri[scannedItem.contentUri]
            if (existingItem == null) {
                upserts += scannedItem
                insertedCount++
            } else if (
                existingItem.fileSize != scannedItem.fileSize ||
                existingItem.dateModifiedEpochSeconds != scannedItem.dateModifiedEpochSeconds
            ) {
                upserts += scannedItem.copy(
                    databaseId = existingItem.databaseId,
                    isFavorite = existingItem.isFavorite,
                    isHidden = existingItem.isHidden
                )
                updatedCount++
            }
        }

        return SyncDiff(
            upserts = upserts,
            deletedUris = existing.asSequence()
                .map(MediaItem::contentUri)
                .filterNot(scannedUris::contains)
                .toList(),
            insertedCount = insertedCount,
            updatedCount = updatedCount
        )
    }
}
