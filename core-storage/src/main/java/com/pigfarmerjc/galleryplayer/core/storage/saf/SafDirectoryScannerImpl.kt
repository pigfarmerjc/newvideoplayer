package com.pigfarmerjc.galleryplayer.core.storage.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.pigfarmerjc.galleryplayer.core.model.*
import com.pigfarmerjc.galleryplayer.core.storage.SafDirectoryScanner
import com.pigfarmerjc.galleryplayer.core.storage.util.MimeMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class SafDirectoryScannerImpl(private val context: Context) : SafDirectoryScanner {

    override suspend fun scanTree(
        treeUri: Uri,
        onProgress: (ScanProgress) -> Unit
    ): List<MediaItem> {
        val volumeKey = "SAF:${treeUri.toString().hashCode()}"

        onProgress(
            ScanProgress(
                phase = ScanPhase.SCANNING,
                sourceType = ScanSourceType.SAF_TREE,
                volumeName = volumeKey,
                scannedCount = 0,
                insertedCount = 0,
                updatedCount = 0,
                deletedCount = 0,
                failedItems = 0,
                lastError = null,
                currentPath = "Persisting Tree URI permissions"
            )
        )

        // 1. Take Persistable Permission
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            Log.w("SafDirectoryScanner", "Could not take persistable Uri permission: ${e.message}", e)
        }

        // 2. Load root directory
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
        if (rootDoc == null || !rootDoc.exists() || !rootDoc.isDirectory) {
            val progress = ScanProgress(
                phase = ScanPhase.FAILED,
                sourceType = ScanSourceType.SAF_TREE,
                volumeName = volumeKey,
                scannedCount = 0,
                insertedCount = 0,
                updatedCount = 0,
                deletedCount = 0,
                failedItems = 0,
                lastError = "Root tree directory does not exist or is invalid",
                currentPath = null
            )
            onProgress(progress)
            throw IllegalArgumentException("Invalid SAF Tree URI: $treeUri")
        }

        val scannedItems = mutableListOf<MediaItem>()
        val state = ScanStateContainer(scannedCount = 0, failedItems = 0, lastError = null)

        try {
            traverseDirectory(
                dir = rootDoc,
                relativePath = "", // Root level relative path is empty
                volumeName = volumeKey,
                scannedItems = scannedItems,
                state = state,
                onProgress = onProgress
            )
        } catch (e: CancellationException) {
            throw e
        }

        onProgress(
            ScanProgress(
                phase = ScanPhase.COMPLETED,
                sourceType = ScanSourceType.SAF_TREE,
                volumeName = volumeKey,
                scannedCount = state.scannedCount,
                insertedCount = 0,
                updatedCount = 0,
                deletedCount = 0,
                failedItems = state.failedItems,
                lastError = state.lastError,
                currentPath = "Completed SAF scanning"
            )
        )

        return scannedItems
    }

    private class ScanStateContainer(
        var scannedCount: Int,
        var failedItems: Int,
        var lastError: String?
    )

    private suspend fun traverseDirectory(
        dir: DocumentFile,
        relativePath: String,
        volumeName: String,
        scannedItems: MutableList<MediaItem>,
        state: ScanStateContainer,
        onProgress: (ScanProgress) -> Unit
    ) {
        currentCoroutineContext().ensureActive()

        val files = dir.listFiles()
        for (file in files) {
            currentCoroutineContext().ensureActive()

            if (file.isDirectory) {
                val subDirName = file.name ?: continue
                val nestedPath = if (relativePath.isEmpty()) "$subDirName/" else "$relativePath$subDirName/"
                traverseDirectory(file, nestedPath, volumeName, scannedItems, state, onProgress)
            } else if (file.isFile) {
                try {
                    val name = file.name ?: ""
                    val type = file.type
                    val size = file.length()
                    val lastMod = file.lastModified() / 1000L // convert to seconds epoch

                    val resolvedMediaType = MimeMapper.map(type, name)
                    if (resolvedMediaType != null) {
                        val item = MediaItem(
                            databaseId = 0L,
                            contentUri = file.uri.toString(),
                            mediaType = resolvedMediaType,
                            volumeName = volumeName,
                            mediaStoreId = null, // SAF items have no mediaStoreId
                            relativePath = relativePath,
                            displayName = name,
                            mimeType = type,
                            fileSize = size,
                            durationMs = null,
                            width = null,
                            height = null,
                            rotationDegrees = null,
                            dateAddedEpochSeconds = lastMod,
                            dateModifiedEpochSeconds = lastMod,
                            dateTakenEpochMillis = null,
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
                        scannedItems.add(item)
                        state.scannedCount++

                        if (state.scannedCount % 50 == 0) {
                            onProgress(
                                ScanProgress(
                                    phase = ScanPhase.SCANNING,
                                    sourceType = ScanSourceType.SAF_TREE,
                                    volumeName = volumeName,
                                    scannedCount = state.scannedCount,
                                    insertedCount = 0,
                                    updatedCount = 0,
                                    deletedCount = 0,
                                    failedItems = state.failedItems,
                                    lastError = state.lastError,
                                    currentPath = item.displayName
                                )
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    state.failedItems++
                    state.lastError = e.message ?: "Failed parsing SAF document"
                    Log.e("SafDirectoryScanner", "Failed parsing SAF file entry: ${e.message}", e)
                }
            }
        }
    }
}
