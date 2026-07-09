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

class IncrementalDiffTest {

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
    fun testIsolatedHardDelete() = runTest {
        // Prepare items for two separate volumes
        val itemVolA = createMediaItem("content://media/A1", "volume_A", 101L)
        val itemVolB = createMediaItem("content://media/B1", "volume_B", 101L)

        // Seed both into DB
        mediaRepository.saveMediaItems(listOf(itemVolA, itemVolB))
        assertEquals(2, mediaRepository.mediaItems.size)

        // Scan volume_A, returning EMPTY scan (simulating that A1 was deleted)
        val stubScannerA = object : MediaStoreScanner {
            override suspend fun scanVolume(volumeName: String, onProgress: (ScanProgress) -> Unit): List<MediaItem> {
                return emptyList()
            }
        }
        val syncManager = StorageSyncManager(mediaRepository, stubScannerA, safDirectoryScanner)
        syncManager.scanAndSyncMediaStore("volume_A") { }

        // A1 must be deleted (HARD_DELETE), but B1 must remain untouched (Isolated Scope!)
        assertNull(mediaRepository.mediaItems[itemVolA.contentUri])
        assertNotNull(mediaRepository.mediaItems[itemVolB.contentUri])
        assertEquals(1, mediaRepository.mediaItems.size)
    }

    @Test
    fun testSameMediaStoreIdAcrossDifferentVolumes() = runTest {
        // Two files on different volumes sharing the same mediaStoreId (e.g. 99)
        val itemVolA = createMediaItem("content://media/A/99", "volume_A", 99L)
        val itemVolB = createMediaItem("content://media/B/99", "volume_B", 99L)

        // Seed them
        val stubScanner = object : MediaStoreScanner {
            override suspend fun scanVolume(volumeName: String, onProgress: (ScanProgress) -> Unit): List<MediaItem> {
                return if (volumeName == "volume_A") listOf(itemVolA) else listOf(itemVolB)
            }
        }

        val syncManager = StorageSyncManager(mediaRepository, stubScanner, safDirectoryScanner)
        syncManager.scanAndSyncMediaStore("volume_A") { }
        syncManager.scanAndSyncMediaStore("volume_B") { }

        // Verify both exist and did not overwrite each other (Room uses unique content_uri index)
        assertNotNull(mediaRepository.mediaItems[itemVolA.contentUri])
        assertNotNull(mediaRepository.mediaItems[itemVolB.contentUri])
        assertEquals(2, mediaRepository.mediaItems.size)
    }

    private fun createMediaItem(
        uri: String,
        volumeName: String,
        mediaStoreId: Long
    ): MediaItem {
        return MediaItem(
            databaseId = 0L,
            contentUri = uri,
            mediaType = MediaType.VIDEO,
            volumeName = volumeName,
            mediaStoreId = mediaStoreId,
            relativePath = "DCIM",
            displayName = uri.substringAfterLast("/"),
            mimeType = "video/mp4",
            fileSize = 1000L,
            durationMs = null,
            width = null,
            height = null,
            rotationDegrees = null,
            dateAddedEpochSeconds = 100L,
            dateModifiedEpochSeconds = 100L,
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
