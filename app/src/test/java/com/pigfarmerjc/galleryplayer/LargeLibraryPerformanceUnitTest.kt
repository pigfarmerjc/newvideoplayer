package com.pigfarmerjc.galleryplayer

import com.pigfarmerjc.galleryplayer.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class LargeLibraryPerformanceUnitTest {

    private val largeLibrary: List<LocalMediaItem> by lazy {
        List(4500) { index ->
            val folderIndex = index % 200
            val relativePath = "Folder_$folderIndex/"
            LocalMediaItem(
                contentUri = "content://media/$index",
                mediaType = MediaType.VIDEO,
                volumeName = if (index % 2 == 0) "external" else "1234-ABCD",
                relativePath = relativePath,
                displayName = "Video_${index}_Title.mp4",
                mimeType = "video/mp4",
                fileSize = (100000L + index * 1000L),
                durationMs = (10000L + index * 100L).takeIf { index % 10 != 0 },
                width = 1920,
                height = 1080,
                dateModifiedEpochSeconds = (1000000L + index),
                isGif = false,
                mediaStoreId = index.toLong()
            )
        }
    }

    @Test
    fun testSearchFilenamePerformance() {
        val duration = measureTimeMillis {
            val result = VideoFilterAndSort.filterAndSort(largeLibrary, "Video_2500_Title", VideoSortMode.NAME_ASC)
            assertEquals(1, result.size)
            assertEquals("Video_2500_Title.mp4", result[0].displayName)
        }
        println("4500 items search filename took: ${duration}ms")
    }

    @Test
    fun testSearchFolderNamePerformance() {
        val duration = measureTimeMillis {
            val result = VideoFilterAndSort.filterAndSort(largeLibrary, "Folder_150", VideoSortMode.NAME_ASC)
            assertTrue(result.size >= 22)
            assertTrue(result.all { it.relativePath == "Folder_150/" })
        }
        println("4500 items search folder name took: ${duration}ms")
    }

    @Test
    fun testSortByNamePerformance() {
        val duration = measureTimeMillis {
            val result = VideoFilterAndSort.filterAndSort(largeLibrary, "", VideoSortMode.NAME_ASC)
            assertEquals(4500, result.size)
        }
        println("4500 items sort by name took: ${duration}ms")
    }

    @Test
    fun testSortByDateModifiedPerformance() {
        val duration = measureTimeMillis {
            val result = VideoFilterAndSort.filterAndSort(largeLibrary, "", VideoSortMode.DATE_MODIFIED_DESC)
            assertEquals(4500, result.size)
            assertTrue((result[0].dateModifiedEpochSeconds ?: 0L) >= (result[4499].dateModifiedEpochSeconds ?: 0L))
        }
        println("4500 items sort by date modified took: ${duration}ms")
    }

    @Test
    fun testSortByDurationPerformance() {
        val duration = measureTimeMillis {
            val result = VideoFilterAndSort.filterAndSort(largeLibrary, "", VideoSortMode.DURATION_DESC)
            assertEquals(4500, result.size)
        }
        println("4500 items sort by duration took: ${duration}ms")
    }

    @Test
    fun testSortBySizePerformance() {
        val duration = measureTimeMillis {
            val result = VideoFilterAndSort.filterAndSort(largeLibrary, "", VideoSortMode.SIZE_DESC)
            assertEquals(4500, result.size)
            assertTrue(result[0].fileSize >= result[4499].fileSize)
        }
        println("4500 items sort by size took: ${duration}ms")
    }

    @Test
    fun testFolderSortPerformance() {
        val videos = largeLibrary
        val folders = videos.groupBy { it.relativePath }.map { (path, items) ->
            val folderName = path.trimEnd('/').split('/').lastOrNull() ?: "Root"
            FolderItem(
                volumeName = items.first().volumeName ?: "external",
                relativePath = path,
                displayName = folderName,
                videoCount = items.size,
                coverUri = items.first().contentUri,
                totalSize = items.sumOf { it.fileSize }
            )
        }
        assertEquals(200, folders.size)

        val duration = measureTimeMillis {
            val sorted = FolderSort.sort(folders, FolderSortMode.DATE_MODIFIED_DESC, videos)
            assertEquals(200, sorted.size)
        }
        println("200 folders sort by date modified took: ${duration}ms")
    }

    @Test
    fun testContinueWatchingSiftPerformance() {
        val progressMap = mapOf(
            "content://media/10" to 0.5f,
            "content://media/20" to 0.1f,
            "content://media/30" to 0.9f
        )
        val historyList = listOf(
            com.pigfarmerjc.galleryplayer.core.database.repository.PlaybackHistoryItem(
                mediaId = 10L,
                contentUri = "content://media/10",
                lastPlayedTime = 1000L,
                playbackPositionMs = 5000L,
                durationMs = 10000L,
                finished = false,
                playCount = 1,
                preferredSpeed = 1.0f,
                mediaItem = null
            ),
            com.pigfarmerjc.galleryplayer.core.database.repository.PlaybackHistoryItem(
                mediaId = 20L,
                contentUri = "content://media/20",
                lastPlayedTime = 2000L,
                playbackPositionMs = 1000L,
                durationMs = 10000L,
                finished = false,
                playCount = 1,
                preferredSpeed = 1.0f,
                mediaItem = null
            ),
            com.pigfarmerjc.galleryplayer.core.database.repository.PlaybackHistoryItem(
                mediaId = 30L,
                contentUri = "content://media/30",
                lastPlayedTime = 3000L,
                playbackPositionMs = 9000L,
                durationMs = 10000L,
                finished = false,
                playCount = 1,
                preferredSpeed = 1.0f,
                mediaItem = null
            )
        )

        val duration = measureTimeMillis {
            val activeUris = progressMap.keys
            val sifted = historyList
                .filter { it.contentUri in activeUris && !it.finished }
                .mapNotNull { historyItem ->
                    largeLibrary.find { it.contentUri == historyItem.contentUri }
                }
                .take(10)
            assertEquals(3, sifted.size)
        }
        println("Continue Watching sift from 4500 items took: ${duration}ms")
    }

    @Test
    fun testEmptySearchAndNullDurationSafe() {
        val emptyVideos = emptyList<LocalMediaItem>()
        val result = VideoFilterAndSort.filterAndSort(emptyVideos, "abc", VideoSortMode.DURATION_DESC)
        assertTrue(result.isEmpty())
    }
}
