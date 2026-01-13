package com.example.androidtviptvapp.ui.screens

import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.example.androidtviptvapp.data.Movie
import com.example.androidtviptvapp.data.TvRepository

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    movieId: String,
    onPlayClick: (String) -> Unit,  // videoUrl
    onBackClick: () -> Unit
) {
    val movie = remember(movieId) {
        TvRepository.movies.find { it.id == movieId }
    }

    if (movie == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Movie not found", color = Color.White)
        }
        return
    }

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Background backdrop with gradient
        AsyncImage(
            model = movie.backdrop.ifEmpty { movie.thumbnail },
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.7f),
                            Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 48.dp, vertical = 32.dp)
        ) {
            // Back button row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                OutlinedButton(
                    onClick = onBackClick
                ) {
                    Text("← Back", color = Color.White)
                }
            }
            
            // Top section: Poster + Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Poster
                AsyncImage(
                    model = movie.thumbnail,
                    contentDescription = movie.title,
                    modifier = Modifier
                        .width(200.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                // Info column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Title
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // Year, Rating, Runtime row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = movie.year.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        if (movie.rating.isNotEmpty() && movie.rating != "0.0") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⭐", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = movie.rating,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White
                                )
                            }
                        }
                        if (movie.runtime > 0) {
                            Text(
                                text = "${movie.runtime} min",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // Genres
                    if (movie.genre.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            movie.genre.take(4).forEach { genre ->
                                Box(
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            RoundedCornerShape(16.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = genre,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Synopsis
                    Text(
                        text = movie.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action buttons
                    val context = LocalContext.current
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Play button - always visible but handles missing/invalid URLs gracefully
                        val hasValidStreamUrl = movie.videoUrl.isNotEmpty() && 
                            (movie.videoUrl.startsWith("http://") || movie.videoUrl.startsWith("https://"))
                        
                        Button(
                            onClick = { 
                                Log.d("MovieDetailScreen", "Play clicked, videoUrl: ${movie.videoUrl}")
                                when {
                                    movie.videoUrl.isEmpty() -> {
                                        android.widget.Toast.makeText(
                                            context, 
                                            "Stream not available yet", 
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    !hasValidStreamUrl -> {
                                        android.widget.Toast.makeText(
                                            context, 
                                            "Invalid stream URL", 
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    else -> {
                                        try {
                                            onPlayClick(movie.videoUrl)
                                        } catch (e: Exception) {
                                            Log.e("MovieDetailScreen", "Error playing movie", e)
                                            android.widget.Toast.makeText(
                                                context, 
                                                "Unable to play video", 
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = if (hasValidStreamUrl) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    Color.Gray,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("▶  Play Now", fontWeight = FontWeight.Bold)
                        }

                        // Watch Trailer button (if available)
                        if (movie.trailerUrl.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { 
                                    // Open trailer URL in browser/YouTube
                                    try {
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(movie.trailerUrl)
                                        )
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(
                                            context, 
                                            "Cannot open trailer", 
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("🎬  Watch Trailer", color = Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Directors section
            if (movie.directors.isNotEmpty()) {
                Text(
                    text = "Directed by",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = movie.directors.joinToString(", "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Cast section
            if (movie.cast.isNotEmpty()) {
                Text(
                    text = "Cast",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(movie.cast) { actor ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(100.dp)
                        ) {
                            // Placeholder avatar
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(Color.Gray.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = actor.firstOrNull()?.uppercase() ?: "?",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = actor,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
