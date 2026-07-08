package com.pigfarmerjc.galleryplayer.player.libvlc

import org.junit.Assert.assertTrue
import org.junit.Test

class VlcOptionsTest {
    @Test
    fun testBaseOptionsContainsRequiredFlags() {
        assertTrue(VlcOptions.baseOptions.contains("-vvv"))
        assertTrue(VlcOptions.baseOptions.contains("--audio-time-stretch"))
    }
}
