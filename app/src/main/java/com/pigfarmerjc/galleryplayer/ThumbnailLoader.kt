package com.pigfarmerjc.galleryplayer

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
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
    private val keyIndex = ThumbnailKeyIndex()

    private val cache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (newValue == null) {
                keyIndex.remove(key)
            }
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

    fun getByUri(contentUri: String): Bitmap? {
        val key = keyIndex.latestKey(contentUri) ?: return null
        return cache.get(key) ?: run {
            keyIndex.remove(key)
            null
        }
    }
    
    fun put(contentUri: String, key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
        keyIndex.record(contentUri, key)
    }

    fun clear() {
        cache.evictAll()
        keyIndex.clear()
        hitCount.set(0)
        missCount.set(0)
    }
}

object ThumbnailLoader {
    // A large library can compose hundreds of cells at once. Keep MediaStore/bitmap work
    // bounded so a fling does not create an IO and native decode storm.
    private val decodeSlots = Semaphore(permits = 3)
    private val decodeDispatcher = Dispatchers.IO.limitedParallelism(3)
    private val requestCoordinator = ThumbnailRequestCoordinator()

    private fun extractVideoFrameFallback(
        context: Context,
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (uri.scheme == "content") {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    retriever.setDataSource(pfd.fileDescriptor)
                } ?: run {
                    retriever.setDataSource(context, uri)
                }
            } else if (uri.scheme == "file") {
                retriever.setDataSource(uri.path)
            } else {
                retriever.setDataSource(context, uri)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && targetWidth > 0 && targetHeight > 0) {
                retriever.getScaledFrameAtTime(
                    1_000_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    targetWidth,
                    targetHeight
                ) ?: retriever.getScaledFrameAtTime(
                    0L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    targetWidth,
                    targetHeight
                ) ?: retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                  ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } else {
                retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {}
        }
    }

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
        requestCoordinator.load(
            key = cacheKey,
            cached = { ThumbnailCache.get(cacheKey) },
            loader = {
                decodeSlots.withPermit {
                    try {
                        val uri = Uri.parse(contentUri)
                        var bitmap: Bitmap? = null
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            try {
                                bitmap = context.contentResolver.loadThumbnail(uri, Size(decodeWidth, decodeHeight), null)
                            } catch (e: Exception) {
                                bitmap = null
                            }
                        } else {
                            val id = uri.lastPathSegment?.toLongOrNull()
                            if (id != null) {
                                bitmap = if (mediaType == MediaType.VIDEO) {
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
                        }

                        if (bitmap == null && mediaType == MediaType.VIDEO) {
                            bitmap = extractVideoFrameFallback(context, uri, decodeWidth, decodeHeight)
                        }

                        if (bitmap != null) ThumbnailCache.put(contentUri, cacheKey, bitmap)
                        bitmap
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        )
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
    contentScale: ContentScale = ContentScale.Crop,
    placeholderColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    showPlaceholderIcon: Boolean = true
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val initialKey = remember(contentUri, width, height, maxDecodeDimension) {
        val decodeWidth = GalleryLayout.thumbnailBucket(width, maxDecodeDimension)
        val decodeHeight = GalleryLayout.thumbnailBucket(height, maxDecodeDimension)
        "${contentUri}_${decodeWidth}_${decodeHeight}"
    }
    var bitmap by remember(contentUri, width, height, maxDecodeDimension) {
        mutableStateOf(ThumbnailCache.get(initialKey) ?: ThumbnailCache.getByUri(contentUri))
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
        if (result != null) {
            bitmap = result
        }
    }

    Box(
        modifier = modifier.background(placeholderColor),
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
        } else if (showPlaceholderIcon) {
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
