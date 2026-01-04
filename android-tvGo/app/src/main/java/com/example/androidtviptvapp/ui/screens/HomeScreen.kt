package com.example.androidtviptvapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.onFocusChanged
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.example.androidtviptvapp.data.Channel
import com.example.androidtviptvapp.data.Movie
import com.example.androidtviptvapp.data.TvRepository
import com.example.androidtviptvapp.data.api.ApiClient
import com.example.androidtviptvapp.ui.components.*

/**
 * Optimized HomeScreen with:
 * - Proper list keys for efficient diffing
 * - Pre-computed category filtering (no filtering during recomposition)
 * - Reduced focus state management overhead
 * - Stable lambdas to prevent recompositions
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onChannelClick: (Channel) -> Unit,
    onMovieClick: (Movie) -> Unit
) {
    // Pre-compute featured content once
    val featuredMovie = remember { derivedStateOf { TvRepository.movies.firstOrNull() } }.value
    val featuredChannels = remember { derivedStateOf { TvRepository.channels.take(10).toList() } }.value
    val trendingMovies = remember { derivedStateOf { TvRepository.movies.take(10).toList() } }.value

    // Pre-compute category movies - done once, not on every recomposition
    val categoriesWithMovies = remember(TvRepository.movies.size) {
        TvRepository.movieCategories.drop(1).mapNotNull { category ->
            val movies = TvRepository.getMoviesByCategory(category.id)
            if (movies.isNotEmpty()) category to movies else null
        }
    }

    // Fetch broadcast messages on first load
    LaunchedEffect(Unit) {
        try {
            val response = ApiClient.service.getBroadcastMessages()
            response.items.forEach { msg ->
                MessagePopupManager.showMessage(
                    PopupMessage(
                        id = msg.id,
                        title = msg.title,
                        body = msg.body,
                        url = msg.url
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeScreen", "Failed to fetch messages: ${e.message}")
        }
    }

    // Show message popup dialog globally
    GlobalMessagePopup()

    TvLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 50.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // Hero Section
        if (featuredMovie != null) {
            item(key = "hero") {
                HeroSection(movie = featuredMovie, onPlayClick = onMovieClick)
            }
        }

        // Featured Channels - with proper key
        item(key = "featured_channels") {
            Column {
                Text(
                    text = "Featured Channels",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 50.dp, bottom = 16.dp)
                )
                TvLazyRow(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(start = 50.dp, end = 50.dp, top = 20.dp, bottom = 20.dp)
                ) {
                    items(
                        items = featuredChannels,
                        key = { it.id }  // Stable key for efficient diffing
                    ) { channel ->
                        OptimizedChannelCard(
                            channel = channel,
                            onClick = onChannelClick
                        )
                    }
                }
            }
        }

        // Trending Movies - with proper key
        item(key = "trending_movies") {
            Column {
                Text(
                    text = "Trending Movies",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 50.dp, bottom = 16.dp)
                )
                TvLazyRow(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(start = 50.dp, end = 50.dp, top = 20.dp, bottom = 20.dp)
                ) {
                    items(
                        items = trendingMovies,
                        key = { it.id }  // Stable key for efficient diffing
                    ) { movie ->
                        OptimizedMovieCard(
                            movie = movie,
                            onClick = onMovieClick
                        )
                    }
                }
            }
        }

        // Movie Categories - pre-computed, not filtered on every recomposition
        items(
            items = categoriesWithMovies,
            key = { it.first.id }  // Category ID as key
        ) { (category, categoryMovies) ->
            Column {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 50.dp, bottom = 16.dp)
                )
                TvLazyRow(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(start = 50.dp, end = 50.dp, top = 20.dp, bottom = 20.dp)
                ) {
                    items(
                        items = categoryMovies,
                        key = { it.id }  // Movie ID as key
                    ) { movie ->
                        OptimizedMovieCard(
                            movie = movie,
                            onClick = onMovieClick
                        )
                    }
                }
            }
        }
    }
}

/**
 * Optimized ChannelCard wrapper that minimizes recomposition
 * Focus state is handled efficiently without zIndex changes
 */
@Composable
private fun OptimizedChannelCard(
    channel: Channel,
    onClick: (Channel) -> Unit
) {
    // Stable modifier - no zIndex changes that cause layout thrashing
    ChannelCard(
        channel = channel,
        onClick = onClick,
        modifier = Modifier
    )
}

/**
 * Optimized MovieCard wrapper that minimizes recomposition
 */
@Composable
private fun OptimizedMovieCard(
    movie: Movie,
    onClick: (Movie) -> Unit
) {
    MovieCard(
        movie = movie,
        onClick = onClick,
        modifier = Modifier
    )
}
