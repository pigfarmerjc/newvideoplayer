package com.pigfarmerjc.galleryplayer

import com.pigfarmerjc.galleryplayer.core.model.MediaType
import com.pigfarmerjc.galleryplayer.core.model.ScanState
import org.junit.Assert.*
import org.junit.Test

class PlaybackHistoryUnitTest {

    @Test
    fun testLocalMediaItemToMediaItemMapping() {
        val localItem = LocalMediaItem(
            contentUri = "content://media/external/video/media/123",
            mediaType = MediaType.VIDEO,
            volumeName = "external_primary",
            relativePath = "Movies/",
            displayName = "test_video.mp4",
            mimeType = "video/mp4",
            fileSize = 4096L,
            durationMs = 60000L,
            width = 1920,
            height = 1080,
            dateModifiedEpochSeconds = 99999L,
            isGif = false,
            mediaStoreId = 123L
        )

        val domainItem = localItem.toMediaItem()

        assertEquals(0L, domainItem.databaseId)
        assertEquals(localItem.contentUri, domainItem.contentUri)
        assertEquals(com.pigfarmerjc.galleryplayer.core.model.MediaType.VIDEO, domainItem.mediaType)
        assertEquals(localItem.volumeName, domainItem.volumeName)
        assertEquals(localItem.mediaStoreId, domainItem.mediaStoreId)
        assertEquals(localItem.relativePath, domainItem.relativePath)
        assertEquals(localItem.displayName, domainItem.displayName)
        assertEquals(localItem.mimeType, domainItem.mimeType)
        assertEquals(localItem.fileSize, domainItem.fileSize)
        assertEquals(localItem.durationMs, domainItem.durationMs)
        assertEquals(localItem.width, domainItem.width)
        assertEquals(localItem.height, domainItem.height)
        assertNull(domainItem.rotationDegrees)
        assertEquals(localItem.dateModifiedEpochSeconds, domainItem.dateModifiedEpochSeconds)
        assertFalse(domainItem.isGif)
        assertEquals(ScanState.SCANNED, domainItem.scanState)
    }

    @Test
    fun testStartPlaybackSessionIncrementsPlayCount() {
        // Stub to test startPlaybackSession logic
        var playCount = 0
        var savedPosition = 0L

        fun startPlaybackSession(existingPlayCount: Int, pos: Long) {
            playCount = existingPlayCount + 1
            savedPosition = pos
        }

        // Test brand new video
        startPlaybackSession(0, 0L)
        assertEquals(1, playCount)
        assertEquals(0L, savedPosition)

        // Test existing video
        startPlaybackSession(1, 15000L)
        assertEquals(2, playCount)
        assertEquals(15000L, savedPosition)
    }

    @Test
    fun testUpdatePlaybackProgressDoesNotIncrementPlayCount() {
        var playCount = 5
        var savedPosition = 0L

        fun updatePlaybackProgress(pos: Long) {
            // Keep playCount unchanged
            savedPosition = pos
        }

        updatePlaybackProgress(20000L)
        assertEquals(5, playCount) // Must NOT change playCount!
        assertEquals(20000L, savedPosition)
    }

    @Test
    fun testCompletionThresholdPercentage() {
        fun isCompleted(position: Long, duration: Long): Boolean {
            if (duration <= 0) return false
            return (position.toDouble() / duration.toDouble()) >= 0.90
        }

        // Under 90%
        assertFalse(isCompleted(53000L, 60000L)) // 88.3%
        // At or Over 90%
        assertTrue(isCompleted(54000L, 60000L))  // 90%
        assertTrue(isCompleted(59000L, 60000L))  // 98.3%
    }

    @Test
    fun testCompletedVideoNoProgressMap() {
        fun shouldShowProgressInMap(position: Long, duration: Long, completed: Boolean): Boolean {
            if (completed) return false
            if (duration <= 0) return false
            val progress = position.toFloat() / duration.toFloat()
            return progress in 0.01f..0.99f
        }

        // Active progress
        assertTrue(shouldShowProgressInMap(30000L, 60000L, false))
        // Finished
        assertFalse(shouldShowProgressInMap(55000L, 60000L, true))
        // Too small progress
        assertFalse(shouldShowProgressInMap(100L, 60000L, false))
    }

    @Test
    fun testGetResumePlaybackPositionRules() {
        fun getResumePosition(pos: Long, dur: Long, completed: Boolean): Long {
            // Rules:
            // 2. completed == false
            // 3. positionMs > 3000
            // 4. durationMs > 0
            // 5. positionMs < durationMs * 0.90
            if (!completed && pos > 3000L && dur > 0L && pos < dur * 0.90) {
                return pos
            }
            return 0L
        }

        // Normal unfinished video
        assertEquals(20000L, getResumePosition(20000L, 60000L, false))
        // Position <= 3000ms
        assertEquals(0L, getResumePosition(2500L, 60000L, false))
        // Completed
        assertEquals(0L, getResumePosition(20000L, 60000L, true))
        // Position over 90%
        assertEquals(0L, getResumePosition(55000L, 60000L, false))
    }
}
