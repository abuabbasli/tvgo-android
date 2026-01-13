package com.example.androidtviptvapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.androidtviptvapp.ui.components.ViewMode
import com.example.androidtviptvapp.ui.screens.*

import androidx.compose.material3.Text // fallback
import com.example.androidtviptvapp.data.TvRepository

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val CHANNELS = "channels"
    const val MOVIES = "movies"
    const val SETTINGS = "settings"
    const val PLAYER = "player/{url}"
    const val PLAYER_CHANNEL = "player_channel/{channelId}"
}

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
                onMovieClick = { onPlayUrl(it.videoUrl) }
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
                onMovieClick = { onPlayUrl(it.videoUrl) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen()
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
                PlayerScreen(videoUrl = decodedUrl!!)
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
                        }
                    )
                } else {
                    // Channel not found - show error
                    Text("Channel not found: $channelId")
                }
            }
        }
    }
}
