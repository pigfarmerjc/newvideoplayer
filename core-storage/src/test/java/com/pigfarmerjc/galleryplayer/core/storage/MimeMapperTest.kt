package com.pigfarmerjc.galleryplayer.core.storage

import com.pigfarmerjc.galleryplayer.core.model.MediaType
import com.pigfarmerjc.galleryplayer.core.storage.util.MimeMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MimeMapperTest {

    @Test
    fun testMimeMappingForGif() {
        // GIF MIME is image/gif OR extension is .gif
        assertEquals(MediaType.GIF, MimeMapper.map("image/gif", "file.gif"))
        assertEquals(MediaType.GIF, MimeMapper.map("image/gif", "file.jpg"))
        assertEquals(MediaType.GIF, MimeMapper.map("image/png", "file.GIF"))
        assertEquals(MediaType.GIF, MimeMapper.map(null, "file.gif"))
    }

    @Test
    fun testMimeMappingForVideo() {
        assertEquals(MediaType.VIDEO, MimeMapper.map("video/mp4", "movie.mp4"))
        assertEquals(MediaType.VIDEO, MimeMapper.map("video/x-matroska", "movie.mkv"))
        assertEquals(MediaType.VIDEO, MimeMapper.map(null, "movie.mp4"))
        assertEquals(MediaType.VIDEO, MimeMapper.map(null, "movie.MKV"))
    }

    @Test
    fun testMimeMappingForImage() {
        assertEquals(MediaType.IMAGE, MimeMapper.map("image/jpeg", "photo.jpg"))
        assertEquals(MediaType.IMAGE, MimeMapper.map("image/png", "photo.png"))
        assertEquals(MediaType.IMAGE, MimeMapper.map(null, "photo.png"))
    }

    @Test
    fun testMimeMappingForAudio() {
        assertEquals(MediaType.AUDIO, MimeMapper.map("audio/mpeg", "song.mp3"))
        assertEquals(MediaType.AUDIO, MimeMapper.map("audio/ogg", "song.ogg"))
        assertEquals(MediaType.AUDIO, MimeMapper.map(null, "song.flac"))
    }

    @Test
    fun testUnknownMimeAndExtensionReturnsNull() {
        assertNull(MimeMapper.map(null, "unknown.doc"))
        assertNull(MimeMapper.map("application/pdf", "document.pdf"))
        assertNull(MimeMapper.map(null, null))
    }
}
