package com.pigfarmerjc.galleryplayer.core.model

enum class ScanSourceType {
    MEDIASTORE,
    SAF_TREE
}

enum class ScanPhase {
    IDLE,
    SCANNING,
    PERSISTING,
    COMPLETED,
    FAILED
}

data class ScanProgress(
    val phase: ScanPhase,
    val sourceType: ScanSourceType?,
    val volumeName: String?,
    val scannedCount: Int,
    val insertedCount: Int,
    val updatedCount: Int,
    val deletedCount: Int,
    val failedItems: Int,
    val lastError: String?,
    val currentPath: String?
)
