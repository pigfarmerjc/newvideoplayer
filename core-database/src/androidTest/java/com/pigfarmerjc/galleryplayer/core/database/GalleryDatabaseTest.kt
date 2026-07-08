package com.pigfarmerjc.galleryplayer.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pigfarmerjc.galleryplayer.core.database.dao.*
import com.pigfarmerjc.galleryplayer.core.database.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class GalleryDatabaseTest {

    private lateinit var db: GalleryDatabase
    private lateinit var mediaItemDao: MediaItemDao
    private lateinit var folderDao: FolderDao
    private lateinit var playbackHistoryDao: PlaybackHistoryDao
    private lateinit var thumbnailCacheDao: ThumbnailCacheDao
    private lateinit var storageVolumeDao: StorageVolumeDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GalleryDatabase::class.java)
            // Allow queries on main thread for test convenience
            .allowMainThreadQueries()
            .build()
        
        mediaItemDao = db.mediaItemDao()
        folderDao = db.folderDao()
        playbackHistoryDao = db.playbackHistoryDao()
        thumbnailCacheDao = db.thumbnailCacheDao()
        storageVolumeDao = db.storageVolumeDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testInsertAndGetMediaItem() = runBlocking {
        val entity = MediaItemEntity(
            contentUri = "content://media/external/video/media/1",
            mediaStoreId = 1L,
            volumeName = "external_primary",
            relativePath = "DCIM/Camera/",
            displayName = "video1.mp4",
            title = "Video 1",
            durationMs = 120000L,
            width = 1920,
            height = 1080,
            dateModified = System.currentTimeMillis() / 1000,
            mediaType = "VIDEO",
            sizeBytes = 10485760L
        )

        mediaItemDao.insertOrReplace(entity)

        val retrieved = mediaItemDao.getByUri(entity.contentUri)
        assertNotNull(retrieved)
        assertEquals(entity.displayName, retrieved?.displayName)
        assertEquals(entity.mediaType, retrieved?.mediaType)

        val allItems = mediaItemDao.getAll().first()
        assertEquals(1, allItems.size)
        assertEquals(entity.contentUri, allItems[0].contentUri)
    }

    @Test
    fun testBulkUpsertMediaItems() = runBlocking {
        val item1 = MediaItemEntity(
            contentUri = "content://media/external/video/media/1",
            mediaStoreId = 1L,
            volumeName = "external_primary",
            relativePath = "DCIM/Camera/",
            displayName = "video1.mp4",
            title = "Video 1",
            durationMs = 120000L,
            width = 1920,
            height = 1080,
            dateModified = 1000L,
            mediaType = "VIDEO",
            sizeBytes = 1000L
        )
        val item2 = MediaItemEntity(
            contentUri = "content://media/external/video/media/2",
            mediaStoreId = 2L,
            volumeName = "external_primary",
            relativePath = "DCIM/Camera/",
            displayName = "video2.mp4",
            title = "Video 2",
            durationMs = 60000L,
            width = 1280,
            height = 720,
            dateModified = 2000L,
            mediaType = "VIDEO",
            sizeBytes = 2000L
        )

        mediaItemDao.upsertAll(listOf(item1, item2))

        val allItems = mediaItemDao.getAll().first()
        assertEquals(2, allItems.size)

        // Test update on conflict
        val updatedItem1 = item1.copy(displayName = "video1_updated.mp4")
        mediaItemDao.upsertAll(listOf(updatedItem1))

        val allItemsAfterUpdate = mediaItemDao.getAll().first()
        assertEquals(2, allItemsAfterUpdate.size)
        val retrievedItem1 = mediaItemDao.getByUri(item1.contentUri)
        assertEquals("video1_updated.mp4", retrievedItem1?.displayName)
    }

    @Test
    fun testCascadeDeletion() = runBlocking {
        val mediaItem = MediaItemEntity(
            contentUri = "content://media/external/video/media/1",
            mediaStoreId = 1L,
            volumeName = "external_primary",
            relativePath = "DCIM/Camera/",
            displayName = "video1.mp4",
            title = "Video 1",
            durationMs = 120000L,
            width = 1920,
            height = 1080,
            dateModified = 1000L,
            mediaType = "VIDEO",
            sizeBytes = 1000L
        )
        mediaItemDao.insertOrReplace(mediaItem)

        // Insert history referencing the media item
        val history = PlaybackHistoryEntity(
            contentUri = mediaItem.contentUri,
            lastPlayedTime = System.currentTimeMillis(),
            playbackPositionMs = 5000L,
            finished = false
        )
        playbackHistoryDao.insertOrReplace(history)

        // Insert thumbnail cache referencing the media item
        val thumbnail = ThumbnailCacheEntity(
            contentUri = mediaItem.contentUri,
            thumbnailPath = "/cache/thumb1.png",
            sizeBytes = 5000L,
            lastAccessed = System.currentTimeMillis()
        )
        thumbnailCacheDao.insertOrReplace(thumbnail)

        // Verify they exist
        val historyList = playbackHistoryDao.getAll().first()
        assertEquals(1, historyList.size)
        assertNotNull(thumbnailCacheDao.getByUri(mediaItem.contentUri))

        // Delete the parent media item
        mediaItemDao.deleteByUri(mediaItem.contentUri)

        // Verify child records are cascade deleted
        val historyListAfterDelete = playbackHistoryDao.getAll().first()
        assertTrue("Playback history should be cascade deleted", historyListAfterDelete.isEmpty())
        assertNull("Thumbnail cache should be cascade deleted", thumbnailCacheDao.getByUri(mediaItem.contentUri))
    }

    @Test
    fun testPlaybackHistoryWithRelation() = runBlocking {
        val mediaItem = MediaItemEntity(
            contentUri = "content://media/external/video/media/1",
            mediaStoreId = 1L,
            volumeName = "external_primary",
            relativePath = "DCIM/Camera/",
            displayName = "video1.mp4",
            title = "Video 1",
            durationMs = 120000L,
            width = 1920,
            height = 1080,
            dateModified = 1000L,
            mediaType = "VIDEO",
            sizeBytes = 1000L
        )
        mediaItemDao.insertOrReplace(mediaItem)

        val history = PlaybackHistoryEntity(
            contentUri = mediaItem.contentUri,
            lastPlayedTime = 123456789L,
            playbackPositionMs = 5000L,
            finished = false
        )
        playbackHistoryDao.insertOrReplace(history)

        val relationList = playbackHistoryDao.getHistoryWithMediaItem().first()
        assertEquals(1, relationList.size)
        assertEquals(mediaItem.displayName, relationList[0].mediaItem?.displayName)
        assertEquals(history.playbackPositionMs, relationList[0].history.playbackPositionMs)
    }

    @Test
    fun testFoldersAndVolumes() = runBlocking {
        val folder = FolderEntity(
            path = "DCIM/Camera/",
            displayName = "Camera",
            dateModified = 12345L
        )
        folderDao.upsertAll(listOf(folder))
        val folders = folderDao.getAll().first()
        assertEquals(1, folders.size)
        assertEquals("Camera", folders[0].displayName)

        val volume = StorageVolumeEntity(
            volumeName = "external_primary",
            path = "/storage/emulated/0",
            description = "Internal Storage",
            totalBytes = 64000000000L,
            freeBytes = 32000000000L,
            isRemovable = false
        )
        storageVolumeDao.upsertAll(listOf(volume))
        val volumes = storageVolumeDao.getAll().first()
        assertEquals(1, volumes.size)
        assertEquals("Internal Storage", volumes[0].description)
    }
}
