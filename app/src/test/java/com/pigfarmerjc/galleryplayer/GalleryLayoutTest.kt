package com.pigfarmerjc.galleryplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryLayoutTest {
    @Test
    fun columnsRespectTabletWidthAndOrientation() {
        assertEquals(2, GalleryLayout.columnsForWidth(360, false))
        assertEquals(4, GalleryLayout.columnsForWidth(600, false))
        assertEquals(5, GalleryLayout.columnsForWidth(900, true))
        assertEquals(5, GalleryLayout.columnsForWidth(900, false))
    }

    @Test
    fun progressRatioClampsAndHidesEmptyEdges() {
        assertNull(GalleryLayout.progressRatio(0L, 100L))
        assertNull(GalleryLayout.progressRatio(100L, 100L))
        assertEquals(0.5f, GalleryLayout.progressRatio(50L, 100L))
        assertNull(GalleryLayout.progressRatio(120L, 100L))
    }

    @Test
    fun thumbnailDimensionsUseStableBoundedBuckets() {
        assertEquals(320, GalleryLayout.thumbnailBucket(319))
        assertEquals(384, GalleryLayout.thumbnailBucket(321))
        assertEquals(768, GalleryLayout.thumbnailBucket(2_000))
        assertEquals(1_536, GalleryLayout.thumbnailBucket(1_500, maxDimension = 2_048))
    }

    @Test
    fun formatFileSizeFormatsAccurately() {
        assertEquals("0 B", GalleryLayout.formatFileSize(0L))
        assertEquals("512 B", GalleryLayout.formatFileSize(512L))
        assertEquals("1.5 KB", GalleryLayout.formatFileSize((1.5 * 1024).toLong()))
        assertEquals("25.0 MB", GalleryLayout.formatFileSize(25L * 1024 * 1024))
        assertEquals("2.4 GB", GalleryLayout.formatFileSize((2.4 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun resolutionDetectionDetects4KAndHD() {
        assertTrue(GalleryLayout.is4K(3840, 2160))
        assertTrue(GalleryLayout.is4K(2160, 3840))
        assertFalse(GalleryLayout.is4K(1920, 1080))

        assertTrue(GalleryLayout.isHD(1920, 1080))
        assertTrue(GalleryLayout.isHD(1280, 720))
        assertFalse(GalleryLayout.isHD(3840, 2160))
        assertFalse(GalleryLayout.isHD(640, 480))
    }
}
