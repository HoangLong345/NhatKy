package com.example.nhatki.ui.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter

@Composable
fun AppBackground(
    backgroundUri: String?,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (backgroundUri != null) {
            Image(
                painter = rememberAsyncImagePainter(model = Uri.parse(backgroundUri)),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.5f // Set transparency
            )
        }
        
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (backgroundUri != null) 
                MaterialTheme.colorScheme.background.copy(alpha = 0.6f) 
            else 
                MaterialTheme.colorScheme.background
        ) {
            content()
        }
    }
}
