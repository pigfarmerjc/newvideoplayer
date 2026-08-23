package com.pigfarmerjc.galleryplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingWindowLayoutTest {

    @Test
    fun `initial window follows landscape and portrait video`() {
        val landscape = initialFloatingWindowSize(1600, 2560, 1920, 1080)
        val portrait = initialFloatingWindowSize(1600, 2560, 1080, 1920)

        assertEquals(16f / 9f, landscape.width.toFloat() / landscape.height, 0.02f)
        assertEquals(9f / 16f, portrait.width.toFloat() / portrait.height, 0.02f)
        assertTrue(landscape.width > portrait.width)
        assertTrue(portrait.height > landscape.height)
    }

    @Test
    fun `resize preserves video ratio and respects screen limits`() {
        val enlarged = resizeFloatingWindow(700, 16f / 9f, 1600, 2560)
        val tooSmall = resizeFloatingWindow(10, 16f / 9f, 1600, 2560)
        val tooLarge = resizeFloatingWindow(5000, 9f / 16f, 1600, 2560)

        assertEquals(16f / 9f, enlarged.width.toFloat() / enlarged.height, 0.02f)
        assertTrue(tooSmall.width >= 352)
        assertTrue(tooLarge.height <= 2048)
    }

    @Test
    fun `drag position is only clamped at visible screen edges`() {
        val size = FloatingWindowSize(500, 800)

        assertEquals(FloatingWindowPosition(321, 654), clampFloatingWindowPosition(321, 654, size, 1600, 2560))
        assertEquals(FloatingWindowPosition(0, 0), clampFloatingWindowPosition(-50, -90, size, 1600, 2560))
        assertEquals(FloatingWindowPosition(1100, 1760), clampFloatingWindowPosition(9000, 9000, size, 1600, 2560))
    }
}
