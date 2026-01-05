package com.example.androidtviptvapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.example.androidtviptvapp.data.Channel
import com.example.androidtviptvapp.data.Movie

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelCard(
    channel: Channel,
    onClick: (Channel) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 260.dp
) {
    val context = LocalContext.current
    
    // Remember image request to avoid recreation on recomposition
    val imageRequest = remember(channel.logo) {
        ImageRequest.Builder(context)
            .data(channel.logo)
            .apply {
                if (channel.logo.endsWith(".svg", ignoreCase = true)) {
                    decoderFactory(SvgDecoder.Factory())
                }
            }
            .crossfade(200)
            .memoryCacheKey(channel.id)
            .diskCacheKey(channel.id)
            .build()
    }

    Card(
        onClick = { onClick(channel) },
        modifier = modifier
            .width(width)
            .aspectRatio(16f / 9f),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color(0xFFE0E0E0))
            ),
            border = Border(
                border = BorderStroke(1.dp, Color(0xFF333333))
            )
        ),
        colors = CardDefaults.colors(containerColor = Color(0xFF1E1E1E))
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Logo centered - use AsyncImage instead of SubcomposeAsyncImage
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = channel.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    // Placeholder shown during loading - no subcomposition needed
                    placeholder = null,
                    error = null
                )
            }

            // Favorite Icon (Top Right)
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Star,
                contentDescription = "Favorite",
                tint = Color.Gray,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieCard(
    movie: Movie,
    onClick: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Remember image request to avoid recreation
    val imageRequest = remember(movie.thumbnail) {
        ImageRequest.Builder(context)
            .data(movie.thumbnail)
            .crossfade(200)
            .memoryCacheKey(movie.id)
            .diskCacheKey(movie.id)
            .build()
    }

    Column {
        Card(
            onClick = { onClick(movie) },
            modifier = modifier
                .width(160.dp)
                .aspectRatio(2f / 3f),
            scale = CardDefaults.scale(focusedScale = 1.08f),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                )
            ),
            colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = movie.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = null,
                    error = null
                )
                
                // Rating badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "★ ${movie.rating}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFD700)
                    )
                }
            }
        }
        
        // Title below card
        Text(
            text = movie.title,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .padding(top = 8.dp)
                .width(160.dp),
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // Year below title
        Text(
            text = movie.year.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
