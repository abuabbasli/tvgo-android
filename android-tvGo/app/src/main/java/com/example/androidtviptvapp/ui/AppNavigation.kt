package com.example.androidtviptvapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.androidtviptvapp.ui.components.ViewMode
import com.example.androidtviptvapp.ui.screens.*
import com.example.androidtviptvapp.data.TvRepository
import androidx.tv.material3.Text
import androidx.tv.material3.ExperimentalTvMaterial3Api

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val CHANNELS = "channels"
    const val MOVIES = "movies"
    const val MOVIE_DETAIL = "movie_detail/{movieId}"
    const val MOVIE_PLAYER = "movie_player/{movieId}"
    const val MESSAGES = "messages"
    const val GAMES = "games"
    const val SETTINGS = "settings"
    const val PLAYER = "player/{url}"
    const val PLAYER_CHANNEL = "player_channel/{channelId}"
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppNavigation(
    navController: NavHostController,
    channelViewMode: ViewMode = ViewMode.GRID,
    onPlayUrl: (String) -> Unit,
    onPlayChannel: (String) -> Unit
) {
    // DEV MODE: Skip login, go straight to home
    val startDest = Routes.HOME // if (TvRepository.isAuthenticated) Routes.HOME else Routes.LOGIN
    
    NavHost(navController = navController, startDestination = startDest) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onChannelClick = { onPlayChannel(it.id) },
                onMovieClick = { movie ->
                    navController.navigate("movie_detail/${movie.id}")
                }
            )
        }
        composable(Routes.CHANNELS) { backStackEntry ->
            // Use collectAsState to reactively observe the result
            val returnedChannelId by backStackEntry.savedStateHandle
                .getStateFlow<String?>("channelId", null)
                .collectAsState()
            
            android.util.Log.d("AppNavigation", "ChannelsScreen received channelId: $returnedChannelId")
            
            ChannelsScreen(
                viewMode = channelViewMode,
                initialChannelId = returnedChannelId,
                onChannelClick = { onPlayChannel(it.id) }
            )
        }
        composable(Routes.MOVIES) {
            MoviesScreen(
                onMovieClick = { movie ->
                    navController.navigate("movie_detail/${movie.id}")
                }
            )
        }
        composable(Routes.MOVIE_DETAIL) { backStackEntry ->
            val movieId = backStackEntry.arguments?.getString("movieId") ?: ""
            MovieDetailScreen(
                movieId = movieId,
                onPlayClick = { _ ->
                    // Navigate to MoviePlayerScreen instead of generic player
                    navController.navigate("movie_player/$movieId")
                },
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Routes.MOVIE_PLAYER) { backStackEntry ->
            val movieId = backStackEntry.arguments?.getString("movieId") ?: ""
            val movie = TvRepository.movies.find { it.id == movieId }
            
            if (movie != null && movie.videoUrl.isNotEmpty()) {
                // Get saved resume position
                val resumePosition = TvRepository.getMoviePosition(movieId)
                
                MoviePlayerScreen(
                    movieId = movieId,
                    videoUrl = movie.videoUrl,
                    resumePosition = resumePosition,
                    onBack = { navController.popBackStack() }
                )
            } else {
                Text("Movie not available: $movieId")
            }
        }
        composable(Routes.SETTINGS) {
            SettingsScreen()
        }
        composable(Routes.MESSAGES) {
            MessagesScreen()
        }
        composable(Routes.GAMES) {
            GamesScreen(
                onBackToHome = { navController.popBackStack() }
            )
        }
        composable(Routes.PLAYER) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url")
            var decodedUrl: String? = null
            
            if (url != null) {
                try {
                    val decodedBytes = android.util.Base64.decode(url, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                    decodedUrl = String(decodedBytes)
                } catch (e: Exception) {
                    android.util.Log.e("AppNavigation", "Error decoding URL", e)
                }
            }
            
            if (decodedUrl != null) {
                PlayerScreen(
                    videoUrl = decodedUrl!!,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Routes.PLAYER_CHANNEL) { backStackEntry ->
            val channelId = backStackEntry.arguments?.getString("channelId")
            android.util.Log.d("AppNavigation", "Looking for channelId: $channelId")
            android.util.Log.d("AppNavigation", "Total channels in repo: ${com.example.androidtviptvapp.data.TvRepository.channels.size}")
            
            if (channelId != null) {
                val channel = com.example.androidtviptvapp.data.TvRepository.channels.find { it.id == channelId }
                android.util.Log.d("AppNavigation", "Found channel: ${channel?.name}, streamUrl: ${channel?.streamUrl}")
                
                if (channel != null) {
                    PlayerScreen(
                        videoUrl = channel.streamUrl, 
                        channelId = channelId,
                        onChannelChanged = { newChannelId ->
                            // Update previous back stack entry (ChannelsScreen) with new selection
                            navController.previousBackStackEntry?.savedStateHandle?.set("channelId", newChannelId)
                        },
                        onBack = { navController.popBackStack() }
                    )
                } else {
                    // Channel not found - show error
                    Text("Channel not found: $channelId")
                }
            }
        }
    }
}
