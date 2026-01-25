package com.example.androidtviptvapp.ui.screens

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.example.androidtviptvapp.data.Channel
import com.example.androidtviptvapp.data.Movie
import com.example.androidtviptvapp.data.TvRepository
import com.example.androidtviptvapp.ui.components.HomeChannelCard
import com.example.androidtviptvapp.ui.components.HeroSection
import com.example.androidtviptvapp.ui.components.MovieCard
import com.example.androidtviptvapp.ui.screens.BabyLockManager

/**
 * HomeScreen with alternating channel and movie category rows
 * Design pattern: Hero → Channel Category → Movie Category → Channel Category → Movie Category...
 *
 * OPTIMIZED: Uses pre-computed data from TvRepository to avoid expensive
 * filtering operations during recomposition.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onChannelClick: (Channel) -> Unit,
    onMovieClick: (Movie) -> Unit
) {
    // Check if baby mode is active
    val isBabyModeActive = BabyLockManager.isBabyModeActive

    // OPTIMIZATION: Trigger re-computation when baby mode changes
    LaunchedEffect(isBabyModeActive) {
        TvRepository.onBabyModeChanged(isBabyModeActive)
    }

    // OPTIMIZATION: Use pre-computed data from TvRepository (computed ONCE after login)
    // This eliminates expensive filtering during every recomposition!
    val isPrecomputeReady by TvRepository.isPrecomputeReady.collectAsState()
    val precomputedChannels = TvRepository.precomputedChannelsByCategory
    val precomputedMovies = TvRepository.precomputedMoviesByCategory

    // Reactive: Read directly from TvRepository state lists (for hero and fallback)
    val movies = TvRepository.movies

    // Featured movie for hero
    val featuredMovie = movies.firstOrNull()

    // Movie category display names
    val movieDisplayNames = mapOf(
        "action" to "Action Movies",
        "scifi" to "Sci-Fi Movies",
        "thriller" to "Thriller Movies",
        "comedy" to "Comedy Movies",
        "drama" to "Drama Movies",
        "horror" to "Horror Movies",
        "animation" to "Animated Movies",
        "family" to "Family Movies"
    )

    // Movie category order for consistent display
    val movieCategoryOrder = if (isBabyModeActive) {
        listOf("comedy", "animation", "family")
    } else {
        listOf("action", "scifi", "thriller", "comedy", "drama", "horror")
    }

    // OPTIMIZATION: Use pre-computed data directly (no filtering here!)
    val channelsByCategory = precomputedChannels.toList()
    val moviesByCategory = movieCategoryOrder.mapNotNull { categoryId ->
        precomputedMovies[categoryId]?.let { movies ->
            Triple(categoryId, movieDisplayNames[categoryId] ?: "$categoryId Movies", movies)
        }
    }

    // Interleave channel categories and movie categories
    val maxPairs = maxOf(channelsByCategory.size, moviesByCategory.size)

    // List state for scroll control
    val listState = androidx.tv.foundation.lazy.list.rememberTvLazyListState()

    // KEY THROTTLING - Smooth one-item-at-a-time scrolling
    var lastNavKeyTime by remember { mutableStateOf(0L) }
    val navKeyThrottleMs = 150L

    // Scroll to top when HomeScreen opens or when data loads
    LaunchedEffect(featuredMovie?.id) {
        if (featuredMovie != null) {
            listState.scrollToItem(0)
        }
    }

    TvLazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                // THROTTLE navigation keys for smooth scrolling
                // Also prevents crash from focus traversal on detached nodes during long press
                try {
                    if (event.type == KeyEventType.KeyDown) {
                        val isNavKey = event.key == Key.DirectionUp ||
                                       event.key == Key.DirectionDown ||
                                       event.key == Key.DirectionLeft ||
                                       event.key == Key.DirectionRight

                        if (isNavKey) {
                            val now = System.currentTimeMillis()
                            if (now - lastNavKeyTime < navKeyThrottleMs) {
                                return@onPreviewKeyEvent true // Block rapid keys
                            }
                            lastNavKeyTime = now
                        }
                    }
                    // Also throttle KeyUp for held keys to prevent rapid focus changes
                    if (event.type == KeyEventType.KeyUp) {
                        val isNavKey = event.key == Key.DirectionUp ||
                                       event.key == Key.DirectionDown ||
                                       event.key == Key.DirectionLeft ||
                                       event.key == Key.DirectionRight
                        if (isNavKey) {
                            val now = System.currentTimeMillis()
                            if (now - lastNavKeyTime < navKeyThrottleMs) {
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Catch any focus traversal exceptions (e.g., unattached node)
                    android.util.Log.w("HomeScreen", "Key event error: ${e.message}")
                    return@onPreviewKeyEvent true
                }
                false
            },
        contentPadding = PaddingValues(bottom = 50.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        // SMOOTH SCROLLING: Keep focused item visible
        pivotOffsets = PivotOffsets(parentFraction = 0.3f, childFraction = 0.0f)
    ) {
        // Hero Section
        if (featuredMovie != null) {
            item(key = "hero") {
                HeroSection(movie = featuredMovie, onPlayClick = onMovieClick)
            }
        }

        // Alternating rows: Channel Category → Movie Category → Channel Category → Movie Category
        for (i in 0 until maxPairs) {
            // Channel row (if available)
            if (i < channelsByCategory.size) {
                val (categoryName, channelsInCategory) = channelsByCategory[i]
                item(key = "channels_$categoryName") {
                    Column {
                        Text(
                            text = "$categoryName Channels",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
                        )
                        TvLazyRow(
                            modifier = Modifier.focusGroup(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 16.dp),
                            pivotOffsets = PivotOffsets(parentFraction = 0.0f, childFraction = 0.0f)
                        ) {
                            items(
                                items = channelsInCategory,
                                key = { it.id }
                            ) { channel ->
                                HomeChannelCard(
                                    channel = channel,
                                    onClick = onChannelClick
                                )
                            }
                        }
                    }
                }
            }

            // Movie row (if available)
            if (i < moviesByCategory.size) {
                val (categoryId, displayName, moviesInCategory) = moviesByCategory[i]
                item(key = "movies_$categoryId") {
                    Column {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
                        )
                        TvLazyRow(
                            modifier = Modifier.focusGroup(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 16.dp),
                            pivotOffsets = PivotOffsets(parentFraction = 0.0f, childFraction = 0.0f)
                        ) {
                            items(
                                items = moviesInCategory,
                                key = { it.id }
                            ) { movie ->
                                MovieCard(
                                    movie = movie,
                                    onClick = onMovieClick
                                )
                            }
                        }
                    }
                }
            }
        }

        // If we have more channels than movies, show remaining channel categories
        if (channelsByCategory.size > moviesByCategory.size) {
            for (i in moviesByCategory.size until channelsByCategory.size) {
                val (categoryName, channelsInCategory) = channelsByCategory[i]
                item(key = "channels_extra_$categoryName") {
                    Column {
                        Text(
                            text = "$categoryName Channels",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
                        )
                        TvLazyRow(
                            modifier = Modifier.focusGroup(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 16.dp),
                            pivotOffsets = PivotOffsets(parentFraction = 0.0f, childFraction = 0.0f)
                        ) {
                            items(
                                items = channelsInCategory,
                                key = { it.id }
                            ) { channel ->
                                HomeChannelCard(
                                    channel = channel,
                                    onClick = onChannelClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
