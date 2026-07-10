package com.pigfarmerjc.galleryplayer

object PhotosGridState {
    fun applyZoomToColumns(
        currentColumns: Int,
        zoomFactor: Float,
        minColumns: Int = 2,
        maxColumns: Int = 12
    ): Int {
        return when {
            zoomFactor > 1.08f -> (currentColumns - 1).coerceIn(minColumns, maxColumns)
            zoomFactor < 0.92f -> (currentColumns + 1).coerceIn(minColumns, maxColumns)
            else -> currentColumns
        }
    }
}
