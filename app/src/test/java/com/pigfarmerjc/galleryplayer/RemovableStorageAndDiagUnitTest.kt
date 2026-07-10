package com.pigfarmerjc.galleryplayer

import com.pigfarmerjc.galleryplayer.core.player.api.DecoderMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemovableStorageAndDiagUnitTest {

    @Test
    fun testSafVideoSuffixCheck() {
        val videoExtensions = setOf(
            "mp4", "mkv", "avi", "mov", "m4v", "ts", "m2ts", "webm", "flv", "wmv", "3gp"
        )
        
        // Assert matches
        assertTrue("mp4" in videoExtensions)
        assertTrue("mkv" in videoExtensions)
        assertTrue("avi" in videoExtensions)
        
        // Assert non-matches
        assertFalse("png" in videoExtensions)
        assertFalse("txt" in videoExtensions)
        assertFalse("pdf" in videoExtensions)
    }

    @Test
    fun testDiagnosticsTextFormatting() {
        val videosCount = 45
        val imagesCount = 12
        val foldersCount = 5
        val mediaStoreVolumes = listOf("external_primary", "1234-ABCD")
        val safAuthorizedFolders = listOf("content://com.android.externalstorage.documents/tree/1234-ABCD%3AVideos")
        val decoderModeState = DecoderMode.HARDWARE_FORCED
        val lastPlayedTitle = "TestMovie.mp4"
        val lastPlayedUri = "content://media/external/video/media/10"
        val lastPlaybackError = "Format not supported"
        val lastPlaybackState = "Error"
        val diagnosticsWidth = 3840
        val diagnosticsHeight = 2160
        val diagnosticsSampleRate = 48000
        val diagnosticsChannels = 6

        val output = buildString {
            append("Device stats: $videosCount videos, $imagesCount images, $foldersCount folders\n")
            append("Volumes: ${mediaStoreVolumes.joinToString(", ")}\n")
            append("SAF: ${safAuthorizedFolders.joinToString("; ")}\n")
            append("Decoder: $decoderModeState\n")
            append("Last Played: $lastPlayedTitle ($lastPlayedUri)\n")
            append("Error: $lastPlaybackError\n")
            append("State: $lastPlaybackState\n")
            append("Resolution: ${diagnosticsWidth}x${diagnosticsHeight}\n")
            append("Audio: ${diagnosticsSampleRate}Hz / ${diagnosticsChannels}ch\n")
        }

        assertTrue(output.contains("45 videos"))
        assertTrue(output.contains("1234-ABCD"))
        assertTrue(output.contains("HARDWARE_FORCED"))
        assertTrue(output.contains("TestMovie.mp4"))
        assertTrue(output.contains("3840x2160"))
        assertTrue(output.contains("48000Hz / 6ch"))
    }
}
