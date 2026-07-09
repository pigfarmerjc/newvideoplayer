package com.pigfarmerjc.galleryplayer.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pigfarmerjc.galleryplayer.core.database.dao.*
import com.pigfarmerjc.galleryplayer.core.database.entity.*
import com.pigfarmerjc.galleryplayer.core.database.repository.*
import com.pigfarmerjc.galleryplayer.core.model.MediaType
import com.pigfarmerjc.galleryplayer.core.model.ScanState
import com.pigfarmerjc.galleryplayer.core.model.MediaItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class GalleryDatabaseTest {

    @get:Rule
    val migrationHelper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GalleryDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private lateinit var db: GalleryDatabase
    private lateinit var mediaItemDao: MediaItemDao
    private lateinit var folderDao: FolderDao
    private lateinit var playbackHistoryDao: PlaybackHistoryDao
    private lateinit var thumbnailCacheDao: ThumbnailCacheDao
    private lateinit var storageVolumeDao: StorageVolumeDao

    private lateinit var mediaRepository: MediaRepository
    private lateinit var playbackHistoryRepository: PlaybackHistoryRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GalleryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        mediaItemDao = db.mediaItemDao()
        folderDao = db.folderDao()
        playbackHistoryDao = db.playbackHistoryDao()
        thumbnailCacheDao = db.thumbnailCacheDao()
        storageVolumeDao = db.storageVolumeDao()

        mediaRepository = RoomMediaRepository(mediaItemDao)
        playbackHistoryRepository = RoomPlaybackHistoryRepository(playbackHistoryDao, mediaItemDao)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testInsertVideo() = runBlocking {
        val entity = MediaItemEntity(
            contentUri = "content://media/external/video/media/1",
            mediaType = "VIDEO",
            volumeName = "external_primary",
            mediaStoreId = 1L,
            relativePath = "DCIM/Camera/",
            displayName = "video1.mp4",
            mimeType = "video/mp4",
            fileSize = 1024L,
            durationMs = 60000L,
            width = 1920,
            height = 1080,
            rotationDegrees = 0,
            dateAddedEpochSeconds = 1000L,
            dateModifiedEpochSeconds = 1000L,
            dateTakenEpochMillis = 1000L,
            videoCodec = "h264",
            audioCodec = "aac",
            audioSampleFormat = "s16",
            audioSampleRate = 44100,
            audioChannels = 2,
            frameRate = 30.0,
            bitrate = 5000000L,
            isHdr = false,
            isGif = false,
            isFavorite = false,
            isHidden = false,
            scanState = "SCANNED",
            lastError = null
        )
        val id = mediaItemDao.upsert(entity)
        assertTrue(id > 0)
        val retrieved = mediaItemDao.getById(id)
        assertNotNull(retrieved)
        assertEquals("video1.mp4", retrieved?.displayName)
        assertEquals("VIDEO", retrieved?.mediaType)
    }

    @Test
    fun testInsertImage() = runBlocking {
        val entity = MediaItemEntity(
            contentUri = "content://media/external/images/media/1",
            mediaType = "IMAGE",
            volumeName = "external_primary",
            mediaStoreId = 1L,
            relativePath = "DCIM/Camera/",
            displayName = "photo.jpg",
            mimeType = "image/jpeg",
            fileSize = 512L,
            durationMs = null,
            width = 4000,
            height = 3000,
            rotationDegrees = 0,
            dateAddedEpochSeconds = 1000L,
            dateModifiedEpochSeconds = 1000L,
            dateTakenEpochMillis = 1000L,
            videoCodec = null,
            audioCodec = null,
            audioSampleFormat = null,
            audioSampleRate = null,
            audioChannels = null,
            frameRate = null,
            bitrate = null,
            isHdr = false,
            isGif = false,
            isFavorite = false,
            isHidden = false,
            scanState = "SCANNED",
            lastError = null
        )
        val id = mediaItemDao.upsert(entity)
        val retrieved = mediaItemDao.getById(id)
        assertNotNull(retrieved)
        assertEquals("IMAGE", retrieved?.mediaType)
    }

    @Test
    fun testInsertGif() = runBlocking {
        val entity = MediaItemEntity(
            contentUri = "content://media/external/images/media/2",
            mediaType = "GIF",
            volumeName = "external_primary",
            mediaStoreId = 2L,
            relativePath = "DCIM/Camera/",
            displayName = "anim.gif",
            mimeType = "image/gif",
            fileSize = 256L,
            durationMs = null,
            width = 500,
            height = 500,
            rotationDegrees = 0,
            dateAddedEpochSeconds = 1000L,
            dateModifiedEpochSeconds = 1000L,
            dateTakenEpochMillis = 1000L,
            videoCodec = null,
            audioCodec = null,
            audioSampleFormat = null,
            audioSampleRate = null,
            audioChannels = null,
            frameRate = null,
            bitrate = null,
            isHdr = false,
            isGif = true,
            isFavorite = false,
            isHidden = false,
            scanState = "SCANNED",
            lastError = null
        )
        val id = mediaItemDao.upsert(entity)
        val retrieved = mediaItemDao.getById(id)
        assertNotNull(retrieved)
        assertEquals("GIF", retrieved?.mediaType)
        assertTrue(retrieved?.isGif == true)
    }

    @Test
    fun testInsertSAFMediaWithoutMediaStoreId() = runBlocking {
        val entity = MediaItemEntity(
            contentUri = "content://com.android.providers.downloads.documents/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2Ftest.mp4",
            mediaType = "VIDEO",
            volumeName = "external_primary",
            mediaStoreId = null,
            relativePath = null,
            displayName = "test.mp4",
            mimeType = "video/mp4",
            fileSize = 2048L,
            durationMs = 12000L,
            width = 1280,
            height = 720,
            rotationDegrees = 0,
            dateAddedEpochSeconds = null,
            dateModifiedEpochSeconds = null,
            dateTakenEpochMillis = null,
            videoCodec = "h264",
            audioCodec = "aac",
            audioSampleFormat = null,
            audioSampleRate = null,
            audioChannels = null,
            frameRate = null,
            bitrate = null,
            isHdr = false,
            isGif = false,
            isFavorite = false,
            isHidden = false,
            scanState = "PENDING",
            lastError = null
        )
        val id = mediaItemDao.upsert(entity)
        val retrieved = mediaItemDao.getById(id)
        assertNotNull(retrieved)
        assertNull(retrieved?.mediaStoreId)
        assertEquals("PENDING", retrieved?.scanState)
    }

    @Test
    fun testUpsertSameUriDoesNotDuplicate() = runBlocking {
        val entity1 = MediaItemEntity(
            contentUri = "content://media/external/video/media/1",
            mediaType = "VIDEO",
            volumeName = "external_primary",
            mediaStoreId = 1L,
            relativePath = "DCIM/Camera/",
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fileSize = 1000L,
            durationMs = 60000L,
            width = 1920,
            height = 1080,
            rotationDegrees = 0,
            dateAddedEpochSeconds = 1000L,
            dateModifiedEpochSeconds = 1000L,
            dateTakenEpochMillis = 1000L,
            videoCodec = null,
            audioCodec = null,
            audioSampleFormat = null,
            audioSampleRate = null,
            audioChannels = null,
            frameRate = null,
            bitrate = null,
            scanState = "SCANNED",
            lastError = null
        )
        val id1 = mediaItemDao.upsert(entity1)

        val entity2 = entity1.copy(displayName = "video_updated.mp4")
        val id2 = mediaItemDao.upsert(entity2)

        assertTrue("Upsert returns either original id or -1 indicating update", id2 == id1 || id2 == -1L)

        val count = mediaItemDao.getAll().first().size
        assertEquals("There should be exactly 1 item in the database", 1, count)

        val retrieved = mediaItemDao.getById(id1)
        assertEquals("video_updated.mp4", retrieved?.displayName)
    }

    @Test
    fun testNoConflictSameMediaStoreIdDifferentVolume() = runBlocking {
        val entityLocal = MediaItemEntity(
            contentUri = "content://media/external_primary/video/media/100",
            mediaType = "VIDEO",
            volumeName = "external_primary",
            mediaStoreId = 100L,
            relativePath = "DCIM/Camera/",
            displayName = "video_local.mp4",
            mimeType = "video/mp4",
            fileSize = 1000L,
            durationMs = null,
            width = null,
            height = null,
            rotationDegrees = null,
            dateAddedEpochSeconds = null,
            dateModifiedEpochSeconds = null,
            dateTakenEpochMillis = null,
            videoCodec = null,
            audioCodec = null,
            audioSampleFormat = null,
            audioSampleRate = null,
            audioChannels = null,
            frameRate = null,
            bitrate = null,
            scanState = "SCANNED",
            lastError = null
        )
        val entitySdCard = entityLocal.copy(
            contentUri = "content://media/sdcard/video/media/100",
            volumeName = "sdcard",
            displayName = "video_sd.mp4"
        )

        val id1 = mediaItemDao.upsert(entityLocal)
        val id2 = mediaItemDao.upsert(entitySdCard)

        assertTrue(id1 != id2)
        assertEquals(2, mediaItemDao.getAll().first().size)
    }

    @Test
    fun testFoldersLocalAndTfCardAreDifferent() = runBlocking {
        val folderLocal = FolderEntity(
            volumeName = "external_primary",
            relativePath = "Movies/",
            displayName = "Movies"
        )
        val folderSdCard = FolderEntity(
            volumeName = "sdcard",
            relativePath = "Movies/",
            displayName = "Movies"
        )

        val id1 = folderDao.upsert(folderLocal)
        val id2 = folderDao.upsert(folderSdCard)

        assertTrue("Folder IDs must be different for different storage volumes", id1 != id2)
        assertEquals(2, folderDao.getAll().first().size)
    }

    @Test
    fun testMediaMetadataUpdateDoesNotDeleteHistory() = runBlocking {
        val mediaItem = MediaItemEntity(
            contentUri = "content://media/external/video/media/1",
            mediaType = "VIDEO",
            volumeName = "external_primary",
            mediaStoreId = 1L,
            relativePath = "DCIM/Camera/",
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fileSize = 1000L,
            durationMs = 60000L,
            width = null,
            height = null,
            rotationDegrees = null,
            dateAddedEpochSeconds = null,
            dateModifiedEpochSeconds = null,
            dateTakenEpochMillis = null,
            videoCodec = null,
            audioCodec = null,
            audioSampleFormat = null,
            audioSampleRate = null,
            audioChannels = null,
            frameRate = null,
            bitrate = null,
            scanState = "SCANNED",
            lastError = null
        )
        val mediaId = mediaItemDao.upsert(mediaItem)

        val history = PlaybackHistoryEntity(
            mediaId = mediaId,
            positionMs = 5000L,
            durationMs = 60000L,
            lastPlayedAt = System.currentTimeMillis(),
            completed = false,
            playCount = 1,
            preferredSpeed = 1.0f
        )
        playbackHistoryDao.upsert(history)

        // Verify history exists
        val historyListBefore = playbackHistoryDao.getAll().first()
        assertEquals(1, historyListBefore.size)

        // Update metadata using @Upsert
        val updatedMedia = mediaItem.copy(id = mediaId, displayName = "video_new.mp4")
        mediaItemDao.upsert(updatedMedia)

        // Verify history is NOT deleted because we used @Upsert instead of REPLACE
        val historyListAfter = playbackHistoryDao.getAll().first()
        assertEquals("History should remain intact after metadata upsert update", 1, historyListAfter.size)
    }

    @Test
    fun testMediaMetadataUpdateDoesNotDeleteThumbnailCache() = runBlocking {
        val mediaItem = MediaItemEntity(
            contentUri = "content://media/external/video/media/1",
            mediaType = "VIDEO",
            volumeName = "external_primary",
            mediaStoreId = 1L,
            relativePath = "DCIM/Camera/",
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fileSize = 1000L,
            durationMs = 60000L,
            width = null,
            height = null,
            rotationDegrees = null,
            dateAddedEpochSeconds = null,
            dateModifiedEpochSeconds = null,
            dateTakenEpochMillis = null,
            videoCodec = null,
            audioCodec = null,
            audioSampleFormat = null,
            audioSampleRate = null,
            audioChannels = null,
            frameRate = null,
            bitrate = null,
            scanState = "SCANNED",
            lastError = null
        )
        val mediaId = mediaItemDao.upsert(mediaItem)

        val thumbnail = ThumbnailCacheEntity(
            mediaId = mediaId,
            thumbnailType = "COVER_SMALL",
            width = 100,
            height = 100,
            framePositionMs = 0L,
            mediaModifiedTime = 12345L,
            cachePath = "/cache/small.png",
            generationState = "SUCCESS",
            lastAccessTime = System.currentTimeMillis()
        )
        thumbnailCacheDao.upsert(thumbnail)

        // Verify cache exists
        val cacheListBefore = thumbnailCacheDao.getByMediaId(mediaId)
        assertEquals(1, cacheListBefore.size)

        // Update metadata using @Upsert
        val updatedMedia = mediaItem.copy(id = mediaId, displayName = "video_new.mp4")
        mediaItemDao.upsert(updatedMedia)

        // Verify cache is NOT deleted
        val cacheListAfter = thumbnailCacheDao.getByMediaId(mediaId)
        assertEquals("Thumbnail cache should remain intact after metadata upsert update", 1, cacheListAfter.size)
    }

    @Test
    fun testSmallMediumLargeThumbnailCoexistence() = runBlocking {
        val mediaItem = MediaItemEntity(
            contentUri = "content://media/external/video/media/1",
            mediaType = "VIDEO",
            volumeName = "external_primary",
            mediaStoreId = 1L,
            relativePath = "DCIM/Camera/",
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fileSize = 1000L,
            durationMs = null,
            width = null,
            height = null,
            rotationDegrees = null,
            dateAddedEpochSeconds = null,
            dateModifiedEpochSeconds = null,
            dateTakenEpochMillis = null,
            videoCodec = null,
            audioCodec = null,
            audioSampleFormat = null,
            audioSampleRate = null,
            audioChannels = null,
            frameRate = null,
            bitrate = null,
            scanState = "SCANNED",
            lastError = null
        )
        val mediaId = mediaItemDao.upsert(mediaItem)

        val smallThumb = ThumbnailCacheEntity(
            mediaId = mediaId,
            thumbnailType = "COVER_SMALL",
            width = 100,
            height = 100,
            framePositionMs = 0L,
            mediaModifiedTime = 12345L,
            cachePath = "/cache/small.png",
            generationState = "SUCCESS",
            lastAccessTime = System.currentTimeMillis()
        )
        val mediumThumb = smallThumb.copy(
            thumbnailType = "COVER_MEDIUM",
            width = 250,
            height = 250,
            cachePath = "/cache/medium.png"
        )
        val largeThumb = smallThumb.copy(
            thumbnailType = "COVER_LARGE",
            width = 500,
            height = 500,
            cachePath = "/cache/large.png"
        )

        thumbnailCacheDao.upsert(smallThumb)
        thumbnailCacheDao.upsert(mediumThumb)
        thumbnailCacheDao.upsert(largeThumb)

        val caches = thumbnailCacheDao.getByMediaId(mediaId)
        assertEquals("Small, Medium and Large cover versions must coexist in the cache table", 3, caches.size)
    }

    @Test
    fun testMultipleTimelineFramesCoexistence() = runBlocking {
        val mediaItem = MediaItemEntity(
            contentUri = "content://media/external/video/media/1",
            mediaType = "VIDEO",
            volumeName = "external_primary",
            mediaStoreId = 1L,
            relativePath = "DCIM/Camera/",
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fileSize = 1000L,
            durationMs = null,
            width = null,
            height = null,
            rotationDegrees = null,
            dateAddedEpochSeconds = null,
            dateModifiedEpochSeconds = null,
            dateTakenEpochMillis = null,
            videoCodec = null,
            audioCodec = null,
            audioSampleFormat = null,
            audioSampleRate = null,
            audioChannels = null,
            frameRate = null,
            bitrate = null,
            scanState = "SCANNED",
            lastError = null
        )
        val mediaId = mediaItemDao.upsert(mediaItem)

        val frame1 = ThumbnailCacheEntity(
            mediaId = mediaId,
            thumbnailType = "TIMELINE_FRAME",
            width = 160,
            height = 90,
            framePositionMs = 10000L, // 10s
            mediaModifiedTime = 12345L,
            cachePath = "/cache/frame_10s.png",
            generationState = "SUCCESS",
            lastAccessTime = System.currentTimeMillis()
        )
        val frame2 = frame1.copy(
            framePositionMs = 20000L, // 20s
            cachePath = "/cache/frame_20s.png"
        )

        thumbnailCacheDao.upsert(frame1)
        thumbnailCacheDao.upsert(frame2)

        val caches = thumbnailCacheDao.getByMediaId(mediaId)
        assertEquals("Multiple timeline frames at different timestamps must coexist in the cache table", 2, caches.size)
    }

    @Test
    fun testPlaybackProgressWriteAndRestore() = runBlocking {
        val domainItem = MediaItem(
            databaseId = 0L,
            contentUri = "content://media/external/video/media/1",
            mediaType = MediaType.VIDEO,
            volumeName = "external_primary",
            mediaStoreId = 1L,
            relativePath = "DCIM/Camera/",
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fileSize = 1024L,
            durationMs = 60000L,
            width = null, height = null, rotationDegrees = null,
            dateAddedEpochSeconds = null, dateModifiedEpochSeconds = null, dateTakenEpochMillis = null,
            videoCodec = null, audioCodec = null, audioSampleFormat = null, audioSampleRate = null, audioChannels = null,
            frameRate = null, bitrate = null, isHdr = false, isGif = false, isFavorite = false, isHidden = false,
            scanState = ScanState.SCANNED, lastError = null
        )
        mediaRepository.saveMediaItems(listOf(domainItem))

        playbackHistoryRepository.saveHistory("content://media/external/video/media/1", 30000L, 60000L, false)

        val historyList = playbackHistoryRepository.getHistory().first()
        assertEquals(1, historyList.size)
        assertEquals(30000L, historyList[0].playbackPositionMs)
        assertEquals(60000L, historyList[0].durationMs)
        assertEquals("video.mp4", historyList[0].mediaItem?.displayName)
    }

    @Test
    fun testDurationMsProperlySaved() = runBlocking {
        val mediaItem = MediaItemEntity(
            contentUri = "content://media/external/video/media/1",
            mediaType = "VIDEO",
            volumeName = "external_primary",
            mediaStoreId = 1L,
            relativePath = "DCIM/Camera/",
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fileSize = 1000L,
            durationMs = 60000L,
            width = null, height = null, rotationDegrees = null,
            dateAddedEpochSeconds = null, dateModifiedEpochSeconds = null, dateTakenEpochMillis = null,
            videoCodec = null, audioCodec = null, audioSampleFormat = null, audioSampleRate = null, audioChannels = null,
            frameRate = null, bitrate = null, scanState = "SCANNED", lastError = null
        )
        val mediaId = mediaItemDao.upsert(mediaItem)

        val history = PlaybackHistoryEntity(
            mediaId = mediaId,
            positionMs = 5000L,
            durationMs = 60000L, // Passed in durationMs
            lastPlayedAt = System.currentTimeMillis(),
            completed = false,
            playCount = 1,
            preferredSpeed = 1.0f
        )
        playbackHistoryDao.upsert(history)

        val retrieved = playbackHistoryDao.getAll().first()[0]
        assertEquals("DurationMs must be persisted in database table", 60000L, retrieved.durationMs)
    }

    @Test
    fun testPlayCountIncreasesCorrectly() = runBlocking {
        val domainItem = MediaItem(
            databaseId = 0L,
            contentUri = "content://media/external/video/media/1",
            mediaType = MediaType.VIDEO,
            volumeName = "external_primary",
            mediaStoreId = 1L,
            relativePath = "DCIM/Camera/",
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fileSize = 1024L,
            durationMs = 60000L,
            width = null, height = null, rotationDegrees = null,
            dateAddedEpochSeconds = null, dateModifiedEpochSeconds = null, dateTakenEpochMillis = null,
            videoCodec = null, audioCodec = null, audioSampleFormat = null, audioSampleRate = null, audioChannels = null,
            frameRate = null, bitrate = null, isHdr = false, isGif = false, isFavorite = false, isHidden = false,
            scanState = ScanState.SCANNED, lastError = null
        )
        mediaRepository.saveMediaItems(listOf(domainItem))

        // First session
        playbackHistoryRepository.saveHistory("content://media/external/video/media/1", 10000L, 60000L, false)
        val list1 = playbackHistoryRepository.getHistory().first()
        assertEquals(1, list1[0].playCount)

        // Second session
        playbackHistoryRepository.saveHistory("content://media/external/video/media/1", 20000L, 60000L, false)
        val list2 = playbackHistoryRepository.getHistory().first()
        assertEquals("playCount should be incremented to 2", 2, list2[0].playCount)
    }

    @Test
    fun testRecentPlaybackOrdering() = runBlocking {
        val item1 = MediaItemEntity(
            contentUri = "content://media/external/video/media/1",
            mediaType = "VIDEO", volumeName = "external_primary", mediaStoreId = 1L, relativePath = "DCIM/", displayName = "1.mp4", mimeType = "video/mp4", fileSize = 1000L, durationMs = null, width = null, height = null, rotationDegrees = null, dateAddedEpochSeconds = null, dateModifiedEpochSeconds = null, dateTakenEpochMillis = null, videoCodec = null, audioCodec = null, audioSampleFormat = null, audioSampleRate = null, audioChannels = null, frameRate = null, bitrate = null, scanState = "SCANNED", lastError = null
        )
        val item2 = item1.copy(contentUri = "content://media/external/video/media/2", mediaStoreId = 2L, displayName = "2.mp4")

        val id1 = mediaItemDao.upsert(item1)
        val id2 = mediaItemDao.upsert(item2)

        val history1 = PlaybackHistoryEntity(
            mediaId = id1, positionMs = 10L, durationMs = 100L, lastPlayedAt = 1000L, completed = false, playCount = 1, preferredSpeed = 1.0f
        )
        val history2 = history1.copy(mediaId = id2, lastPlayedAt = 5000L) // More recent

        playbackHistoryDao.upsert(history1)
        playbackHistoryDao.upsert(history2)

        val historyList = playbackHistoryDao.getAll().first()
        assertEquals(id2, historyList[0].mediaId) // id2 is first because lastPlayedAt is more recent
        assertEquals(id1, historyList[1].mediaId)
    }

    @Test
    fun testFavoriteAndHiddenUpdates() = runBlocking {
        val item = MediaItemEntity(
            contentUri = "content://media/external/video/media/1",
            mediaType = "VIDEO", volumeName = "external_primary", mediaStoreId = 1L, relativePath = "DCIM/", displayName = "1.mp4", mimeType = "video/mp4", fileSize = 1000L, durationMs = null, width = null, height = null, rotationDegrees = null, dateAddedEpochSeconds = null, dateModifiedEpochSeconds = null, dateTakenEpochMillis = null, videoCodec = null, audioCodec = null, audioSampleFormat = null, audioSampleRate = null, audioChannels = null, frameRate = null, bitrate = null, isFavorite = false, isHidden = false, scanState = "SCANNED", lastError = null
        )
        val id = mediaItemDao.upsert(item)

        val updatedFav = item.copy(id = id, isFavorite = true, isHidden = true)
        mediaItemDao.upsert(updatedFav)

        val retrieved = mediaItemDao.getById(id)
        assertTrue(retrieved?.isFavorite == true)
        assertTrue(retrieved?.isHidden == true)
    }

    @Test
    fun testMigration1to2() {
        // 1. Create database in version 1
        val db1 = migrationHelper.createDatabase("migration-test", 1)

        db1.execSQL("""
            INSERT INTO media_items (content_uri, media_store_id, volume_name, relative_path, display_name, title, duration_ms, width, height, date_modified, media_type, size_bytes)
            VALUES ('content://media/external/1', 101, 'external_primary', 'DCIM/Camera/', 'video.mp4', 'Video', 5000, 1920, 1080, 123456789, 'VIDEO', 1024)
        """)

        db1.execSQL("""
            INSERT INTO folders (path, display_name, date_modified)
            VALUES ('DCIM/Camera/', 'Camera', 123456789)
        """)

        db1.execSQL("""
            INSERT INTO playback_history (content_uri, last_played_time, playback_position_ms, finished)
            VALUES ('content://media/external/1', 123456789, 2000, 0)
        """)

        db1.execSQL("""
            INSERT INTO thumbnail_cache (content_uri, thumbnail_path, size_bytes, last_accessed)
            VALUES ('content://media/external/1', '/cache/thumb1.png', 5000, 123456789)
        """)
        db1.close()

        // 2. Perform Migration to version 2
        val db2 = migrationHelper.runMigrationsAndValidate("migration-test", 2, true, GalleryDatabase.MIGRATION_1_2)

        // 3. Verify Migrated Fields in version 2
        val mediaCursor = db2.query("SELECT * FROM media_items WHERE content_uri = 'content://media/external/1'")
        assertTrue(mediaCursor.moveToFirst())
        val idCol = mediaCursor.getColumnIndex("id")
        val idVal = mediaCursor.getLong(idCol)
        assertTrue(idVal > 0)
        assertEquals("video.mp4", mediaCursor.getString(mediaCursor.getColumnIndex("display_name")))
        mediaCursor.close()

        val folderCursor = db2.query("SELECT * FROM folders WHERE relative_path = 'DCIM/Camera/'")
        assertTrue(folderCursor.moveToFirst())
        assertEquals("Camera", folderCursor.getString(folderCursor.getColumnIndex("display_name")))
        folderCursor.close()

        val historyCursor = db2.query("SELECT * FROM playback_history WHERE media_id = $idVal")
        assertTrue(historyCursor.moveToFirst())
        assertEquals(2000L, historyCursor.getLong(historyCursor.getColumnIndex("position_ms")))
        assertEquals(0, historyCursor.getInt(historyCursor.getColumnIndex("completed")))
        historyCursor.close()

        val cacheCursor = db2.query("SELECT * FROM thumbnail_cache WHERE media_id = $idVal")
        assertTrue(cacheCursor.moveToFirst())
        assertEquals("/cache/thumb1.png", cacheCursor.getString(cacheCursor.getColumnIndex("cache_path")))
        assertEquals("COVER_MEDIUM", cacheCursor.getString(cacheCursor.getColumnIndex("thumbnail_type")))
        cacheCursor.close()
    }

    @Test
    fun testForeignKeyConstraints() = runBlocking {
        val mediaItem = MediaItemEntity(
            contentUri = "content://media/external/video/media/1",
            mediaType = "VIDEO", volumeName = "external_primary", mediaStoreId = 1L, relativePath = "DCIM/", displayName = "1.mp4", mimeType = "video/mp4", fileSize = 1000L, durationMs = null, width = null, height = null, rotationDegrees = null, dateAddedEpochSeconds = null, dateModifiedEpochSeconds = null, dateTakenEpochMillis = null, videoCodec = null, audioCodec = null, audioSampleFormat = null, audioSampleRate = null, audioChannels = null, frameRate = null, bitrate = null, scanState = "SCANNED", lastError = null
        )
        val mediaId = mediaItemDao.upsert(mediaItem)

        val history = PlaybackHistoryEntity(
            mediaId = mediaId, positionMs = 10L, durationMs = 100L, lastPlayedAt = 1000L, completed = false, playCount = 1, preferredSpeed = 1.0f
        )
        playbackHistoryDao.upsert(history)

        val thumbnail = ThumbnailCacheEntity(
            mediaId = mediaId, thumbnailType = "COVER_SMALL", width = 100, height = 100, framePositionMs = 0L, mediaModifiedTime = 12345L, cachePath = "/cache/small.png", generationState = "SUCCESS", lastAccessTime = System.currentTimeMillis()
        )
        thumbnailCacheDao.upsert(thumbnail)

        // Delete parent MediaItem
        mediaItemDao.deleteById(mediaId)

        // Verify cascade delete
        val historyList = playbackHistoryDao.getAll().first()
        assertTrue("Playback history must be cascade deleted", historyList.isEmpty())

        val caches = thumbnailCacheDao.getByMediaId(mediaId)
        assertTrue("Thumbnail cache must be cascade deleted", caches.isEmpty())
    }
}
