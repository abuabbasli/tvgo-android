package com.example.androidtviptvapp.ui.screens

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
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.androidtviptvapp.data.TvRepository
import com.example.androidtviptvapp.data.api.ApiClient

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Settings Screen")
    }
}

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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Movie not found", color = Color.White)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background image with gradient overlay
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(movie.backdrop.ifEmpty { movie.thumbnail })
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient overlay for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.95f),
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp)
        ) {
            // Left side - Movie info
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = movie.year.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    if (movie.runtime > 0) {
                        Text(
                            text = "${movie.runtime} min",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFFD700).copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "★ ${movie.rating}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFFFD700)
                        )
                    }
                }

                if (movie.genre.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        movie.genre.take(3).forEach { genre ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = genre,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = movie.description.ifEmpty { "No description available." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = {
                            if (movie.videoUrl.isNotEmpty()) {
                                onPlayClick(movie.videoUrl)
                            }
                        }
                    ) {
                        Text("▶ Play")
                    }

                    if (movie.trailerUrl.isNotEmpty()) {
                        Button(
                            onClick = { onPlayClick(movie.trailerUrl) }
                        ) {
                            Text("Trailer")
                        }
                    }

                    Button(onClick = onBackClick) {
                        Text("Back")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MessagesScreen() {
    var messages by remember { mutableStateOf<List<com.example.androidtviptvapp.data.api.MessageItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val response = ApiClient.service.getMessages()
            messages = response.items
        } catch (e: Exception) {
            android.util.Log.e("MessagesScreen", "Failed to load messages: ${e.message}")
        }
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading messages...", color = Color.White.copy(alpha = 0.5f))
            }
        } else if (messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No messages", color = Color.White.copy(alpha = 0.5f))
            }
        } else {
            TvLazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Messages",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                items(messages, key = { it.id }) { message ->
                    Surface(
                        onClick = { /* Mark as read */ },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color(0xFF1E1E2E),
                            focusedContainerColor = Color(0xFF2E2E3E)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = message.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                if (!message.isRead) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF3B82F6))
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = message.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GamesScreen(
    onBackToHome: () -> Unit = {}
) {
    var games by remember { mutableStateOf<List<com.example.androidtviptvapp.data.api.GameItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val response = ApiClient.service.getGames()
            games = response.items.filter { it.isActive }
        } catch (e: Exception) {
            android.util.Log.e("GamesScreen", "Failed to load games: ${e.message}")
        }
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading games...", color = Color.White.copy(alpha = 0.5f))
            }
        } else if (games.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No games available", color = Color.White.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBackToHome) {
                        Text("Back to Home")
                    }
                }
            }
        } else {
            TvLazyColumn(
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    Text(
                        text = "Games",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                item {
                    TvLazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(games, key = { it.id }) { game ->
                            Card(
                                onClick = { /* Launch game */ },
                                modifier = Modifier
                                    .width(200.dp)
                                    .aspectRatio(16f / 9f),
                                scale = CardDefaults.scale(focusedScale = 1.05f)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (!game.imageUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = game.imageUrl,
                                            contentDescription = game.name,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0xFF2E2E3E)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = game.name.take(2).uppercase(),
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = Color.White.copy(alpha = 0.5f)
                                            )
                                        }
                                    }

                                    // Game name overlay
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .background(Color.Black.copy(alpha = 0.7f))
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = game.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
