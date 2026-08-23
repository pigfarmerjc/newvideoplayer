package com.pigfarmerjc.galleryplayer.core.storage

import com.pigfarmerjc.galleryplayer.core.model.MediaItem
import com.pigfarmerjc.galleryplayer.core.model.MediaType
import com.pigfarmerjc.galleryplayer.core.model.ScanState
import com.pigfarmerjc.galleryplayer.core.storage.sync.SyncDiffPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDiffPlannerTest {

    @Test
    fun planSeparatesInsertUpdateDeleteAndUnchangedItems() {
        val unchanged = media(uri = "content://video/unchanged", databaseId = 11)
        val changed = media(uri = "content://video/changed", databaseId = 12, size = 100)
        val deleted = media(uri = "content://video/deleted", databaseId = 13)
        val scanned = listOf(
            unchanged.copy(databaseId = 0),
            changed.copy(databaseId = 0, fileSize = 200, dateModifiedEpochSeconds = 200),
            media(uri = "content://video/new", databaseId = 0)
        )

        val diff = SyncDiffPlanner.plan(listOf(unchanged, changed, deleted), scanned)

        assertEquals(1, diff.insertedCount)
        assertEquals(1, diff.updatedCount)
        assertEquals(setOf("content://video/deleted"), diff.deletedUris.toSet())
        assertEquals(setOf("content://video/changed", "content://video/new"), diff.upserts.map { it.contentUri }.toSet())
    }

    @Test
    fun updatedScannerItemPreservesDatabaseIdentityAndUserFlags() {
        val existing = media(
            uri = "content://video/changed",
            databaseId = 42,
            size = 100,
            favorite = true,
            hidden = true
        )
        val scanned = existing.copy(
            databaseId = 0,
            fileSize = 200,
            isFavorite = false,
            isHidden = false
        )

        val updated = SyncDiffPlanner.plan(listOf(existing), listOf(scanned)).upserts.single()

        assertEquals(42, updated.databaseId)
        assertTrue(updated.isFavorite)
        assertTrue(updated.isHidden)
        assertEquals(200, updated.fileSize)
    }

    @Test
    fun insertedScannerItemKeepsGeneratedIdAndDefaultFlags() {
        val scanned = media(uri = "content://video/new", databaseId = 0)

        val inserted = SyncDiffPlanner.plan(emptyList(), listOf(scanned)).upserts.single()

        assertEquals(0, inserted.databaseId)
        assertFalse(inserted.isFavorite)
        assertFalse(inserted.isHidden)
    }

    private fun media(
        uri: String,
        databaseId: Long,
        size: Long = 100,
        favorite: Boolean = false,
        hidden: Boolean = false
    ) = MediaItem(
        databaseId = databaseId,
        contentUri = uri,
        mediaType = MediaType.VIDEO,
        volumeName = "external_primary",
        mediaStoreId = databaseId.takeIf { it > 0 },
        relativePath = "Movies/",
        displayName = uri.substringAfterLast('/'),
        mimeType = "video/mp4",
        fileSize = size,
        durationMs = 10_000,
        width = 1920,
        height = 1080,
        rotationDegrees = 0,
        dateAddedEpochSeconds = 100,
        dateModifiedEpochSeconds = 100,
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
        isFavorite = favorite,
        isHidden = hidden,
        scanState = ScanState.SCANNED,
        lastError = null
    )
}
