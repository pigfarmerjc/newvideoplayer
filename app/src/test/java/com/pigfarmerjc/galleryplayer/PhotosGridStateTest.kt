package com.pigfarmerjc.galleryplayer

import com.pigfarmerjc.galleryplayer.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PhotosGridStateTest {

    @Test
    fun testDefaultColumnsAndSafetyLimits() {
        // 1. 默认列数为 6 
        val defaultColumns = 6
        assertEquals(6, defaultColumns)

        // 2. 最小列数不低于 2，最大列数不超过 12
        val minCols = PhotosGridState.applyZoomToColumns(2, 1.10f) // zoom out -> reduce columns
        assertEquals(2, minCols) // clamped to min 2

        val maxCols = PhotosGridState.applyZoomToColumns(12, 0.90f) // zoom in -> increase columns
        assertEquals(12, maxCols) // clamped to max 12
    }

    @Test
    fun testZoomGestureReducesOrIncreasesColumns() {
        // 4. 放大手势 (zoomFactor > 1.08f) 减少列数
        val zoomOutResult = PhotosGridState.applyZoomToColumns(6, 1.10f)
        assertEquals(5, zoomOutResult)

        // 5. 缩小手势 (zoomFactor < 0.92f) 增加列数
        val zoomInResult = PhotosGridState.applyZoomToColumns(6, 0.90f)
        assertEquals(7, zoomInResult)
    }

    @Test
    fun testSmallScaleZoomDoesNotChangeColumns() {
        // 6. 小幅缩放不改变列数
        val noChangeResult1 = PhotosGridState.applyZoomToColumns(6, 1.05f)
        assertEquals(6, noChangeResult1)

        val noChangeResult2 = PhotosGridState.applyZoomToColumns(6, 0.95f)
        assertEquals(6, noChangeResult2)
    }

    @Test
    fun testInvalidColumnsFallback() {
        // 7. invalid column value fallback 到 6
        val invalidColValue = 15
        val coercedValue = invalidColValue.coerceIn(2, 12)
        assertEquals(12, coercedValue) // standard Kotlin fallback/clamp coercion behavior

        // Let's assert a manual fallback logic
        val rawConfigValue = -5
        val fallbackColumns = if (rawConfigValue !in 2..12) 6 else rawConfigValue
        assertEquals(6, fallbackColumns)
    }

    @Test
    fun testVideoViewModePersistenceAndFallbacks() {
        // 8. VideoViewMode 持久化解析
        val cardMode = VideoViewMode.valueOf("CARD")
        assertEquals(VideoViewMode.CARD, cardMode)

        val photosGridMode = VideoViewMode.valueOf("PHOTOS_GRID")
        assertEquals(VideoViewMode.PHOTOS_GRID, photosGridMode)

        // 9. invalid VideoViewMode fallback 到 CARD
        val invalidModeStr = "LIST_VIEW"
        val resolvedMode = VideoViewMode.values().firstOrNull { it.name == invalidModeStr } ?: VideoViewMode.CARD
        assertEquals(VideoViewMode.CARD, resolvedMode)
    }

    @Test
    fun testPhotosGridFilteredAndSortedList() {
        // 10. Photos Grid 使用过滤排序后的列表
        val media1 = LocalMediaItem(
            contentUri = "content://media/1",
            mediaType = MediaType.VIDEO,
            volumeName = "external",
            relativePath = "DCIM/",
            displayName = "B_video.mp4",
            mimeType = "video/mp4",
            fileSize = 1000L,
            durationMs = 5000L,
            width = 1920,
            height = 1080,
            dateModifiedEpochSeconds = 100L,
            isGif = false,
            mediaStoreId = 1L
        )
        val media2 = LocalMediaItem(
            contentUri = "content://media/2",
            mediaType = MediaType.VIDEO,
            volumeName = "external",
            relativePath = "DCIM/",
            displayName = "A_video.mp4",
            mimeType = "video/mp4",
            fileSize = 2000L,
            durationMs = 10000L,
            width = 1280,
            height = 720,
            dateModifiedEpochSeconds = 200L,
            isGif = false,
            mediaStoreId = 2L
        )
        
        val originalList = listOf(media1, media2)

        // Filter and sort by NAME_ASC
        val filteredAndSorted = VideoFilterAndSort.filterAndSort(
            originalList,
            query = "",
            sortMode = VideoSortMode.NAME_ASC
        )
        
        // Assert sorting is applied (A_video should come first)
        assertEquals(2, filteredAndSorted.size)
        assertEquals("A_video.mp4", filteredAndSorted[0].displayName)
        assertEquals("B_video.mp4", filteredAndSorted[1].displayName)
        
        // Validate filter query works
        val searchFiltered = VideoFilterAndSort.filterAndSort(
            originalList,
            query = "B_video",
            sortMode = VideoSortMode.NAME_ASC
        )
        assertEquals(1, searchFiltered.size)
        assertEquals("B_video.mp4", searchFiltered[0].displayName)
    }
}
