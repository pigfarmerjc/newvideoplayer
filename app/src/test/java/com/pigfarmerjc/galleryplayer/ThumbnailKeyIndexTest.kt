package com.pigfarmerjc.galleryplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThumbnailKeyIndexTest {

    @Test
    fun latestKeyReturnsMostRecentlyRecordedSizeForUri() {
        val index = ThumbnailKeyIndex()

        index.record("content://video/1", "content://video/1_160_160")
        index.record("content://video/1", "content://video/1_320_320")

        assertEquals("content://video/1_320_320", index.latestKey("content://video/1"))
    }

    @Test
    fun removingOlderSizeDoesNotRemoveCurrentFallback() {
        val index = ThumbnailKeyIndex()
        val oldKey = "content://video/1_160_160"
        val currentKey = "content://video/1_320_320"
        index.record("content://video/1", oldKey)
        index.record("content://video/1", currentKey)

        index.remove(oldKey)

        assertEquals(currentKey, index.latestKey("content://video/1"))
    }

    @Test
    fun removingCurrentSizeClearsFallback() {
        val index = ThumbnailKeyIndex()
        val key = "content://video/1_320_320"
        index.record("content://video/1", key)

        index.remove(key)

        assertNull(index.latestKey("content://video/1"))
    }

    @Test
    fun reassigningKeyToDifferentUriDoesNotLeaveStaleLookup() {
        val index = ThumbnailKeyIndex()
        val key = "shared-key"
        index.record("content://video/1", key)

        index.record("content://video/2", key)

        assertNull(index.latestKey("content://video/1"))
        assertEquals(key, index.latestKey("content://video/2"))
    }
}
