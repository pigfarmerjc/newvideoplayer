package com.pigfarmerjc.galleryplayer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pigfarmerjc.galleryplayer.core.model.MediaType
import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewerScreen(
    images: List<LocalMediaItem>,
    initialIndex: Int,
    onBack: () -> Unit,
    isFavorite: (String) -> Boolean = { false },
    onToggleFavorite: (String) -> Unit = {}
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { images.size }
    )

    BackHandler {
        onBack()
    }

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val bgAlpha = remember(dragOffsetY) {
        if (dragOffsetY > 0f) {
            (1f - (dragOffsetY / (screenHeightPx * 0.55f))).coerceIn(0f, 1f)
        } else {
            1f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = bgAlpha))
            .pointerInput(Unit) {
                var velocityTracker = VelocityTracker()
                detectDragGestures(
                    onDragStart = {
                        dragOffsetY = 0f
                        velocityTracker = VelocityTracker()
                    },
                    onDrag = { change, dragAmount ->
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        if (dragAmount.y > 0f || dragOffsetY > 0f) {
                            dragOffsetY = (dragOffsetY + dragAmount.y).coerceAtLeast(0f)
                            if (dragOffsetY > 0f) {
                                change.consume()
                            }
                        }
                    },
                    onDragEnd = {
                        val velocityY = velocityTracker.calculateVelocity().y
                        if (dragOffsetY > 110.dp.toPx() || velocityY > 800f) {
                            coroutineScope.launch {
                                Animatable(dragOffsetY).animateTo(screenHeightPx, tween(180, easing = FastOutSlowInEasing)) {
                                    dragOffsetY = value
                                }
                                onBack()
                            }
                        } else {
                            coroutineScope.launch {
                                Animatable(dragOffsetY).animateTo(0f, spring(0.82f, Spring.StiffnessMediumLow)) {
                                    dragOffsetY = value
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            Animatable(dragOffsetY).animateTo(0f, spring(0.82f, Spring.StiffnessMediumLow)) {
                                dragOffsetY = value
                            }
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val progress = if (size.height > 0f) (dragOffsetY / size.height).coerceIn(0f, 1f) else 0f
                    translationY = dragOffsetY
                    val scale = (1f - progress * 0.22f).coerceIn(0.75f, 1f)
                    scaleX = scale
                    scaleY = scale
                    clip = progress > 0.005f
                    shape = RoundedCornerShape((28f * (progress * 2.5f).coerceIn(0f, 1f)).dp)
                }
        ) {
            // Custom Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Spacer(modifier = Modifier.width(8.dp))
                val currentItem = images.getOrNull(pagerState.currentPage)
                Text(
                    text = currentItem?.displayName ?: "图片预览",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (currentItem != null) {
                    val fav = isFavorite(currentItem.contentUri)
                    IconButton(onClick = { onToggleFavorite(currentItem.contentUri) }) {
                        Icon(
                            imageVector = if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (fav) "取消收藏" else "加入收藏",
                            tint = if (fav) Color(0xFFFF3B30) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (images.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${images.size}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) { pageIndex ->
                val image = images[pageIndex]
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (image.isGif) {
                        AnimatedGifView(
                            contentUri = image.contentUri,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Show fullscreen image using our thumbnail loader
                        MediaThumbnail(
                            contentUri = image.contentUri,
                            mediaType = image.mediaType,
                            modifier = Modifier.fillMaxSize(),
                            maxDecodeDimension = 2_048,
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedGifView(
    contentUri: String,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            android.widget.ImageView(ctx).apply {
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }
        },
        update = { imageView ->
            val uri = android.net.Uri.parse(contentUri)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                    val drawable = android.graphics.ImageDecoder.decodeDrawable(source) { decoder, _, _ ->
                        decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                    imageView.setImageDrawable(drawable)
                    if (drawable is android.graphics.drawable.AnimatedImageDrawable) {
                        drawable.repeatCount = android.graphics.drawable.AnimatedImageDrawable.REPEAT_INFINITE
                        drawable.start()
                    }
                } else {
                    val bitmap = android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
                    imageView.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                android.util.Log.e("AnimatedGifView", "Error decoding GIF: ${e.message}", e)
            }
        },
        modifier = modifier
    )
}
