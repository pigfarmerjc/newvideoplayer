package com.pigfarmerjc.galleryplayer

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.pigfarmerjc.galleryplayer.core.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaStoreHelper {

    suspend fun queryAllVideos(context: Context): List<LocalMediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LocalMediaItem>()
        val volumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                MediaStore.getExternalVolumeNames(context).toList()
            } catch (e: Exception) {
                listOf("external")
            }
        } else {
            listOf("external")
        }

        for (volume in volumes) {
            try {
                val uri = MediaStore.Video.Media.getContentUri(volume)
                list.addAll(queryVideosForUri(context, uri, volume))
            } catch (e: Exception) {
                // Ignore failure on specific volume
            }
        }
        return@withContext list
    }

    suspend fun queryAllImages(context: Context): List<LocalMediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LocalMediaItem>()
        val volumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                MediaStore.getExternalVolumeNames(context).toList()
            } catch (e: Exception) {
                listOf("external")
            }
        } else {
            listOf("external")
        }

        for (volume in volumes) {
            try {
                val uri = MediaStore.Images.Media.getContentUri(volume)
                list.addAll(queryImagesForUri(context, uri, volume))
            } catch (e: Exception) {
                // Ignore failure on specific volume
            }
        }
        return@withContext list
    }

    fun queryVideosForUri(context: Context, uri: Uri, volumeName: String): List<LocalMediaItem> {
        val list = mutableListOf<LocalMediaItem>()
        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Video.Media.RELATIVE_PATH)
        }

        context.contentResolver.query(uri, projection.toTypedArray(), null, null, "${MediaStore.Video.Media.DATE_MODIFIED} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val wCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val hCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val modCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
            } else {
                -1
            }

            while (cursor.moveToNext()) {
                val idVal = cursor.getLong(idCol)
                val nameVal = cursor.getString(nameCol) ?: ""
                val sizeVal = cursor.getLong(sizeCol)
                val durVal = cursor.getLong(durCol)
                val wVal = cursor.getInt(wCol)
                val hVal = cursor.getInt(hCol)
                val modVal = cursor.getLong(modCol)
                val mimeVal = cursor.getString(mimeCol)
                val pathVal = if (pathCol != -1) cursor.getString(pathCol) ?: "" else ""

                val itemUri = ContentUris.withAppendedId(uri, idVal).toString()
                list.add(
                    LocalMediaItem(
                        contentUri = itemUri,
                        mediaType = MediaType.VIDEO,
                        volumeName = volumeName,
                        relativePath = pathVal,
                        displayName = nameVal,
                        mimeType = mimeVal,
                        fileSize = sizeVal,
                        durationMs = if (durVal > 0) durVal else null,
                        width = if (wVal > 0) wVal else null,
                        height = if (hVal > 0) hVal else null,
                        dateModifiedEpochSeconds = modVal,
                        isGif = false,
                        mediaStoreId = idVal
                    )
                )
            }
        }
        return list
    }

    fun queryImagesForUri(context: Context, uri: Uri, volumeName: String): List<LocalMediaItem> {
        val list = mutableListOf<LocalMediaItem>()
        val projection = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.MIME_TYPE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Images.Media.RELATIVE_PATH)
        }

        context.contentResolver.query(uri, projection.toTypedArray(), null, null, "${MediaStore.Images.Media.DATE_MODIFIED} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val wCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val hCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val modCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            } else {
                -1
            }

            while (cursor.moveToNext()) {
                val idVal = cursor.getLong(idCol)
                val nameVal = cursor.getString(nameCol) ?: ""
                val sizeVal = cursor.getLong(sizeCol)
                val wVal = cursor.getInt(wCol)
                val hVal = cursor.getInt(hCol)
                val modVal = cursor.getLong(modCol)
                val mimeVal = cursor.getString(mimeCol)
                val pathVal = if (pathCol != -1) cursor.getString(pathCol) ?: "" else ""

                val isGif = mimeVal == "image/gif" || nameVal.lowercase().endsWith(".gif")
                val itemUri = ContentUris.withAppendedId(uri, idVal).toString()
                list.add(
                    LocalMediaItem(
                        contentUri = itemUri,
                        mediaType = if (isGif) MediaType.GIF else MediaType.IMAGE,
                        volumeName = volumeName,
                        relativePath = pathVal,
                        displayName = nameVal,
                        mimeType = mimeVal,
                        fileSize = sizeVal,
                        durationMs = null,
                        width = if (wVal > 0) wVal else null,
                        height = if (hVal > 0) hVal else null,
                        dateModifiedEpochSeconds = modVal,
                        isGif = isGif,
                        mediaStoreId = idVal
                    )
                )
            }
        }
        return list
    }

    suspend fun querySafVideos(context: Context, treeUriStr: String): List<LocalMediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LocalMediaItem>()
        val rootDoc = try {
            DocumentFile.fromTreeUri(context, Uri.parse(treeUriStr))
        } catch (e: Exception) {
            null
        } ?: return@withContext emptyList()

        val videoExtensions = setOf(
            "mp4", "mkv", "avi", "mov", "m4v", "ts", "m2ts", "webm", "flv", "wmv", "3gp"
        )

        fun traverse(dir: DocumentFile, relativePath: String) {
            val files = try { dir.listFiles() } catch (e: Exception) { emptyArray() }
            for (file in files) {
                if (file.isDirectory) {
                    val subName = file.name ?: continue
                    val nestedPath = if (relativePath.isEmpty()) "$subName/" else "$relativePath$subName/"
                    traverse(file, nestedPath)
                } else if (file.isFile) {
                    val name = file.name ?: ""
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext in videoExtensions) {
                        val size = file.length()
                        val lastMod = file.lastModified() / 1000L
                        list.add(
                            LocalMediaItem(
                                contentUri = file.uri.toString(),
                                mediaType = MediaType.VIDEO,
                                volumeName = "SAF",
                                relativePath = relativePath,
                                displayName = name,
                                mimeType = file.type ?: "video/*",
                                fileSize = size,
                                durationMs = null,
                                width = null,
                                height = null,
                                dateModifiedEpochSeconds = lastMod,
                                isGif = false,
                                mediaStoreId = null
                            )
                        )
                    }
                }
            }
        }

        traverse(rootDoc, "")
        return@withContext list
    }
}
