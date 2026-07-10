package com.pigfarmerjc.galleryplayer

import com.pigfarmerjc.galleryplayer.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortAndModeUnitTest {

    private val videos = listOf(
        LocalMediaItem(
            contentUri = "content://media/1",
            mediaType = MediaType.VIDEO,
            volumeName = "external",
            relativePath = "Download/",
            displayName = "ActionMovie.mp4",
            mimeType = "video/mp4",
            fileSize = 5000000L,
            durationMs = 60000L,
            width = 1920,
            height = 1080,
            dateModifiedEpochSeconds = 1000L,
            isGif = false,
            mediaStoreId = 1L
        ),
        LocalMediaItem(
            contentUri = "content://media/2",
            mediaType = MediaType.VIDEO,
            volumeName = "external",
            relativePath = "Movies/",
            displayName = "ComedyMovie.mp4",
            mimeType = "video/mp4",
            fileSize = 2000000L,
            durationMs = 120000L,
            width = 1280,
            height = 720,
            dateModifiedEpochSeconds = 3000L,
            isGif = false,
            mediaStoreId = 2L
        ),
        LocalMediaItem(
            contentUri = "content://media/3",
            mediaType = MediaType.VIDEO,
            volumeName = "external",
            relativePath = "Download/",
            displayName = "DramaShow.mp4",
            mimeType = "video/mp4",
            fileSize = 8000000L,
            durationMs = 30000L,
            width = 1920,
            height = 1080,
            dateModifiedEpochSeconds = 2000L,
            isGif = false,
            mediaStoreId = 3L
        )
    )

    @Test
    fun testVideoSearchByDisplayName() {
        val result = VideoFilterAndSort.filterAndSort(videos, "comedy", VideoSortMode.NAME_ASC)
        assertEquals(1, result.size)
        assertEquals("ComedyMovie.mp4", result[0].displayName)
    }

    @Test
    fun testVideoSearchByRelativePath() {
        val result = VideoFilterAndSort.filterAndSort(videos, "movies", VideoSortMode.NAME_ASC)
        assertEquals(1, result.size)
        assertEquals("ComedyMovie.mp4", result[0].displayName)
    }

    @Test
    fun testVideoSearchEmptyReturnsAll() {
        val result = VideoFilterAndSort.filterAndSort(videos, "  ", VideoSortMode.NAME_ASC)
        assertEquals(3, result.size)
    }

    @Test
    fun testVideoSortByNameAsc() {
        val result = VideoFilterAndSort.filterAndSort(videos, "", VideoSortMode.NAME_ASC)
        assertEquals("ActionMovie.mp4", result[0].displayName)
        assertEquals("ComedyMovie.mp4", result[1].displayName)
        assertEquals("DramaShow.mp4", result[2].displayName)
    }

    @Test
    fun testVideoSortByDateModifiedDesc() {
        val result = VideoFilterAndSort.filterAndSort(videos, "", VideoSortMode.DATE_MODIFIED_DESC)
        assertEquals("ComedyMovie.mp4", result[0].displayName) // 3000L
        assertEquals("DramaShow.mp4", result[1].displayName)   // 2000L
        assertEquals("ActionMovie.mp4", result[2].displayName) // 1000L
    }

    @Test
    fun testVideoSortByDurationDesc() {
        val result = VideoFilterAndSort.filterAndSort(videos, "", VideoSortMode.DURATION_DESC)
        assertEquals("ComedyMovie.mp4", result[0].displayName) // 120000L
        assertEquals("ActionMovie.mp4", result[1].displayName) // 60000L
        assertEquals("DramaShow.mp4", result[2].displayName)   // 30000L
    }

    @Test
    fun testVideoSortBySizeDesc() {
        val result = VideoFilterAndSort.filterAndSort(videos, "", VideoSortMode.SIZE_DESC)
        assertEquals("DramaShow.mp4", result[0].displayName)   // 8MB
        assertEquals("ActionMovie.mp4", result[1].displayName) // 5MB
        assertEquals("ComedyMovie.mp4", result[2].displayName) // 2MB
    }

    @Test
    fun testPlaybackRepeatModeTransitions() {
        var mode = PlaybackRepeatMode.NONE
        
        // Cycle mode: NONE -> ONE
        mode = when (mode) {
            PlaybackRepeatMode.NONE -> PlaybackRepeatMode.ONE
            PlaybackRepeatMode.ONE -> PlaybackRepeatMode.ALL
            PlaybackRepeatMode.ALL -> PlaybackRepeatMode.NONE
        }
        assertEquals(PlaybackRepeatMode.ONE, mode)

        // Cycle mode: ONE -> ALL
        mode = when (mode) {
            PlaybackRepeatMode.NONE -> PlaybackRepeatMode.ONE
            PlaybackRepeatMode.ONE -> PlaybackRepeatMode.ALL
            PlaybackRepeatMode.ALL -> PlaybackRepeatMode.NONE
        }
        assertEquals(PlaybackRepeatMode.ALL, mode)

        // Cycle mode: ALL -> NONE
        mode = when (mode) {
            PlaybackRepeatMode.NONE -> PlaybackRepeatMode.ONE
            PlaybackRepeatMode.ONE -> PlaybackRepeatMode.ALL
            PlaybackRepeatMode.ALL -> PlaybackRepeatMode.NONE
        }
        assertEquals(PlaybackRepeatMode.NONE, mode)
    }
}
