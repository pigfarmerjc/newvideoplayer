package com.pigfarmerjc.galleryplayer.core.storage

import android.net.Uri
import com.pigfarmerjc.galleryplayer.core.model.*
import com.pigfarmerjc.galleryplayer.core.storage.sync.StorageSyncManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FolderAggregationTest {

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

    private val syncManager = StorageSyncManager(mediaRepository, mediaStoreScanner, safDirectoryScanner)

    @Test
    fun testFolderAggregationPathFallbackAndCoverPriority() = runTest {
        // Prepare mock scanned list
        val itemVideoOld = createMediaItem("content://media/1", MediaType.VIDEO, "DCIM/Camera", 1000L, 100L)
        val itemVideoNew = createMediaItem("content://media/2", MediaType.VIDEO, "DCIM/Camera", 2000L, 120L)
        val itemImageNew = createMediaItem("content://media/3", MediaType.IMAGE, "DCIM/Camera", 3000L, 80L)
        
        // Null path item (falls back to root "")
        val itemNullPath = createMediaItem("content://media/4", MediaType.IMAGE, null, 1500L, 50L)

        val scannedList = listOf(itemVideoOld, itemVideoNew, itemImageNew, itemNullPath)

        // Seed repository so covers can resolve their database IDs
        mediaRepository.saveMediaItems(scannedList)

        // Run recalculateFolders manually (by accessing private method via reflection or just simulating syncScannedItems)
        // Since syncScannedItems is private, we can call scanAndSyncMediaStore but stub the scanner to return scannedList!
        val stubbedMediaStoreScanner = object : MediaStoreScanner {
            override suspend fun scanVolume(volumeName: String, onProgress: (ScanProgress) -> Unit): List<MediaItem> {
                return scannedList
            }
        }
        val customSyncManager = StorageSyncManager(mediaRepository, stubbedMediaStoreScanner, safDirectoryScanner)

        customSyncManager.scanAndSyncMediaStore("external_primary") { }

        // 1. Verify DCIM/Camera folder
        val cameraFolder = mediaRepository.getFolderByVolumeAndPath("external_primary", "DCIM/Camera")
        assertNotNull(cameraFolder)
        assertEquals("Camera", cameraFolder!!.displayName)
        assertEquals(3, cameraFolder.mediaCount)
        assertEquals(2, cameraFolder.videoCount)
        assertEquals(1, cameraFolder.imageCount)
        assertEquals(300L, cameraFolder.totalSize)
        assertEquals(3000L, cameraFolder.latestModified) // max of 1000, 2000, 3000

        // Primary cover selection priority: latest modified VIDEO (itemVideoNew, modified 2000L)
        // even though image is newer (3000L), video is prioritized!
        val expectedCoverId = mediaRepository.mediaItems["content://media/2"]?.databaseId
        assertNotNull(expectedCoverId)
        assertEquals(expectedCoverId, cameraFolder.primaryCoverMediaId)

        // 2. Verify Null path fallback folder (root "")
        val rootFolder = mediaRepository.getFolderByVolumeAndPath("external_primary", "")
        assertNotNull(rootFolder)
        assertEquals("external_primary", rootFolder!!.displayName)
        assertEquals(1, rootFolder.mediaCount)
        assertEquals(0, rootFolder.videoCount)
        assertEquals(1, rootFolder.imageCount)
        assertEquals(50L, rootFolder.totalSize)
        assertEquals(1500L, rootFolder.latestModified)
    }

    private fun createMediaItem(
        uri: String,
        type: MediaType,
        relativePath: String?,
        modified: Long,
        size: Long
    ): MediaItem {
        return MediaItem(
            databaseId = 0L,
            contentUri = uri,
            mediaType = type,
            volumeName = "external_primary",
            mediaStoreId = uri.hashCode().toLong(),
            relativePath = relativePath,
            displayName = uri.substringAfterLast("/"),
            mimeType = if (type == MediaType.VIDEO) "video/mp4" else "image/jpeg",
            fileSize = size,
            durationMs = null,
            width = null,
            height = null,
            rotationDegrees = null,
            dateAddedEpochSeconds = modified,
            dateModifiedEpochSeconds = modified,
            dateTakenEpochMillis = null,
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
