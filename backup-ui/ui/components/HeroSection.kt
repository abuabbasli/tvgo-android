package com.example.androidtviptvapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.example.androidtviptvapp.data.Movie

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HeroSection(
    movie: Movie,
    onPlayClick: (Movie) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
    ) {
        // Background Image
        AsyncImage(
            model = movie.thumbnail,
            contentDescription = movie.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter
        )

        // Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF1A1A1A)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF1A1A1A).copy(alpha = 0.9f),
                            Color.Transparent
                        ),
                        startX = 0f,
                        endX = 1000f
                    )
                )
        )

        // Content
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 50.dp, bottom = 40.dp)
                .widthIn(max = 600.dp)
        ) {
            Text(
                text = movie.title,
                style = MaterialTheme.typography.displayMedium,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = movie.description,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 3
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { onPlayClick(movie) },
                colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Watch Now")
            }
        }
    }
}
