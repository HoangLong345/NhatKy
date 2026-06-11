package com.example.nhatki.ui.components

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // Improved zoom sensitivity and bounds
                    scale = (scale * zoom).coerceIn(1f, 10f)
                    
                    // Only allow panning when zoomed in
                    if (scale > 1f) {
                        offsetX += pan.x * scale
                        offsetY += pan.y * scale
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY
            )
    ) {
        content()
    }
}
