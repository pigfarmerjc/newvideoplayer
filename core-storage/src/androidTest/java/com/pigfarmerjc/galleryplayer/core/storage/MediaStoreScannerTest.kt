package com.pigfarmerjc.galleryplayer.core.storage

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pigfarmerjc.galleryplayer.core.database.GalleryDatabase
import com.pigfarmerjc.galleryplayer.core.database.repository.RoomMediaRepository
import com.pigfarmerjc.galleryplayer.core.model.MediaType
import com.pigfarmerjc.galleryplayer.core.model.ScanPhase
import com.pigfarmerjc.galleryplayer.core.storage.mediastore.MediaStoreScannerImpl
import com.pigfarmerjc.galleryplayer.core.storage.saf.SafDirectoryScannerImpl
import com.pigfarmerjc.galleryplayer.core.storage.sync.StorageSyncManager
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.rule.GrantPermissionRule

@RunWith(AndroidJUnit4::class)
class MediaStoreScannerTest {

    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.READ_MEDIA_VIDEO,
        android.Manifest.permission.READ_MEDIA_IMAGES,
        android.Manifest.permission.READ_MEDIA_AUDIO,
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    )

    private lateinit var context: Context
    private lateinit var db: GalleryDatabase
    private lateinit var repository: RoomMediaRepository
    private lateinit var scanner: MediaStoreScannerImpl
    private lateinit var safScanner: SafDirectoryScannerImpl
    private lateinit var syncManager: StorageSyncManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, GalleryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomMediaRepository(db.mediaItemDao(), db.folderDao())
        scanner = MediaStoreScannerImpl(context)
        safScanner = SafDirectoryScannerImpl(context)
        syncManager = StorageSyncManager(repository, scanner, safScanner)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testMediaStoreMockScanAndSync() = runTest {
        // Insert a mock file into MediaStore so the test can scan it
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "test_instrumentation_video_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.SIZE, 4567L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/TestScans/")
            }
        }
        val volume = "external_primary"
        val insertUri = resolver.insert(MediaStore.Video.Media.getContentUri(volume), values)
        assertNotNull(insertUri)

        try {
            val phases = mutableListOf<ScanPhase>()
            // Run scanner through sync coordinator
            val scannedItems = syncManager.scanAndSyncMediaStore(volume) { progress ->
                phases.add(progress.phase)
            }

            // Assert scan ran and progressed through phases
            assertTrue(phases.contains(ScanPhase.SCANNING))
            assertTrue(phases.contains(ScanPhase.PERSISTING))
            assertTrue(phases.contains(ScanPhase.COMPLETED))

            // Verify our inserted video is fetched and written to database
            val itemsInDb = repository.getMediaItemsOnVolumeSync(volume)
            val matchedItem = itemsInDb.find { it.contentUri == insertUri.toString() }
            assertNotNull(matchedItem)
            assertEquals("video/mp4", matchedItem!!.mimeType)
            assertEquals(MediaType.VIDEO, matchedItem.mediaType)
            assertEquals(4567L, matchedItem.fileSize)

            // Verify folder aggregate entity is populated
            val expectedPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "Movies/TestScans/" else ""
            val folder = repository.getFolderByVolumeAndPath(volume, expectedPath)
            assertNotNull(folder)
            assertTrue(folder!!.mediaCount >= 1)
            assertTrue(folder.videoCount >= 1)
        } finally {
            // Cleanup the mock MediaStore entry
            resolver.delete(insertUri!!, null, null)
        }
    }
}
