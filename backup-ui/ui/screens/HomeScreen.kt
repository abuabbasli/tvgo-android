package com.example.androidtviptvapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
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
import com.example.androidtviptvapp.ui.components.ChannelCard
import com.example.androidtviptvapp.ui.components.HeroSection
import com.example.androidtviptvapp.ui.components.MovieCard

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onChannelClick: (Channel) -> Unit,
    onMovieClick: (Movie) -> Unit
) {
    val featuredMovie = TvRepository.movies.firstOrNull()

    TvLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 50.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // Hero Section
        if (featuredMovie != null) {
            item {
                HeroSection(movie = featuredMovie, onPlayClick = onMovieClick)
            }
        }

        // Featured Channels
        item {
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
                    items(TvRepository.channels.take(10)) { channel ->
                        var isFocused by remember { mutableStateOf(false) }
                        ChannelCard(
                            channel = channel, 
                            onClick = onChannelClick,
                            modifier = Modifier
                                .onFocusChanged { isFocused = it.isFocused }
                                .zIndex(if (isFocused) 1f else 0f)
                        )
                    }
                }
            }
        }

        // Trending Movies
        item {
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
                    items(TvRepository.movies.take(10)) { movie ->
                        var isFocused by remember { mutableStateOf(false) }
                        MovieCard(
                            movie = movie, 
                            onClick = onMovieClick,
                            modifier = Modifier
                                .onFocusChanged { isFocused = it.isFocused }
                                .zIndex(if (isFocused) 1f else 0f)
                        )
                    }
                }
            }
        }
        
        // Movie Categories
         items(TvRepository.movieCategories.drop(1)) { category ->
             val categoryMovies = TvRepository.movies.filter { it.category == category.id }
             if (categoryMovies.isNotEmpty()) {
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
                        items(categoryMovies) { movie ->
                             var isFocused by remember { mutableStateOf(false) }
                             MovieCard(
                                 movie = movie, 
                                 onClick = onMovieClick,
                                 modifier = Modifier
                                     .onFocusChanged { isFocused = it.isFocused }
                                     .zIndex(if (isFocused) 1f else 0f)
                             )
                        }
                    }
                 }
             }
         }
    }
}
