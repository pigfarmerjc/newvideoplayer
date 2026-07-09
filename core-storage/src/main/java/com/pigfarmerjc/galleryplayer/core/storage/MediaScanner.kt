package com.pigfarmerjc.galleryplayer.core.storage

import android.net.Uri
import com.pigfarmerjc.galleryplayer.core.model.MediaItem
import com.pigfarmerjc.galleryplayer.core.model.ScanProgress

interface MediaStoreScanner {
    suspend fun scanVolume(volumeName: String, onProgress: (ScanProgress) -> Unit): List<MediaItem>
}

interface SafDirectoryScanner {
    suspend fun scanTree(treeUri: Uri, onProgress: (ScanProgress) -> Unit): List<MediaItem>
}
