package com.pigfarmerjc.galleryplayer.player.libvlc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VlcOptionsTest {
    @Test
    fun testBaseOptionsContainsRequiredFlags() {
        assertFalse(VlcOptions.baseOptions.contains("-vvv"))
        assertTrue(VlcOptions.baseOptions.contains("--audio-time-stretch"))
        assertTrue(VlcOptions.baseOptions.contains("--file-caching=300"))
    }
}
