package com.pigfarmerjc.galleryplayer

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThumbnailRequestCoordinatorTest {

    @Test
    fun concurrentSameKeyRequestsPerformOneLoad() = runTest {
        val coordinator = ThumbnailRequestCoordinator()
        val loadCount = AtomicInteger(0)
        var cachedValue: String? = null

        val results = List(20) {
            async {
                coordinator.load(
                    key = "content://video/1_320_320",
                    cached = { cachedValue },
                    loader = {
                        loadCount.incrementAndGet()
                        delay(10)
                        "thumbnail".also { cachedValue = it }
                    }
                )
            }
        }.awaitAll()

        assertEquals(List(20) { "thumbnail" }, results)
        assertEquals(1, loadCount.get())
    }

    @Test
    fun failedLoadCanBeRetried() = runTest {
        val coordinator = ThumbnailRequestCoordinator()
        var cachedValue: String? = null

        val first = coordinator.load(
            key = "content://video/1_320_320",
            cached = { cachedValue },
            loader = { null }
        )
        val second = coordinator.load(
            key = "content://video/1_320_320",
            cached = { cachedValue },
            loader = { "retry-result".also { cachedValue = it } }
        )

        assertNull(first)
        assertEquals("retry-result", second)
    }

    @Test
    fun throwingLoadDoesNotBlockLaterRequest() = runTest {
        val coordinator = ThumbnailRequestCoordinator()
        runCatching {
            coordinator.load<String>(
                key = "content://video/1_320_320",
                cached = { null },
                loader = { error("decode failed") }
            )
        }

        val result = coordinator.load(
            key = "content://video/1_320_320",
            cached = { null },
            loader = { "recovered" }
        )

        assertEquals("recovered", result)
    }
}
