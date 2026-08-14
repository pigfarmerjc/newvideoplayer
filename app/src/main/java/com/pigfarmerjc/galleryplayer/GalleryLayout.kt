package com.pigfarmerjc.galleryplayer

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
}
