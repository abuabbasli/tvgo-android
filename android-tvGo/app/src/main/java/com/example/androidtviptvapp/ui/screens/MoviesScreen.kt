package com.example.androidtviptvapp.ui.screens

import androidx.compose.foundation.focusGroup
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
import com.example.androidtviptvapp.data.Category
import com.example.androidtviptvapp.data.Movie
import com.example.androidtviptvapp.data.TvRepository
import com.example.androidtviptvapp.ui.components.CategoryFilter
import com.example.androidtviptvapp.ui.components.HeroSection
import com.example.androidtviptvapp.ui.components.MovieCard
import com.example.androidtviptvapp.ui.screens.BabyLockManager

@Composable
fun MoviesScreen(
    onMovieClick: (Movie) -> Unit
) {
    // Check if baby mode is active - filter to family-friendly content only
    val isBabyModeActive = BabyLockManager.isBabyModeActive

    // Family-friendly categories for baby mode
    val familyFriendlyCategories = listOf("comedy", "animation", "family", "kids")

    var selectedCategory by remember { mutableStateOf("all") }

    // Update selected category when baby mode changes
    LaunchedEffect(isBabyModeActive) {
        if (isBabyModeActive) {
            selectedCategory = "all" // Show all family-friendly movies
        }
    }

    // Get movies reactively - this will update when TvRepository.movies changes
    val allMovies = TvRepository.movies

    // Filter movies based on baby mode
    val movies = remember(allMovies.size, isBabyModeActive) {
        if (isBabyModeActive) {
            // Baby mode: only family-friendly movies
            allMovies.filter { movie ->
                val cat = movie.category.lowercase()
                val genres = movie.genre.map { it.lowercase() }
                familyFriendlyCategories.any { friendly ->
                    cat.contains(friendly) || genres.any { it.contains(friendly) }
                }
            }
        } else {
            allMovies.toList()
        }
    }

    // Available categories - limited in baby mode
    val availableCategories = remember(isBabyModeActive) {
        if (isBabyModeActive) {
            // Only family-friendly categories
            listOf(
                Category("all", "All"),
                Category("comedy", "Comedy"),
                Category("animation", "Animation"),
                Category("family", "Family")
            )
        } else {
            TvRepository.movieCategories
        }
    }

    // Featured movie - react to movie list changes
    val featuredMovie = if (isBabyModeActive) {
        movies.firstOrNull()
    } else {
        movies.find { it.category == "action" || it.category == "sci-fi" } ?: movies.firstOrNull()
    }

    // Show loading state if no movies yet
    if (movies.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (isBabyModeActive) "No family-friendly movies available" else "Loading movies...",
                color = Color.Gray
            )
        }
        return
    }

    TvLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp)
    ) {
        // 1. Categories Filter (Top) - limited in baby mode
        item {
            Box(modifier = Modifier.padding(top = 24.dp, start = 48.dp, bottom = 24.dp)) {
                CategoryFilter(
                    categories = availableCategories,
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
                                modifier = Modifier.focusGroup(),
                                contentPadding = PaddingValues(horizontal = 48.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
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
                            modifier = Modifier.focusGroup(),
                            contentPadding = PaddingValues(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            items(
                                items = filtered,
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

