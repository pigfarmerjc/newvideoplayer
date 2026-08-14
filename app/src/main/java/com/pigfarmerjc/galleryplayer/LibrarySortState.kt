package com.pigfarmerjc.galleryplayer

enum class VideoSortMode {
    DATE_MODIFIED_DESC,
    NAME_ASC,
    NAME_DESC,
    DURATION_DESC,
    DURATION_ASC,
    SIZE_DESC,
    SIZE_ASC
}

enum class FolderSortMode {
    NAME_ASC,
    VIDEO_COUNT_DESC,
    TOTAL_SIZE_DESC,
    DATE_MODIFIED_DESC
}

object VideoFilterAndSort {
    fun filterAndSort(
        videos: List<LocalMediaItem>,
        query: String,
        sortMode: VideoSortMode
    ): List<LocalMediaItem> {
        val filtered = if (query.isBlank()) {
            videos
        } else {
            val q = query.trim().lowercase()
            videos.filter { video ->
                val nameMatch = video.displayName.lowercase().contains(q)
                val folderMatch = video.relativePath.lowercase().contains(q)
                nameMatch || folderMatch
            }
        }

        return when (sortMode) {
            VideoSortMode.DATE_MODIFIED_DESC -> filtered.sortedByDescending { it.dateModifiedEpochSeconds ?: 0L }
            VideoSortMode.NAME_ASC -> filtered.sortedBy { it.displayName.lowercase() }
            VideoSortMode.NAME_DESC -> filtered.sortedByDescending { it.displayName.lowercase() }
            VideoSortMode.DURATION_DESC -> filtered.sortedByDescending { it.durationMs ?: 0L }
            VideoSortMode.DURATION_ASC -> filtered.sortedBy { it.durationMs ?: 0L }
            VideoSortMode.SIZE_DESC -> filtered.sortedByDescending { it.fileSize }
            VideoSortMode.SIZE_ASC -> filtered.sortedBy { it.fileSize }
        }
    }
}

object FolderSort {
    fun videosInFolder(
        videos: List<LocalMediaItem>,
        volumeName: String,
        relativePath: String
    ): List<LocalMediaItem> = videos.filter { video ->
        video.volumeName == volumeName && video.relativePath == relativePath
    }

    fun sort(
        folders: List<FolderItem>,
        sortMode: FolderSortMode,
        videos: List<LocalMediaItem>
    ): List<FolderItem> {
        return when (sortMode) {
            FolderSortMode.NAME_ASC -> folders.sortedBy { it.displayName.lowercase() }
            FolderSortMode.VIDEO_COUNT_DESC -> folders.sortedByDescending { it.videoCount }
            FolderSortMode.TOTAL_SIZE_DESC -> folders.sortedByDescending { it.totalSize }
            FolderSortMode.DATE_MODIFIED_DESC -> {
                val newestByFolder = HashMap<Pair<String, String>, Long>(folders.size)
                videos.forEach { video ->
                    val key = video.volumeName to video.relativePath
                    val modified = video.dateModifiedEpochSeconds ?: 0L
                    if (modified > (newestByFolder[key] ?: Long.MIN_VALUE)) {
                        newestByFolder[key] = modified
                    }
                }
                folders.sortedByDescending { folder ->
                    newestByFolder[folder.volumeName to folder.relativePath] ?: 0L
                }
            }
        }
    }
}
