package com.example.androidtviptvapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.example.androidtviptvapp.data.Channel
import com.example.androidtviptvapp.data.Movie

/**
 * ChannelCard with logos - optimized for smooth scrolling.
 *
 * Uses simple AsyncImage with memory caching and no crossfade animation
 * to minimize GPU work during scroll.
 *
 * @param showName If true, displays channel name below the card (for Home screen design)
 */
@Composable
fun ChannelCard(
    channel: Channel,
    onClick: (Channel) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 140.dp,
    showName: Boolean = false
) {
    val context = LocalContext.current

    // Stable image request with memory-first caching, NO crossfade
    // Use logo URL as cache key for consistent caching across sessions
    val imageRequest = remember(channel.logo) {
        if (channel.logo.isNotBlank() && channel.logo.startsWith("http")) {
            ImageRequest.Builder(context)
                .data(channel.logo)
                .memoryCacheKey(channel.logo) // Use URL as cache key
                .diskCacheKey(channel.logo)   // Use URL as disk cache key
                .crossfade(false) // No animation - faster
                .build()
        } else null
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            onClick = { onClick(channel) },
            modifier = modifier
                .width(width)
                .aspectRatio(16f / 9f),
            scale = CardDefaults.scale(focusedScale = 1.08f),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(3.dp, Color.White)
                ),
                border = Border(
                    border = BorderStroke(1.dp, Color(0xFF444444))
                )
            ),
            colors = CardDefaults.colors(containerColor = Color(0xFF1E1E1E))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (imageRequest != null) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = channel.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    // Fallback: channel initials
                    Text(
                        text = channel.name.take(2).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }

                // Order number badge in top-left corner
                if (channel.order > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = channel.order.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Channel name below card (when showName is true)
        if (showName) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.width(width),
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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

    // Check if thumbnail is valid before creating request
    val hasValidThumbnail = remember(movie.thumbnail) {
        !movie.thumbnail.isNullOrBlank() && movie.thumbnail.startsWith("http")
    }

    // Remember image request to avoid recreation - only create if valid URL
    // Use thumbnail URL as cache key for consistent caching
    val imageRequest = remember(movie.thumbnail) {
        if (hasValidThumbnail) {
            ImageRequest.Builder(context)
                .data(movie.thumbnail)
                .memoryCacheKey(movie.thumbnail!!) // Use URL as cache key
                .diskCacheKey(movie.thumbnail!!)   // Use URL as disk cache key
                .crossfade(150)
                .build()
        } else null
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
            colors = CardDefaults.colors(containerColor = Color(0xFF333333))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (imageRequest != null) {
                    SubcomposeAsyncImage(
                        model = imageRequest,
                        contentDescription = movie.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    ) {
                        when (val state = painter.state) {
                            is AsyncImagePainter.State.Loading -> {
                                // Show gradient placeholder while loading
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color(0xFF3A3A3A), Color(0xFF2A2A2A))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = movie.title.take(1).uppercase(),
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = Color.Gray
                                    )
                                }
                            }
                            is AsyncImagePainter.State.Error -> {
                                // Show gradient with movie initials on error
                                MoviePlaceholder(movie.title)
                            }
                            else -> {
                                SubcomposeAsyncImageContent()
                            }
                        }
                    }
                } else {
                    // No valid thumbnail URL - show placeholder
                    MoviePlaceholder(movie.title)
                }
                
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

/**
 * Placeholder for movies without valid thumbnail URLs
 */
@Composable
private fun MoviePlaceholder(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF4A90A4), Color(0xFF2D5A6A))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title.take(2).uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
    }
}

/**
 * HomeChannelCard - Special channel card design for HomeScreen
 *
 * Features:
 * - Light/white background to match prototype
 * - Wider card (200dp) with 16:9 aspect ratio
 * - No order badge
 * - Channel name displayed below card
 */
@Composable
fun HomeChannelCard(
    channel: Channel,
    onClick: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cardWidth = 200.dp

    // Stable image request with memory-first caching
    val imageRequest = remember(channel.logo) {
        if (channel.logo.isNotBlank() && channel.logo.startsWith("http")) {
            ImageRequest.Builder(context)
                .data(channel.logo)
                .memoryCacheKey(channel.logo)
                .diskCacheKey(channel.logo)
                .crossfade(false)
                .build()
        } else null
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            onClick = { onClick(channel) },
            modifier = modifier
                .width(cardWidth)
                .aspectRatio(16f / 9f),
            scale = CardDefaults.scale(focusedScale = 1.05f),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(3.dp, Color.White)
                ),
                border = Border(
                    border = BorderStroke(1.dp, Color(0xFF333333))
                )
            ),
            colors = CardDefaults.colors(containerColor = Color(0xFFF5F5F5)) // Light background
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (imageRequest != null) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = channel.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    // Fallback: channel initials on light background
                    Text(
                        text = channel.name.take(2).uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color(0xFF333333)
                    )
                }
            }
        }

        // Channel name below card
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.width(cardWidth),
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
