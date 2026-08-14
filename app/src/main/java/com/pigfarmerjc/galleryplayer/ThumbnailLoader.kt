package com.pigfarmerjc.galleryplayer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.pigfarmerjc.galleryplayer.core.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

object ThumbnailCache {
    val hitCount = AtomicInteger(0)
    val missCount = AtomicInteger(0)

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 12).coerceIn(16 * 1024, 64 * 1024)

    private val cache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun get(key: String): Bitmap? {
        val bitmap = cache.get(key)
        if (bitmap != null) {
            hitCount.incrementAndGet()
        } else {
            missCount.incrementAndGet()
        }
        return bitmap
    }
    
    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    fun clear() {
        cache.evictAll()
        hitCount.set(0)
        missCount.set(0)
    }
}

object ThumbnailLoader {
    // A large library can compose hundreds of cells at once. Keep MediaStore/bitmap work
    // bounded so a fling does not create an IO and native decode storm.
    private val decodeSlots = Semaphore(permits = 3)
    private val decodeDispatcher = Dispatchers.IO.limitedParallelism(3)

    suspend fun loadMediaThumbnail(
        context: Context,
        contentUri: String,
        mediaType: MediaType,
        width: Int,
        height: Int,
        maxDimension: Int = 768
    ): Bitmap? = withContext(decodeDispatcher) {
        val decodeWidth = GalleryLayout.thumbnailBucket(width, maxDimension)
        val decodeHeight = GalleryLayout.thumbnailBucket(height, maxDimension)
        val cacheKey = "${contentUri}_${decodeWidth}_${decodeHeight}"
        val cached = ThumbnailCache.get(cacheKey)
        if (cached != null) {
            return@withContext cached
        }

        return@withContext decodeSlots.withPermit {
            try {
                val uri = Uri.parse(contentUri)
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(uri, Size(decodeWidth, decodeHeight), null)
                } else {
                    val id = uri.lastPathSegment?.toLongOrNull() ?: return@withPermit null
                    if (mediaType == MediaType.VIDEO) {
                        MediaStore.Video.Thumbnails.getThumbnail(
                            context.contentResolver,
                            id,
                            MediaStore.Video.Thumbnails.MINI_KIND,
                            null
                        )
                    } else {
                        MediaStore.Images.Thumbnails.getThumbnail(
                            context.contentResolver,
                            id,
                            MediaStore.Images.Thumbnails.MINI_KIND,
                            null
                        )
                    }
                }
                if (bitmap != null) ThumbnailCache.put(cacheKey, bitmap)
                bitmap
            } catch (e: Exception) {
                null
            }
        }
    }
}

@Composable
fun MediaThumbnail(
    contentUri: String,
    mediaType: MediaType,
    modifier: Modifier = Modifier,
    width: Int = 320,
    height: Int = 180,
    maxDecodeDimension: Int = 768,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val initialKey = remember(contentUri, width, height, maxDecodeDimension) {
        val decodeWidth = GalleryLayout.thumbnailBucket(width, maxDecodeDimension)
        val decodeHeight = GalleryLayout.thumbnailBucket(height, maxDecodeDimension)
        "${contentUri}_${decodeWidth}_${decodeHeight}"
    }
    var bitmap by remember(contentUri, width, height, maxDecodeDimension) {
        mutableStateOf(ThumbnailCache.get(initialKey))
    }
    var measuredWidth by remember(contentUri) { mutableIntStateOf(width) }
    var measuredHeight by remember(contentUri) { mutableIntStateOf(height) }

    LaunchedEffect(contentUri, measuredWidth, measuredHeight, maxDecodeDimension) {
        if (measuredWidth <= 0 || measuredHeight <= 0) return@LaunchedEffect
        val result = ThumbnailLoader.loadMediaThumbnail(
            context,
            contentUri,
            mediaType,
            measuredWidth.coerceAtLeast(1),
            measuredHeight.coerceAtLeast(1),
            maxDecodeDimension
        )
        bitmap = result
    }

    Box(
        modifier = modifier
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    measuredWidth = size.width
                    measuredHeight = size.height
                }
            }
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val b = bitmap
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val icon = if (mediaType == MediaType.VIDEO) {
                Icons.Default.PlayCircle
            } else {
                Icons.Default.AccountBox
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
