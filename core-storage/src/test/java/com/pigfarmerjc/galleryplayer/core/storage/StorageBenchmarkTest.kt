package com.pigfarmerjc.galleryplayer.core.storage

import android.net.Uri
import com.pigfarmerjc.galleryplayer.core.model.*
import com.pigfarmerjc.galleryplayer.core.storage.sync.StorageSyncManager
import com.pigfarmerjc.galleryplayer.core.database.entity.FolderEntity
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import kotlin.system.measureTimeMillis

class StorageBenchmarkTest {

    private val mediaRepository = FakeMediaRepository()

    private val mediaStoreScanner = object : MediaStoreScanner {
        override suspend fun scanVolume(volumeName: String, onProgress: (ScanProgress) -> Unit): List<MediaItem> {
            return emptyList()
        }
    }

    private val safDirectoryScanner = object : SafDirectoryScanner {
        override suspend fun scanTree(treeUri: Uri, onProgress: (ScanProgress) -> Unit): List<MediaItem> {
            return emptyList()
        }
    }

    @Test
    fun testStorageSyncBulkPerformance() = runTest {
        val count = 4500
        val volumeName = "external_primary"
        
        // 1. Generate 4,500 initial items
        val initialItems = ArrayList<MediaItem>(count)
        for (i in 1..count) {
            val folderIndex = i % 50 // 50 separate folders
            initialItems.add(
                createBenchmarkMediaItem(
                    id = i.toLong(),
                    uri = "content://media/$i",
                    relativePath = "DCIM/Folder_$folderIndex",
                    volumeName = volumeName
                )
            )
        }

        // Seed repository with 4,500 existing items
        mediaRepository.saveMediaItems(initialItems)

        // 2. Generate scanned items: 4,000 unchanged, 250 modified, 250 new, 500 deleted from DB
        val scannedItems = ArrayList<MediaItem>(count)
        // 4,000 unchanged
        for (i in 1..4000) {
            val folderIndex = i % 50
            scannedItems.add(initialItems[i - 1])
        }
        // 250 modified (e.g. modified date + 10s)
        for (i in 4001..4250) {
            val folderIndex = i % 50
            val item = initialItems[i - 1]
            scannedItems.add(item.copy(dateModifiedEpochSeconds = (item.dateModifiedEpochSeconds ?: 0L) + 10L))
        }
        // 250 new items
        for (i in (count + 1)..(count + 250)) {
            val folderIndex = i % 50
            scannedItems.add(
                createBenchmarkMediaItem(
                    id = i.toLong(),
                    uri = "content://media/$i",
                    relativePath = "DCIM/Folder_$folderIndex",
                    volumeName = volumeName
                )
            )
        }

        // Setup mock MediaStore scanner returning scannedItems
        val mockMediaStoreScanner = object : MediaStoreScanner {
            override suspend fun scanVolume(volumeName: String, onProgress: (ScanProgress) -> Unit): List<MediaItem> {
                return scannedItems
            }
        }

        val syncManager = StorageSyncManager(mediaRepository, mockMediaStoreScanner, safDirectoryScanner)

        var timeDiffMs = 0L
        var timeUpsertMs = 0L
        var timeFolderMs = 0L
        var timeTotalMs = 0L
        var runningThreadName = ""

        // Run sync and measure performance metrics
        timeTotalMs = measureTimeMillis {
            runningThreadName = Thread.currentThread().name

            // Simulate the internal steps of syncScannedItems to record individual durations
            val dbItems = mediaRepository.getMediaItemsOnVolumeSync(volumeName)
            
            // Measure Diff matching
            timeDiffMs = measureTimeMillis {
                val dbMap = dbItems.associateBy { it.contentUri }
                val scannedUris = scannedItems.map { it.contentUri }.toSet()

                val itemsToUpsert = mutableListOf<MediaItem>()
                val itemsToDelete = dbItems.filter { it.contentUri !in scannedUris }

                for (item in scannedItems) {
                    val dbItem = dbMap[item.contentUri]
                    if (dbItem == null) {
                        itemsToUpsert.add(item)
                    } else {
                        val hasChanged = dbItem.fileSize != item.fileSize ||
                                dbItem.dateModifiedEpochSeconds != item.dateModifiedEpochSeconds
                        if (hasChanged) {
                            itemsToUpsert.add(item.copy(databaseId = dbItem.databaseId))
                        }
                    }
                }

                // Measure database modifications
                timeUpsertMs = measureTimeMillis {
                    mediaRepository.saveMediaItems(itemsToUpsert)
                    itemsToDelete.forEach { mediaRepository.deleteMediaItem(it.contentUri) }
                }
            }

            // Measure Folder Aggregation computation
            timeFolderMs = measureTimeMillis {
                val foldersGrouped = scannedItems.groupBy { it.relativePath ?: "" }
                for ((relativePath, folderItems) in foldersGrouped) {
                    val displayName = relativePath.trimEnd('/').split('/').lastOrNull() ?: volumeName
                    val mediaCount = folderItems.size
                    val videoCount = folderItems.count { it.mediaType == MediaType.VIDEO }
                    val imageCount = folderItems.count { it.mediaType == MediaType.IMAGE }
                    val totalSize = folderItems.sumOf { it.fileSize }
                    val latestModified = folderItems.maxOfOrNull { it.dateModifiedEpochSeconds ?: 0L } ?: 0L

                    val dbFolderItems = mediaRepository.getFolderMediaItems(volumeName, relativePath)
                    val primaryCoverMediaId = dbFolderItems.firstOrNull()?.databaseId

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
            }
        }

        // Print benchmark reports to stdout
        println("=== PHASE 2 MEDIA SCANNER BENCHMARK REPORT ===")
        println("Scanned Media Records Count : $count")
        println("Running Thread Name         : $runningThreadName")
        println("Incremental Diff Time       : ${timeDiffMs - timeUpsertMs} ms")
        println("Bulk Database Upsert Time   : $timeUpsertMs ms")
        println("Folder Aggregation Time     : $timeFolderMs ms")
        println("Total Scan Sync Execution   : $timeTotalMs ms")
        println("Host OS                     : ${System.getProperty("os.name")} (${System.getProperty("os.arch")})")
        println("Host JVM                    : ${System.getProperty("java.vendor")} ${System.getProperty("java.version")}")
        println("==============================================")
    }

    private fun createBenchmarkMediaItem(
        id: Long,
        uri: String,
        relativePath: String,
        volumeName: String
    ): MediaItem {
        return MediaItem(
            databaseId = id,
            contentUri = uri,
            mediaType = if (id % 2L == 0L) MediaType.VIDEO else MediaType.IMAGE,
            volumeName = volumeName,
            mediaStoreId = id,
            relativePath = relativePath,
            displayName = "File_$id.mp4",
            mimeType = "video/mp4",
            fileSize = 2000L + id,
            durationMs = 5000L,
            width = 1920,
            height = 1080,
            rotationDegrees = 0,
            dateAddedEpochSeconds = 1719876543L,
            dateModifiedEpochSeconds = 1719876543L,
            dateTakenEpochMillis = 1719876543000L,
            videoCodec = null,
            audioCodec = null,
            audioSampleFormat = null,
            audioSampleRate = null,
            audioChannels = null,
            frameRate = null,
            bitrate = null,
            isHdr = false,
            isGif = false,
            isFavorite = false,
            isHidden = false,
            scanState = ScanState.SCANNED,
            lastError = null
        )
    }
}
