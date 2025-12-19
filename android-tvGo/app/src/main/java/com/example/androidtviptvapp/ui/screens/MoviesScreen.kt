package com.example.androidtviptvapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.androidtviptvapp.data.Movie
import com.example.androidtviptvapp.data.TvRepository
import com.example.androidtviptvapp.ui.components.CategoryFilter
import com.example.androidtviptvapp.ui.components.HeroSection
import com.example.androidtviptvapp.ui.components.MovieCard

@Composable
fun MoviesScreen(
    onMovieClick: (Movie) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("all") }
    
    // Get movies reactively - this will update when TvRepository.movies changes
    val movies = TvRepository.movies
    
    // Featured movie - react to movie list changes
    val featuredMovie = movies.find { it.category == "action" || it.category == "sci-fi" } ?: movies.firstOrNull()
    
    // Show loading state if no movies yet
    if (movies.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading movies...", color = Color.Gray)
        }
        return
    }

    TvLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp)
    ) {
        // 1. Categories Filter (Top)
        item {
            Box(modifier = Modifier.padding(top = 24.dp, start = 48.dp, bottom = 24.dp)) {
                CategoryFilter(
                    categories = TvRepository.movieCategories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it }
                )
            }
        }

        // 2. Hero Section
        if (featuredMovie != null) {
            item {
                HeroSection(
                    movie = featuredMovie,
                    onPlayClick = onMovieClick
                )
            }
        }

        // 3. Content Rows
        if (selectedCategory == "all") {
            // Show all movies grouped by category
            val categories = movies.map { it.category }.distinct()
            categories.forEach { category ->
                val moviesInCategory = movies.filter { it.category == category }
                if (moviesInCategory.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(top = 32.dp)) {
                            Text(
                                text = "${category.replaceFirstChar { it.uppercase() }} Movies",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(start = 48.dp, bottom = 16.dp)
                            )
                            TvLazyRow(
                                contentPadding = PaddingValues(horizontal = 48.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                items(moviesInCategory) { movie ->
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
        } else {
            // Show filtered movies for specific category
            val filtered = movies.filter { it.category == selectedCategory }
            if (filtered.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 32.dp)) {
                        Text(
                            text = "${selectedCategory.replaceFirstChar { it.uppercase() }} Movies",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 48.dp, bottom = 16.dp)
                        )
                        TvLazyRow(
                            contentPadding = PaddingValues(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            items(filtered) { movie ->
                                MovieCard(
                                    movie = movie,
                                    onClick = onMovieClick
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = "No movies found in this category",
                        modifier = Modifier.padding(48.dp),
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

