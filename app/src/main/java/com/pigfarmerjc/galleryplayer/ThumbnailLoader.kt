package com.pigfarmerjc.galleryplayer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.AccountBox // fallback placeholder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.pigfarmerjc.galleryplayer.core.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThumbnailLoader {

    suspend fun loadMediaThumbnail(context: Context, contentUri: String, mediaType: MediaType): Bitmap? = withContext(Dispatchers.IO) {
        return@withContext try {
            val uri = Uri.parse(contentUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(320, 240), null)
            } else {
                val id = uri.lastPathSegment?.toLongOrNull() ?: return@withContext null
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
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
fun MediaThumbnail(
    contentUri: String,
    mediaType: MediaType,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember(contentUri) { mutableStateOf<Bitmap?>(null) }
    var loaded by remember(contentUri) { mutableStateOf(false) }

    LaunchedEffect(contentUri) {
        // Load thumbnail in background IO dispatcher
        val result = ThumbnailLoader.loadMediaThumbnail(
            context,
            contentUri,
            mediaType
        )
        bitmap = result
        loaded = true
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val b = bitmap
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Placeholder fallback
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
