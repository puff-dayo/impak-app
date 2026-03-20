package dev.setsuna.impakviewer.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import dev.setsuna.impakviewer.ui.theme.DarkCanvas

@Composable
fun ZoomableImage(
    bitmap: Bitmap,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onTapLeft: () -> Unit,
    onTapRight: () -> Unit,
    onScrubDrag: ((dragAmount: Float?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var scaleBeforeDoubleTap by remember(bitmap) { mutableFloatStateOf(1f) }
    var isZoomed by remember(bitmap) { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "scale",
    )
    val animatedOffsetX by animateFloatAsState(
        targetValue = offset.x,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "offsetX",
    )
    val animatedOffsetY by animateFloatAsState(
        targetValue = offset.y,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "offsetY",
    )

    Box(
        modifier = modifier
            .background(DarkCanvas)
            .pointerInput(bitmap) {
                size = this.size
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(0.5f, 8f)

                    if (zoom != 1f) {
                        val centroidInContent = centroid - Offset(
                            size.width / 2f,
                            size.height / 2f,
                        )
                        offset =
                            (offset - centroidInContent) * (newScale / scale) + centroidInContent
                    }

                    scale = newScale

                    if (scale > 1f) {
                        offset += pan
                        val maxX = size.width * (scale - 1f) / 2f
                        val maxY = size.height * (scale - 1f) / 2f
                        offset = Offset(
                            offset.x.coerceIn(-maxX, maxX),
                            offset.y.coerceIn(-maxY, maxY),
                        )
                    } else {
                        offset = Offset.Zero
                    }
                }
            }
            .pointerInput(bitmap) {
                size = this.size
                detectTapGestures(
                    onTap = { tapOffset ->
                        val leftBoundary = size.width * 0.10f
                        val rightBoundary = size.width * 0.90f
                        when {
                            tapOffset.x < leftBoundary -> onTapLeft()
                            tapOffset.x > rightBoundary -> onTapRight()
                        }
                    },
                    onDoubleTap = { tapOffset ->
                        if (isZoomed) {
                            // Second
                            scale = scaleBeforeDoubleTap
                            offset = Offset.Zero
                            isZoomed = false
                        } else {
                            // First
                            scaleBeforeDoubleTap = scale
                            val newScale = (scale * 1.25f).coerceIn(0.5f, 8f)
                            val centroidInContent = tapOffset - Offset(
                                size.width / 2f,
                                size.height / 2f,
                            )
                            offset =
                                (offset - centroidInContent) * (newScale / scale) + centroidInContent
                            scale = newScale
                            isZoomed = true
                        }
                    },
                )
            }
            .pointerInput(bitmap) {
                size = this.size
                var swipeFired = false
                detectHorizontalDragGestures(
                    onDragEnd    = { swipeFired = false; onScrubDrag?.invoke(null) },
                    onDragCancel = { swipeFired = false; onScrubDrag?.invoke(null) },
                ) { change, dragAmount ->
                    if (scale <= 1f) {
                        change.consume()
                        onScrubDrag?.invoke(dragAmount)
                        if (!swipeFired) {
                            when {
                                dragAmount < -30f -> { onSwipeLeft();  swipeFired = true }
                                dragAmount > 30f  -> { onSwipeRight(); swipeFired = true }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Frame",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = animatedScale,
                    scaleY = animatedScale,
                    translationX = animatedOffsetX,
                    translationY = animatedOffsetY,
                ),
        )
    }
}