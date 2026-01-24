package com.example.androidtviptvapp.ui.screens

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
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
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onChannelClick: (Channel) -> Unit,
    onMovieClick: (Movie) -> Unit
) {
    // Check if baby mode is active
    val isBabyModeActive = BabyLockManager.isBabyModeActive

    // Channel categories mapping: Azerbaijani name -> English display name
    // When baby mode is active, only show Kids category
    val channelCategoryMapping = if (isBabyModeActive) {
        mapOf("Uşaq" to "Kids")
    } else {
        mapOf(
            "Uşaq" to "Kids",
            "İdman" to "Sports",
            "İnformasiya" to "News",
            "Əyləncə" to "Entertainment",
            "Film" to "Movies"
        )
    }

    // Movie categories to show - when baby mode is active, only show family-friendly content
    val movieCategoryOrder = if (isBabyModeActive) {
        listOf("comedy", "animation", "family") // Family-friendly categories
    } else {
        listOf("action", "scifi", "thriller", "comedy", "drama", "horror")
    }

    // Reactive: Read directly from TvRepository state lists
    val channels = TvRepository.channels
    val movies = TvRepository.movies

    // Featured movie for hero
    val featuredMovie = movies.firstOrNull()

    // Compute channels by category - directly compute when channels change or baby mode changes
    val channelsByCategory = remember(channels.size, isBabyModeActive) {
        android.util.Log.d("HomeScreen", "Computing channelsByCategory, channels.size=${channels.size}, babyMode=$isBabyModeActive")
        channelCategoryMapping.entries.mapNotNull { (azName, displayName) ->
            val channelsInCategory = channels.filter {
                it.category.equals(azName, ignoreCase = true)
            }.take(15)
            if (channelsInCategory.isNotEmpty()) displayName to channelsInCategory else null
        }.also {
            android.util.Log.d("HomeScreen", "channelsByCategory result: ${it.size} categories")
        }
    }

    // Compute movies by category - directly compute each time movies change or baby mode changes
    val moviesByCategory = remember(movies.size, isBabyModeActive) {
        android.util.Log.d("HomeScreen", "Computing moviesByCategory, movies.size=${movies.size}, babyMode=$isBabyModeActive")
        if (movies.isEmpty()) {
            android.util.Log.d("HomeScreen", "Movies list is empty")
            emptyList()
        } else {
            val uniqueCategories = movies.map { it.category }.distinct()
            android.util.Log.d("HomeScreen", "Movie categories in data: $uniqueCategories")
            movieCategoryOrder.mapNotNull { categoryId ->
                val moviesInCategory = TvRepository.getMoviesByCategory(categoryId).take(15)
                android.util.Log.d("HomeScreen", "Category '$categoryId' has ${moviesInCategory.size} movies")
                val displayName = when(categoryId) {
                    "action" -> "Action Movies"
                    "scifi" -> "Sci-Fi Movies"
                    "thriller" -> "Thriller Movies"
                    "comedy" -> "Comedy Movies"
                    "drama" -> "Drama Movies"
                    "horror" -> "Horror Movies"
                    "animation" -> "Animated Movies"
                    "family" -> "Family Movies"
                    else -> "${categoryId.replaceFirstChar { it.uppercase() }} Movies"
                }
                if (moviesInCategory.isNotEmpty()) Triple(categoryId, displayName, moviesInCategory) else null
            }.also {
                android.util.Log.d("HomeScreen", "moviesByCategory result: ${it.size} categories")
            }
        }
    }

    // Interleave channel categories and movie categories
    val maxPairs = maxOf(channelsByCategory.size, moviesByCategory.size)

    android.util.Log.d("HomeScreen", "Rendering: ${channelsByCategory.size} channel cats, ${moviesByCategory.size} movie cats, maxPairs=$maxPairs")

    // List state for scroll control
    val listState = androidx.tv.foundation.lazy.list.rememberTvLazyListState()

    // Scroll to top when HomeScreen opens or when data loads
    LaunchedEffect(featuredMovie?.id) {
        if (featuredMovie != null) {
            listState.scrollToItem(0)
        }
    }

    TvLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 50.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
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
                            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 16.dp)
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
                            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 16.dp)
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
                            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 16.dp)
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
