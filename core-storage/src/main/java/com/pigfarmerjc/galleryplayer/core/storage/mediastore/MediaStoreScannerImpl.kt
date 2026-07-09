package com.pigfarmerjc.galleryplayer.core.storage.mediastore

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.pigfarmerjc.galleryplayer.core.model.*
import com.pigfarmerjc.galleryplayer.core.storage.MediaStoreScanner
import com.pigfarmerjc.galleryplayer.core.storage.util.MimeMapper
import com.pigfarmerjc.galleryplayer.core.storage.util.PermissionChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class MediaStoreScannerImpl(private val context: Context) : MediaStoreScanner {

    override suspend fun scanVolume(
        volumeName: String,
        onProgress: (ScanProgress) -> Unit
    ): List<MediaItem> {
        // 1. Permission Check
        if (!PermissionChecker.hasStoragePermission(context)) {
            val progress = ScanProgress(
                phase = ScanPhase.FAILED,
                sourceType = ScanSourceType.MEDIASTORE,
                volumeName = volumeName,
                scannedCount = 0,
                insertedCount = 0,
                updatedCount = 0,
                deletedCount = 0,
                failedItems = 0,
                lastError = "Missing required storage permissions",
                currentPath = null
            )
            onProgress(progress)
            throw SecurityException("Missing storage permissions to scan MediaStore")
        }

        onProgress(
            ScanProgress(
                phase = ScanPhase.SCANNING,
                sourceType = ScanSourceType.MEDIASTORE,
                volumeName = volumeName,
                scannedCount = 0,
                insertedCount = 0,
                updatedCount = 0,
                deletedCount = 0,
                failedItems = 0,
                lastError = null,
                currentPath = "Initializing scan for volume: $volumeName"
            )
        )

        val scannedItems = mutableListOf<MediaItem>()
        var scannedCount = 0
        var failedItems = 0
        var lastError: String? = null

        // 2. Define Query Tables
        val videoUri = MediaStore.Video.Media.getContentUri(volumeName)
        val imageUri = MediaStore.Images.Media.getContentUri(volumeName)
        val audioUri = MediaStore.Audio.Media.getContentUri(volumeName)

        val queries = listOf(
            Triple(videoUri, MediaType.VIDEO, "video"),
            Triple(imageUri, MediaType.IMAGE, "image"),
            Triple(audioUri, MediaType.AUDIO, "audio")
        )

        for ((contentTableUri, mediaTypeCategory, label) in queries) {
            currentCoroutineContext().ensureActive()
            
            val projection = getProjectionForType(mediaTypeCategory)
            
            try {
                context.contentResolver.query(
                    contentTableUri,
                    projection,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        try {
                            val item = parseMediaItem(cursor, volumeName, contentTableUri, mediaTypeCategory)
                            if (item != null) {
                                scannedItems.add(item)
                                scannedCount++
                                if (scannedCount % 50 == 0) {
                                    onProgress(
                                        ScanProgress(
                                            phase = ScanPhase.SCANNING,
                                            sourceType = ScanSourceType.MEDIASTORE,
                                            volumeName = volumeName,
                                            scannedCount = scannedCount,
                                            insertedCount = 0,
                                            updatedCount = 0,
                                            deletedCount = 0,
                                            failedItems = failedItems,
                                            lastError = lastError,
                                            currentPath = item.displayName
                                        )
                                    )
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failedItems++
                            lastError = e.message ?: "Failed parsing cursor row"
                            Log.e("MediaStoreScanner", "Failed to parse item row: ${e.message}", e)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failedItems++
                lastError = e.message ?: "Query error for $label collection"
                Log.e("MediaStoreScanner", "Query failed on uri $contentTableUri: ${e.message}", e)
            }
        }

        onProgress(
            ScanProgress(
                phase = ScanPhase.COMPLETED,
                sourceType = ScanSourceType.MEDIASTORE,
                volumeName = volumeName,
                scannedCount = scannedCount,
                insertedCount = 0,
                updatedCount = 0,
                deletedCount = 0,
                failedItems = failedItems,
                lastError = lastError,
                currentPath = "Completed MediaStore scanning"
            )
        )

        return scannedItems
    }

    private fun getProjectionForType(type: MediaType): Array<String> {
        val base = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            base.add(MediaStore.MediaColumns.RELATIVE_PATH)
        }

        when (type) {
            MediaType.VIDEO -> {
                base.add(MediaStore.MediaColumns.DURATION)
                base.add(MediaStore.MediaColumns.WIDTH)
                base.add(MediaStore.MediaColumns.HEIGHT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    base.add(MediaStore.Video.VideoColumns.COLOR_STANDARD) // check presence
                }
                base.add(MediaStore.MediaColumns.DATE_TAKEN)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    base.add(MediaStore.MediaColumns.ORIENTATION)
                }
            }
            MediaType.IMAGE -> {
                base.add(MediaStore.MediaColumns.WIDTH)
                base.add(MediaStore.MediaColumns.HEIGHT)
                base.add(MediaStore.MediaColumns.DATE_TAKEN)
                base.add(MediaStore.MediaColumns.ORIENTATION)
            }
            MediaType.AUDIO -> {
                base.add(MediaStore.MediaColumns.DURATION)
            }
            else -> {}
        }
        return base.toTypedArray()
    }

    private fun parseMediaItem(
        cursor: Cursor,
        volumeName: String,
        tableUri: Uri,
        category: MediaType
    ): MediaItem? {
        val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val idVal = cursor.getLong(idIdx)

        val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val nameVal = cursor.getString(nameIdx) ?: ""

        val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
        val mimeVal = cursor.getString(mimeIdx)

        // 1. Resolve exact MediaType (especially distinguishing GIF)
        val resolvedMediaType = MimeMapper.map(mimeVal, nameVal) ?: return null

        val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
        val sizeVal = cursor.getLong(sizeIdx)

        val addedIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
        val addedVal = if (cursor.isNull(addedIdx)) null else cursor.getLong(addedIdx)

        val modifiedIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
        val modifiedVal = if (cursor.isNull(modifiedIdx)) null else cursor.getLong(modifiedIdx)

        val relativePathIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
        val relativePathVal = if (relativePathIdx != -1 && !cursor.isNull(relativePathIdx)) {
            cursor.getString(relativePathIdx)
        } else {
            null
        }

        // DURATION
        val durationIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
        val durationVal = if (durationIdx != -1 && !cursor.isNull(durationIdx)) {
            val d = cursor.getLong(durationIdx)
            if (d > 0) d else null
        } else {
            null
        }

        // WIDTH & HEIGHT
        val widthIdx = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
        val widthVal = if (widthIdx != -1 && !cursor.isNull(widthIdx)) {
            val w = cursor.getInt(widthIdx)
            if (w > 0) w else null
        } else {
            null
        }

        val heightIdx = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
        val heightVal = if (heightIdx != -1 && !cursor.isNull(heightIdx)) {
            val h = cursor.getInt(heightIdx)
            if (h > 0) h else null
        } else {
            null
        }

        // DATE_TAKEN
        val dateTakenIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
        val dateTakenVal = if (dateTakenIdx != -1 && !cursor.isNull(dateTakenIdx)) {
            val t = cursor.getLong(dateTakenIdx)
            if (t > 0) t else null
        } else {
            null
        }

        // ORIENTATION
        val orientationIdx = cursor.getColumnIndex(MediaStore.MediaColumns.ORIENTATION)
        val orientationVal = if (orientationIdx != -1 && !cursor.isNull(orientationIdx)) {
            cursor.getInt(orientationIdx)
        } else {
            null
        }

        // Construct stable contentUri
        val contentUri = ContentUris.withAppendedId(tableUri, idVal).toString()

        return MediaItem(
            databaseId = 0L,
            contentUri = contentUri,
            mediaType = resolvedMediaType,
            volumeName = volumeName,
            mediaStoreId = idVal,
            relativePath = relativePathVal ?: "",
            displayName = nameVal,
            mimeType = mimeVal,
            fileSize = sizeVal,
            durationMs = durationVal,
            width = widthVal,
            height = heightVal,
            rotationDegrees = orientationVal,
            dateAddedEpochSeconds = addedVal,
            dateModifiedEpochSeconds = modifiedVal,
            dateTakenEpochMillis = dateTakenVal,
            videoCodec = null,
            audioCodec = null,
            audioSampleFormat = null,
            audioSampleRate = null,
            audioChannels = null,
            frameRate = null,
            bitrate = null,
            isHdr = false,
            isGif = (resolvedMediaType == MediaType.GIF),
            isFavorite = false,
            isHidden = false,
            scanState = ScanState.SCANNED,
            lastError = null
        )
    }
}
