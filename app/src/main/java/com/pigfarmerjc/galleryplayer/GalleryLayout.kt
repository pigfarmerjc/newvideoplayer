package com.pigfarmerjc.galleryplayer

import java.util.Locale

/** Layout decisions kept pure so the grid can be tuned without touching Compose code. */
object GalleryLayout {
    fun columnsForWidth(widthDp: Int, isLandscape: Boolean): Int = when {
        widthDp >= 840 -> 5
        widthDp >= 600 -> if (isLandscape) 5 else 4
        widthDp >= 420 -> if (isLandscape) 4 else 3
        else -> if (isLandscape) 3 else 2
    }

    fun progressRatio(positionMs: Long, durationMs: Long): Float? {
        if (positionMs <= 0L || durationMs <= 0L) return null
        return (positionMs.toDouble() / durationMs.toDouble())
            .coerceIn(0.0, 1.0)
            .toFloat()
            .takeUnless { it <= 0.005f || it >= 0.995f }
    }

    fun thumbnailBucket(dimensionPx: Int, maxDimension: Int = 768): Int {
        val safeMax = maxDimension.coerceAtLeast(64)
        val safe = dimensionPx.coerceIn(1, safeMax)
        return (((safe + 63) / 64) * 64).coerceAtMost(safeMax)
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    fun is4K(width: Int, height: Int): Boolean {
        return width >= 3840 || height >= 2160 || (width >= 2160 && height >= 3840)
    }

    fun isHD(width: Int, height: Int): Boolean {
        return (width in 1280..3839 || height in 720..2159) && !is4K(width, height)
    }
}
