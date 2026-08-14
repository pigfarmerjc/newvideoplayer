package com.pigfarmerjc.galleryplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
