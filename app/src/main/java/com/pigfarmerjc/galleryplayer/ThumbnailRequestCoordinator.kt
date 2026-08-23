package com.pigfarmerjc.galleryplayer

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ThumbnailRequestCoordinator(stripeCount: Int = 32) {
    private val locks = List(stripeCount.coerceAtLeast(1)) { Mutex() }

    suspend fun <T> load(
        key: String,
        cached: () -> T?,
        loader: suspend () -> T?
    ): T? {
        cached()?.let { return it }
        val lockIndex = (key.hashCode() and Int.MAX_VALUE) % locks.size
        return locks[lockIndex].withLock {
            cached() ?: loader()
        }
    }
}
