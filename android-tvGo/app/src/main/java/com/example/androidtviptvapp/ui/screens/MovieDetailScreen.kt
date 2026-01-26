package com.example.androidtviptvapp.ui.screens

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.example.androidtviptvapp.data.Movie
import com.example.androidtviptvapp.data.TvRepository

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    movieId: String,
    onPlayClick: (String) -> Unit,
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

    Box(modifier = Modifier.fillMaxSize()) {
        // Background backdrop image
        AsyncImage(
            model = movie.backdrop.ifEmpty { movie.thumbnail },
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient overlay - darker on left for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.6f),
                            Color.Black.copy(alpha = 0.3f)
                        )
                    )
                )
        )

        // Bottom gradient for cast section
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        ),
                        startY = 400f
                    )
                )
        )

        // Main content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 48.dp, top = 80.dp, bottom = 32.dp)
        ) {
            // Left side - Movie info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Top
            ) {
                // Title
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Year, Rating, Runtime row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Year
                    Text(
                        text = movie.year.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE57373) // Coral/red color like in screenshot
                    )

                    // Rating with star
                    if (movie.rating.isNotEmpty() && movie.rating != "0.0") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "★",
                                fontSize = 18.sp,
                                color = Color(0xFFFFD700) // Gold star
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = movie.rating,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }

                    // Runtime with clock icon
                    if (movie.runtime > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⏱",
                                fontSize = 16.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${movie.runtime} min",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Genre badges - red/coral style like screenshot
                if (movie.genre.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        movie.genre.take(4).forEach { genre ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        Color(0xFFE57373), // Coral red background
                                        RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = genre,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Synopsis/Description
                Text(
                    text = movie.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 24.sp,
                    modifier = Modifier.widthIn(max = 550.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Play button - large white button like screenshot
                val context = LocalContext.current
                val hasValidStreamUrl = movie.videoUrl.isNotEmpty() &&
                    (movie.videoUrl.startsWith("http://") || movie.videoUrl.startsWith("https://"))

                Surface(
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
                    modifier = Modifier
                        .height(56.dp)
                        .widthIn(min = 160.dp),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(28.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White,
                        focusedContainerColor = Color.White.copy(alpha = 0.9f),
                        contentColor = Color.Black
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(3.dp, Color.White),
                            shape = RoundedCornerShape(28.dp)
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "▶",
                            fontSize = 20.sp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Play",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Directed by section
                if (movie.directors.isNotEmpty()) {
                    Text(
                        text = "Directed by",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = movie.directors.joinToString(", "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Cast section
                if (movie.cast.isNotEmpty()) {
                    Text(
                        text = "Cast",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    TvLazyRow(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(end = 32.dp)
                    ) {
                        items(movie.cast.take(8)) { actor ->
                            CastMemberItem(name = actor)
                        }
                    }
                }
            }

            // Right side - Movie poster
            Column(
                modifier = Modifier
                    .padding(start = 48.dp)
                    .width(280.dp),
                horizontalAlignment = Alignment.End
            ) {
                Spacer(modifier = Modifier.height(80.dp))

                // Movie poster with shadow
                Box(
                    modifier = Modifier
                        .shadow(
                            elevation = 24.dp,
                            shape = RoundedCornerShape(16.dp),
                            spotColor = Color.Black.copy(alpha = 0.5f)
                        )
                ) {
                    AsyncImage(
                        model = movie.thumbnail,
                        contentDescription = movie.title,
                        modifier = Modifier
                            .width(240.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CastMemberItem(name: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        // Circle avatar with initial
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFF3A3A3A)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "?",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Actor name
        Text(
            text = name.split(" ").firstOrNull() ?: name,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Last name (if exists)
        val nameParts = name.split(" ")
        if (nameParts.size > 1) {
            Text(
                text = nameParts.drop(1).joinToString(" "),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
